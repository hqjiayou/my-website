package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyDto;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueRequest;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueResponse;
import com.mywebsite.admin.entity.AdminGatewayAccessKeyEntity;
import com.mywebsite.admin.mapper.AdminGatewayAccessKeyMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** issue 存哈希不存明文、明文仅在签发响应中出现一次；revoke 置 revokedAt 且幂等。 */
@ExtendWith(MockitoExtension.class)
class AdminGatewayAccessKeyServiceImplTest {

    @Mock
    private AdminGatewayAccessKeyMapper accessKeyMapper;

    private AdminGatewayAccessKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminGatewayAccessKeyServiceImpl(accessKeyMapper);
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

    @Test
    void issueStoresHashNotPlaintextAndReturnsPlaintextOnlyInResponse() {
        ArgumentCaptor<AdminGatewayAccessKeyEntity> captor = ArgumentCaptor.forClass(AdminGatewayAccessKeyEntity.class);
        when(accessKeyMapper.insert(captor.capture())).thenAnswer(invocation -> {
            captor.getValue().setId(1L);
            return 1;
        });
        // issue() 在 insert 后会回读一行，取 DB 填充的 created_at / version；
        // mock 须把回读行补上，否则 stored 为 null（Issue #163 的 NPE 来源）。
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        when(accessKeyMapper.selectById(1L)).thenAnswer(invocation -> {
            AdminGatewayAccessKeyEntity row = captor.getValue();
            row.setCreatedAt(createdAt);
            row.setVersion(0);
            return row;
        });

        AdminGatewayAccessKeyIssueResponse response = service.issue(new AdminGatewayAccessKeyIssueRequest("ext-client", null));

        AdminGatewayAccessKeyEntity stored = captor.getValue();
        assertThat(response.apiKey()).startsWith("mwgw_");
        assertThat(stored.getKeyHash()).isEqualTo(sha256Hex(response.apiKey()));
        assertThat(stored.getKeyHash()).isNotEqualTo(response.apiKey());
        assertThat(response.keyHint()).isEqualTo(response.apiKey().substring(response.apiKey().length() - 4));
        assertThat(stored.getKeyHint()).isEqualTo(response.keyHint());
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.version()).isEqualTo(0);
    }

    @Test
    void listMapsToDtoWithoutHashOrPlaintext() {
        AdminGatewayAccessKeyEntity entity = new AdminGatewayAccessKeyEntity();
        entity.setId(2L);
        entity.setName("ext-client");
        entity.setKeyHash("deadbeef");
        entity.setKeyHint("ab12");
        when(accessKeyMapper.selectList(any())).thenReturn(List.of(entity));

        AdminGatewayAccessKeyDto dto = service.list().get(0);

        assertThat(dto.keyHint()).isEqualTo("ab12");
        assertThat(dto.id()).isEqualTo(2L);
    }

    @Test
    void revokeSetsRevokedAtAndIsIdempotent() {
        AdminGatewayAccessKeyEntity entity = new AdminGatewayAccessKeyEntity();
        entity.setId(3L);
        entity.setName("ext-client");
        when(accessKeyMapper.selectById(3L)).thenReturn(entity);
        when(accessKeyMapper.updateById(any(AdminGatewayAccessKeyEntity.class))).thenReturn(1);

        AdminGatewayAccessKeyDto revoked = service.revoke(3L);
        assertThat(revoked.revokedAt()).isNotNull();

        // 第二次调用：mapper 返回已吊销的实体（revokedAt 非空），服务保持原样幂等返回。
        OffsetDateTime firstRevokedAt = entity.getRevokedAt();
        AdminGatewayAccessKeyDto revokedAgain = service.revoke(3L);
        assertThat(revokedAgain.revokedAt()).isEqualTo(firstRevokedAt);
    }
}
