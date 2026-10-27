"""扫描版 PDF 转写：把 PDF 作为原生文档输入交给生效模型链一次裸调用。

不挂工具/技能/检索；模型链本身是 FallbackModel（入口备援顺链），
本模块只负责提示词、文档输入与输出归一。转写结果按模型给出的
Markdown 原样返回，作为知识库文档正文入库（与文本层抽取同口径）。
"""

import logging

from pydantic_ai import Agent, BinaryContent
from pydantic_ai.exceptions import ModelAPIError
from pydantic_ai.models import Model

logger = logging.getLogger(__name__)

OCR_INSTRUCTION = (
    "Transcribe the attached PDF document into Markdown text. "
    "Reproduce the document content faithfully in its original language, preserving headings, "
    "paragraphs, lists and tables. Output only the document text, with no commentary, "
    "no explanations and no code fences."
)


class OcrUnavailable(Exception):
    """模型链全部失败：上层映射为 EXTERNAL_DEPENDENCY_BLOCKED。"""


async def transcribe_pdf(model: Model, pdf_bytes: bytes) -> str:
    agent = Agent(model)
    try:
        result = await agent.run(
            [OCR_INSTRUCTION, BinaryContent(data=pdf_bytes, media_type="application/pdf")]
        )
    except ModelAPIError as exc:
        raise OcrUnavailable(f"model chain failed for PDF transcription: {exc}") from exc
    text = str(result.output).strip()
    if not text:
        logger.warning("ocr produced empty text (model=%s)", model)
    return text


OCR_IMAGE_INSTRUCTION = (
    "Transcribe all text visible in the attached image into Markdown text. "
    "Reproduce the text faithfully in its original language, preserving headings, "
    "paragraphs, lists and tables where the image has them. Output only the "
    "transcribed text, with no commentary and no code fences. If the image "
    "contains no text, output nothing."
)


async def transcribe_image(model: Model, image_bytes: bytes, media_type: str) -> str:
    agent = Agent(model)
    try:
        result = await agent.run(
            [OCR_IMAGE_INSTRUCTION, BinaryContent(data=image_bytes, media_type=media_type)]
        )
    except ModelAPIError as exc:
        raise OcrUnavailable(f"model chain failed for image transcription: {exc}") from exc
    text = str(result.output).strip()
    if not text:
        logger.warning("image ocr produced empty text (model=%s)", model)
    return text
