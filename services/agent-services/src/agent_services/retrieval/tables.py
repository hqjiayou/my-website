"""结构化表查询。操作集封闭，不拼 SQL，不计算公式。"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime

MAX_RETURN_ROWS = 50
MAX_SCAN_ROWS = 1000

_DURATION = re.compile(r"^(\d+):([0-5]\d)(?::([0-5]\d))?$")


@dataclass(frozen=True)
class StoredSheet:
    source_id: str
    title: str
    sheet: str
    header_row: int
    headers: list[str]
    rows: list[tuple[int, list[str]]]
    truncated: bool = False


def coerce_measure(text: str) -> float | None:
    """先数字，再 H:MM / H:MM:SS（十进制小时）。都失败返回 None。"""
    number = _parse_number(text)
    if number is not None:
        return number
    return _parse_duration_hours(text)


def run_table_query(
    sheets: list[StoredSheet],
    *,
    op: str,
    source_id: str = "",
    sheet: str = "",
    column: str = "",
    match: str = "",
    value: str = "",
    low: str = "",
    high: str = "",
    aggregate: str = "",
) -> str:
    operation = op.strip().lower()
    if operation == "list":
        return _list_sheets(sheets)
    chosen = _select_sheet(sheets, source_id.strip(), sheet.strip())
    if isinstance(chosen, str):
        return chosen
    if operation == "filter":
        return _filter_sheet(chosen, column, match, value, low, high)
    if operation == "aggregate":
        return _aggregate_sheet(chosen, column, match, value, low, high, aggregate)
    return "Unsupported operation. Use list, filter, or aggregate."


def _list_sheets(sheets: list[StoredSheet]) -> str:
    if not sheets:
        return "No structured table data is indexed for this site."
    lines = ["Structured tables:"]
    for item in sheets:
        columns = ", ".join(header for header in item.headers if header) or "(none)"
        note = " truncated" if item.truncated else ""
        lines.append(
            f"- {item.title} | {item.sheet} | source: {item.source_id} | columns: {columns}{note}"
        )
    return "\n".join(lines)


def _select_sheet(sheets: list[StoredSheet], source_id: str, sheet: str) -> StoredSheet | str:
    matches = [
        item
        for item in sheets
        if (not source_id or item.source_id == source_id) and (not sheet or item.sheet == sheet)
    ]
    if not matches:
        return "No structured table data is available for that document."
    if len(matches) > 1:
        names = ", ".join(f"{item.title}/{item.sheet}" for item in matches)
        return f"Multiple tables match. Name the document source and sheet. Matches: {names}"
    return matches[0]


def _filter_sheet(item: StoredSheet, column: str, match: str, value: str, low: str, high: str) -> str:
    if len(item.rows) > MAX_SCAN_ROWS or item.truncated:
        return _over_scan(item)
    index = _column_index(item, column)
    if isinstance(index, str):
        return index
    matched = _matching_rows(item, index, match, value, low, high)
    if isinstance(matched, str):
        return matched
    shown = matched[:MAX_RETURN_ROWS]
    span = _row_span(shown)
    lines = [
        f"Document: {item.title} (source: {item.source_id})",
        f"Sheet: {item.sheet}",
        f"Rows: {span}",
        f"Matched: {len(matched)}",
    ]
    if len(matched) > MAX_RETURN_ROWS:
        lines.append(
            f"Returned {MAX_RETURN_ROWS} of {len(matched)} rows. The result is truncated at {MAX_RETURN_ROWS}."
        )
    header = " | ".join(item.headers)
    lines.append(header)
    for row_number, cells in shown:
        padded = list(cells) + [""] * (len(item.headers) - len(cells))
        lines.append(f"{row_number}: " + " | ".join(padded[: len(item.headers)]))
    return "\n".join(lines)


def _aggregate_sheet(
    item: StoredSheet,
    column: str,
    match: str,
    value: str,
    low: str,
    high: str,
    aggregate: str,
) -> str:
    if len(item.rows) > MAX_SCAN_ROWS or item.truncated:
        return _over_scan(item)
    kind = aggregate.strip().lower()
    if kind not in {"count", "sum", "avg", "min", "max"}:
        return "Unsupported aggregate. Use count, sum, avg, min, or max."
    index: int | None = None
    if kind != "count" or column.strip():
        found = _column_index(item, column)
        if isinstance(found, str):
            return found
        index = found
    matched = item.rows
    if match.strip():
        if index is None:
            return "count with a filter needs a column."
        filtered = _matching_rows(item, index, match, value, low, high)
        if isinstance(filtered, str):
            return filtered
        matched = filtered
    if kind == "count":
        span = _row_span(matched)
        return "\n".join(
            [
                f"Document: {item.title} (source: {item.source_id})",
                f"Sheet: {item.sheet}",
                f"Rows: {span}",
                f"count: {len(matched)}",
            ]
        )
    assert index is not None
    values: list[float] = []
    for _row_number, cells in matched:
        text = cells[index].strip() if index < len(cells) else ""
        if not text:
            continue
        measured = coerce_measure(text)
        if measured is None:
            return (
                f"Column '{column.strip()}' cannot be aggregated. "
                "Values are neither numbers nor H:MM durations."
            )
        values.append(measured)
    if not values:
        return (
            f"Document: {item.title} (source: {item.source_id})\n"
            f"Sheet: {item.sheet}\n"
            f"Column: {column.strip()}\n"
            f"{kind}: 0\n"
            "Rows: none"
        )
    if kind == "sum":
        result = sum(values)
    elif kind == "avg":
        result = sum(values) / len(values)
    elif kind == "min":
        result = min(values)
    else:
        result = max(values)
    span = _row_span(matched)
    return "\n".join(
        [
            f"Document: {item.title} (source: {item.source_id})",
            f"Sheet: {item.sheet}",
            f"Column: {column.strip()}",
            f"Rows: {span}",
            f"{kind}: {_format_measure(result)}",
        ]
    )


def _matching_rows(
    item: StoredSheet, index: int, match: str, value: str, low: str, high: str
) -> list[tuple[int, list[str]]] | str:
    kind = match.strip().lower()
    if kind not in {"eq", "contains", "range"}:
        return "Unsupported match. Use eq, contains, or range."
    if kind == "range":
        bounds = _range_bounds(low, high)
        if isinstance(bounds, str):
            return bounds
        matched: list[tuple[int, list[str]]] = []
        saw_unparsed = False
        for row in item.rows:
            cells = row[1]
            text = cells[index].strip() if index < len(cells) else ""
            if not text:
                continue
            parsed = _parse_bound_value(text, bounds[0])
            if parsed is None:
                saw_unparsed = True
                continue
            if bounds[1] <= parsed <= bounds[2]:
                matched.append(row)
        if saw_unparsed and not matched:
            return (
                f"Column '{item.headers[index]}' cannot be filtered by range. "
                "Values are neither numbers, H:MM durations, nor dates."
            )
        return matched
    needle = value.strip()
    matched = []
    for row in item.rows:
        cells = row[1]
        text = cells[index].strip() if index < len(cells) else ""
        if kind == "eq" and text == needle:
            matched.append(row)
        elif kind == "contains" and needle.casefold() in text.casefold():
            matched.append(row)
    return matched


def _column_index(item: StoredSheet, column: str) -> int | str:
    name = column.strip()
    if not name:
        return "Column is required."
    try:
        return item.headers.index(name)
    except ValueError:
        listed = ", ".join(header for header in item.headers if header) or "(none)"
        return f"Column '{name}' is not in this sheet. Columns: {listed}"


def _over_scan(item: StoredSheet) -> str:
    return (
        f"Sheet {item.sheet} exceeds the scan limit of {MAX_SCAN_ROWS} rows. "
        "The result was not computed."
    )


def _row_span(rows: list[tuple[int, list[str]]]) -> str:
    if not rows:
        return "none"
    numbers = [row[0] for row in rows]
    return f"{min(numbers)}-{max(numbers)}"


def _format_measure(value: float) -> str:
    if value.is_integer():
        return str(int(value))
    return format(value, "f").rstrip("0").rstrip(".")


def _parse_number(text: str) -> float | None:
    cleaned = text.strip().replace(",", "")
    if not cleaned:
        return None
    try:
        value = float(cleaned)
    except ValueError:
        return None
    if value != value or value in {float("inf"), float("-inf")}:
        return None
    return value


def _parse_duration_hours(text: str) -> float | None:
    match = _DURATION.fullmatch(text.strip())
    if match is None:
        return None
    hours = int(match.group(1))
    minutes = int(match.group(2))
    seconds = int(match.group(3) or 0)
    return hours + minutes / 60 + seconds / 3600


def _parse_date(text: str) -> date | None:
    cleaned = text.strip()
    for fmt in ("%Y-%m-%d", "%Y/%m/%d"):
        try:
            return datetime.strptime(cleaned, fmt).date()
        except ValueError:
            continue
    parts = re.fullmatch(r"(\d{4})[-/](\d{1,2})[-/](\d{1,2})", cleaned)
    if parts is None:
        return None
    try:
        return date(int(parts.group(1)), int(parts.group(2)), int(parts.group(3)))
    except ValueError:
        return None


def _range_bounds(low: str, high: str) -> tuple[str, float | date, float | date] | str:
    low_date = _parse_date(low)
    high_date = _parse_date(high)
    if low_date is not None and high_date is not None:
        return ("date", low_date, high_date)
    low_measure = coerce_measure(low)
    high_measure = coerce_measure(high)
    if low_measure is not None and high_measure is not None:
        return ("measure", low_measure, high_measure)
    return "Range bounds must both be numbers, H:MM durations, or dates."


def _parse_bound_value(text: str, kind: str) -> float | date | None:
    if kind == "date":
        return _parse_date(text)
    return coerce_measure(text)
