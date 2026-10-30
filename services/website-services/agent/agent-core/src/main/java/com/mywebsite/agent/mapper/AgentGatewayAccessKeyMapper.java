package com.mywebsite.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.agent.entity.AgentGatewayAccessKeyEntity;
import org.apache.ibatis.annotations.Mapper;

/** 只读：网关 {@code /v1/**} 鉴权过滤器按 key_hash 查询。签发/吊销走 admin 侧。 */
@Mapper
public interface AgentGatewayAccessKeyMapper extends BaseMapper<AgentGatewayAccessKeyEntity> {}
