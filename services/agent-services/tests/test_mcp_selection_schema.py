"""ChatRequest 的 mcpServerNames 纵深防御（Issue #192 UoW-3）。

Java 组装是权威，本层只钉形状：缺席=None（三态之 INHERIT）、空列表原样
保留（OFF，与缺席严格区分）、条数与名长超限直接 422 式校验失败。
"""

import pytest
from pydantic import ValidationError

from agent_services.schemas.chat import ChatRequest


def _request(**extra: object) -> ChatRequest:
    return ChatRequest(agentId="1", message="hi", **extra)


def test_mcp_names_absent_defaults_none() -> None:
    assert _request().mcp_server_names is None


def test_mcp_names_empty_list_preserved() -> None:
    assert _request(mcpServerNames=[]).mcp_server_names == []


def test_mcp_names_list_parsed() -> None:
    assert _request(mcpServerNames=["docs", "echo"]).mcp_server_names == ["docs", "echo"]


def test_mcp_names_count_capped() -> None:
    with pytest.raises(ValidationError):
        _request(mcpServerNames=[f"s{i}" for i in range(33)])


def test_mcp_names_length_capped() -> None:
    with pytest.raises(ValidationError):
        _request(mcpServerNames=["x" * 65])
