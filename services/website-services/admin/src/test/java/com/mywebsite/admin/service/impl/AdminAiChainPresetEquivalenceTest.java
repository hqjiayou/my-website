package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.entity.AdminAgentEntity;
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
 * 组链等价链（三代同数据）：同一批入口数据，先按平台化前语义（agent 作用域层 →
 * 全站层，基线由 {@link ModelChainResolver} 对旧分组直接计算并手写钉死）算出各
 * agent 的旧链；#210 UoW-7 时旧链落成预设（V63），#214 UoW-3 的 V69 再把预设
 * 内容按成员模式拆分逐字拷入 agent 自有链。本类夹具的自有链字段即 V69 对原
 * 预设文本的拷贝结果（甲原预设 {@code [8, 10, 9, 11, 1, 3, 2]} → KEY
 * {@code [8, 9, 1, 2]}／CLI {@code [10, 11, 3]}；乙/丙原共用预设
 * {@code [1, 3, 2]} → KEY {@code [1, 2]}／CLI {@code [3]}），断言<b>逐模式</b>
 * 解析与旧链逐条等价——这正是「迁移前后同一 agent 同一模式解析等价」的钉子。
 *
 * <p>整链（mode=null）视图是唯一变了的口径：预设时代两模式同存一份名单、
 * 交错序即预设序；拆分存储后整链定义为 KEY 链在前、CLI 链在后（见
 * {@code AdminAiChainPresetService.ownChainEntryIds}）。生产路径自 #208 起
 * 均显式传模式，整链仅剩兼容面，本类按新定义钉死、不再与旧交错序比对。
 *
 * <p>夹具与 UoW-6 等价测试同一形态（全站 3 条＋甲专属 4 条，两级在 KEY/CLI
 * 各有默认、专属默认 id 更大）。
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

    /** agent 实体只带自有链两字段（即 V69 拷贝结果），其余与组链无关。 */
    private static AdminAgentEntity agent(long id, String keyEntryIds, String cliEntryIds) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(id);
        agent.setChainKeyEntryIds(keyEntryIds);
        agent.setChainCliEntryIds(cliEntryIds);
        return agent;
    }

    /** 可变的甲实体：改链用例直接改它的自有链字段。 */
    private AdminAgentEntity agentJia;

    @BeforeEach
    void setUp() {
        AdminAiChainPresetServiceImpl chainPresetService = new AdminAiChainPresetServiceImpl(
                chainPresetMapper, modelEntryMapper, agentMapper, new ObjectMapper());
        service = new AdminAiModelEntryServiceImpl(
                modelEntryMapper, cipher, embeddingClassService, chainPresetService);
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
        });
        // V69 拷贝形态：甲原预设 [8, 10, 9, 11, 1, 3, 2]（专属块在前＋平台块在后）
        // 按成员模式拆分 → KEY [8, 9, 1, 2]、CLI [10, 11, 3]；乙/丙原共用纯平台链
        // 预设 [1, 3, 2]，各自拷得 KEY [1, 2]、CLI [3]（拷贝后互不相干）。
        agentJia = agent(10L, "[8, 9, 1, 2]", "[10, 11, 3]");
        // 各用例只问其中一两个 agent，整套 stub 放宽避免 strict stub 误报
        lenient().when(agentMapper.selectById(10L)).thenReturn(agentJia);
        lenient().when(agentMapper.selectById(20L)).thenReturn(agent(20L, "[1, 2]", "[3]"));
        lenient().when(agentMapper.selectById(30L)).thenReturn(agent(30L, "[1, 2]", "[3]"));
        List<AdminAiModelEntryEntity> all =
                MIXED_ROWS.stream().map(AdminAiChainPresetEquivalenceTest::converted).toList();
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            return all.stream().filter(entry -> ids.contains(entry.getId())).toList();
        });
    }

    private List<Long> ownChainIds(long agentId, String mode) {
        return service.chainCandidates(agentId, mode).stream().map(ChainCandidate::id).toList();
    }

    @Test
    void copiedOwnChainResolvesPerModeExactlyLikeTheLegacyChain() {
        // 迁移等价：V69 拷入的自有链在每个模式下的解析与平台化前旧链逐条一致
        assertThat(legacyChainIds(MIXED_ROWS, 10L, "KEY")).containsExactly(8L, 9L, 1L, 2L);
        assertThat(legacyChainIds(MIXED_ROWS, 10L, "CLI")).containsExactly(10L, 11L, 3L);

        assertThat(ownChainIds(10L, "KEY")).containsExactly(8L, 9L, 1L, 2L);
        assertThat(ownChainIds(10L, "CLI")).containsExactly(10L, 11L, 3L);
    }

    @Test
    void wholeChainViewIsKeyChainThenCliChain() {
        // 整链新定义（类注释）：不再是旧交错序 [8, 10, 9, 11, 1, 3, 2]，
        // 而是 KEY 链整体在前、CLI 链整体在后。
        assertThat(ownChainIds(10L, null)).containsExactly(8L, 9L, 1L, 2L, 10L, 11L, 3L);
    }

    @Test
    void agentWithoutOwnEntriesGetsPurePlatformChain() {
        assertThat(legacyChainIds(MIXED_ROWS, 20L, "KEY")).containsExactly(1L, 2L);
        assertThat(legacyChainIds(MIXED_ROWS, 20L, "CLI")).containsExactly(3L);

        assertThat(ownChainIds(20L, "KEY")).containsExactly(1L, 2L);
        assertThat(ownChainIds(20L, "CLI")).containsExactly(3L);
        assertThat(ownChainIds(20L, null)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void agentsWithIdenticalCopiedChainsResolveIdentically() {
        // 乙与丙在 V63 时代共用一条预设，V69 后各持一份内容相同的拷贝：解析一致
        assertThat(ownChainIds(30L, null)).containsExactlyElementsOf(ownChainIds(20L, null));
        assertThat(ownChainIds(30L, "KEY")).containsExactly(1L, 2L);
    }

    @Test
    void rewritingOwnChainSwapsChainAndPinScope() {
        assertThat(service.selectionOnChain(10L, "KEY", 9L).spec()).isEqualTo("openai:agent-key-alt");

        // 甲的自有链改写成纯平台链内容：链随之变为平台链，且点名只认链内成员
        agentJia.setChainKeyEntryIds("[1, 2]");
        agentJia.setChainCliEntryIds("[3]");

        assertThat(ownChainIds(10L, null)).containsExactly(1L, 2L, 3L);
        assertThatThrownBy(() -> service.selectionOnChain(10L, "KEY", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        assertThat(service.selectionOnChain(10L, "KEY", 1L).spec()).isEqualTo("google:global-key-default");
    }
}
