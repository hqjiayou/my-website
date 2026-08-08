package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteCreateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteDetailDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListItemDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListQuery;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminSiteService {

    PageResult<AdminSiteListItemDto> listSites(AdminSiteListQuery query);

    AdminSiteDetailDto getSite(long id);

    AdminSiteDetailDto createSite(AdminSiteCreateRequest request);

    AdminSiteDetailDto updateSite(long id, AdminSiteUpdateRequest request);

    void deleteSite(long id);

    AdminSiteDetailDto updateStatus(long id, AdminSiteStatusUpdateRequest request);
}
