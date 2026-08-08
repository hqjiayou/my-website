package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateDetailDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListItemDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListQuery;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateStatusUpdateRequest;
import com.mywebsite.admin.service.AdminTemplateService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/templates")
@Tag(name = "Admin · Templates")
@SecurityRequirement(name = "adminBearer")
public class AdminTemplateController {

    private final AdminTemplateService adminTemplateService;

    public AdminTemplateController(AdminTemplateService adminTemplateService) {
        this.adminTemplateService = adminTemplateService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:template:list')")
    @Operation(summary = "模板分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminTemplateListItemDto>>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String siteType) {
        PageResult<AdminTemplateListItemDto> data = adminTemplateService.listTemplates(
                new AdminTemplateListQuery(pageNum, pageSize, status, siteType));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:template:detail')")
    @Operation(summary = "按编码查询模板详情")
    public ResponseEntity<HttpMessageBody<AdminTemplateDetailDto>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminTemplateService.getTemplateByCode(code)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:template:detail')")
    @Operation(summary = "模板详情")
    public ResponseEntity<HttpMessageBody<AdminTemplateDetailDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminTemplateService.getTemplate(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:template:status')")
    @Operation(summary = "变更模板状态")
    public ResponseEntity<HttpMessageBody<AdminTemplateDetailDto>> updateStatus(
            @PathVariable long id, @Valid @RequestBody AdminTemplateStatusUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminTemplateService.updateStatus(id, request)));
    }
}
