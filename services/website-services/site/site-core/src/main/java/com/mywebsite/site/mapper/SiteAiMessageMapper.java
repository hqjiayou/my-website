package com.mywebsite.site.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SiteAiMessageMapper extends BaseMapper<SiteAiMessageEntity> {

    /**
     * 物理删除该会话全部消息。外键 {@code ON DELETE RESTRICT}，且 {@code @TableLogic} 会使
     * {@code BaseMapper.delete} 变成软删，显式 DELETE 才满足硬删。
     */
    @Delete("DELETE FROM site_ai_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") long conversationId);
}
