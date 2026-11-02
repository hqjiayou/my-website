package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigDto;
import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigUpdateRequest;
import com.mywebsite.admin.service.AdminAiRuntimeConfigService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentRuntimeConfigPayload;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.mywebsite.agent.entity.AgentRuntimeConfigEntity;
import com.mywebsite.agent.mapper.AgentRuntimeConfigMapper;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 运行配置读写＋推送（Issue #146 UoW-1）。
 *
 * <p>刻意不把「保存＋推送」包进一个事务：先落库（真相源），提交后再串行推送，
 * 推送结果单独更新 last_push_* 三列——推送失败绝不回滚保存。每次回写前都重读单行，
 * 保证乐观锁 version 是最新值。
 *
 * <p>脱敏纪律：DSN 原文只在解密后用于推送载荷与生成脱敏形，GET 响应、日志、
 * last_push_message 三处都不许出现 DSN/key 正文（落状态前再做一遍包含检查兜底）。
 */
@Service
public class AdminAiRuntimeConfigServiceImpl implements AdminAiRuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiRuntimeConfigServiceImpl.class);

    private static final int MAX_PUSH_MESSAGE_LENGTH = 512;

    private final AgentRuntimeConfigMapper runtimeConfigMapper;
    private final ModelEntryCipher cipher;
    private final AgentClient agentClient;

    public AdminAiRuntimeConfigServiceImpl(
            AgentRuntimeConfigMapper runtimeConfigMapper, ModelEntryCipher cipher, AgentClient agentClient) {
        this.runtimeConfigMapper = runtimeConfigMapper;
        this.cipher = cipher;
        this.agentClient = agentClient;
    }

    @Override
    public AdminAiRuntimeConfigDto getConfig() {
        return toDto(loadRow());
    }

    @Override
    public AdminAiRuntimeConfigDto updateConfig(AdminAiRuntimeConfigUpdateRequest request) {
        AgentRuntimeConfigEntity row = loadRow();
        if (StringUtils.hasText(request.retrievalDatabaseUrl())) {
            row.setRetrievalDatabaseUrl(cipher.encrypt(request.retrievalDatabaseUrl().trim()));
        }
        if (StringUtils.hasText(request.geminiApiKey())) {
            row.setGeminiApiKey(cipher.encrypt(request.geminiApiKey().trim()));
        }
        row.setAiEmbeddingModel(trimToNull(request.aiEmbeddingModel()));
        row.setAiEmbeddingDimensions(request.aiEmbeddingDimensions());
        row.setConfigVersion(currentVersion(row) + 1);
        persist(row);
        return pushAndRecord();
    }

    @Override
    public AdminAiRuntimeConfigDto retryPush() {
        return pushAndRecord();
    }

    /**
     * 推送当前行并把结果落 last_push_*。推送载荷用当前行解密值＋当前 config_version
     * （只重推最新版，不重发旧值）；推送本身在 {@link AgentClient} 内吞掉通道异常。
     */
    private AdminAiRuntimeConfigDto pushAndRecord() {
        AgentRuntimeConfigEntity row = loadRow();
        AgentRuntimeConfigPayload payload = new AgentRuntimeConfigPayload(
                decryptOrNull(row.getRetrievalDatabaseUrl()),
                row.getAiEmbeddingModel(),
                row.getAiEmbeddingDimensions(),
                decryptOrNull(row.getGeminiApiKey()),
                currentVersion(row));
        AgentRuntimeConfigPushResult result = agentClient.pushRuntimeConfig(payload);
        if (result == null) {
            result = AgentRuntimeConfigPushResult.failed("agent-services push returned no result");
        }
        AgentRuntimeConfigEntity statusRow = loadRow();
        statusRow.setLastPushStatus(result.status());
        statusRow.setLastPushMessage(sanitizePushMessage(result.message(), payload));
        statusRow.setLastPushedAt(OffsetDateTime.now(ZoneOffset.UTC));
        persist(statusRow);
        log.info(
                "runtime config push recorded configVersion={} status={}",
                currentVersion(statusRow),
                result.status());
        return toDto(loadRow());
    }

    /** V47 已预置 id=1 空行；行缺失（测试夹具等）时补插一行空配置，并发撞键则重读。 */
    private AgentRuntimeConfigEntity loadRow() {
        AgentRuntimeConfigEntity row = runtimeConfigMapper.selectById(AgentRuntimeConfigEntity.SINGLETON_ID);
        if (row != null) {
            return row;
        }
        AgentRuntimeConfigEntity empty = new AgentRuntimeConfigEntity();
        empty.setId(AgentRuntimeConfigEntity.SINGLETON_ID);
        empty.setConfigVersion(0L);
        try {
            runtimeConfigMapper.insert(empty);
            return empty;
        } catch (DuplicateKeyException ex) {
            AgentRuntimeConfigEntity raced = runtimeConfigMapper.selectById(AgentRuntimeConfigEntity.SINGLETON_ID);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
    }

    /** 调用方都先经 {@link #loadRow()} 保证单行存在，这里只做更新。 */
    private void persist(AgentRuntimeConfigEntity row) {
        runtimeConfigMapper.updateById(row);
    }

    private AdminAiRuntimeConfigDto toDto(AgentRuntimeConfigEntity row) {
        String plainDsn = decryptOrNull(row.getRetrievalDatabaseUrl());
        String plainKey = decryptOrNull(row.getGeminiApiKey());
        return new AdminAiRuntimeConfigDto(
                StringUtils.hasText(plainDsn),
                maskDsn(plainDsn),
                row.getAiEmbeddingModel(),
                row.getAiEmbeddingDimensions(),
                StringUtils.hasText(plainKey),
                AdminAiModelEntryServiceImpl.apiKeyHint(plainKey),
                currentVersion(row),
                row.getLastPushStatus(),
                row.getLastPushMessage(),
                row.getLastPushedAt(),
                row.getVersion(),
                row.getUpdatedAt());
    }

    /**
     * DSN 脱敏：解析 URI 后只拼 {@code scheme://host:port/dbname}，丢弃 userinfo 与
     * query（口令在 userinfo 里）。无 scheme 的连接串保留 scheme 段（authority 含口令风险，
     * 宁可少给）；解析不出 host 时返回 null，调用方以 configured 布尔表达已配置。
     */
    static String maskDsn(String dsn) {
        if (!StringUtils.hasText(dsn)) {
            return null;
        }
        String value = dsn.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme)) {
                return null;
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return scheme + "://…";
            }
            StringBuilder masked = new StringBuilder(scheme).append("://").append(host);
            if (uri.getPort() > 0) {
                masked.append(':').append(uri.getPort());
            }
            String path = uri.getPath();
            if (StringUtils.hasText(path) && !"/".equals(path)) {
                masked.append(path);
            }
            return masked.toString();
        } catch (URISyntaxException ex) {
            int schemeEnd = value.indexOf("://");
            return schemeEnd > 0 ? value.substring(0, schemeEnd) + "://…" : null;
        }
    }

    /**
     * 推送状态说明的兜底清洗：万一上游原因里混入了 DSN 或 key 正文，整条换成通用文案；
     * 长度截到列宽 512。
     */
    private static String sanitizePushMessage(String message, AgentRuntimeConfigPayload payload) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        if ((StringUtils.hasText(payload.retrievalDatabaseUrl()) && trimmed.contains(payload.retrievalDatabaseUrl()))
                || (StringUtils.hasText(payload.geminiApiKey()) && trimmed.contains(payload.geminiApiKey()))) {
            return "push rejected (reason withheld: contained secret material)";
        }
        return trimmed.length() > MAX_PUSH_MESSAGE_LENGTH
                ? trimmed.substring(0, MAX_PUSH_MESSAGE_LENGTH)
                : trimmed;
    }

    /** 密文解不开（未配口令等）时按未配置处理，不把密文当明文用（与 ModelEntryCipher 口径一致）。 */
    private String decryptOrNull(String stored) {
        String plain = cipher.decrypt(stored);
        return StringUtils.hasText(plain) ? plain : null;
    }

    private static long currentVersion(AgentRuntimeConfigEntity row) {
        return row.getConfigVersion() == null ? 0L : row.getConfigVersion();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
