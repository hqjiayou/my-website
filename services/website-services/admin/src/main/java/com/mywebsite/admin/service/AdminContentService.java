package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminContentDtos.AdminContentCreateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentDetailDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListItemDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListQuery;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminContentService {

    PageResult<AdminContentListItemDto> listContents(long siteId, AdminContentListQuery query);

    AdminContentDetailDto getContent(long siteId, long contentId);

    AdminContentDetailDto createContent(long siteId, AdminContentCreateRequest request);

    AdminContentDetailDto updateContent(long siteId, long contentId, AdminContentUpdateRequest request);

    AdminContentDetailDto updateStatus(
            long siteId, long contentId, AdminContentStatusUpdateRequest request);

    void deleteContent(long siteId, long contentId);

    /** 站点软删联动：软删该站点全部内容。 */
    void softDeleteBySiteId(long siteId);
}
