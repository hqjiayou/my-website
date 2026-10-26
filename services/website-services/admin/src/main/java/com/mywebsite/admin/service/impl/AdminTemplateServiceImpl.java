package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminTemplateConstants;
import com.mywebsite.admin.constant.AdminTemplateMessageKeys;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateDetailDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListItemDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListQuery;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateStatusUpdateRequest;
import com.mywebsite.admin.entity.AdminTemplateEntity;
import com.mywebsite.admin.mapper.AdminTemplateMapper;
import com.mywebsite.admin.service.AdminTemplateService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminTemplateServiceImpl implements AdminTemplateService {

    private final AdminTemplateMapper adminTemplateMapper;

    public AdminTemplateServiceImpl(AdminTemplateMapper adminTemplateMapper) {
        this.adminTemplateMapper = adminTemplateMapper;
    }

    @Override
    public PageResult<AdminTemplateListItemDto> listTemplates(AdminTemplateListQuery query) {
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminTemplateConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminTemplateConstants.MAX_PAGE_SIZE)
                : AdminTemplateConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.status())) {
                requireStatus(query.status());
                wrapper.eq(AdminTemplateEntity::getStatus, query.status());
            }
            if (StringUtils.hasText(query.siteType())) {
                // 不再校验闭集：按任意标签过滤，无匹配时自然返回空列表（模板目录本身仍是旧类型种子）。
                wrapper.eq(AdminTemplateEntity::getSiteType, query.siteType());
            }
        }
        wrapper.orderByAsc(AdminTemplateEntity::getSortOrder).orderByAsc(AdminTemplateEntity::getId);

        Page<AdminTemplateEntity> page =
                adminTemplateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminTemplateDetailDto getTemplate(long id) {
        return toDetail(requireTemplate(id));
    }

    @Override
    public AdminTemplateDetailDto getTemplateByCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminTemplateMessageKeys.TEMPLATE_NOT_FOUND);
        }
        AdminTemplateEntity entity = adminTemplateMapper.selectOne(
                new LambdaQueryWrapper<AdminTemplateEntity>().eq(AdminTemplateEntity::getCode, code));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminTemplateMessageKeys.TEMPLATE_NOT_FOUND);
        }
        return toDetail(entity);
    }

    @Override
    public AdminTemplateDetailDto updateStatus(long id, AdminTemplateStatusUpdateRequest request) {
        requireStatus(request.status());
        AdminTemplateEntity template = requireTemplate(id);
        template.setStatus(request.status());
        template.setVersion(request.version());
        int updated = adminTemplateMapper.updateById(template);
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminTemplateMessageKeys.TEMPLATE_VERSION_CONFLICT);
        }
        return toDetail(requireTemplate(id));
    }

    private AdminTemplateEntity requireTemplate(long id) {
        AdminTemplateEntity template = adminTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminTemplateMessageKeys.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private static void requireStatus(String status) {
        if (!AdminTemplateConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminTemplateMessageKeys.TEMPLATE_STATUS_INVALID);
        }
    }

    private AdminTemplateListItemDto toListItem(AdminTemplateEntity entity) {
        return new AdminTemplateListItemDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSiteType(),
                entity.getStatus(),
                entity.getPreviewImageUrl(),
                entity.getDefaultPrimaryColor(),
                entity.getSortOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminTemplateDetailDto toDetail(AdminTemplateEntity entity) {
        return new AdminTemplateDetailDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSiteType(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getPreviewImageUrl(),
                entity.getDefaultPrimaryColor(),
                entity.getSortOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
