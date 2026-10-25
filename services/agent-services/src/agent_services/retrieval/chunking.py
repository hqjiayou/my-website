"""KB 文档分块：标题硬边界，其下按段落、行、句界滑窗切分。"""

import re

# ATX 标题（行首 1–6 个 #，后接空白）。无标题文本不走这条路径。
_HEADING_RE = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
# 句末符，以及紧随的引号 / 空白。只在窗口后半段采用，避免切出过短的块。
_SENTENCE_END_RE = re.compile(r"""[。．.！!？?]["'“”‘’「」『』\s]*""")


def split_text(content: str, *, max_chars: int = 1000, overlap: int = 100) -> list[str]:
    text = content.strip()
    if not text:
        return []
    if max_chars <= 0:
        raise ValueError("max_chars must be positive")
    if overlap >= max_chars:
        raise ValueError("overlap must be smaller than max_chars")

    chunks: list[str] = []
    for path, body in _sections(text):
        # 前缀占预算：正文容量先扣掉「§ 路径」行，避免块长超过 max_chars。
        prefix, limit = _prefix_budget(path, max_chars)
        paragraphs = [paragraph.strip() for paragraph in body.split("\n\n") if paragraph.strip()]
        for piece in _pack_paragraphs(paragraphs, limit=limit, overlap=overlap):
            chunks.append(f"{prefix}{piece}")
    return chunks


def _sections(text: str) -> list[tuple[str, str]]:
    """按 ATX 标题行切开。无标题时原样返回，保证纯段落文本不被重排。"""
    lines = text.split("\n")
    if not any(_HEADING_RE.match(line) for line in lines):
        return [("", text)]

    sections: list[tuple[str, str]] = []
    stack: list[tuple[int, str]] = []
    current: list[str] = []
    path = ""

    def flush() -> None:
        body = "\n".join(current).strip()
        current.clear()
        if body:
            sections.append((path, body))

    for line in lines:
        match = _HEADING_RE.match(line)
        if match is None:
            current.append(line)
            continue
        # 标题是硬边界：先收起上一节，再从该标题行开始新节。
        flush()
        level = len(match.group(1))
        title = match.group(2).strip()
        while stack and stack[-1][0] >= level:
            stack.pop()
        stack.append((level, title))
        path = " > ".join(item[1] for item in stack)
        current.append(line)
    flush()
    return sections


def _prefix_budget(path: str, max_chars: int) -> tuple[str, int]:
    if not path:
        return "", max_chars
    prefix = f"§ {path}\n"
    limit = max_chars - len(prefix)
    if limit > 0:
        return prefix, limit
    # 前缀自身已占满窗口时仍留出至少 1 个正文字，保证滑窗能前进且块长不超预算。
    if max_chars <= 1:
        return "", max_chars
    prefix = prefix[: max_chars - 1]
    return prefix, max_chars - len(prefix)


def _pack_paragraphs(paragraphs: list[str], *, limit: int, overlap: int) -> list[str]:
    chunks: list[str] = []
    buffer = ""
    for paragraph in paragraphs:
        candidate = f"{buffer}\n\n{paragraph}" if buffer else paragraph
        if len(candidate) <= limit:
            buffer = candidate
            continue
        if buffer:
            chunks.append(buffer)
            buffer = ""
        if len(paragraph) <= limit:
            buffer = paragraph
            continue
        buffer = _pack_lines(paragraph, limit=limit, overlap=overlap, chunks=chunks)
    if buffer:
        chunks.append(buffer)
    return chunks


def _pack_lines(paragraph: str, *, limit: int, overlap: int, chunks: list[str]) -> str:
    """超长段落按行打包，规则与段落打包相同。返回可与下一段合并的剩余行。"""
    buffer = ""
    for line in paragraph.split("\n"):
        candidate = f"{buffer}\n{line}" if buffer else line
        if len(candidate) <= limit:
            buffer = candidate
            continue
        if buffer:
            chunks.append(buffer)
            buffer = ""
        if len(line) <= limit:
            buffer = line
            continue
        chunks.extend(_slide(line, limit=limit, overlap=overlap))
    return buffer


def _slide(text: str, *, limit: int, overlap: int) -> list[str]:
    if limit <= 0:
        limit = 1
    pieces: list[str] = []
    start = 0
    length = len(text)
    while start < length:
        window_end = start + limit
        if window_end < length:
            cut = _sentence_cut(text[start:window_end])
            if cut is not None and cut > 0:
                next_start = start + cut - overlap
                # 回退 overlap 后若不能严格前进，放弃句界，改走硬切。
                if next_start > start:
                    pieces.append(text[start : start + cut])
                    start = next_start
                    continue
        pieces.append(text[start:window_end])
        next_start = start + limit - overlap
        if next_start <= start:
            next_start = start + limit
            if next_start <= start:
                next_start = start + 1
        start = next_start
    return pieces


def _sentence_cut(window: str) -> int | None:
    half = len(window) // 2
    cut: int | None = None
    for match in _SENTENCE_END_RE.finditer(window):
        if match.start() >= half:
            cut = match.end()
    return cut
