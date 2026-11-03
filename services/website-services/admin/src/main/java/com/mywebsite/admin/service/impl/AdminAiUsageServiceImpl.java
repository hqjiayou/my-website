package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.dto.AdminAiUsageDtos.AdminAiUsageReportDto;
import com.mywebsite.admin.dto.AdminAiUsageDtos.ByAgentDto;
import com.mywebsite.admin.dto.AdminAiUsageDtos.ByModelDto;
import com.mywebsite.admin.dto.AdminAiUsageDtos.DailyDto;
import com.mywebsite.admin.dto.AdminAiUsageDtos.RecentDto;
import com.mywebsite.admin.service.AdminAiUsageService;
import com.mywebsite.agent.entity.AgentAiUsageEntity;
import com.mywebsite.agent.mapper.AgentAiUsageMapper;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用量聚合（Issue #140 UoW-2）。单人自用的量级：查窗口内的行在内存聚合，
 * 不写原生 SQL 分组；窗口与行数都有上限兜底（{@link #MAX_WINDOW_ROWS}），
 * 超限时只聚合最近的行并记日志，不静默截断。
 */
@Service
public class AdminAiUsageServiceImpl implements AdminAiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiUsageServiceImpl.class);

    private static final Set<String> SOURCES =
            Set.of(
                    AgentUsageService.SOURCE_PORTAL,
                    AgentUsageService.SOURCE_ADMIN_PREVIEW,
                    AgentUsageService.SOURCE_GATEWAY_V1,
                    AgentUsageService.SOURCE_EXPERIENCE);
    private static final int MAX_WINDOW_ROWS = 20_000;

    private final AgentAiUsageMapper usageMapper;

    public AdminAiUsageServiceImpl(AgentAiUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    public AdminAiUsageReportDto usageReport(String source, int days, int limit) {
        String resolvedSource = resolveSource(source);
        int resolvedDays = Math.min(Math.max(days, 1), 90);
        int resolvedLimit = Math.min(Math.max(limit, 1), 200);
        OffsetDateTime windowStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays(resolvedDays - 1L)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);

        LambdaQueryWrapper<AgentAiUsageEntity> query = new LambdaQueryWrapper<AgentAiUsageEntity>()
                .ge(AgentAiUsageEntity::getCreatedAt, windowStart)
                .orderByDesc(AgentAiUsageEntity::getCreatedAt)
                .orderByDesc(AgentAiUsageEntity::getId)
                .last("LIMIT " + (MAX_WINDOW_ROWS + 1));
        if (resolvedSource != null) {
            query.eq(AgentAiUsageEntity::getSource, resolvedSource);
        }
        List<AgentAiUsageEntity> rows = usageMapper.selectList(query);
        if (rows == null) {
            rows = List.of();
        }
        if (rows.size() > MAX_WINDOW_ROWS) {
            log.warn("ai usage report window truncated at {} rows (days={})", MAX_WINDOW_ROWS, resolvedDays);
            rows = rows.subList(0, MAX_WINDOW_ROWS);
        }

        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        Map<Long, TotalAccumulator> byAgent = new LinkedHashMap<>();
        Map<Long, ModelAccumulator> byModel = new LinkedHashMap<>();
        for (AgentAiUsageEntity row : rows) {
            LocalDate date = row.getCreatedAt() == null ? null : row.getCreatedAt().toLocalDate();
            if (date != null) {
                daily.computeIfAbsent(date, key -> new DailyAccumulator()).add(row);
            }
            byAgent.computeIfAbsent(row.getAgentId(), key -> new TotalAccumulator()).add(row);
            ModelAccumulator model = byModel.computeIfAbsent(row.getEntryId(), key -> new ModelAccumulator());
            model.add(row);
            if (model.alias == null && StringUtils.hasText(row.getAlias())) {
                model.alias = row.getAlias().trim();
            }
        }

        List<DailyDto> dailyDtos = daily.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyDto(
                        entry.getKey(),
                        entry.getValue().calls,
                        entry.getValue().promptTokens,
                        entry.getValue().completionTokens,
                        entry.getValue().unknownTokenCalls))
                .toList();
        List<ByAgentDto> byAgentDtos = byAgent.entrySet().stream()
                .map(entry -> new ByAgentDto(
                        entry.getKey(), entry.getValue().calls, entry.getValue().promptTokens, entry.getValue().completionTokens))
                .sorted(Comparator.comparingLong(ByAgentDto::calls).reversed())
                .toList();
        List<ByModelDto> byModelDtos = byModel.entrySet().stream()
                .map(entry -> new ByModelDto(
                        entry.getKey(),
                        entry.getValue().alias,
                        entry.getValue().calls,
                        entry.getValue().promptTokens,
                        entry.getValue().completionTokens))
                .sorted(Comparator.comparingLong(ByModelDto::calls).reversed())
                .toList();
        List<RecentDto> recent = new ArrayList<>();
        for (AgentAiUsageEntity row : rows) {
            if (recent.size() >= resolvedLimit) {
                break;
            }
            recent.add(new RecentDto(
                    row.getId(),
                    row.getCreatedAt(),
                    row.getSource(),
                    row.getAgentId(),
                    row.getEntryId(),
                    row.getAlias(),
                    row.getMode(),
                    row.getPromptTokens(),
                    row.getCompletionTokens(),
                    row.getLatencyMs(),
                    row.getStatus(),
                    row.getGatewayAccessKeyId()));
        }
        return new AdminAiUsageReportDto(dailyDtos, byAgentDtos, byModelDtos, List.copyOf(recent));
    }

    private static String resolveSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String value = source.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SOURCES.contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    private static class TotalAccumulator {
        long calls;
        long promptTokens;
        long completionTokens;

        void add(AgentAiUsageEntity row) {
            calls++;
            promptTokens += row.getPromptTokens() == null ? 0L : row.getPromptTokens();
            completionTokens += row.getCompletionTokens() == null ? 0L : row.getCompletionTokens();
        }
    }

    private static final class DailyAccumulator extends TotalAccumulator {
        long unknownTokenCalls;

        @Override
        void add(AgentAiUsageEntity row) {
            super.add(row);
            if (row.getPromptTokens() == null || row.getCompletionTokens() == null) {
                unknownTokenCalls++;
            }
        }
    }

    private static final class ModelAccumulator extends TotalAccumulator {
        String alias;
    }
}
