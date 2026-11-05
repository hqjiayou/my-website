package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.entity.AgentAiKnowledgeBaseEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.entity.AgentEmbeddingClassEntity;
import com.mywebsite.agent.entity.AgentEmbeddingClassMaterializationEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeBaseMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMapper;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMaterializationMapper;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.utils.AgentEmbeddingClassUtils;
import com.mywebsite.agent.utils.ModelChainResolver;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 嵌入类登记读取与首次读物化（Issue #126 UoW-2；scope 于 Issue #209 UoW-3
 * 由站点改知识空间）。
 *
 * <p>物化等价性：旧推算把站点与全站入口一次扫描、一次 dedupe（站点优先）；
 * 这里按 scope 分别物化（每 scope 内按旧扫描序——默认在前、id 升序——逐类取
 * 第一个能组装出凭证的入口为胜者），读取时再按空间行在前、全站行在后合并
 * dedupe。空间 scope 的旧推算输入是该空间创建者的站点入口：UoW-3 前空间
 * 文档经创建者的站点 scope 解析类集，V61 又把创建者的存量登记行扇出到其
 * 每个空间，因此「读时物化（从创建者入口推算）」与「迁移扇出（复制创建者
 * 登记行）」对同一空间给出同一类集，两种到达登记态的路径不互相矛盾。
 * {@code google-gla:} 与 {@code google:} 跨 scope 撞类时空间胜出：全站 scope
 * 照常物化自己的那一行，只是合并 dedupe 时被空间同名类压掉。
 */
@Service
public class AgentEmbeddingClassServiceImpl implements AgentEmbeddingClassService {

    private static final Logger log = LoggerFactory.getLogger(AgentEmbeddingClassServiceImpl.class);

    private static final String GLOBAL_SCOPE_KEY = "GLOBAL";
    private static final String SPACE_SCOPE_PREFIX = "SPACE:";

    private final AgentEmbeddingClassMapper registrationMapper;
    private final AgentEmbeddingClassMaterializationMapper materializationMapper;
    private final AgentAiModelEntryMapper modelEntryMapper;
    private final AgentAiKnowledgeBaseMapper knowledgeBaseMapper;
    private final ModelEntryCipher cipher;

