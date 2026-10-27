"""POST /internal/v1/ocr/pdf 契约：扫描版 PDF 经模型原生文档理解转写为文本。

请求与 chat 同一形状携带模型入口链（model + fallbackModels，各自带凭证），
另加 contentBase64（PDF 原始字节）。响应只回转写文本与命中的入口 spec。
"""

from pydantic import BaseModel, Field

from agent_services.schemas.chat import ModelSelection

MAX_PDF_BYTES = 10 * 1024 * 1024  # 与 Java 上传上限对齐
MAX_PDF_BASE64_CHARS = ((MAX_PDF_BYTES + 2) // 3) * 4 + 4


class OcrPdfRequest(BaseModel):
    content_base64: str = Field(alias="contentBase64", min_length=4, max_length=MAX_PDF_BASE64_CHARS)
    model_selection: ModelSelection | None = Field(default=None, alias="model")
    fallback_models: list[ModelSelection] = Field(
        default_factory=list, alias="fallbackModels", max_length=8
    )

    model_config = {"populate_by_name": True}


class OcrPdfResponse(BaseModel):
    text: str
    model_spec: str = Field(alias="modelSpec")

    model_config = {"populate_by_name": True}


MAX_IMAGE_BYTES = 10 * 1024 * 1024  # 与 Java 上传上限对齐
MAX_IMAGE_BASE64_CHARS = ((MAX_IMAGE_BYTES + 2) // 3) * 4 + 4

# 知识库图片上传支持的媒体类型（与 Java 侧图片扩展名一一对应）
OCR_IMAGE_MEDIA_TYPES = ("image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp")


class OcrImageRequest(BaseModel):
    content_base64: str = Field(alias="contentBase64", min_length=4, max_length=MAX_IMAGE_BASE64_CHARS)
    media_type: str = Field(alias="mediaType")
    model_selection: ModelSelection | None = Field(default=None, alias="model")
    fallback_models: list[ModelSelection] = Field(
        default_factory=list, alias="fallbackModels", max_length=8
    )

    model_config = {"populate_by_name": True}


class OcrImageResponse(BaseModel):
    text: str
    model_spec: str = Field(alias="modelSpec")

    model_config = {"populate_by_name": True}
