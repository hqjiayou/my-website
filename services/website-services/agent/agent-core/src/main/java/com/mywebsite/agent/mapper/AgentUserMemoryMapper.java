package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentUserMemoryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentUserMemoryMapper extends BaseMapper<AgentUserMemoryEntity> {

    @Select("""
            SELECT content FROM site_user_memory
            WHERE site_id = #{agentId} AND user_id = #{userId} AND deleted = FALSE
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<String> selectRecentContents(
            @Param("agentId") long agentId, @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT content FROM site_user_memory
            WHERE site_id = #{agentId} AND user_id = #{userId} AND deleted = FALSE
            """)
    List<String> selectActiveContents(@Param("agentId") long agentId, @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*) FROM site_user_memory
            WHERE site_id = #{agentId} AND user_id = #{userId} AND deleted = FALSE
            """)
    long countActive(@Param("agentId") long agentId, @Param("userId") long userId);

    @Select("""
            SELECT id FROM site_user_memory
            WHERE site_id = #{agentId} AND user_id = #{userId} AND deleted = FALSE
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """)
    Long selectOldestId(@Param("agentId") long agentId, @Param("userId") long userId);

    @Select("""
            SELECT id, site_id, user_id, content, source_conversation_id,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM site_user_memory
            WHERE site_id = #{agentId} AND user_id = #{userId} AND deleted = FALSE
            ORDER BY created_at DESC, id DESC
            """)
    List<AgentUserMemoryEntity> selectOwn(@Param("agentId") long agentId, @Param("userId") long userId);
}
