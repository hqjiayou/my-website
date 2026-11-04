-- =============================================================================
-- website-services | Flyway V52 | MCP 配置页面化：全局注册表（Issue #192 UoW-1）
-- agent_runtime_config 单行加一列存整份 MCP server 条目 JSON：条目里的
-- headers / env 可能含密钥，故整份 JSON 以 ModelEntryCipher 加密后存（P2 定案）。
-- NULL＝页面从未保存过 MCP 配置：Python 侧据此保留当前生效配置（env 基线或
-- 已推送值），不收敛为空；页面清空全部条目时存的是加密后的 "[]"，与 NULL 严格区分。
-- 生效通道沿 #146：保存后经同一 config_version 与 pushAndRecord 管线推送
-- （PUT /internal/v1/config/retrieval 的 mcpServersJson 字段），Python 启动拉取同路收敛。
-- =============================================================================

ALTER TABLE agent_runtime_config
    ADD COLUMN mcp_servers_json TEXT;

COMMENT ON COLUMN agent_runtime_config.mcp_servers_json IS 'MCP server 条目整份 JSON（ModelEntryCipher 密文，#192）；NULL＝从未经页面保存，空数组＝页面已清空全部条目';
