package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiChainPresetEntity;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiChainPresetMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.utils.ModelChainResolver;
import com.mywebsite.agent.utils.ModelChainResolver.EntrySnapshot;
import com.mywebsite.agent.utils.ModelChainResolver.ResolvedLink;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 链预设组链等价（Issue #210 UoW-7 验收，关 UoW-6 遗留）：同一批入口数据，先按
 * 平台化前语义（agent 作用域层 → 全站层，基线由 {@link ModelChainResolver} 对旧
 * 分组直接计算并手写钉死）算出各 agent 的旧链，再按 V63 的生成口径落成预设
 * （甲的预设＝甲专属块在前＋平台块在后；乙无专属入口、与丙共用同一条纯平台链
 * 预设——去重共用的可观察形态），走改造后的预设组链，断言有序 entry id 逐条
 * 等价；并断言换预设即换链、点名范围限预设内。
 *
 * <p>夹具与 UoW-6 等价测试同一形态（全站 3 条＋甲专属 4 条，两级在 KEY/CLI
 * 各有默认、专属默认 id 更大），使三代口径（旧作用域链／UoW-6 来源分层链／
 * UoW-7 预设链）在同一数据上可比。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiChainPresetEquivalenceTest {

    @Mock
    private AdminAiModelEntryMapper modelEntryMapper;

    @Mock
    private AdminAiChainPresetMapper chainPresetMapper;

    @Mock
    private AdminAgentMapper agentMapper;

    @Mock
    private ModelEntryCipher cipher;

    @Mock
    private AgentEmbeddingClassService embeddingClassService;

    private AdminAiModelEntryServiceImpl service;

    /** 改造前的一行入口数据（agentId 作用域口径）。 */
    private record LegacyRow(long id, Long agentId, String spec, boolean isDefault, String mode) {}

    /** 全站 3 条 + agent 10（甲）专属 4 条，两级在 KEY/CLI 各有默认，专属默认 id 更大。 */
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
                .map(AdminAiChainPresetEquivalenceTest::snapshot)
                .toList();
        List<EntrySnapshot> globalScope = rows.stream()
                .filter(row -> row.agentId() == null)
                .sorted(LEGACY_ORDER)
                .map(AdminAiChainPresetEquivalenceTest::snapshot)
                .toList();
        return ModelChainResolver.resolveChain(agentScope, globalScope, mode).stream()
                .map(ResolvedLink::id)
                .toList();
    }

    private static EntrySnapshot snapshot(LegacyRow row) {
        return new EntrySnapshot(
                row.id(), "e" + row.id(), row.spec(), "key-" + row.id(), null, null, row.isDefault(), row.mode());
    }

    /** 平台化后的实体（agent_id 置空、原值转存 source_agent_id；组链已不读这两个字段）。 */
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

    private static AdminAiChainPresetEntity preset(long id, String entryIdsJson) {
        AdminAiChainPresetEntity preset = new AdminAiChainPresetEntity();
        preset.setId(id);
        preset.setName("preset-" + id);
        preset.setPurpose("CHAT");
        preset.setEntryIds(entryIdsJson);
        return preset;
    }

    private static AdminAgentEntity agent(long id, Long chainPresetId) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(id);
        agent.setChainPresetId(chainPresetId);
        return agent;
    }

    /** 可变的甲实体：换预设用例直接改它的引用。 */
    private AdminAgentEntity agentJia;

    @BeforeEach
    void setUp() {
        AdminAiChainPresetServiceImpl chainPresetService = new AdminAiChainPresetServiceImpl(
                chainPresetMapper, modelEntryMapper, agentMapper, new ObjectMapper());
        service = new AdminAiModelEntryServiceImpl(
                modelEntryMapper, cipher, embeddingClassService, chainPresetService,
                chainPresetMapper, new ObjectMapper());
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
        });
        // V63 生成形态：甲的预设＝专属块 [8,10,9,11]＋平台块 [1,3,2]（各块默认在前、id 升序，
        // entry_ids 文本与 jsonb 序列化一致带空格）；乙/丙无专属入口，共用纯平台链预设 902。
        agentJia = agent(10L, 901L);
        // 各用例只问其中一两个 agent，整套 stub 放宽避免 strict stub 误报
        lenient().when(agentMapper.selectById(10L)).thenReturn(agentJia);
        lenient().when(agentMapper.selectById(20L)).thenReturn(agent(20L, 902L));
        lenient().when(agentMapper.selectById(30L)).thenReturn(agent(30L, 902L));
        lenient().when(chainPresetMapper.selectById(901L)).thenReturn(preset(901L, "[8, 10, 9, 11, 1, 3, 2]"));
        lenient().when(chainPresetMapper.selectById(902L)).thenReturn(preset(902L, "[1, 3, 2]"));
        List<AdminAiModelEntryEntity> all =
                MIXED_ROWS.stream().map(AdminAiChainPresetEquivalenceTest::converted).toList();
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            return all.stream().filter(entry -> ids.contains(entry.getId())).toList();
        });
    }

    private List<Long> presetChainIds(long agentId, String mode) {
        return service.chainCandidates(agentId, mode).stream().map(ChainCandidate::id).toList();
    }

    @Test
    void agentWithOwnEntriesKeepsLegacyChainWhole() {
        List<Long> baseline = legacyChainIds(MIXED_ROWS, 10L, null);
        assertThat(baseline).containsExactly(8L, 10L, 9L, 11L, 1L, 3L, 2L);

        assertThat(presetChainIds(10L, null)).containsExactlyElementsOf(baseline);
    }

    @Test
    void agentWithOwnEntriesKeepsLegacyChainPerMode() {
        assertThat(legacyChainIds(MIXED_ROWS, 10L, "KEY")).containsExactly(8L, 9L, 1L, 2L);
        assertThat(legacyChainIds(MIXED_ROWS, 10L, "CLI")).containsExactly(10L, 11L, 3L);

        assertThat(presetChainIds(10L, "KEY")).containsExactly(8L, 9L, 1L, 2L);
        assertThat(presetChainIds(10L, "CLI")).containsExactly(10L, 11L, 3L);
    }

    @Test
    void agentWithoutOwnEntriesGetsPurePlatformChain() {
        assertThat(legacyChainIds(MIXED_ROWS, 20L, null)).containsExactly(1L, 3L, 2L);
        assertThat(legacyChainIds(MIXED_ROWS, 20L, "KEY")).containsExactly(1L, 2L);
        assertThat(legacyChainIds(MIXED_ROWS, 20L, "CLI")).containsExactly(3L);

        assertThat(presetChainIds(20L, null)).containsExactly(1L, 3L, 2L);
        assertThat(presetChainIds(20L, "KEY")).containsExactly(1L, 2L);
        assertThat(presetChainIds(20L, "CLI")).containsExactly(3L);
    }

    @Test
    void agentsSharingThePlatformPresetResolveIdentically() {
        // 乙与丙同引一条纯平台链预设（V63 去重共用）：问到的链逐条一致
        assertThat(presetChainIds(30L, null)).containsExactlyElementsOf(presetChainIds(20L, null));
        assertThat(presetChainIds(30L, "KEY")).containsExactly(1L, 2L);
    }

    @Test
    void swappingPresetReferenceSwapsChainAndPinScope() {
        assertThat(service.selectionOnChain(10L, "KEY", 9L).spec()).isEqualTo("openai:agent-key-alt");

        // 甲改指纯平台链预设：链随之变为平台链，且点名只认预设内成员
        agentJia.setChainPresetId(902L);

        assertThat(presetChainIds(10L, null)).containsExactly(1L, 3L, 2L);
        assertThatThrownBy(() -> service.selectionOnChain(10L, "KEY", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        assertThat(service.selectionOnChain(10L, "KEY", 1L).spec()).isEqualTo("google:global-key-default");
    }
}
