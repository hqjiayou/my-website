package com.mywebsite.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentMcpSelection;
import com.mywebsite.agent.entity.AgentAiMcpSelectionEntity;
import com.mywebsite.agent.mapper.AgentAiMcpSelectionMapper;
import com.mywebsite.agent.service.AgentMcpSelectionService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentMcpSelectionServiceImpl implements AgentMcpSelectionService {

    private static final Logger log = LoggerFactory.getLogger(AgentMcpSelectionServiceImpl.class);

    /** 与 Python 侧 ChatRequest 的纵深防御上限同值（mcpServerNames 条数/名长）。 */
    static final int MAX_SERVER_NAMES = 32;

    static final int MAX_SERVER_NAME_LENGTH = 64;

    private final AgentAiMcpSelectionMapper selectionMapper;
    private final ObjectMapper objectMapper;

    public AgentMcpSelectionServiceImpl(AgentAiMcpSelectionMapper selectionMapper, ObjectMapper objectMapper) {
        this.selectionMapper = selectionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentMcpSelection getSelection(long agentId) {
        AgentAiMcpSelectionEntity row = selectionMapper.selectById(agentId);
        if (row == null) {
            return AgentMcpSelection.inherit();
        }
        String mode = normalizeMode(row.getMode());
        if (!AgentAiMcpSelectionEntity.MODE_CUSTOM.equals(mode)) {
            return new AgentMcpSelection(mode, List.of());
        }
        return new AgentMcpSelection(mode, parseNames(row.getServerNames()));
    }

    @Override
    public AgentMcpSelection saveSelection(long agentId, String mode, List<String> serverNames) {
        String normalizedMode = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!AgentAiMcpSelectionEntity.MODE_INHERIT.equals(normalizedMode)
                && !AgentAiMcpSelectionEntity.MODE_OFF.equals(normalizedMode)
                && !AgentAiMcpSelectionEntity.MODE_CUSTOM.equals(normalizedMode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        List<String> names = AgentAiMcpSelectionEntity.MODE_CUSTOM.equals(normalizedMode)
                ? normalizeNames(serverNames)
                : List.of();
        if (AgentAiMcpSelectionEntity.MODE_CUSTOM.equals(normalizedMode) && names.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        AgentAiMcpSelectionEntity row = selectionMapper.selectById(agentId);
        if (row == null) {
            row = new AgentAiMcpSelectionEntity();
            row.setAgentId(agentId);
            row.setMode(normalizedMode);
            row.setServerNames(names.isEmpty() ? null : toJson(names));
            try {
                selectionMapper.insert(row);
                return getSelection(agentId);
            } catch (DuplicateKeyException ex) {
                row = selectionMapper.selectById(agentId);
                if (row == null) {
                    throw ex;
                }
            }
        }
        row.setMode(normalizedMode);
        row.setServerNames(names.isEmpty() ? null : toJson(names));
        selectionMapper.updateById(row);
        return getSelection(agentId);
    }

    @Override
    public List<String> resolveWireNames(long agentId) {
        return getSelection(agentId).wireNames();
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return AgentAiMcpSelectionEntity.MODE_INHERIT;
        }
        String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case AgentAiMcpSelectionEntity.MODE_OFF, AgentAiMcpSelectionEntity.MODE_CUSTOM -> normalized;
            default -> AgentAiMcpSelectionEntity.MODE_INHERIT;
        };
    }

    /** 名字规整：去空白、去重保序、逐个验长/条数；非法名字抛 BAD_REQUEST（保存路径专用）。 */
    private static List<String> normalizeNames(List<String> serverNames) {
        if (serverNames == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : serverNames) {
            if (!StringUtils.hasText(raw)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String name = raw.trim();
            if (name.length() > MAX_SERVER_NAME_LENGTH) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            unique.add(name);
        }
        if (unique.size() > MAX_SERVER_NAMES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return List.copyOf(unique);
    }

    /** 存量 JSON 解析失败按空名单处理并记 warning：不因一行坏数据拖垮对话组装。 */
    private List<String> parseNames(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            List<String> names = new ArrayList<>();
            for (String name : parsed) {
                if (StringUtils.hasText(name)) {
                    names.add(name.trim());
                }
            }
            return List.copyOf(names);
        } catch (JsonProcessingException ex) {
            log.warn("agent_ai_mcp_selection.server_names is not a JSON string array; treating as empty");
            return List.of();
        }
    }

    private String toJson(List<String> names) {
        try {
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
