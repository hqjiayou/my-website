package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewSkillDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpReadyDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiRuntimeDto;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AdminAiPreviewService {

    SseEmitter previewStream(long agentId, AdminAiPreviewChatRequest request);

    /** 预览可选 skill 目录；agent-services 不可用时抛 EXTERNAL_DEPENDENCY_BLOCKED / SYSTEM_ERROR。 */
    List<AdminAiPreviewSkillDto> listSkills(long agentId);

    /** Agent 运行时快照；agent-services 不可用时不抛错，返回 reachable=false 供面板如实展示。 */
    AdminAiRuntimeDto runtimeStatus(long agentId);

    /** MCP 深检（手动触发，不并入 /runtime）；agent-services 不可达时 reachable=false。 */
    AdminAiMcpReadyDto mcpReady(long agentId);
}
