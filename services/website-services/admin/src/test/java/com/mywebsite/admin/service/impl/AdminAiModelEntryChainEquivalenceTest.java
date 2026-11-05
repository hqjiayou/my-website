package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.utils.ModelChainResolver;
import com.mywebsite.agent.utils.ModelChainResolver.EntrySnapshot;
import com.mywebsite.agent.utils.ModelChainResolver.ResolvedLink;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 组链快照对照（Issue #210 UoW-6 验收）：同一批入口数据，先按改造前语义
 * （agent 作用域层 → 全站层，基线由 {@link ModelChainResolver} 对旧分组直接计算）
 * 算出某 agent 的有效链，再把数据按 V61 口径转换（agent_id 置空、原值转存
 * source_agent_id）走改造后的平台组链，断言有序 entry id 逐条等价。
 *
 * <p>夹具是「全站 + 单 agent 专属混合」形态且故意让两级在同模式各有默认、
 * 专属默认 id 大于全站默认（平台入口先建、专属后建的真实顺序）——这是等价
 * 最容易被「简单合并排序」打破的形态。基线期望值同时手写钉死，防基线算法
 * 与新实现一起漂移。
 *
 * <p>多 agent 各有不同专属入口的形态不在等价范围内：平台化后各专属入口同属
 * 一个优先层、必然互相串入，任何单条平台链都不可能同时等于两个 agent 的旧链；
 * 该形态的差异由链预设（UoW-7）承接，本类不为它弱化断言。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiModelEntryChainEquivalenceTest {

    @Mock
    private AdminAiModelEntryMapper modelEntryMapper;

    @Mock
    private ModelEntryCipher cipher;

    @Mock
    private AgentEmbeddingClassService embeddingClassService;

    private AdminAiModelEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiModelEntryServiceImpl(modelEntryMapper, cipher, embeddingClassService);
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
        });
    }

    /** 改造前的一行入口数据（agentId 作用域口径）。 */
    private record LegacyRow(long id, Long agentId, String spec, boolean isDefault, String mode) {}

    /** 全站 3 条 + agent 10 专属 4 条，两级在 KEY/CLI 各有默认，专属默认 id 更大。 */
    private static final List<LegacyRow> MIXED_ROWS = List.of(
            new LegacyRow(1, null, "google:global-key-default", true, "KEY"),
            new LegacyRow(2, null, "anthropic:global-key-alt", false, "KEY"),
            new LegacyRow(3, null, "openai:global-cli-default", true, "CLI"),
            new LegacyRow(8, 10L, "google:agent-key-default", true, "KEY"),
            new LegacyRow(9, 10L, "openai:agent-key-alt", false, "KEY"),
            new LegacyRow(10, 10L, "openai:agent-cli-default", true, "CLI"),
            new LegacyRow(11, 10L, "anthropic:agent-cli-alt", false, "CLI"));

    private static final Comparator<LegacyRow> LEGACY_ORDER =
            Comparator.comparing((LegacyRow row) -> !row.isDefault()).thenComparingLong(LegacyRow::id);

    /** 改造前语义基线：该 agent 的作用域层 → 全站层（各层默认在前、id 升序）。 */
    private static List<Long> legacyChainIds(List<LegacyRow> rows, long agentId, String mode) {
        List<EntrySnapshot> agentScope = rows.stream()
                .filter(row -> row.agentId() != null && row.agentId() == agentId)
                .sorted(LEGACY_ORDER)
                .map(AdminAiModelEntryChainEquivalenceTest::snapshot)
                .toList();
        List<EntrySnapshot> globalScope = rows.stream()
                .filter(row -> row.agentId() == null)
                .sorted(LEGACY_ORDER)
                .map(AdminAiModelEntryChainEquivalenceTest::snapshot)
                .toList();
        return ModelChainResolver.resolveChain(agentScope, globalScope, mode).stream()
                .map(ResolvedLink::id)
                .toList();
    }

    private static EntrySnapshot snapshot(LegacyRow row) {
        return new EntrySnapshot(
                row.id(), "e" + row.id(), row.spec(), "key-" + row.id(), null, null, row.isDefault(), row.mode());
    }

    /** V61 转换后的实体：agent_id 置空、原值转存 source_agent_id。 */
    private static AdminAiModelEntryEntity converted(LegacyRow row) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(row.id());
        entry.setAgentId(null);
        entry.setSourceAgentId(row.agentId());
        entry.setModelSpec(row.spec());
        entry.setApiKey("enc:key-" + row.id());
        entry.setEnabled(true);
        entry.setDefaultEntry(row.isDefault());
        entry.setMode(row.mode());
        return entry;
    }

    /** 让 mapper 按改造后的分层查询顺序返回：优先层（来源标注）在前、平台层在后。 */
    private void stubConvertedRows(List<LegacyRow> rows) {
        List<AdminAiModelEntryEntity> provenanceLayer = rows.stream()
                .filter(row -> row.agentId() != null)
                .sorted(LEGACY_ORDER)
                .map(AdminAiModelEntryChainEquivalenceTest::converted)
                .toList();
        List<AdminAiModelEntryEntity> platformLayer = rows.stream()
                .filter(row -> row.agentId() == null)
                .sorted(LEGACY_ORDER)
                .map(AdminAiModelEntryChainEquivalenceTest::converted)
                .toList();
        when(modelEntryMapper.selectList(any())).thenReturn(provenanceLayer, platformLayer);
    }

    private List<Long> platformChainIds(long agentId, String mode) {
        return service.chainCandidates(agentId, mode).stream().map(ChainCandidate::id).toList();
    }

    @Test
    void wholeChainEquivalentForMixedScopeSingleAgent() {
        List<Long> baseline = legacyChainIds(MIXED_ROWS, 10L, null);
        assertThat(baseline).containsExactly(8L, 10L, 9L, 11L, 1L, 3L, 2L);

        stubConvertedRows(MIXED_ROWS);

        assertThat(platformChainIds(10L, null)).containsExactlyElementsOf(baseline);
    }

    @Test
    void keyChainEquivalentForMixedScopeSingleAgent() {
        List<Long> baseline = legacyChainIds(MIXED_ROWS, 10L, "KEY");
        assertThat(baseline).containsExactly(8L, 9L, 1L, 2L);

        stubConvertedRows(MIXED_ROWS);

        assertThat(platformChainIds(10L, "KEY")).containsExactlyElementsOf(baseline);
    }

    @Test
    void cliChainEquivalentForMixedScopeSingleAgent() {
        List<Long> baseline = legacyChainIds(MIXED_ROWS, 10L, "CLI");
        assertThat(baseline).containsExactly(10L, 11L, 3L);

        stubConvertedRows(MIXED_ROWS);

        assertThat(platformChainIds(10L, "CLI")).containsExactlyElementsOf(baseline);
    }

    @Test
    void platformChainIsTheSameWhicheverAgentAsks() {
        stubConvertedRows(MIXED_ROWS);
        // chainCandidates 每次调用查两层；第二轮问答需再喂一轮同样数据
        List<Long> forAgent10 = platformChainIds(10L, "KEY");
        stubConvertedRows(MIXED_ROWS);
        List<Long> forAgent20 = platformChainIds(20L, "KEY");

        assertThat(forAgent20).containsExactlyElementsOf(forAgent10);
    }

    @Test
    void agentWithoutOwnDefaultKeepsPlatformPrimaryAsBefore() {
        List<LegacyRow> rows = List.of(
                new LegacyRow(1, null, "google:global-key-default", true, "KEY"),
                new LegacyRow(2, null, "anthropic:global-key-alt", false, "KEY"),
                new LegacyRow(5, 10L, "openai:agent-key-alt", false, "KEY"));
        List<Long> baseline = legacyChainIds(rows, 10L, null);
        assertThat(baseline).containsExactly(1L, 5L, 2L);

        stubConvertedRows(rows);

        assertThat(platformChainIds(10L, null)).containsExactlyElementsOf(baseline);
    }

    @Test
    void purePlatformRowsKeepTheirChain() {
        List<LegacyRow> rows = List.of(
                new LegacyRow(1, null, "google:global-key-default", true, "KEY"),
                new LegacyRow(2, null, "anthropic:global-key-alt", false, "KEY"),
                new LegacyRow(3, null, "openai:global-cli-default", true, "CLI"));
        assertThat(legacyChainIds(rows, 10L, null)).containsExactly(1L, 3L, 2L);

        stubConvertedRows(rows);

        assertThat(platformChainIds(10L, null)).containsExactly(1L, 3L, 2L);
    }
}
