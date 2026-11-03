package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentExperienceLessonMapper extends BaseMapper<AgentExperienceLessonEntity> {

    /**
     * 当前已分配的最大 L 编号（不分状态：停用条目的编号不回收、永不重用）；
     * 尚无已批准条目时为 null，由服务层按下限 {@code FIRST_LESSON_NO} 起分。
     */
    @Select("""
            SELECT MAX(lesson_no) FROM agent_experience_lesson
            WHERE lesson_no IS NOT NULL AND deleted = FALSE
            """)
    Integer selectMaxLessonNo();

    /** 审核队列：按状态取条目（创建时间升序，先来先审）。 */
    @Select("""
            SELECT id, title, symptom, lesson, evidence,
                   source_agent_id, source_conversation_id, source_message_id,
                   status, lesson_no, reviewed_at,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM agent_experience_lesson
            WHERE status = #{status} AND deleted = FALSE
            ORDER BY created_at ASC, id ASC
            """)
    List<AgentExperienceLessonEntity> selectByStatus(@Param("status") String status);

    /** 已批准条目（UoW-4 请求注入用）：按 L 编号升序，即批准先后。 */
    @Select("""
            SELECT id, title, symptom, lesson, evidence,
                   source_agent_id, source_conversation_id, source_message_id,
                   status, lesson_no, reviewed_at,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM agent_experience_lesson
            WHERE status = 'APPROVED' AND deleted = FALSE
            ORDER BY lesson_no ASC
            """)
    List<AgentExperienceLessonEntity> selectApproved();
}