    public AgentEmbeddingClassServiceImpl(
            AgentEmbeddingClassMapper registrationMapper,
            AgentEmbeddingClassMaterializationMapper materializationMapper,
            AgentAiModelEntryMapper modelEntryMapper,
            AgentAiKnowledgeBaseMapper knowledgeBaseMapper,
            ModelEntryCipher cipher) {
        this.registrationMapper = registrationMapper;
        this.materializationMapper = materializationMapper;
        this.modelEntryMapper = modelEntryMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public List<AgentModelSelection> embeddingClassesForSpace(long baseId) {
        ensureMaterializedForSpace(baseId);
        ensureMaterializedGlobal();

        List<AgentEmbeddingClassEntity> rows = new ArrayList<>();
        rows.addAll(selectScopeRows(baseId));
        rows.addAll(selectScopeRows(null));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, AgentAiModelEntryEntity> entriesById = loadEntries(rows);
        List<AgentModelSelection> raw = new ArrayList<>();
        for (AgentEmbeddingClassEntity row : rows) {
            AgentModelSelection selection = resolveRow(row, entriesById);
            if (selection != null) {
                raw.add(selection);
            }
        }
        return AgentEmbeddingClassUtils.dedupe(raw);
    }

    // ------------------------------------------------------------------
    // 首次读物化
    // ------------------------------------------------------------------

    /**
     * 该 scope 未物化过时，把旧推算在该 scope 的输出写成登记行并落 marker，
     * 与随后的读取同事务。marker 已存在则什么都不做——登记行（含被人工
     * 删空的状态）就是唯一真相源。胜者推算经 {@code winnersSupplier} 惰性
     * 求值：marker 已在时不扫入口、不解密，与 #126 原实现的短路口径一致。
     *
     * <p>marker 不在但登记行已存在（管理面先行保存过登记、marker 未及落下）
     * 时同样不动登记行：只补 marker。否则首次读会把推算胜者追加进人工登记，
     * 污染类集（解耦深审 M-1）。注意「人工删空」后行数为 0、与从未登记不可
     * 区分——那种情形仍走推算物化，但管理面保存路径会同步落 marker（见
     * admin 侧 {@code replaceRegistrations}），删空后 marker 已在、不会走到这里。
     */
    private void ensureMaterialized(
            String scopeKey,
            java.util.function.Supplier<List<AgentAiModelEntryEntity>> winnersSupplier,
            Long rowBaseId) {
        if (materializationMapper.selectById(scopeKey) != null) {
            return;
        }
        if (hasRegistrationRows(rowBaseId)) {
            insertMarker(scopeKey);
            log.info(
                    "embedding classes already registered scope={}; marker backfilled without materialization",
                    scopeKey);
            return;
        }
        List<AgentAiModelEntryEntity> winners = winnersSupplier.get();
        for (int i = 0; i < winners.size(); i++) {
            AgentEmbeddingClassEntity row = new AgentEmbeddingClassEntity();
            row.setBaseId(rowBaseId);
            row.setEntryId(winners.get(i).getId());
            row.setEnabled(true);
            row.setSortOrder(i);
            registrationMapper.insert(row);
        }
        insertMarker(scopeKey);
        log.info(
                "embedding classes materialized scope={} classes={}",
                scopeKey,
                winners.stream().map(AgentAiModelEntryEntity::getId).toList());
    }

    /** 该 scope 现存未软删登记行数（不分启用与否：人工登记过即以登记行为准）。 */
    private boolean hasRegistrationRows(Long baseId) {
        LambdaQueryWrapper<AgentEmbeddingClassEntity> query = new LambdaQueryWrapper<>();
        if (baseId == null) {
            query.isNull(AgentEmbeddingClassEntity::getBaseId);
        } else {
            query.eq(AgentEmbeddingClassEntity::getBaseId, baseId);
        }
        Long count = registrationMapper.selectCount(query);
        return count != null && count > 0;
    }

    private void insertMarker(String scopeKey) {
        AgentEmbeddingClassMaterializationEntity marker = new AgentEmbeddingClassMaterializationEntity();
        marker.setScopeKey(scopeKey);
        marker.setMaterializedAt(OffsetDateTime.now(ZoneOffset.UTC));
        materializationMapper.insert(marker);
    }

    /**
     * 空间 scope 的物化：旧推算输入是空间创建者的站点入口（见类注释的等价性
     * 说明）。空间不存在或创建者为空时无可推算，仍落 marker 把该 scope 钉为
     * 「已物化、无空间行」，避免每次读都重试解析。
     */
    private void ensureMaterializedForSpace(long baseId) {
        ensureMaterialized(
                SPACE_SCOPE_PREFIX + baseId,
                () -> {
                    Long creatorId = creatorOf(baseId);
                    return creatorId == null ? List.of() : legacyScopeWinners(creatorId);
                },
                baseId);
    }

    /** 全站 scope 的物化：输入是全站入口（agent_id 为 NULL），与 #126 口径不变。 */
    private void ensureMaterializedGlobal() {
        ensureMaterialized(GLOBAL_SCOPE_KEY, () -> legacyScopeWinners(null), null);
    }

    private Long creatorOf(long baseId) {
        AgentAiKnowledgeBaseEntity base = knowledgeBaseMapper.selectById(baseId);
        return base == null ? null : base.getCreatedByAgentId();
    }

    /**
     * 旧推算在单个入口 scope 内的胜者入口：按旧扫描序（默认在前、id 升序）遍历
     * 启用入口，只取 mode=KEY、{@code embeddingSpec} 非空且凭证能组装出的，
     * 按规范化类键保留第一个（＝旧 {@code appendEmbeddings}＋{@code dedupe}
     * 在该 scope 内的结果），上限与 dedupe 同为 {@code MAX_EMBEDDING_CLASSES}。
     */
    private List<AgentAiModelEntryEntity> legacyScopeWinners(Long agentId) {
        List<AgentAiModelEntryEntity> winners = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AgentAiModelEntryEntity entry : enabledEntries(agentId)) {
            if (!"KEY".equals(ModelChainResolver.entryMode(entry.getMode()))
                    || !StringUtils.hasText(entry.getEmbeddingSpec())) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection == null || !StringUtils.hasText(selection.embeddingSpec())) {
                continue;
            }
            String className = AgentEmbeddingClassUtils.className(selection.embeddingSpec());
            if (className == null || !seen.add(className)) {
                continue;
            }
            winners.add(entry);
            if (winners.size() == AgentEmbeddingClassUtils.MAX_EMBEDDING_CLASSES) {
                break;
            }
        }
        return winners;
    }

    /** 作用域内启用入口：默认在前，其余 id 升序（与两侧旧扫描的查询逐字一致）。 */
    private List<AgentAiModelEntryEntity> enabledEntries(Long agentId) {
        LambdaQueryWrapper<AgentAiModelEntryEntity> query = new LambdaQueryWrapper<AgentAiModelEntryEntity>()
                .eq(AgentAiModelEntryEntity::getEnabled, true)
                .orderByDesc(AgentAiModelEntryEntity::getDefaultEntry)
                .orderByAsc(AgentAiModelEntryEntity::getId);
        if (agentId == null) {
            query.isNull(AgentAiModelEntryEntity::getAgentId);
        } else {
            query.eq(AgentAiModelEntryEntity::getAgentId, agentId);
        }
        List<AgentAiModelEntryEntity> entries = modelEntryMapper.selectList(query);
        return entries == null ? List.of() : entries;
    }

    // ------------------------------------------------------------------
    // 登记行读取
    // ------------------------------------------------------------------

    /** 该 scope 启用的登记行：sort_order 升序、同序按 id 升序。 */
    private List<AgentEmbeddingClassEntity> selectScopeRows(Long baseId) {
        LambdaQueryWrapper<AgentEmbeddingClassEntity> query = new LambdaQueryWrapper<AgentEmbeddingClassEntity>()
                .eq(AgentEmbeddingClassEntity::getEnabled, true)
                .orderByAsc(AgentEmbeddingClassEntity::getSortOrder)
                .orderByAsc(AgentEmbeddingClassEntity::getId);
        if (baseId == null) {
            query.isNull(AgentEmbeddingClassEntity::getBaseId);
        } else {
            query.eq(AgentEmbeddingClassEntity::getBaseId, baseId);
        }
        List<AgentEmbeddingClassEntity> rows = registrationMapper.selectList(query);
        return rows == null ? List.of() : rows;
    }

    /** 批量回读被引用入口；已软删的入口被逻辑删除过滤掉，映射里没有它。 */
    private Map<Long, AgentAiModelEntryEntity> loadEntries(List<AgentEmbeddingClassEntity> rows) {
        List<Long> entryIds = rows.stream().map(AgentEmbeddingClassEntity::getEntryId).distinct().toList();
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AgentAiModelEntryEntity> byId = new HashMap<>();
        for (AgentAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            byId.put(entry.getId(), entry);
        }
        return byId;
    }

    /**
     * 一行登记解析成凭证。被引用入口不存在/已软删/被禁用、mode 非 KEY、
     * {@code embeddingSpec} 空白或密钥解不出/组装不出凭证时跳过该行（记日志），
     * 不让坏行拖垮整批——与旧推算「坏入口静默不进类」的容忍口径一致，只是
     * 现在要留痕。
     */
    private AgentModelSelection resolveRow(
            AgentEmbeddingClassEntity row, Map<Long, AgentAiModelEntryEntity> entriesById) {
        AgentAiModelEntryEntity entry = entriesById.get(row.getEntryId());
        if (entry == null) {
            log.warn(
                    "embedding class registration {} references missing/deleted entry {}",
                    row.getId(),
                    row.getEntryId());
            return null;
        }
        if (!Boolean.TRUE.equals(entry.getEnabled())) {
            log.warn("embedding class registration {} references disabled entry {}", row.getId(), entry.getId());
            return null;
        }
        if (!"KEY".equals(ModelChainResolver.entryMode(entry.getMode()))) {
            log.warn(
                    "embedding class registration {} references non-KEY entry {} (mode={})",
                    row.getId(),
                    entry.getId(),
                    entry.getMode());
            return null;
        }
        if (!StringUtils.hasText(entry.getEmbeddingSpec())) {
            log.warn(
                    "embedding class registration {} references entry {} without embeddingSpec",
                    row.getId(),
                    entry.getId());
            return null;
        }
        AgentModelSelection selection = toSelection(entry);
        if (selection == null) {
            log.warn(
                    "embedding class registration {} entry {} has no usable credentials"
                            + " (blank modelSpec or undecryptable key)",
                    row.getId(),
                    entry.getId());
            return null;
        }
        return selection;
    }

    /**
     * 凭证组装：与 {@code ModelChainResolver} 推算时代的 {@code toSelection}
     * 逐字同规——无 modelSpec 或密钥解不出/为空返回 null；baseUrl 与
     * embeddingSpec 去空白、空转 null；CLI 的 embeddingSpec 强制清空
     * （调用方已先按 KEY 过滤，这里保留同规防御）。
     */
    private AgentModelSelection toSelection(AgentAiModelEntryEntity entry) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String plainKey = cipher.decrypt(entry.getApiKey());
        if (!StringUtils.hasText(plainKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.getBaseUrl()) ? entry.getBaseUrl().trim() : null;
        String embeddingSpec = null;
        if (!"CLI".equals(ModelChainResolver.entryMode(entry.getMode()))
                && StringUtils.hasText(entry.getEmbeddingSpec())) {
            embeddingSpec = entry.getEmbeddingSpec().trim();
        }
        return new AgentModelSelection(entry.getModelSpec().trim(), plainKey, baseUrl, embeddingSpec);
    }
}
