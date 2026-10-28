package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.agent.dto.AgentAuthMeResponseDto;
import com.mywebsite.agent.dto.AgentAuthMeAgentDto;
import com.mywebsite.agent.dto.AgentAuthMeUserDto;
import com.mywebsite.agent.dto.AgentMenuNodeDto;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.entity.AgentUserEntity;
import com.mywebsite.agent.entity.AgentUserProfileEntity;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserProfileMapper;
import com.mywebsite.agent.service.AgentAuthMeService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AgentAuthMeServiceImpl implements AgentAuthMeService {

    private static final String ACTIVE_SITE_USER_STATUS = "ACTIVE";

    private final AgentUserMapper agentUserMapper;

    private final AgentUserProfileMapper agentUserProfileMapper;

    private final AgentMapper agentMapper;

    public AgentAuthMeServiceImpl(
            AgentUserMapper agentUserMapper, AgentUserProfileMapper agentUserProfileMapper, AgentMapper agentMapper) {
        this.agentUserMapper = agentUserMapper;
        this.agentUserProfileMapper = agentUserProfileMapper;
        this.agentMapper = agentMapper;
    }

    @Override
    public AgentAuthMeResponseDto loadCurrentAgentUserContext() {
        AuthenticatedUser current = AuthContext.currentUser().orElseThrow(IllegalStateException::new);
        long userId = SubjectCodes.parseAgentUserId(current.subject())
                .orElseThrow(() -> new IllegalStateException("invalid agent subject in security context"));
        Long jwtAgentId = current.agentIdOrNull();
        if (jwtAgentId == null || jwtAgentId <= 0L) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessageKey());
        }

        AgentUserEntity entity = agentUserMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessageKey());
        }
        // JWT agent_id 必须与库中用户归属一致，防止跨站点上下文冒用
        if (!Objects.equals(jwtAgentId, entity.getAgentId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessageKey());
        }
        String userStatus = entity.getStatus() == null ? "" : entity.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessageKey());
        }

        AgentUserProfileEntity profile = agentUserProfileMapper.selectOne(new LambdaQueryWrapper<AgentUserProfileEntity>()
                .eq(AgentUserProfileEntity::getAgentUserId, userId));

        String userCode = SubjectCodes.formatAgentUserSubject(userId);
        AgentAuthMeUserDto user = new AgentAuthMeUserDto(
                userCode,
                entity.getUsername(),
                profile == null ? null : profile.getDisplayName(),
                profile == null ? null : profile.getAvatarUrl(),
                entity.getStatus());

        // 本轮不做 site_role RBAC：roles / permissions / menus 固定空列表
        List<String> roles = List.of();
        List<String> permissions = List.of();
        List<AgentMenuNodeDto> menus = List.of();

        // 站点行缺失时 agent 为 null，不让 /me 因站点资料缺口整体失败
        AgentEntity agentEntity = agentMapper.selectById(jwtAgentId);
        AgentAuthMeAgentDto agent = null;
        if (agentEntity != null && agentEntity.getId() != null) {
            agent = new AgentAuthMeAgentDto(agentEntity.getId(), agentEntity.getAgentCode(), agentEntity.getAgentName());
        }

        return new AgentAuthMeResponseDto(user, roles, permissions, menus, agent);
    }
}
