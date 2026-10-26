# MCP 2026-07-28 規範評估

> **性質**：評估快照（2026-10-26），不是現況總覽，也不取代 [`docs/overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md)。
> **範圍**：本倉庫的 MCP client（`services/agent-services`）。此處不下規格以外的協議推論。
> **規範條目**：只記錄下面已核實的變化。與官方 changelog 衝突時以官方為準。

## 來源

- [Stateless MCP servers: what the 2026-07-28 spec changed](https://dev.to/fabwaseem/stateless-mcp-servers-what-the-2026-07-28-spec-changed-and-how-to-migrate-5ccl)
- [Skyvia — AI MCP server](https://skyvia.com/blog/ai-mcp-server/)
- 官方 changelog 優先於上述二手整理。

## 已核實的規範變化

- **無狀態核心**（SEP-2567 / SEP-2575）：移除 initialize 握手與 `Mcp-Session-Id`。每個請求在 `_meta` 攜帶 `protocolVersion`、`clientCapabilities`、`clientInfo`。新增 `server/discover`。版本不合回 `UnsupportedProtocolVersionError`。
- **MRTR**（SEP-2322）：server → client 的 sampling / elicitation 改為回 `resultType: "input_required"` 加上不透明 `requestState`，client 帶答案重試同一個請求。
- **列表可快取**（SEP-2549）：`tools` / `prompts` / `resources` 的 list 帶 `ttlMs`、`cacheScope`，並要求確定性排序。
- **Streamable HTTP**（SEP-2243）：請求必帶路由頭 `Mcp-Method`、`Mcp-Name`。移除 HTTP GET 串流與 SSE 可恢復性。變更通知收斂為 `subscriptions/listen`。
- **擴展**：Tasks 轉為官方擴展 `io.modelcontextprotocol/tasks`。MCP Apps 為第一個官方擴展。
- **棄用**（至少 12 個月通知期）：Roots、Sampling、Logging、HTTP+SSE 傳輸、Dynamic Client Registration（由 Client ID Metadata Documents 取代）。
- **雙時代相容**：server 可以同時支援新舊協議。只支援新版的 server，舊 client 連不上。

## 本倉庫實際怎麼用 MCP

本服務是純 client，協議幀由依賴庫發出，業務代碼不組 JSON-RPC。

| 用法 | 落點 |
| --- | --- |
| 配置解析與 Client 建構 | `services/agent-services/src/agent_services/agent/mcp_tools.py`：`parse_mcp_server_configs`、`build_mcp_client`。`http` → `StreamableHttpTransport`，`sse` → `SSETransport`，`stdio` → `StdioTransport`。鎖檔為 `fastmcp-slim` 4.1.0、`pydantic-ai-slim` 2.61.0（`pyproject.toml` 下限 `fastmcp-slim[client]>=4.0.0`、`pydantic-ai-slim[mcp]>=2.54.0,<3`）。 |
| 工具註冊 | `agent/toolset_factory.py` 的 `build_execution_toolsets` 把 `build_mcp_toolsets` 的 `MCPToolset` 併進該輪 toolsets。 |
| `tools/list` | `probe_mcp_ready` → `_probe_one` → `client.list_tools()`。HTTP 入口是 `api/internal.py` 的 `GET /internal/v1/mcp/ready`。對話路徑沒有另一套 list 實作，連線點是 `runtime.py` / `stream_runtime.py` 的 `async with agent`。 |
| `tools/call` | `agent/runtime.py` 的 `run_chat` 與 `agent/stream_runtime.py` 的 `stream_chat_events`：有 MCP toolset 時 `async with agent`，連上之後才 `agent.run`。呼叫本身在 `MCPToolset` 裡。 |
| 連線失敗 | 尚未進入 agent context 的例外收成 `MCP_UNAVAILABLE`（`runtime.py` / `stream_runtime.py`）。ready 探針失敗同樣是 503 `MCP_UNAVAILABLE`（`api/internal.py`）。 |
| 提示詞 | `agent/harness.py` 的 `_inspect_toolsets` 只把 MCP server 名稱寫進系統提示，不寫 URL，也不列 MCP 工具 schema。 |
| 未使用 | `src/agent_services` 沒有 MCP sampling、elicitation、roots、MCP logging、prompts/resources list、`subscriptions/listen`、DCR、`server/discover` 的呼叫。`agent/todo_tools.py` 是平台自己的 `FunctionToolset`，不是擴展 `io.modelcontextprotocol/tasks`。 |

跨請求不保留 MCP session：每次 `run_chat` / `stream_chat_events` 新建 agent 再連線（agent-services 跨 business request stateless）。

## 對本項目的影響

1. **短期沒有強制改協議。** `build_mcp_client` 只把 transport 與 `init_timeout` / `timeout` 交給 FastMCP `Client`，不手寫 initialize、`Mcp-Session-Id`、`_meta` 或 `server/discover`。鎖檔 `fastmcp-slim` 4.1.0 的 `Client` 預設 `mode="auto"`（`fastmcp/client/client.py`）：連線時先探 `server/discover`，對不是新時代的 peer 退回 initialize。http、sse、stdio 都是這個 `Client`，雙時代協商是庫預設，不是本倉庫另寫的分支。
2. **協商失敗仍走現有失敗路徑。** `async with agent` 進不去就是 `MCP_UNAVAILABLE`（`runtime.py` / `stream_runtime.py`）；ready 探針由 `_probe_one` 收成 `ok: false`。只支援新版、且 4.1.0 的 auto 模式認不出來的 server，會落在這兩處，而不是本倉庫再包一層握手。stdio 子進程同樣經 `build_mcp_client`。
3. **HTTP+SSE 已加棄用提醒，連接方式沒改。** `parse_mcp_server_configs` 在解析結果為 `sse`（顯式 `transport: sse`，或 url 以 `/sse` 結尾而推斷為 sse）時打一條 warning。`build_mcp_client` 仍是 `Client(SSETransport(...))`。新配置繼續用 `transport: http`（`StreamableHttpTransport`）。`Mcp-Method` / `Mcp-Name`、拿掉 GET 串流與 SSE 可恢復性，都落在 FastMCP 的 HTTP transport，本倉庫沒有自己組這些頭。
4. **4.1.0 已經帶 2026-07-28 的 client 開關，本倉庫用的是預設。** `mode` 見上。`cache` 預設 `None`：文件寫明 `None` / `False` 關閉 SEP-2549，`True` 才按 server 的 `ttlMs` / `cacheScope` 做進程內快取，且只在新時代連線上生效。`input_required_max_rounds` 有預設上限，但 `build_mcp_client` 沒傳 `sampling_handler` / `elicitation_handler`。升級 FastMCP 時要看這三個預設；呼叫點仍是 `mcp_tools.py` 的 `Client(...)`。
5. **`tools/list` 快取還沒開。** 本倉庫沒有讀 `ttlMs` / `cacheScope`，也沒有給 `Client` 傳 `cache=True`。`probe_mcp_ready` 每次請求都 `list_tools`。對話每次 `run_chat` / `stream_chat_events` 新建 agent 再連線。若要吃 SEP-2549，改動點是 `build_mcp_client` 的 `Client` 參數。快取不能當業務真相，也不能跨請求假裝 session 還在。
6. **MRTR、Sampling、Roots、Logging、DCR 沒有業務層要改。** `runtime.py` 不處理 `input_required` / `requestState`；SEP-2322 的重試輪數只存在於 FastMCP `Client` 預設，本倉庫沒有 sampling / elicitation handler。棄用通知期內這些能力本來就沒接。Tasks 擴展與 MCP Apps 也未接；平台待辦仍是 `todo_tools.py`。
