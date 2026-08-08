package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationCreateRequest;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationDetailDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListItemDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListQuery;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationStatusUpdateRequest;
import com.mywebsite.admin.service.AdminReservationService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sites/{siteId}/reservations")
@Tag(name = "Admin · Reservation")
@SecurityRequirement(name = "adminBearer")
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    public AdminReservationController(AdminReservationService adminReservationService) {
        this.adminReservationService = adminReservationService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:reservation:list')")
    @Operation(summary = "站点预约分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminReservationListItemDto>>> list(
            @PathVariable long siteId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status) {
        PageResult<AdminReservationListItemDto> data = adminReservationService.listReservations(
                siteId, new AdminReservationListQuery(pageNum, pageSize, status));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:reservation:list')")
    @Operation(summary = "站点预约详情")
    public ResponseEntity<HttpMessageBody<AdminReservationDetailDto>> get(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminReservationService.getReservation(siteId, id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:reservation:create')")
    @Operation(summary = "创建站点预约（运营代录）")
    public ResponseEntity<HttpMessageBody<AdminReservationDetailDto>> create(
            @PathVariable long siteId, @Valid @RequestBody AdminReservationCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminReservationService.createReservation(siteId, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:reservation:update')")
    @Operation(summary = "变更站点预约状态")
    public ResponseEntity<HttpMessageBody<AdminReservationDetailDto>> updateStatus(
            @PathVariable long siteId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminReservationStatusUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminReservationService.updateStatus(siteId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:reservation:delete')")
    @Operation(summary = "删除站点预约（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminReservationService.deleteReservation(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
