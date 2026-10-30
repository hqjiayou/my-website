"""答案质量的确定性评分。只看本轮文本和来源 id，不调用模型。"""

from __future__ import annotations

from collections.abc import Mapping, Sequence

# 产品 harness 要求不编造站点事实。这里收的是回答里会真的出现的拒答措辞，
# 不是「相关信息」这类正常作答也会带的片段。
# 2026-10-27 真跑分（gemini-3.5-flash）实锤补充：模型实际拒答措辞
# （未查到／暂无相关／没有相关的…／not available）此前不在表内，
# 4 题实质正确拒答全被记 refusal_correct=False。新增条目以实测原文为准，
# 仍保持具体性（如只收「暂无法提供」，不收正常作答也会出现的「无法提供」）。
# 2026-10-29 A/B 补验 Arm B 原文实锤补充两条：unans-orbit 答"I could not find
# information about the Zephyr satellite's orbital period in the site's
# documents."（"unable to find"不命中）；unans-snow 答「在站内知识库中未找到
# 关于"人工降雪机夜间票价"的相关信息。」（「未找到相关」因中间隔了宾语不命中，
# 补裸条目「未找到」，与既有裸条目「未查到」为先例一致）。
REFUSAL_CUES: tuple[str, ...] = (
    "无法确认",
    "無法確認",
    "没有相关信息",
    "没有相关资讯",
    "沒有相關資訊",
    "没有相关资料",
    "沒有相關資料",
    "没有相关的",
    "沒有相關的",
    "未查到",
    "未找到",
    "未找到相关",
    "未找到相關",
    "暂无法提供",
    "暫無法提供",
    "暂无相关",
    "暫無相關",
    "わかりません",
    "分かりません",
    "確認できません",
    "确认できません",
    "情報はありません",
    "情報がありません",
    "cannot confirm",
    "can't confirm",
    "no relevant information",
    "no information available",
    "not available",
    "unable to find",
    "could not find",
    "i don't know",
    "i do not know",
)

COVERAGE_PASS_AT = 0.5

PASS_RULE = (
    "可答题 pass：retrieval_hit 且 keyword_coverage ≥ 0.5；"
    "不可答题 pass：refusal_correct。"
)

METRIC_NAMES: tuple[str, ...] = (
    "retrieval_hit",
    "citation_precision",
    "keyword_coverage",
    "refusal_correct",
    "passed",
)


def retrieval_hit(expected_sources: Sequence[str], observed_sources: Sequence[str]) -> bool | None:
    """期望来源是否都出现在本轮 citations 与检索命中的来源并集里。

    没有期望来源时不记分（不可答题走 refusal_correct）。
    """
    if not expected_sources:
        return None
    observed = set(observed_sources)
    return all(source in observed for source in expected_sources)


def citation_precision(
    expected_sources: Sequence[str], citation_source_ids: Sequence[str] | None
) -> float | None:
    """citations 非空时，去重后的来源里期望来源占多少。没有 citations 不记分。"""
    if not citation_source_ids:
        return None
    unique: list[str] = []
    for source_id in citation_source_ids:
        if source_id not in unique:
            unique.append(source_id)
    if not unique:
        return None
    expected = set(expected_sources)
    return sum(1 for source_id in unique if source_id in expected) / len(unique)


def keyword_coverage(expected_keywords: Sequence[str], answer: str) -> float | None:
    """期望关键词在回答中的子串命中比例，大小写不敏感。没有关键词时不记分。"""
    if not expected_keywords:
        return None
    folded = answer.casefold()
    matched = sum(1 for keyword in expected_keywords if keyword.casefold() in folded)
    return matched / len(expected_keywords)


def has_refusal_cue(answer: str) -> bool:
    folded = answer.casefold()
    return any(cue.casefold() in folded for cue in REFUSAL_CUES)


def refusal_correct(
    *,
    category: str,
    answer: str,
    citation_source_ids: Sequence[str] | None,
) -> bool | None:
    """仅不可答题：回答带拒答线索，且本轮没有 citations。"""
    if category != "unanswerable":
        return None
    return has_refusal_cue(answer) and not citation_source_ids


def case_passed(
    category: str,
    *,
    retrieval_hit: bool | None,
    keyword_coverage: float | None,
    refusal_correct: bool | None,
) -> bool:
    if category == "unanswerable":
        return refusal_correct is True
    if retrieval_hit is None or keyword_coverage is None:
        return False
    return bool(retrieval_hit) and keyword_coverage >= COVERAGE_PASS_AT


def score_case(
    *,
    category: str,
    expected_sources: Sequence[str],
    expected_keywords: Sequence[str],
    observed_sources: Sequence[str],
    citation_source_ids: Sequence[str] | None,
    answer: str,
) -> dict[str, object]:
    hit = retrieval_hit(expected_sources, observed_sources)
    precision = citation_precision(expected_sources, citation_source_ids)
    coverage = keyword_coverage(expected_keywords, answer)
    refusal = refusal_correct(
        category=category, answer=answer, citation_source_ids=citation_source_ids
    )
    return {
        "retrieval_hit": hit,
        "citation_precision": precision,
        "keyword_coverage": coverage,
        "refusal_correct": refusal,
        "passed": case_passed(
            category,
            retrieval_hit=hit,
            keyword_coverage=coverage,
            refusal_correct=refusal,
        ),
    }


def _as_float(value: object) -> float | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return float(value)
    return None


def _mean(rows: Sequence[Mapping[str, object]], name: str) -> float | None:
    values = [number for row in rows if (number := _as_float(row.get(name))) is not None]
    if not values:
        return None
    return round(sum(values) / len(values), 6)


def _block(rows: Sequence[Mapping[str, object]]) -> dict[str, object]:
    block: dict[str, object] = {"count": len(rows)}
    for name in METRIC_NAMES:
        block[name] = _mean(rows, name)
    return block


def aggregate(rows: Sequence[Mapping[str, object]]) -> dict[str, object]:
    """按 category 与总体取均值。值为 None 的格子不进均值。"""
    grouped: dict[str, list[Mapping[str, object]]] = {}
    for row in rows:
        category = str(row.get("category") or "")
        grouped.setdefault(category, []).append(row)
    return {
        "overall": _block(rows),
        "byCategory": {category: _block(grouped[category]) for category in sorted(grouped)},
    }
