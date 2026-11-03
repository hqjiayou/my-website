package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.service.AdminAiExperienceExtractionService;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentExperienceExtractRequest;
import com.mywebsite.agent.dto.AgentExperienceExtractResult;
import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentUsageService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 经验提炼编排（Issue #184 UoW-2）。
 *
 * <p><b>信号预筛定义</b>（零模型成本，只读已落库正式对话的 {@code agent_meta}，
 * 以 {@link AgentRunMeta} 的实际字段为准，不臆造不存在的信号）：
 *
 * <ul>
 *   <li>{@code TOOL_FAILURE}：本轮工具轨迹里任一 {@code tools[].ok == false}
 *       （轮次最终成功，但中途有工具失败——最典型的可提炼失败案例）；
 *   <li>{@code EMPTY_SEARCH}：本轮知识搜索里任一 {@code searches[].hitCount == 0}
 *       （检索落空，回答可能降级或编造）；
 *   <li>{@code HEAVY_TOOL_USE}：本轮 {@code usage.toolCalls >= 10}
 *       （反复试错的高消耗轮，挣扎痕迹明显）。
 * </ul>
 *
 * <p>注意原料边界：整轮失败（如 {@code AGENT_BUDGET_EXCEEDED} 中止、供应商故障）
 * 在 agent-app 侧<b>不落任何消息</b>（同步抛错、流式无 DONE 都不 persist），
 * {@code agent_meta} 里也没有 error/超限字段——这类教训 v1 无原料，靠 UoW-3 的
 * 手动新建候选兜底，本预筛只覆盖「成功轮内的失败信号」。
 *
 * <p><b>组链口径</b>（P3 定案）：按来源 agent 分组，每组用该 agent 的生效链
 * （与 KB OCR 同口径先 KEY 链、空了才 CLI 链）——链首即该 agent 的主用入口
 * （站默认，无站默认时全站默认，即定案所说手动批量用平台默认入口的落点）；
 * 备援随请求带给执行层顺链。链为空的组整组计入失败轮数，不静默跳过。
 *
 * <p><b>记账</b>（#140 一表制）：每次提炼调用成功后经 {@link AgentUsageService}
 * 落一行 {@code source=EXPERIENCE}；执行层没回报 usage 时 tokens 记 NULL。
 * 调用失败不记账（与试聊「无 DONE 不记」同口径）。
 */
