package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateDetailDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListItemDto;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateListQuery;
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateStatusUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminTemplateService {

    PageResult<AdminTemplateListItemDto> listTemplates(AdminTemplateListQuery query);

    AdminTemplateDetailDto getTemplate(long id);

    AdminTemplateDetailDto getTemplateByCode(String code);

    AdminTemplateDetailDto updateStatus(long id, AdminTemplateStatusUpdateRequest request);
}
