package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaDetailDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListItemDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListQuery;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaPreviewDto;
import com.mywebsite.common.page.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface AdminMediaService {

    PageResult<AdminMediaListItemDto> listMedia(long agentId, AdminMediaListQuery query);

    AdminMediaDetailDto getMedia(long agentId, long mediaId);

    AdminMediaDetailDto upload(long agentId, MultipartFile file);

    AdminMediaPreviewDto preview(long agentId, long mediaId);

    /** 为本站建跨站引用：文件须经 PLATFORM 对本站可见；来源站本人与重复引用均为幂等成功。 */
    void referenceMedia(long agentId, long mediaId);

    /** 删本站引用：来源站本人 400、无引用 404。 */
    void unreferenceMedia(long agentId, long mediaId);

    /** 改可见范围：仅来源站可改，非法值 400。 */
    AdminMediaDetailDto updateScope(long agentId, long mediaId, String scope);

    void deleteMedia(long agentId, long mediaId);

    /** 站点软删联动：软删该站点全部媒体（对象由 orphan 任务清理）。 */
    void softDeleteByAgentId(long agentId);

    /** 定时扫尾：软删/FAILED/超时 UPLOADING 的对象清理。 */
    void purgeOrphans();
}
