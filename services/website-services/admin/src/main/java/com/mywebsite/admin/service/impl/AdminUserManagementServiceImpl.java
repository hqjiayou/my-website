package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminRbacMessageKeys;
import com.mywebsite.admin.constant.AdminUserConstants;
import com.mywebsite.admin.constant.AdminUserMessageKeys;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserCreateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserDetailDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListItemDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListQuery;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserUpdateRequest;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.service.AdminPermissionQueryService;
import com.mywebsite.admin.service.AdminUserManagementService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminUserManagementServiceImpl implements AdminUserManagementService {

    private final AdminUserMapper adminUserMapper;

    private final AdminPermissionQueryService adminPermissionQueryService;

    private final PasswordEncoder passwordEncoder;

    public AdminUserManagementServiceImpl(
            AdminUserMapper adminUserMapper,
            AdminPermissionQueryService adminPermissionQueryService,
            PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.adminPermissionQueryService = adminPermissionQueryService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<AdminUserListItemDto> listUsers(AdminUserListQuery query) {
        LambdaQueryWrapper<AdminUserEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.username())) {
                wrapper.eq(AdminUserEntity::getUsername, query.username().trim());
            }
            if (StringUtils.hasText(query.status())) {
                requireStatus(query.status());
                wrapper.eq(AdminUserEntity::getStatus, query.status());
            }
            if (StringUtils.hasText(query.displayName())) {
                wrapper.like(AdminUserEntity::getDisplayName, query.displayName().trim());
            }
        }
        wrapper.orderByAsc(AdminUserEntity::getId);
        return adminUserMapper.selectList(wrapper).stream().map(this::toListItem).toList();
    }

    @Override
    public AdminUserDetailDto getUser(long id) {
        return toDetail(requireUser(id));
    }

    @Override
    public AdminUserDetailDto createUser(AdminUserCreateRequest request) {
        String username = request.username().trim();
        ensureUsernameFree(username);

        String email = normalizeEmail(request.email());
        if (email != null) {
            ensureEmailFree(email, null);
        }

        String phone = normalizePhone(request.phone());
        if (phone != null) {
            ensurePhoneFree(phone, null);
        }

        String password = request.password();
        if (password.length() < AdminUserConstants.PASSWORD_MIN_LENGTH
                || password.length() > AdminUserConstants.PASSWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminUserMessageKeys.PASSWORD_POLICY);
        }

        AdminUserEntity entity = new AdminUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setDisplayName(normalizeDisplayName(request.displayName()));
        entity.setEmail(email);
        entity.setPhone(phone);
        entity.setStatus(AdminUserConstants.STATUS_ACTIVE);
        try {
            adminUserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminUserMessageKeys.USERNAME_DUPLICATE);
        }
        return toDetail(requireUser(entity.getId()));
    }

    @Override
    public AdminUserDetailDto updateUser(long id, AdminUserUpdateRequest request) {
        AdminUserEntity user = requireUser(id);
        rejectIfSuperAdmin(id);

        String email = normalizeEmail(request.email());
        if (email != null) {
            ensureEmailFree(email, id);
        }
        String phone = normalizePhone(request.phone());
        if (phone != null) {
            ensurePhoneFree(phone, id);
        }

        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setEmail(email);
        user.setPhone(phone);
        adminUserMapper.updateById(user);
        return toDetail(requireUser(id));
    }

    @Override
    public AdminUserDetailDto updateStatus(long id, AdminUserStatusUpdateRequest request) {
        requireStatus(request.status());
        AdminUserEntity user = requireUser(id);
        rejectIfSelf(id, AdminUserMessageKeys.SELF_STATUS_DENIED);
        rejectIfSuperAdmin(id);
        user.setStatus(request.status());
        adminUserMapper.updateById(user);
        return toDetail(requireUser(id));
    }

    @Override
    public void deleteUser(long id) {
        requireUser(id);
        rejectIfSelf(id, AdminUserMessageKeys.SELF_DELETE_DENIED);
        rejectIfSuperAdmin(id);
        adminUserMapper.deleteById(id);
    }

    private AdminUserEntity requireUser(long id) {
        AdminUserEntity user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminRbacMessageKeys.USER_NOT_FOUND);
        }
        return user;
    }

    private void rejectIfSuperAdmin(long userId) {
        if (adminPermissionQueryService.isSuperAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.SUPER_ADMIN_PROTECTED);
        }
    }

    private void rejectIfSelf(long userId, String messageKey) {
        if (userId == requireCurrentUserId()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), messageKey);
        }
    }

    private long requireCurrentUserId() {
        return AuthContext.currentUser()
                .filter(u -> u.audience() == TokenAudience.ADMIN)
                .flatMap(u -> SubjectCodes.parseAdminUserId(u.subject()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "auth.unauthorized"));
    }

    /** 按 username 查未逻辑删除行（{@code @TableLogic}）；不按 ACTIVE 过滤，用于唯一性校验。 */
    private void ensureUsernameFree(String username) {
        AdminUserEntity dup = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>().eq(AdminUserEntity::getUsername, username));
        if (dup != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminUserMessageKeys.USERNAME_DUPLICATE);
        }
    }

    private void ensureEmailFree(String email, Long excludeUserId) {
        LambdaQueryWrapper<AdminUserEntity> wrapper =
                new LambdaQueryWrapper<AdminUserEntity>().eq(AdminUserEntity::getEmail, email);
        if (excludeUserId != null) {
            wrapper.ne(AdminUserEntity::getId, excludeUserId);
        }
        if (adminUserMapper.selectOne(wrapper) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminUserMessageKeys.EMAIL_DUPLICATE);
        }
    }

    private void ensurePhoneFree(String phone, Long excludeUserId) {
        LambdaQueryWrapper<AdminUserEntity> wrapper =
                new LambdaQueryWrapper<AdminUserEntity>().eq(AdminUserEntity::getPhone, phone);
        if (excludeUserId != null) {
            wrapper.ne(AdminUserEntity::getId, excludeUserId);
        }
        if (adminUserMapper.selectOne(wrapper) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminUserMessageKeys.PHONE_DUPLICATE);
        }
    }

    private static void requireStatus(String status) {
        if (!AdminUserConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminUserMessageKeys.STATUS_INVALID);
        }
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.trim();
    }

    private static String normalizeDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return null;
        }
        return displayName.trim();
    }

    private AdminUserListItemDto toListItem(AdminUserEntity entity) {
        long id = entity.getId();
        return new AdminUserListItemDto(
                id,
                SubjectCodes.formatAdminSubject(id),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getStatus(),
                adminPermissionQueryService.isSuperAdmin(id));
    }

    private AdminUserDetailDto toDetail(AdminUserEntity entity) {
        long id = entity.getId();
        return new AdminUserDetailDto(
                id,
                SubjectCodes.formatAdminSubject(id),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getStatus(),
                adminPermissionQueryService.isSuperAdmin(id),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
