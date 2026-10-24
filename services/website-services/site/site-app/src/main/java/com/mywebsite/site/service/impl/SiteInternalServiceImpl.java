package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationCreateRequest;
import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentItemDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentListDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteSummaryDto;
import com.mywebsite.site.entity.SiteContentEntity;
import com.mywebsite.site.entity.SiteEntity;
import com.mywebsite.site.entity.SiteReservationEntity;
import com.mywebsite.site.mapper.SiteContentMapper;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.mapper.SiteReservationMapper;
import com.mywebsite.site.service.SiteInternalService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class SiteInternalServiceImpl implements SiteInternalService {

    private static final int MAX_CONTENT_LIMIT = 50;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final SiteMapper siteMapper;
    private final SiteContentMapper siteContentMapper;
    private final SiteReservationMapper siteReservationMapper;
    private final TransactionTemplate requiresNew;

    public SiteInternalServiceImpl(
            SiteMapper siteMapper,
            SiteContentMapper siteContentMapper,
            SiteReservationMapper siteReservationMapper,
            PlatformTransactionManager transactionManager) {
        this.siteMapper = siteMapper;
        this.siteContentMapper = siteContentMapper;
        this.siteReservationMapper = siteReservationMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public SiteSummaryDto getSiteSummary(long siteId) {
        SiteEntity site = requireSite(siteId);
        return new SiteSummaryDto(site.getId(), site.getSiteName(), site.getSiteType(), site.getDescription());
    }

    @Override
    public SiteContentListDto listPublishedContents(long siteId, String type, int limit) {
        requireSite(siteId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_CONTENT_LIMIT);
        LambdaQueryWrapper<SiteContentEntity> wrapper = new LambdaQueryWrapper<SiteContentEntity>()
                .eq(SiteContentEntity::getSiteId, siteId)
                .eq(SiteContentEntity::getStatus, SiteContentEntity.STATUS_PUBLISHED)
                .orderByAsc(SiteContentEntity::getSortOrder)
                .orderByDesc(SiteContentEntity::getId)
                .last("LIMIT " + effectiveLimit);
        if (StringUtils.hasText(type)) {
            wrapper.eq(SiteContentEntity::getType, type.trim());
        }
        return new SiteContentListDto(siteContentMapper.selectList(wrapper).stream()
                .map(c -> new SiteContentItemDto(c.getId(), c.getType(), c.getTitle(), c.getBody()))
                .toList());
    }

    @Override
    @Transactional
    public InternalReservationDto createReservation(long siteId, InternalReservationCreateRequest request) {
        return createReservation(siteId, request, null);
    }

    @Override
    @Transactional
    public InternalReservationDto createReservation(
            long siteId, InternalReservationCreateRequest request, String idempotencyKey) {
        requireSite(siteId);
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            SiteReservationEntity existing = findActiveByIdempotencyKey(siteId, key);
            if (existing != null) {
                return toDto(existing);
            }
        }
        SiteReservationEntity entity = new SiteReservationEntity();
        entity.setSiteId(siteId);
        entity.setCustomerName(request.customerName().trim());
        // PII：仅持久化，禁止日志打全量 phone
        entity.setPhone(request.phone().trim());
        entity.setServiceType(request.serviceType().trim());
        entity.setReservedAt(request.reservedAt());
        entity.setStatus(SiteReservationEntity.STATUS_NEW);
        entity.setNote(StringUtils.hasText(request.note()) ? request.note() : null);
        entity.setIdempotencyKey(key);
        try {
            insertReservation(entity, key != null);
        } catch (DataIntegrityViolationException ex) {
            if (key == null) {
                throw ex;
            }
            // 并发插入撞上 (site_id, idempotency_key) 部分唯一索引：回已提交的那一行
            SiteReservationEntity existing = findActiveByIdempotencyKey(siteId, key);
            if (existing == null) {
                throw ex;
            }
            return toDto(existing);
        }
        return toDto(entity);
    }

    /**
     * 唯一索引冲突会让 PostgreSQL 当前事务进入 aborted，之后无法再 SELECT。
     * 有幂等键时插入放在独立事务里，失败回滚后外层事务仍能重查既有行。
     */
    private void insertReservation(SiteReservationEntity entity, boolean isolated) {
        if (!isolated) {
            siteReservationMapper.insert(entity);
            return;
        }
        requiresNew.executeWithoutResult(status -> siteReservationMapper.insert(entity));
    }

    private SiteReservationEntity findActiveByIdempotencyKey(long siteId, String idempotencyKey) {
        return siteReservationMapper.selectOne(new LambdaQueryWrapper<SiteReservationEntity>()
                .eq(SiteReservationEntity::getSiteId, siteId)
                .eq(SiteReservationEntity::getIdempotencyKey, idempotencyKey)
                .eq(SiteReservationEntity::getDeleted, false)
                .last("LIMIT 1"));
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return key;
    }

    private static InternalReservationDto toDto(SiteReservationEntity entity) {
        return new InternalReservationDto(
                entity.getId(),
                entity.getCustomerName(),
                entity.getServiceType(),
                entity.getReservedAt(),
                entity.getStatus());
    }

    private SiteEntity requireSite(long siteId) {
        SiteEntity site = siteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return site;
    }
}
