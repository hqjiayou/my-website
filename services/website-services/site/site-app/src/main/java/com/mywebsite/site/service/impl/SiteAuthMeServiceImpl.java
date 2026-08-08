package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.site.dto.SiteAuthMeResponseDto;
import com.mywebsite.site.dto.SiteAuthMeUserDto;
import com.mywebsite.site.dto.SiteMenuNodeDto;
import com.mywebsite.site.entity.SiteUserEntity;
import com.mywebsite.site.entity.SiteUserProfileEntity;
import com.mywebsite.site.mapper.SiteUserMapper;
import com.mywebsite.site.mapper.SiteUserProfileMapper;
import com.mywebsite.site.service.SiteAuthMeService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SiteAuthMeServiceImpl implements SiteAuthMeService {

    private static final String ACTIVE_SITE_USER_STATUS = "ACTIVE";

    private final SiteUserMapper siteUserMapper;

    private final SiteUserProfileMapper siteUserProfileMapper;

    public SiteAuthMeServiceImpl(SiteUserMapper siteUserMapper, SiteUserProfileMapper siteUserProfileMapper) {
        this.siteUserMapper = siteUserMapper;
        this.siteUserProfileMapper = siteUserProfileMapper;
    }

    @Override
    public SiteAuthMeResponseDto loadCurrentSiteUserContext() {
        AuthenticatedUser current = AuthContext.currentUser().orElseThrow(IllegalStateException::new);
        long userId = SubjectCodes.parseSiteUserId(current.subject())
                .orElseThrow(() -> new IllegalStateException("invalid site subject in security context"));
        Long jwtSiteId = current.siteIdOrNull();
        if (jwtSiteId == null || jwtSiteId <= 0L) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessageKey());
        }

        SiteUserEntity entity = siteUserMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessageKey());
        }
        // JWT site_id 必须与库中用户归属一致，防止跨站点上下文冒用
        if (!Objects.equals(jwtSiteId, entity.getSiteId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessageKey());
        }
        String userStatus = entity.getStatus() == null ? "" : entity.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessageKey());
        }

        SiteUserProfileEntity profile = siteUserProfileMapper.selectOne(new LambdaQueryWrapper<SiteUserProfileEntity>()
                .eq(SiteUserProfileEntity::getSiteUserId, userId));

        String userCode = SubjectCodes.formatSiteUserSubject(userId);
        SiteAuthMeUserDto user = new SiteAuthMeUserDto(
                userCode,
                entity.getUsername(),
                profile == null ? null : profile.getDisplayName(),
                profile == null ? null : profile.getAvatarUrl(),
                entity.getStatus());

        // 本轮不做 site_role RBAC：roles / permissions / menus 固定空列表
        List<String> roles = List.of();
        List<String> permissions = List.of();
        List<SiteMenuNodeDto> menus = List.of();

        return new SiteAuthMeResponseDto(user, roles, permissions, menus);
    }
}
