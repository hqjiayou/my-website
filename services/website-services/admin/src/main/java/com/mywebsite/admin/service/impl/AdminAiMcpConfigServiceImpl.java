package com.mywebsite.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.McpServerEntryDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.McpServerEntryInput;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.SecretKvDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.SecretKvInput;
import com.mywebsite.admin.service.AdminAiMcpConfigService;
import com.mywebsite.admin.service.AdminAiRuntimeConfigService;
import com.mywebsite.agent.entity.AgentRuntimeConfigEntity;
import com.mywebsite.agent.mapper.AgentRuntimeConfigMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MCP 全局注册表读写＋推送（Issue #192 UoW-1）。
 *
 * <p>存储：整份条目 JSON 以 {@code ModelEntryCipher} 加密存单行
 * {@code mcp_servers_json} 列（条目内 headers/env/url 可能含密钥，整块加密
 * 最稳妥）。保存与推送分工沿 #146：先落库（真相源、config_version +1），
 * 再调 {@link AdminAiRuntimeConfigService#retryPush()} 走同一推送管线把全量
 * 配置（含本次 MCP）推给 agent-services 并落 last_push_*；推送失败不回滚保存。
 *
 * <p>合并语义（与 DTO javadoc 一致）：条目按 name 对齐既有条目；url 与
 * headers/env 的值是密钥面——留空保留、clear 删键、填值替换；其余字段整体
 * 替换。脱敏纪律：条目明文只在内存合并与加密/推送载荷里出现，GET 响应、
 * 日志、异常信息一律不带 headers/env/url 正文。
 */
@Service
public class AdminAiMcpConfigServiceImpl implements AdminAiMcpConfigService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiMcpConfigServiceImpl.class);

    /** 整份条目 JSON 的大小上限：注册表是人工维护的小配置，超此视为误提交。 */
    static final int MAX_SERVERS_JSON_LENGTH = 262_144;

    private final AgentRuntimeConfigMapper runtimeConfigMapper;
    private final ModelEntryCipher cipher;
    private final AdminAiRuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;

    public AdminAiMcpConfigServiceImpl(
            AgentRuntimeConfigMapper runtimeConfigMapper,
            ModelEntryCipher cipher,
            AdminAiRuntimeConfigService runtimeConfigService,
            ObjectMapper objectMapper) {
        this.runtimeConfigMapper = runtimeConfigMapper;
        this.cipher = cipher;
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AdminAiMcpConfigDto getConfig() {
        return toDto(loadRow());
    }

    @Override
    public AdminAiMcpConfigDto updateConfig(AdminAiMcpConfigUpdateRequest request) {
        AgentRuntimeConfigEntity row = loadRow();
        Map<String, ObjectNode> existingByName = existingEntriesByName(row);
        List<McpServerEntryInput> inputs = request == null || request.servers() == null
                ? List.of()
                : request.servers();
        ArrayNode merged = objectMapper.createArrayNode();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (McpServerEntryInput input : inputs) {
            if (input == null || !StringUtils.hasText(input.name())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String name = input.name().trim();
            if (!seen.add(name)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            merged.add(mergeEntry(input, name, existingByName.get(name)));
        }
        String plainJson;
        try {
            plainJson = objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (plainJson.length() > MAX_SERVERS_JSON_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        row.setMcpServersJson(cipher.encrypt(plainJson));
        row.setConfigVersion(currentVersion(row) + 1);
        runtimeConfigMapper.updateById(row);
        // 与运行配置共用同一行与 config_version：走 retryPush 推全量并落推送状态。
        runtimeConfigService.retryPush();
        return toDto(loadRow());
    }

    /** 单条合并：密钥面（url 值、headers/env 值）留空保留，其余字段整体替换。 */
    private ObjectNode mergeEntry(McpServerEntryInput input, String name, ObjectNode existing) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("name", name);
        if (StringUtils.hasText(input.transport())) {
            out.put("transport", input.transport().trim());
        }
        // url 可能内嵌 userinfo 凭证：按密钥字段处理，留空＝保留既有值。
        if (StringUtils.hasText(input.url())) {
            out.put("url", input.url().trim());
        } else if (existing != null && existing.hasNonNull("url")) {
            out.set("url", existing.get("url"));
        }
        if (input.enabled() != null) {
            out.put("enabled", input.enabled());
        }
        if (StringUtils.hasText(input.toolPrefix())) {
            out.put("toolPrefix", input.toolPrefix().trim());
        }
        if (input.initTimeoutSeconds() != null) {
            out.put("initTimeoutSeconds", input.initTimeoutSeconds());
        }
        if (StringUtils.hasText(input.command())) {
            out.put("command", input.command().trim());
        }
        if (input.args() != null) {
            ArrayNode args = out.putArray("args");
            for (String arg : input.args()) {
                args.add(arg);
            }
        }
        if (StringUtils.hasText(input.cwd())) {
            out.put("cwd", input.cwd().trim());
        }
        mergeSecretMap(out, "headers", input.headers(), existing);
        mergeSecretMap(out, "env", input.env(), existing);
        return out;
    }

    /**
     * 密钥 map 合并：inputs 为 null＝保留既有整表；非 null＝按列表定键集合——
     * 每项 clear=true 删键、value 有值替换、value 留空保留既有值（无既有则不建键）。
     */
    private void mergeSecretMap(
            ObjectNode out, String field, List<SecretKvInput> inputs, ObjectNode existing) {
        JsonNode existingMap = existing == null ? null : existing.get(field);
        if (inputs == null) {
            if (existingMap != null && existingMap.isObject()) {
                out.set(field, existingMap);
            }
            return;
        }
        ObjectNode map = objectMapper.createObjectNode();
        for (SecretKvInput kv : inputs) {
            if (kv == null || !StringUtils.hasText(kv.key())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String key = kv.key().trim();
            if (Boolean.TRUE.equals(kv.clear())) {
                continue;
            }
            if (StringUtils.hasText(kv.value())) {
                map.put(key, kv.value());
            } else if (existingMap != null && existingMap.hasNonNull(key)) {
                map.set(key, existingMap.get(key));
            }
        }
        if (!map.isEmpty()) {
            out.set(field, map);
        }
    }

    /** 既有条目按 name 索引（解密＋解析）；存量损坏时按空注册表处理并记 warning，不拖垮页面。 */
    private Map<String, ObjectNode> existingEntriesByName(AgentRuntimeConfigEntity row) {
        Map<String, ObjectNode> byName = new LinkedHashMap<>();
        for (ObjectNode entry : parseEntries(row)) {
            String name = entry.path("name").asText("");
            if (StringUtils.hasText(name)) {
                byName.put(name, entry);
            }
        }
        return byName;
    }

    private List<ObjectNode> parseEntries(AgentRuntimeConfigEntity row) {
        String plain = decryptOrNull(row.getMcpServersJson());
        if (!StringUtils.hasText(plain)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(plain);
            if (!root.isArray()) {
                log.warn("stored mcp_servers_json is not a JSON array; treating as empty registry");
                return List.of();
            }
            List<ObjectNode> entries = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isObject()) {
                    entries.add((ObjectNode) node);
                }
            }
            return entries;
        } catch (JsonProcessingException ex) {
            log.warn("stored mcp_servers_json is malformed; treating as empty registry");
            return List.of();
        }
    }

    private AdminAiMcpConfigDto toDto(AgentRuntimeConfigEntity row) {
        List<McpServerEntryDto> servers = new ArrayList<>();
        for (ObjectNode entry : parseEntries(row)) {
            servers.add(toEntryDto(entry));
        }
        return new AdminAiMcpConfigDto(
                servers,
                currentVersion(row),
                row.getLastPushStatus(),
                row.getLastPushMessage(),
                row.getLastPushedAt(),
                row.getVersion(),
                row.getUpdatedAt());
    }

    private McpServerEntryDto toEntryDto(ObjectNode entry) {
        String url = entry.path("url").isTextual() ? entry.get("url").asText() : null;
        JsonNode timeout = entry.get("initTimeoutSeconds");
        List<String> args = new ArrayList<>();
        JsonNode argsNode = entry.get("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode arg : argsNode) {
                args.add(arg.asText());
            }
        }
        return new McpServerEntryDto(
                entry.path("name").asText(""),
                entry.path("transport").isTextual() ? entry.get("transport").asText() : null,
                StringUtils.hasText(url),
                AdminAiModelEntryServiceImpl.apiKeyHint(url),
                !entry.hasNonNull("enabled") || entry.path("enabled").asBoolean(true),
                entry.path("toolPrefix").isTextual() ? entry.get("toolPrefix").asText() : null,
                timeout != null && timeout.isNumber() ? timeout.asInt() : null,
                entry.path("command").isTextual() ? entry.get("command").asText() : null,
                args,
                entry.path("cwd").isTextual() ? entry.get("cwd").asText() : null,
                secretKvDtos(entry.get("headers")),
                secretKvDtos(entry.get("env")));
    }

    private List<SecretKvDto> secretKvDtos(JsonNode mapNode) {
        List<SecretKvDto> out = new ArrayList<>();
        if (mapNode != null && mapNode.isObject()) {
            mapNode.fields()
                    .forEachRemaining(
                            field -> out.add(
                                    new SecretKvDto(
                                            field.getKey(),
                                            true,
                                            AdminAiModelEntryServiceImpl.apiKeyHint(
                                                    field.getValue().asText("")))));
        }
        return out;
    }

    /** V47 已预置 id=1 空行；行缺失（测试夹具等）时补插一行空配置，并发撞键则重读（同 #146 口径）。 */
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

    /** 密文解不开（未配口令等）时按未配置处理，不把密文当明文用（与 ModelEntryCipher 口径一致）。 */
    private String decryptOrNull(String stored) {
        String plain = cipher.decrypt(stored);
        return StringUtils.hasText(plain) ? plain : null;
    }

    private static long currentVersion(AgentRuntimeConfigEntity row) {
        return row.getConfigVersion() == null ? 0L : row.getConfigVersion();
    }
}
