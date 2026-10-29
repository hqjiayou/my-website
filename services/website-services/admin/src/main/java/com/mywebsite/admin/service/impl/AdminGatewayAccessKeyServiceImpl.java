package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyDto;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueRequest;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueResponse;
import com.mywebsite.admin.entity.AdminGatewayAccessKeyEntity;
import com.mywebsite.admin.mapper.AdminGatewayAccessKeyMapper;
import com.mywebsite.admin.service.AdminGatewayAccessKeyService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminGatewayAccessKeyServiceImpl implements AdminGatewayAccessKeyService {

    private static final String KEY_PREFIX = "mwgw_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminGatewayAccessKeyMapper accessKeyMapper;

    public AdminGatewayAccessKeyServiceImpl(AdminGatewayAccessKeyMapper accessKeyMapper) {
        this.accessKeyMapper = accessKeyMapper;
    }

    @Override
    @Transactional
    public AdminGatewayAccessKeyIssueResponse issue(AdminGatewayAccessKeyIssueRequest request) {
        String plainKey = generateApiKey();
        AdminGatewayAccessKeyEntity entity = new AdminGatewayAccessKeyEntity();
        entity.setName(request.name().trim());
        entity.setKeyHash(sha256Hex(plainKey));
        entity.setKeyHint(plainKey.substring(plainKey.length() - 4));
        entity.setExpiresAt(request.expiresAt());
        accessKeyMapper.insert(entity);
        // 回读一次，拿到 DB 填充的 created_at / version（insert 后的内存对象这两项为空）。
        AdminGatewayAccessKeyEntity stored = accessKeyMapper.selectById(entity.getId());
        return new AdminGatewayAccessKeyIssueResponse(
                stored.getId(),
                stored.getName(),
                stored.getKeyHint(),
                stored.getExpiresAt(),
                stored.getRevokedAt(),
                stored.getCreatedAt(),
                stored.getVersion(),
                plainKey);
    }

    @Override
    @Transactional
    public List<AdminGatewayAccessKeyDto> list() {
        return accessKeyMapper
                .selectList(new LambdaQueryWrapper<AdminGatewayAccessKeyEntity>()
                        .orderByDesc(AdminGatewayAccessKeyEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AdminGatewayAccessKeyDto revoke(long id) {
        AdminGatewayAccessKeyEntity entity = accessKeyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (entity.getRevokedAt() != null) {
            // 已吊销：重复调用保持原样，不触碰 version。
            return toDto(entity);
        }
        entity.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        int updated = accessKeyMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(accessKeyMapper.selectById(id));
    }

    private static String generateApiKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private AdminGatewayAccessKeyDto toDto(AdminGatewayAccessKeyEntity entity) {
        return new AdminGatewayAccessKeyDto(
                entity.getId(),
                entity.getName(),
                entity.getKeyHint(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
