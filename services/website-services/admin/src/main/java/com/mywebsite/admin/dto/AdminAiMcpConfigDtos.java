package com.mywebsite.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * MCP 服务器全局注册表（Issue #192 UoW-1）：与运行配置共用
 * {@code agent_runtime_config} 单行与推送管线，但端点分开
 * （{@code /api/admin/ai/mcp-config}）。
 *
 * <p>脱敏纪律：headers / env 的值可能含密钥，GET 只回每个键的
 * {@code valueHint}（末 4 位）与 {@code configured}，永不回明文；PUT 时
 * 某键 {@code value} 留空＝保留原值、{@code clear=true}＝删除该键、
 * 填新值＝替换。{@code url} 可能内嵌 userinfo 凭证，按同一纪律处理：
 * GET 只回 hint（{@code urlConfigured}＋{@code urlHint}），PUT 留空＝保留。
 */
public final class AdminAiMcpConfigDtos {

    private AdminAiMcpConfigDtos() {}

    /** 密钥型键值对的回显形：只有键名、配置与否与末 4 位 hint。 */
    public record SecretKvDto(String key, boolean configured, String valueHint) {}

    /** MCP 条目视图。{@code args} 不是密钥、原样回；{@code headers}/{@code env} 只回 hint 形。 */
    public record McpServerEntryDto(
            String name,
            String transport,
            boolean urlConfigured,
            String urlHint,
            boolean enabled,
            String toolPrefix,
            Integer initTimeoutSeconds,
            String command,
            List<String> args,
            String cwd,
            List<SecretKvDto> headers,
            List<SecretKvDto> env) {}

    /** 注册表视图：条目列表＋与运行配置同口径的版本与推送状态（共用同一行/管线）。 */
    public record AdminAiMcpConfigDto(
            List<McpServerEntryDto> servers,
            long configVersion,
            String lastPushStatus,
            String lastPushMessage,
            OffsetDateTime lastPushedAt,
            Integer version,
            OffsetDateTime updatedAt) {}

    /** 密钥型键值对的提交形：value 留空＝保留原值；clear=true＝删除该键（优先于 value）。 */
    public record SecretKvInput(
            @Size(max = 128) String key, @Size(max = 4096) String value, Boolean clear) {}

    /**
     * MCP 条目提交形。非密钥字段整体替换（null＝清掉该字段）；headers / env
     * 为 null＝保留既有整表，非 null＝按提交列表整体定键集合：列表外的既有键
     * 删除，列表内按 SecretKvInput 语义合并。
     * {@code url} 是唯一例外：可能内嵌凭证，按密钥字段处理（留空＝保留原值）。
     */
    public record McpServerEntryInput(
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$") @Size(max = 64) String name,
            @Pattern(regexp = "^(http|sse|stdio)$") String transport,
            @Size(max = 2048) String url,
            Boolean enabled,
            @Size(max = 64) String toolPrefix,
            @Min(1) @Max(600) Integer initTimeoutSeconds,
            @Size(max = 512) String command,
            @Size(max = 32) List<@Size(max = 1024) String> args,
            @Size(max = 512) String cwd,
            @Size(max = 64) List<@Valid SecretKvInput> headers,
            @Size(max = 64) List<@Valid SecretKvInput> env) {}

    /** 保存注册表：条目列表整体替换（顺序即注册顺序）；空列表＝清空全部条目。 */
    public record AdminAiMcpConfigUpdateRequest(@Size(max = 64) List<@Valid McpServerEntryInput> servers) {}
}
