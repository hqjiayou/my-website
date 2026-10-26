package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteUserMemoryDto;
import java.util.List;

/** 站点用户长期记忆。业务真相在 Java；Python 只负责抽取。 */
public interface SiteUserMemoryService {

    /** 注入模型的最近记忆，按时间从旧到新，最多 20 条。 */
    List<String> recentContents(long siteId, long userId);

    /**
     * 把本轮抽取结果规范化、与既有内容精确去重后逐条写入。
     * 空或缺失不写。每个 (site, user) 最多 50 条，超出时先删最旧再写。
     */
    void storeExtracted(long siteId, long userId, Long conversationId, List<String> extracted);

    /** 当前用户的记忆，时间倒序。 */
    List<SiteUserMemoryDto> listOwn(long siteId, long userId);

    /** 非本人或不存在为 NOT_FOUND，且不删除任何行。 */
    void deleteOwn(long siteId, long userId, long memoryId);

    /** 只清空该 (site, user) 的记忆。 */
    void clearOwn(long siteId, long userId);
}
