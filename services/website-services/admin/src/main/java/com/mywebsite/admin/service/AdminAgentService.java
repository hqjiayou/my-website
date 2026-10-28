package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminAgentService {

    PageResult<AdminAgentListItemDto> listSites(AdminAgentListQuery query);

    AdminAgentDetailDto getSite(long id);

    AdminAgentDetailDto createSite(AdminAgentCreateRequest request);

    AdminAgentDetailDto updateSite(long id, AdminAgentUpdateRequest request);

    void deleteSite(long id);

    AdminAgentDetailDto updateStatus(long id, AdminAgentStatusUpdateRequest request);
}
