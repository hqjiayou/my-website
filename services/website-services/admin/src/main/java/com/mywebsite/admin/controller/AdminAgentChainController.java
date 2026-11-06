package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainLoadTemplateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainSaveAsTemplateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainUpdateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.service.AdminAiChainPresetService;
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
 * agent 自有对话链（Issue #214 UoW-3）：链存 agent 自有字段（按模式各一份有序
 * entry ids），工作台链编辑器读写本组端点；保存即换链（正式对话、试聊、OCR、
 * 提炼同口径随之变化）。模板载入/另存走拷贝语义，与模板库脱钩。权限码沿链
 * 预设既有口径（{@code admin:ai-model:read}），不新造。取代 #210 的
 * {@code /ai/chain-preset} 引用端点（已删）。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentId}/ai/chain")
@Tag(name = "Admin · Agent AI Chain")
@SecurityRequirement(name = "adminBearer")
public class AdminAgentChainController {

    private final AdminAiChainPresetService chainPresetService;

    public AdminAgentChainController(AdminAiChainPresetService chainPresetService) {
        this.chainPresetService = chainPresetService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "查看 agent 的自有对话链（Key/CLI 两模式成员名单）")
    public ResponseEntity<HttpMessageBody<AdminAgentChainDto>> get(@PathVariable long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, chainPresetService.getAgentChain(agentId)));
    }

    @PutMapping("/{mode}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "整表保存某模式的自有链（成员顺序即链序；空列表为清空该模式链）")
    public ResponseEntity<HttpMessageBody<AdminAgentChainDto>> update(
            @PathVariable long agentId,
            @PathVariable String mode,
            @RequestBody AdminAgentChainUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                chainPresetService.updateAgentChain(
                        agentId, mode, request == null ? null : request.entryIds())));
    }

    @PostMapping("/load-template")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "从链模板载入某模式的自有链（拷贝模板中属于该模式的成员，覆盖该模式链）")
    public ResponseEntity<HttpMessageBody<AdminAgentChainDto>> loadTemplate(
            @PathVariable long agentId, @Valid @RequestBody AdminAgentChainLoadTemplateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                chainPresetService.loadAgentChainFromTemplate(agentId, request.mode(), request.templateId())));
    }

    @PostMapping("/save-as-template")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "把某模式自有链另存为链模板（返回新建模板）")
    public ResponseEntity<HttpMessageBody<AdminAiChainPresetDto>> saveAsTemplate(
            @PathVariable long agentId, @Valid @RequestBody AdminAgentChainSaveAsTemplateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                chainPresetService.saveAgentChainAsTemplate(agentId, request.mode(), request.name())));
    }
}
