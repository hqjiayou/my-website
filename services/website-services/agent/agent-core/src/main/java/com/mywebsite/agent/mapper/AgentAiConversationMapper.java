package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentAiConversationMapper extends BaseMapper<AgentAiConversationEntity> {

    /** 物理删除会话行，绕过 {@code @TableLogic}。调用前须已删除其消息（外键 RESTRICT）。 */
    @Delete("DELETE FROM site_ai_conversation WHERE id = #{id}")
    int deleteByIdPhysical(@Param("id") long id);
}
