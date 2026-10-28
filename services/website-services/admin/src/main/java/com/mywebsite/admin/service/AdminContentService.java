package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminContentDtos.AdminContentCreateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentDetailDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListItemDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListQuery;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminContentService {

    PageResult<AdminContentListItemDto> listContents(long agentId, AdminContentListQuery query);

    AdminContentDetailDto getContent(long agentId, long contentId);

    AdminContentDetailDto createContent(long agentId, AdminContentCreateRequest request);

    AdminContentDetailDto updateContent(long agentId, long contentId, AdminContentUpdateRequest request);

    AdminContentDetailDto updateStatus(
            long agentId, long contentId, AdminContentStatusUpdateRequest request);

    void deleteContent(long agentId, long contentId);

    /** 站点软删联动：软删该站点全部内容。 */
    void softDeleteByAgentId(long agentId);
}
