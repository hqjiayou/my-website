"""POST /internal/v1/ocr/pdf 契约测试：扫描版 PDF 转写。

覆盖：无模型 BLOCKED、请求入口链命中、PDF 文档输入确实交给模型、
非法 base64 / 非 PDF 拒绝、模型链失败映射为 BLOCKED。
"""

import base64

import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai import BinaryContent
from pydantic_ai.exceptions import ModelAPIError
from pydantic_ai.messages import ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.app import create_app

_PDF_BYTES = b"%PDF-1.4 scanned-fixture"
_PDF_B64 = base64.b64encode(_PDF_BYTES).decode()


def _client(model) -> TestClient:
    handle = ModelHandle(model=model, spec="test:model", blocked_reason=None)
    return TestClient(create_app(make_settings(), model_handle=handle))


def test_ocr_blocked_without_model() -> None:
    response = TestClient(create_app(make_settings())).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PDF_B64},
    )
    assert response.status_code == 503
    assert response.json()["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"


def test_ocr_transcribes_pdf_via_document_input() -> None:
    seen: list[object] = []

    def respond(messages, _info: AgentInfo) -> ModelResponse:
        seen.extend(messages)
        return ModelResponse(parts=[TextPart("# 扫描件标题\n正文内容")])

    response = _client(FunctionModel(respond)).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PDF_B64},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["text"] == "# 扫描件标题\n正文内容"
    assert data["modelSpec"] == "test:model"

    binary_parts = [
        item
        for message in seen
        for part in getattr(message, "parts", [])
        for item in (part.content if isinstance(part.content, list) else [part.content])
        if isinstance(item, BinaryContent)
    ]
    assert len(binary_parts) == 1
    assert binary_parts[0].media_type == "application/pdf"
    assert binary_parts[0].data == _PDF_BYTES


def test_ocr_uses_request_model_entry(monkeypatch: pytest.MonkeyPatch) -> None:
    def build(spec: str, *, api_key: str, base_url: str) -> TestModel:
        assert spec == "fake:from-java"
        assert api_key == "secret-key"
        return TestModel(custom_output_text="entry text")

    monkeypatch.setattr("agent_services.agent.model_factory._build_primary", build)
    response = TestClient(create_app(make_settings())).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={
            "contentBase64": _PDF_B64,
            "model": {"spec": "fake:from-java", "apiKey": "secret-key"},
        },
    )
    assert response.status_code == 200
    assert response.json()["data"]["text"] == "entry text"


def test_ocr_rejects_invalid_base64() -> None:
    response = _client(TestModel()).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={"contentBase64": "!!!not-base64!!!"},
    )
    assert response.status_code == 400
    assert response.json()["errorCode"] == "INVALID_PDF"


def test_ocr_rejects_non_pdf_bytes() -> None:
    response = _client(TestModel()).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={"contentBase64": base64.b64encode(b"plain text").decode()},
    )
    assert response.status_code == 400
    assert response.json()["errorCode"] == "INVALID_PDF"


_PNG_BYTES = b"\x89PNG\r\n\x1a\n" + b"fake-png-payload"
_PNG_B64 = base64.b64encode(_PNG_BYTES).decode()


def test_ocr_image_blocked_without_model() -> None:
    response = TestClient(create_app(make_settings())).post(
        "/internal/v1/ocr/image",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PNG_B64, "mediaType": "image/png"},
    )
    assert response.status_code == 503
    assert response.json()["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"


def test_ocr_image_transcribes_via_binary_input() -> None:
    seen: list[object] = []

    def respond(messages, _info: AgentInfo) -> ModelResponse:
        seen.extend(messages)
        return ModelResponse(parts=[TextPart("图片中的文字")])

    response = _client(FunctionModel(respond)).post(
        "/internal/v1/ocr/image",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PNG_B64, "mediaType": "image/png"},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["text"] == "图片中的文字"
    assert data["modelSpec"] == "test:model"
    binary_parts = [
        item
        for message in seen
        for part in getattr(message, "parts", [])
        for item in (part.content if isinstance(part.content, list) else [part.content])
        if isinstance(item, BinaryContent)
    ]
    assert len(binary_parts) == 1
    assert binary_parts[0].media_type == "image/png"
    assert binary_parts[0].data == _PNG_BYTES


def test_ocr_image_rejects_magic_mismatch_and_bad_type() -> None:
    client = _client(TestModel())
    mismatch = client.post(
        "/internal/v1/ocr/image",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PDF_B64, "mediaType": "image/png"},
    )
    assert mismatch.status_code == 400
    assert mismatch.json()["errorCode"] == "INVALID_IMAGE"
    bad_type = client.post(
        "/internal/v1/ocr/image",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PNG_B64, "mediaType": "image/tiff"},
    )
    assert bad_type.status_code == 400
    assert bad_type.json()["errorCode"] == "INVALID_IMAGE"


def test_ocr_model_failure_maps_to_blocked() -> None:
    def boom(_messages, _info: AgentInfo) -> ModelResponse:
        raise ModelAPIError("fake", "provider down")

    response = _client(FunctionModel(boom)).post(
        "/internal/v1/ocr/pdf",
        headers=TOKEN_HEADER,
        json={"contentBase64": _PDF_B64},
    )
    assert response.status_code == 503
    assert response.json()["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