@Service
public class AdminAiExperienceExtractionServiceImpl implements AdminAiExperienceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiExperienceExtractionServiceImpl.class);

    static final String SIGNAL_TOOL_FAILURE = "TOOL_FAILURE";
    static final String SIGNAL_EMPTY_SEARCH = "EMPTY_SEARCH";
    static final String SIGNAL_HEAVY_TOOL_USE = "HEAVY_TOOL_USE";

    /** 高工具消耗的定死阈值：单轮工具调用达到两位数即视为反复试错的挣扎轮。 */
    static final int HEAVY_TOOL_CALLS_THRESHOLD = 10;

    static final int DEFAULT_WINDOW_DAYS = 14;
    static final int MAX_WINDOW_DAYS = 90;
    static final int DEFAULT_MAX_TURNS = 40;
    static final int MAX_TURNS_LIMIT = 100;
    /** 预筛扫描上限：窗口内最多读这么多条 ASSISTANT 消息做信号判定（按时间倒序，新的优先）。 */
    static final int SCAN_LIMIT = 600;
    /** 执行层单请求轮数上限（与 Python schemas/experience 的 MAX_TURNS 对齐）。 */
    static final int TURNS_PER_CALL = 20;

    private final AdminAiMessageMapper messageMapper;
    private final AdminAiModelEntryService modelEntryService;
    private final AgentClient agentClient;
    private final AgentExperienceLessonService lessonService;
    private final AgentUsageService usageService;
    private final ObjectMapper objectMapper;

    public AdminAiExperienceExtractionServiceImpl(
            AdminAiMessageMapper messageMapper,
            AdminAiModelEntryService modelEntryService,
            AgentClient agentClient,
            AgentExperienceLessonService lessonService,
            AgentUsageService usageService,
            ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.modelEntryService = modelEntryService;
        this.agentClient = agentClient;
        this.lessonService = lessonService;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AdminAiExperienceExtractionResultDto extractRecent(AdminAiExperienceExtractionRequestDto request) {
        int windowDays = clamp(request == null ? null : request.windowDays(), DEFAULT_WINDOW_DAYS, MAX_WINDOW_DAYS);
        int maxTurns = clamp(request == null ? null : request.maxTurns(), DEFAULT_MAX_TURNS, MAX_TURNS_LIMIT);

        List<CandidateTurn> hits = prefilter(windowDays, maxTurns);
        List<String> existingTitles = existingTitles();

        Map<Long, List<CandidateTurn>> byAgent = new LinkedHashMap<>();
        int failedTurns = 0;
        for (CandidateTurn hit : hits) {
            Long agentId = hit.assistantMessage().getAgentId();
            if (agentId == null) {
                // 无来源 agent 的轮次组不出链，计入失败而不是悄悄丢弃。
                failedTurns++;
                continue;
            }
            byAgent.computeIfAbsent(agentId, key -> new ArrayList<>()).add(hit);
        }

        int extractionCalls = 0;
        int produced = 0;
        for (Map.Entry<Long, List<CandidateTurn>> group : byAgent.entrySet()) {
            long agentId = group.getKey();
            List<AgentModelSelection> chain = modelEntryService.selectionChain(agentId, "KEY");
            String mode = "KEY";
            if (chain.isEmpty()) {
                chain = modelEntryService.selectionChain(agentId, "CLI");
                mode = "CLI";
            }
            if (chain.isEmpty()) {
                failedTurns += group.getValue().size();
                log.warn("experience extraction skipped {} turns for agent {}: no usable model entry chain",
                        group.getValue().size(), agentId);
                continue;
            }
            Long entryId = firstChainEntryId(agentId, mode);
            for (List<CandidateTurn> chunk : chunks(group.getValue())) {
                extractionCalls++;
                long startedNanos = System.nanoTime();
                AgentExperienceExtractResult result;
                try {
                    result = agentClient.extractExperience(new AgentExperienceExtractRequest(
                            chain.get(0),
                            chain.size() > 1 ? chain.subList(1, chain.size()) : List.of(),
                            chunk.stream().map(AdminAiExperienceExtractionServiceImpl::toWireTurn).toList(),
                            List.copyOf(existingTitles)));
                } catch (RuntimeException ex) {
                    // 单片失败不拖垮整批：计入失败轮数、继续其余组/片。
                    failedTurns += chunk.size();
                    log.warn("experience extraction call failed for agent {} ({} turns)", agentId, chunk.size(), ex);
                    continue;
                }
                produced += persistCandidates(result, chunk, existingTitles);
                recordExtractionUsage(agentId, entryId, mode, result, startedNanos);
            }
        }
        return new AdminAiExperienceExtractionResultDto(
                windowDays, hits.size(), extractionCalls, produced, failedTurns);
    }

    /** 信号预筛：窗口内带 agent_meta 的 ASSISTANT 消息按时间倒序扫，命中任一信号即收，直到 maxTurns。 */
    private List<CandidateTurn> prefilter(int windowDays, int maxTurns) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(windowDays);
        List<AdminAiMessageEntity> rows = messageMapper.selectList(
                new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getRole, AdminAiMessageEntity.ROLE_ASSISTANT)
                        .isNotNull(AdminAiMessageEntity::getAgentMeta)
                        .ge(AdminAiMessageEntity::getCreatedAt, since)
                        .orderByDesc(AdminAiMessageEntity::getCreatedAt)
                        .orderByDesc(AdminAiMessageEntity::getId)
                        .last("LIMIT " + SCAN_LIMIT));
        List<CandidateTurn> hits = new ArrayList<>();
        if (rows == null) {
            return hits;
        }
        for (AdminAiMessageEntity row : rows) {
            if (hits.size() >= maxTurns) {
                break;
            }
            AgentRunMeta meta = readAgentMeta(row);
            List<String> signals = signalsOf(meta);
            if (signals.isEmpty()) {
                continue;
            }
            hits.add(new CandidateTurn(row, precedingUserContent(row), meta, signals));
        }
        return hits;
    }

    /** 本轮的用户问：同对话内、该 ASSISTANT 消息之前最近的一条 USER 消息；没有则空串。 */
    private String precedingUserContent(AdminAiMessageEntity assistantMessage) {
        AdminAiMessageEntity user = messageMapper.selectOne(
                new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, assistantMessage.getConversationId())
                        .eq(AdminAiMessageEntity::getRole, AdminAiMessageEntity.ROLE_USER)
                        .lt(AdminAiMessageEntity::getId, assistantMessage.getId())
                        .orderByDesc(AdminAiMessageEntity::getId)
                        .last("LIMIT 1"));
        return user == null || user.getContent() == null ? "" : user.getContent();
    }

    /** 信号判定（定死口径见类注释）；meta 缺失/损坏时无信号。 */
    static List<String> signalsOf(AgentRunMeta meta) {
        if (meta == null) {
            return List.of();
        }
        List<String> signals = new ArrayList<>();
        if (meta.tools().stream().anyMatch(tool -> !tool.ok())) {
            signals.add(SIGNAL_TOOL_FAILURE);
        }
        if (meta.searches().stream().anyMatch(search -> search.hitCount() == 0)) {
            signals.add(SIGNAL_EMPTY_SEARCH);
        }
        if (meta.usage().toolCalls() >= HEAVY_TOOL_CALLS_THRESHOLD) {
            signals.add(SIGNAL_HEAVY_TOOL_USE);
        }
        return signals;
    }

    /** 已批准条目 + 待审候选的标题：提炼去重的比对全集（批准的教训与排队中的都不再重复提炼）。 */
    private List<String> existingTitles() {
        List<String> titles = new ArrayList<>();
        for (AgentExperienceLessonEntity entity : lessonService.listApproved()) {
            if (StringUtils.hasText(entity.getTitle())) {
                titles.add(entity.getTitle());
            }
        }
        for (AgentExperienceLessonEntity entity : lessonService.listByStatus(AgentExperienceLessonEntity.STATUS_PENDING)) {
            if (StringUtils.hasText(entity.getTitle())) {
                titles.add(entity.getTitle());
            }
        }
        return titles;
    }

    private int persistCandidates(
            AgentExperienceExtractResult result, List<CandidateTurn> chunk, List<String> existingTitles) {
        if (result == null || result.candidates() == null) {
            return 0;
        }
        Map<String, CandidateTurn> byTurnId = new LinkedHashMap<>();
        for (CandidateTurn turn : chunk) {
            byTurnId.put(String.valueOf(turn.assistantMessage().getId()), turn);
        }
        int produced = 0;
        for (AgentExperienceExtractResult.Candidate candidate : result.candidates()) {
            CandidateTurn source = candidate == null ? null : byTurnId.get(candidate.sourceTurnId());
            if (source == null) {
                log.warn("experience candidate references unknown turn {}, dropped", candidate == null ? null : candidate.sourceTurnId());
                continue;
            }
            try {
                lessonService.createCandidate(new AgentExperienceLessonDraft(
                        candidate.title(),
                        candidate.symptom(),
                        candidate.lesson(),
                        candidate.evidence(),
                        source.assistantMessage().getAgentId(),
                        source.assistantMessage().getConversationId(),
                        source.assistantMessage().getId()));
                produced++;
                if (StringUtils.hasText(candidate.title())) {
                    existingTitles.add(candidate.title());
                }
            } catch (RuntimeException ex) {
                log.warn("experience candidate persist failed (title={})", candidate.title(), ex);
            }
        }
        return produced;
    }

    /** 提炼记账（source=EXPERIENCE）：usage 缺失记 NULL；recordUsage 自身吞异常不影响主流程。 */
    private void recordExtractionUsage(
            long agentId, Long entryId, String mode, AgentExperienceExtractResult result, long startedNanos) {
        AgentRunMeta.Usage usage = result == null ? null : result.usage();
        usageService.recordUsage(new AgentUsageRecord(
                agentId,
                entryId,
                null,
                mode,
                AgentUsageService.SOURCE_EXPERIENCE,
                usage == null ? null : (long) usage.inputTokens(),
                usage == null ? null : (long) usage.outputTokens(),
                (System.nanoTime() - startedNanos) / 1_000_000L,
                AgentUsageService.STATUS_SUCCESS,
                null));
    }

    /** 入口归属 id：与试聊记账同口径，取该模式链首的候选 id；链信息拿不到时为 null。 */
    private Long firstChainEntryId(long agentId, String mode) {
        List<ChainCandidate> candidates = modelEntryService.chainCandidates(agentId, mode);
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0).id();
    }

    private static AgentExperienceExtractRequest.Turn toWireTurn(CandidateTurn turn) {
        return new AgentExperienceExtractRequest.Turn(
                String.valueOf(turn.assistantMessage().getId()),
                turn.userMessage(),
                turn.assistantMessage().getContent() == null ? "" : turn.assistantMessage().getContent(),
                metaSummary(turn));
    }

    /** 给执行层看的 run_meta 摘要：信号、失败工具名、搜索落空数与用量，一行一项、只报事实。 */
    private static String metaSummary(CandidateTurn turn) {
        AgentRunMeta meta = turn.meta();
        StringBuilder summary = new StringBuilder("signals: ").append(String.join(", ", turn.signals()));
        List<String> failedTools = meta.tools().stream().filter(tool -> !tool.ok()).map(AgentRunMeta.ToolCall::name).toList();
        if (!failedTools.isEmpty()) {
            summary.append("\nfailed tools: ").append(String.join(", ", failedTools));
        }
        if (!meta.searches().isEmpty()) {
            long empty = meta.searches().stream().filter(search -> search.hitCount() == 0).count();
            summary.append("\nknowledge searches: ")
                    .append(meta.searches().size())
                    .append(" queries, ")
                    .append(empty)
                    .append(" with no hits");
        }
        summary.append("\ntool calls: ")
                .append(meta.usage().toolCalls())
                .append("; model requests: ")
                .append(meta.usage().requests());
        if (!meta.skillsApplied().isEmpty()) {
            summary.append("\nskills applied: ").append(String.join(", ", meta.skillsApplied()));
        }
        if (StringUtils.hasText(meta.model())) {
            summary.append("\nserved model: ").append(meta.model());
        }
        return summary.toString();
    }

    private AgentRunMeta readAgentMeta(AdminAiMessageEntity entity) {
        if (!StringUtils.hasText(entity.getAgentMeta())) {
            return null;
        }
        try {
            return objectMapper
                    .readerFor(AgentRunMeta.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(entity.getAgentMeta());
        } catch (JsonProcessingException ex) {
            log.warn("agent_ai_message {} has malformed agent_meta, ignored", entity.getId(), ex);
            return null;
        }
    }

    private static int clamp(Integer value, int defaultValue, int maxValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.min(Math.max(value, 1), maxValue);
    }

    private static List<List<CandidateTurn>> chunks(List<CandidateTurn> turns) {
        List<List<CandidateTurn>> chunks = new ArrayList<>();
        for (int start = 0; start < turns.size(); start += TURNS_PER_CALL) {
            chunks.add(turns.subList(start, Math.min(start + TURNS_PER_CALL, turns.size())));
        }
        return chunks;
    }

    /** 预筛命中的一轮：ASSISTANT 消息本体 + 配对的用户问 + 解析后的 meta 与信号。 */
    private record CandidateTurn(
            AdminAiMessageEntity assistantMessage, String userMessage, AgentRunMeta meta, List<String> signals) {}
}
