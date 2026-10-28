package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.admin.entity.AdminAgentEntity;
import org.apache.ibatis.annotations.Mapper;

/** 映射平台 {@code agent} 表；与 agent-core {@code AgentMapper} 为同表双映射，admin 不依赖 agent-core。 */
@Mapper
public interface AdminAgentMapper extends BaseMapper<AdminAgentEntity> {}
