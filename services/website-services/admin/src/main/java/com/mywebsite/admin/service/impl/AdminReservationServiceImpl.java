package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminReservationConstants;
import com.mywebsite.admin.constant.AdminReservationMessageKeys;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationCreateRequest;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationDetailDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListItemDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListQuery;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationStatusUpdateRequest;
import com.mywebsite.admin.entity.AdminReservationEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminReservationMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminReservationService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminReservationServiceImpl implements AdminReservationService {

    private final AdminReservationMapper adminReservationMapper;
    private final AdminSiteMapper adminSiteMapper;

    public AdminReservationServiceImpl(
            AdminReservationMapper adminReservationMapper, AdminSiteMapper adminSiteMapper) {
        this.adminReservationMapper = adminReservationMapper;
        this.adminSiteMapper = adminSiteMapper;
    }

    @Override
    public PageResult<AdminReservationListItemDto> listReservations(
            long siteId, AdminReservationListQuery query) {
        requireSite(siteId);
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminReservationConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminReservationConstants.MAX_PAGE_SIZE)
                : AdminReservationConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminReservationEntity> wrapper =
                new LambdaQueryWrapper<AdminReservationEntity>()
                        .eq(AdminReservationEntity::getSiteId, siteId)
                        .orderByDesc(AdminReservationEntity::getReservedAt)
                        .orderByDesc(AdminReservationEntity::getId);
        if (query != null && StringUtils.hasText(query.status())) {
            requireStatus(query.status());
            wrapper.eq(AdminReservationEntity::getStatus, query.status());
        }

        Page<AdminReservationEntity> page =
                adminReservationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminReservationDetailDto getReservation(long siteId, long reservationId) {
        requireSite(siteId);
        return toDetail(requireReservationInSite(siteId, reservationId));
    }

    @Override
    @Transactional
    public AdminReservationDetailDto createReservation(
            long siteId, AdminReservationCreateRequest request) {
        requireSite(siteId);

        AdminReservationEntity entity = new AdminReservationEntity();
        entity.setSiteId(siteId);
        entity.setCustomerName(request.customerName().trim());
        // PII：仅持久化，禁止日志打全量 phone
        entity.setPhone(request.phone().trim());
        entity.setServiceType(request.serviceType().trim());
        entity.setReservedAt(request.reservedAt());
        entity.setStatus(AdminReservationConstants.STATUS_NEW);
        entity.setNote(blankToNull(request.note()));
        adminReservationMapper.insert(entity);
        return toDetail(requireReservationInSite(siteId, entity.getId()));
    }

    @Override
    @Transactional
    public AdminReservationDetailDto updateStatus(
            long siteId, long reservationId, AdminReservationStatusUpdateRequest request) {
        requireSite(siteId);
        requireStatus(request.status());
        AdminReservationEntity entity = requireReservationInSite(siteId, reservationId);
        requireAllowedTransition(entity.getStatus(), request.status());

        entity.setStatus(request.status());
        entity.setVersion(request.version());
        int updated = adminReservationMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminReservationMessageKeys.RESERVATION_VERSION_CONFLICT);
        }
        return toDetail(requireReservationInSite(siteId, reservationId));
    }

    @Override
    @Transactional
    public void deleteReservation(long siteId, long reservationId) {
        requireSite(siteId);
        AdminReservationEntity entity = requireReservationInSite(siteId, reservationId);
        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminReservationMapper.updateById(entity);
        int deleted = adminReservationMapper.deleteById(entity.getId());
        if (deleted == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminReservationMessageKeys.RESERVATION_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void softDeleteBySiteId(long siteId) {
        List<AdminReservationEntity> reservations = adminReservationMapper.selectList(
                new LambdaQueryWrapper<AdminReservationEntity>()
                        .eq(AdminReservationEntity::getSiteId, siteId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminReservationEntity reservation : reservations) {
            reservation.setDeletedAt(now);
            adminReservationMapper.updateById(reservation);
            adminReservationMapper.deleteById(reservation.getId());
        }
    }

    private AdminSiteEntity requireSite(long siteId) {
        AdminSiteEntity site = adminSiteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
        return site;
    }

    private AdminReservationEntity requireReservationInSite(long siteId, long reservationId) {
        AdminReservationEntity reservation = adminReservationMapper.selectById(reservationId);
        // 跨站或不存在统一 404，避免泄露归属
        if (reservation == null || !Long.valueOf(siteId).equals(reservation.getSiteId())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminReservationMessageKeys.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    private static void requireStatus(String status) {
        if (!AdminReservationConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(),
                    AdminReservationMessageKeys.RESERVATION_STATUS_INVALID);
        }
    }

    private static void requireAllowedTransition(String from, String to) {
        if (from.equals(to)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(),
                    AdminReservationMessageKeys.RESERVATION_STATUS_TRANSITION_INVALID);
        }
        Set<String> allowed = AdminReservationConstants.ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(),
                    AdminReservationMessageKeys.RESERVATION_STATUS_TRANSITION_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private AdminReservationListItemDto toListItem(AdminReservationEntity entity) {
        return new AdminReservationListItemDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getCustomerName(),
                entity.getPhone(),
                entity.getServiceType(),
                entity.getReservedAt(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminReservationDetailDto toDetail(AdminReservationEntity entity) {
        return new AdminReservationDetailDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getCustomerName(),
                entity.getPhone(),
                entity.getServiceType(),
                entity.getReservedAt(),
                entity.getStatus(),
                entity.getNote(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
