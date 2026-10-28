package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentAiMessageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentAiMessageMapper extends BaseMapper<AgentAiMessageEntity> {

    /**
     * 物理删除该会话全部消息。外键 {@code ON DELETE RESTRICT}，且 {@code @TableLogic} 会使
     * {@code BaseMapper.delete} 变成软删，显式 DELETE 才满足硬删。
     */
    @Delete("DELETE FROM site_ai_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") long conversationId);

    /**
     * 物理删除该会话中 id 不小于 {@code fromMessageId} 的全部消息（编辑/重答的后端截断）。
     * 同样须显式 DELETE 绕开 {@code @TableLogic} 软删。
     */
    @Delete("DELETE FROM site_ai_message WHERE conversation_id = #{conversationId} AND id >= #{fromMessageId}")
    int deleteFromMessage(
            @Param("conversationId") long conversationId, @Param("fromMessageId") long fromMessageId);
}
