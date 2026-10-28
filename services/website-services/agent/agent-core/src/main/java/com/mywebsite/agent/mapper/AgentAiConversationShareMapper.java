package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentAiConversationShareEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentAiConversationShareMapper extends BaseMapper<AgentAiConversationShareEntity> {

    /** 物理删除该会话的分享（撤销）。须显式 DELETE 绕开 {@code @TableLogic} 软删，token 立即失效。 */
    @Delete("DELETE FROM agent_ai_conversation_share WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") long conversationId);
}
