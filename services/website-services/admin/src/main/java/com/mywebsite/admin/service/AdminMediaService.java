package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaDetailDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListItemDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListQuery;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaPreviewDto;
import com.mywebsite.common.page.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface AdminMediaService {

    PageResult<AdminMediaListItemDto> listMedia(long siteId, AdminMediaListQuery query);

    AdminMediaDetailDto getMedia(long siteId, long mediaId);

    AdminMediaDetailDto upload(long siteId, MultipartFile file);

    AdminMediaPreviewDto preview(long siteId, long mediaId);

    void deleteMedia(long siteId, long mediaId);

    /** 站点软删联动：软删该站点全部媒体（对象由 orphan 任务清理）。 */
    void softDeleteBySiteId(long siteId);

    /** 定时扫尾：软删/FAILED/超时 UPLOADING 的对象清理。 */
    void purgeOrphans();
}
