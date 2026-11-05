package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.admin.entity.AdminAgentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 映射平台 {@code agent} 表；与 agent-core {@code AgentMapper} 为同表双映射，admin 不依赖 agent-core。 */
@Mapper
public interface AdminAgentMapper extends BaseMapper<AdminAgentEntity> {

    /**
     * 已软删 agent 的 id：{@code @TableLogic} 会把 deleted 行从常规查询里滤掉，
     * 需要覆盖已删 agent 的场景（如全量重建对旧分区的清扫与残留检查）直查。
     */
    @Select("SELECT id FROM agent WHERE deleted = TRUE")
    List<Long> selectDeletedAgentIds();
}
