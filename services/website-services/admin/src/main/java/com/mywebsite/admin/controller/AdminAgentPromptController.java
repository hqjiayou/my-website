package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptLoadTemplateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptSaveAsTemplateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateDto;
import com.mywebsite.admin.service.AdminAiPromptTemplateService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent Prompt 配置（Issue #214 UoW-4）：Prompt 是 agent 自有数据——本地副本
 * （systemPrompt＋temperature）＋自有默认 skill 集存 agent 行，工作台提示词
 * 面板读写本组端点；保存即生效（正式对话、试聊同口径随之变化）。模板套用/
 * 另存走拷贝语义，与模板库脱钩。权限码沿 ai-prompt 既有口径
 * （{@code admin:ai-prompt:read}），不新造。取代 #213 的
 * {@code /config-preset} 引用端点（已删）。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentId}/ai/prompt")
@Tag(name = "Admin · Agent AI Prompt")
@SecurityRequirement(name = "adminBearer")
public class AdminAgentPromptController {

    private final AdminAiPromptTemplateService promptTemplateService;

    public AdminAgentPromptController(AdminAiPromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "查看 agent 的 Prompt 配置（本地副本＋来源留痕＋自有技能集）")
    public ResponseEntity<HttpMessageBody<AdminAgentPromptConfigDto>> get(@PathVariable long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, promptTemplateService.getAgentPromptConfig(agentId)));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "整表保存本地副本与自有技能集（空白 prompt/空集为清空；不改来源留痕）")
    public ResponseEntity<HttpMessageBody<AdminAgentPromptConfigDto>> update(
            @PathVariable long agentId, @Valid @RequestBody AdminAgentPromptConfigUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, promptTemplateService.updateAgentPromptConfig(agentId, request)));
    }

    @PostMapping("/load-template")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "从 Prompt 模板套用（拷贝模板的 prompt 部分覆盖本地副本，自有技能集不动）")
    public ResponseEntity<HttpMessageBody<AdminAgentPromptConfigDto>> loadTemplate(
            @PathVariable long agentId, @Valid @RequestBody AdminAgentPromptLoadTemplateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                promptTemplateService.loadAgentPromptFromTemplate(agentId, request.templateId())));
    }

    @PostMapping("/save-as-template")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "把本地副本另存为 Prompt 模板（返回新建模板，留痕指向它）")
    public ResponseEntity<HttpMessageBody<AdminAiPromptTemplateDto>> saveAsTemplate(
            @PathVariable long agentId, @Valid @RequestBody AdminAgentPromptSaveAsTemplateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                promptTemplateService.saveAgentPromptAsTemplate(agentId, request.name())));
    }
}
