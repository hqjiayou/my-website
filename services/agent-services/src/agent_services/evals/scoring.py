"""答案质量的确定性评分。只看本轮文本和来源 id，不调用模型。"""

from __future__ import annotations

import re
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

# citation_precision 的警告阈值（#190 UoW-1）：低于此值记 low_citation_precision
# 警告。警告只提示引用面噪声，不进 pass 判准（口径见 evals/README.md）。
CITATION_PRECISION_WARNING_BELOW = 0.5

PASS_RULE = (
    "可答题 pass：retrieval_hit 且 keyword_coverage ≥ 0.5；"
    "不可答题 pass：refusal_correct；"
    "skill 题集同规则按可用指标判（缺失指标不计、无可用指标不 pass）；"
    "声明 behavior_checks 的题：所声明行为检查须全过（判不出的检查不计）。"
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


# ---------------------------------------------------------------------------
# 行为检查（#190 UoW-1）：题目级 opt-in 的确定性检查，声明了才判、计入 pass。
# 未声明 behavior_checks 的题完全不受影响。
# ---------------------------------------------------------------------------

BEHAVIOR_CHECK_NAMES: tuple[str, ...] = (
    "no_clarifying_question",
    "all_expected_sources_cited",
)

# 澄清提问收尾的模式表（定死，中英日）：只看回答的最后一个句段（tail）。
# 强标记本身即澄清提问/请示口吻，不要求问号结尾；其余标记须与问句形式
# （？/? 结尾）同时出现才算，避免正常作答里提到相关字眼被误伤。
_CLARIFYING_STRONG_MARKERS: tuple[str, ...] = (
    "请问",
    "請問",
    "could you clarify",
    "please clarify",
    "教えてください",
    "教えていただけますか",
    # 日语疑问词尾本身即问句形式，单算成立。
    "ですか",
    "でしょうか",
    "どちら",
)
_CLARIFYING_TAIL_MARKERS: tuple[str, ...] = (
    "您问的",
    "您問的",
    "你问的",
    "指的是",
    "想问的",
    "想問的",
    "需要我",
    "哪个",
    "哪個",
    "哪种",
    "哪種",
    "哪一",
    "do you mean",
    "would you like",
    "are you asking",
    "should i",
    "shall i",
    "let me know if",
)
_CLARIFYING_EITHER_OR = re.compile(r"是[^。！？?\n]{1,24}(还是|還是)")
_EN_QUESTION_LEAD = re.compile(
    r"^(which|what|do|does|would|are|is|should|shall|can|could|may)\b"
)
_SENTENCE_SPLIT = re.compile(r"(?<=[。．.!！?？])|\n+")


def _tail_fragment(answer: str) -> str:
    """回答的最后一个句段（含其结尾标点）；空回答返回空串。"""
    parts = [part.strip() for part in _SENTENCE_SPLIT.split(answer) if part.strip()]
    return parts[-1] if parts else ""


def ends_with_clarifying_question(answer: str) -> bool:
    """回答是否以反问/澄清提问收尾（模式表见上，定死口径）。

    只判尾句段：正文中间出现问句、最后以陈述收尾的，不算反问收尾。
    """
    tail = _tail_fragment(answer)
    if not tail:
        return False
    folded = tail.casefold()
    if any(
        marker in tail for marker in _CLARIFYING_STRONG_MARKERS if not marker.isascii()
    ):
        return True
    if any(
        marker in folded for marker in _CLARIFYING_STRONG_MARKERS if marker.isascii()
    ):
        return True
    if not tail.endswith(("？", "?")):
        return False
    if any(
        marker in tail for marker in _CLARIFYING_TAIL_MARKERS if not marker.isascii()
    ):
        return True
    if any(marker in folded for marker in _CLARIFYING_TAIL_MARKERS if marker.isascii()):
        return True
    if _CLARIFYING_EITHER_OR.search(tail):
        return True
    return bool(_EN_QUESTION_LEAD.search(folded))


def check_no_clarifying_question(answer: str) -> bool | None:
    """回答不以反问/澄清提问收尾。空回答判不出，记 None（不计）。"""
    if not answer.strip():
        return None
    return not ends_with_clarifying_question(answer)


def check_all_expected_sources_cited(
    expected_sources: Sequence[str], citation_source_ids: Sequence[str] | None
) -> bool | None:
    """期望来源是否全部出现在 citations 里。

    与 retrieval_hit 的区别：retrieval_hit 看「检索命中 ∪ citations」的并集，
    本检查只看 citations 本身——集合级题「枚举全部并逐份抽取」的引用面证据。
    没有期望来源时不记分（None）；有期望来源但没 citations 记 False。
    """
    if not expected_sources:
        return None
    if not citation_source_ids:
        return False
    cited = set(citation_source_ids)
    return all(source in cited for source in expected_sources)


def evaluate_behavior_checks(
    checks: Sequence[str],
    *,
    answer: str,
    expected_sources: Sequence[str],
    citation_source_ids: Sequence[str] | None,
) -> dict[str, bool | None]:
    """逐项计算题目声明的行为检查；未知检查名抛 ValueError（题集错误要响）。"""
    results: dict[str, bool | None] = {}
    for name in checks:
        if name == "no_clarifying_question":
            results[name] = check_no_clarifying_question(answer)
        elif name == "all_expected_sources_cited":
            results[name] = check_all_expected_sources_cited(
                expected_sources, citation_source_ids
            )
        else:
            raise ValueError(f"unknown behavior check: {name}")
    return results


def case_warnings(*, citation_precision: float | None) -> list[str]:
    """逐题警告（不进 pass）：引用精度过低时记 low_citation_precision。"""
    warnings: list[str] = []
    if (
        citation_precision is not None
        and citation_precision < CITATION_PRECISION_WARNING_BELOW
    ):
        warnings.append("low_citation_precision")
    return warnings


def case_passed(
    category: str,
    *,
    retrieval_hit: bool | None,
    keyword_coverage: float | None,
    refusal_correct: bool | None,
    behavior_results: Mapping[str, bool | None] | None = None,
) -> bool:
    if category == "unanswerable":
        base = refusal_correct is True
    else:
        # 按可用指标判：缺失（None）的指标不计，无任何可用指标时不 pass。
        # 黄金集可答题经 validate_golden 保证来源与关键词俱全，两项指标恒可算，
        # 结果与旧口径（两项皆须达标）逐题一致；泛化只对 skill 题集这类锚点
        # 字段不全的题面生效（#187 UoW-1 判分口径）。
        checks: list[bool] = []
        if retrieval_hit is not None:
            checks.append(bool(retrieval_hit))
        if keyword_coverage is not None:
            checks.append(keyword_coverage >= COVERAGE_PASS_AT)
        base = all(checks) if checks else False
    if not base:
        return False
    # behavior_checks 计入 pass（#190 定案）：声明的检查里取非 None 值，
    # 须全过；全为 None（如空回答判不出）时不阻断，与「缺失指标不计」同口径。
    if behavior_results:
        declared = [value for value in behavior_results.values() if value is not None]
        if declared and not all(declared):
            return False
    return True


def score_case(
    *,
    category: str,
    expected_sources: Sequence[str],
    expected_keywords: Sequence[str],
    observed_sources: Sequence[str],
    citation_source_ids: Sequence[str] | None,
    answer: str,
    behavior_checks: Sequence[str] = (),
) -> dict[str, object]:
    hit = retrieval_hit(expected_sources, observed_sources)
    precision = citation_precision(expected_sources, citation_source_ids)
    coverage = keyword_coverage(expected_keywords, answer)
    refusal = refusal_correct(
        category=category, answer=answer, citation_source_ids=citation_source_ids
    )
    # 行为检查的结果不进本 dict（保持五键形状）；逐项结果由 runner 经
    # evaluate_behavior_checks 另行附到报告行，这里只把结论计入 passed。
    behavior = (
        evaluate_behavior_checks(
            behavior_checks,
            answer=answer,
            expected_sources=expected_sources,
            citation_source_ids=citation_source_ids,
        )
        if behavior_checks
        else {}
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
            behavior_results=behavior,
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
