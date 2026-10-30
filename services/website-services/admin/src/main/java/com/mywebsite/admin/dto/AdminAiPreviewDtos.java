package com.mywebsite.admin.dto;

import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AdminAiPreviewDtos {

    private AdminAiPreviewDtos() {}

    /**
     * 后台预览 chat：不落库。history / todos 仅用于当前预览线程上下文（前端持有上轮 DONE.todos 回传）；
     * {@code promptProfile} 仍由 Java 从站点配置注入，不接受前端直传。
     */
    public record AdminAiPreviewChatRequest(
            @NotBlank @Size(max = 4000) String message,
            @Size(max = 20) List<@Valid AdminAiPreviewHistoryMessage> history,
            /** 可选：激活 agent-services skill id（内容仍在 Python skills/，前端只传 id）。 */
            @Size(max = 16) List<@Size(max = 64) String> skills,
            /** 可选：上轮 Todo 快照；上限与 agent-services 一致（§21.4）。 */
            @Size(max = 20) List<@Valid AdminAiPreviewTodoItem> todos,
            /**
             * 试聊模式，与接入里的 CLI / KEY 对应。缺省时服务端在有 CLI 默认入口时用 CLI，否则 KEY。
             * 试聊只调用该模式的主用入口。
             */
            @Size(max = 8) String mode) {}

    public record AdminAiPreviewHistoryMessage(
            @NotBlank @Size(max = 16) String role, @NotBlank @Size(max = 8000) String content) {}

    public record AdminAiPreviewTodoItem(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED|CANCELLED") String status) {}

    /** agent-services 技能目录条目（只元数据）。 */
    public record AdminAiPreviewSkillDto(String id, String description, String version) {}

    /**
     * Agent 运行时面板。{@code reachable=false} 时 {@code status} 为 null，
     * {@code errorCode} 为 EXTERNAL_DEPENDENCY_BLOCKED（未配置令牌等）或 SYSTEM_ERROR（不可达）。
     */
    public record AdminAiRuntimeDto(boolean reachable, String errorCode, AgentRuntimeStatus status) {}

    /**
     * MCP 深检。{@code reachable=false} 表示 agent-services 本身不可达；
     * {@code reachable=true} 且 {@code probe.ok=false} 表示进程可达但某个 MCP 不通。
     */
    public record AdminAiMcpReadyDto(boolean reachable, String errorCode, AgentMcpReady probe) {}

    /**
     * 试聊「实际会用哪个模型」回填：{@code mode}/{@code modelSpec}/{@code available} 与
     * {@code previewStream} 走同一个 previewMode + selectionChain 判定路径算出，不是另一份近似规则。
     * {@code mode} 请求缺省时为服务端按默认判定选出的模式；{@code modelSpec} 为该模式链首 spec，
     * 无链时为 null；{@code available} 即链是否非空（含默认入口 key 解密失败导致整链为空的场景）。
     */
    public record AdminAiPreviewModelDto(String mode, String modelSpec, boolean available) {}
}
