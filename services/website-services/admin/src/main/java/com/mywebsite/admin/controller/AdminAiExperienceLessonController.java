package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonApproveRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonCreateRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonDetailDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonListItemDto;
import com.mywebsite.admin.service.AdminAiExperienceLessonService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经验沉淀人审（Issue #184 UoW-3，P2 定案：AI 设置页「经验沉淀」一节的后端）。
 * 权限沿用 AI 设置页口径（同运行配置）：读挂 ai-model:read、写动作
 * （批/驳/停用/新建/触发提炼）挂 ai-model:update，不新造权限码。
 * 流转非法由状态机服务抛业务异常，走 admin 统一错误形状（409/404/400）。
 */
@RestController
@RequestMapping("/api/admin/ai/experience-lessons")
@Tag(name = "Admin · AI Experience Lessons")
@SecurityRequirement(name = "adminBearer")
public class AdminAiExperienceLessonController {

    private final AdminAiExperienceLessonService experienceLessonService;

    public AdminAiExperienceLessonController(AdminAiExperienceLessonService experienceLessonService) {
        this.experienceLessonService = experienceLessonService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "按状态列经验条目（默认 PENDING 待审队列，创建时间升序）；lesson 只回摘要，全文走详情")
    public ResponseEntity<HttpMessageBody<List<AdminAiExperienceLessonListItemDto>>> list(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.listByStatus(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "经验条目详情：四字段全文＋来源三 id")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceLessonDetailDto>> detail(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.getDetail(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "手动新建候选（兜底试聊等未落库场景）：来源三 id 留空，落 PENDING 待审")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceLessonDetailDto>> create(
            @Valid @RequestBody AdminAiExperienceLessonCreateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.createCandidate(request)));
    }

    @PostMapping("/extract")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "手动批量提炼近期正式对话（P1 定案）：信号预筛后逐条落 PENDING，返回扫描/调用/产出/失败汇总")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceExtractionResultDto>> extract(
            @RequestBody(required = false) AdminAiExperienceExtractionRequestDto request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.extractRecent(request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "批准候选：可带四字段修订（null 字段不改），存改写后版本并分配 L 编号；非 PENDING 回 409")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceLessonDetailDto>> approve(
            @PathVariable long id, @Valid @RequestBody(required = false) AdminAiExperienceLessonApproveRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.approve(id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "驳回候选；非 PENDING 回 409")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceLessonDetailDto>> reject(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.reject(id)));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "停用已批准条目（L 编号保留不回收）；非 APPROVED 回 409")
    public ResponseEntity<HttpMessageBody<AdminAiExperienceLessonDetailDto>> disable(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, experienceLessonService.disable(id)));
    }
}
