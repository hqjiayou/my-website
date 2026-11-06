package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话生效链（Issue #210 UoW-7 起、#214 UoW-3 内置化）：链由 agent 自有链
 * 决定——按模式名单序取启用成员，名单序内第一个默认作主用，其后按名单序
 * 备援；坏主用整链不启用。夹具以 {@link #givenChainMembers} 声明各模式成员
 * （入口未标 mode 按 KEY 归属；entry 的第二参数来源标注自 UoW-7 起不再参与
 * 组链、仅留痕）。与平台化前旧链的逐条等价由
 * {@link AdminAiChainPresetEquivalenceTest} 钉死。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiModelEntryServiceImplTest {

    @Mock
    private AdminAiModelEntryMapper modelEntryMapper;

    @Mock
    private ModelEntryCipher cipher;

    @Mock
    private AgentEmbeddingClassService embeddingClassService;

    @Mock
    private com.mywebsite.admin.service.AdminAiChainPresetService chainPresetService;

    private AdminAiModelEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiModelEntryServiceImpl(
                modelEntryMapper, cipher, embeddingClassService, chainPresetService);
        // 无默认入口的用例走不到解密，stub 放宽避免 strict stub 误报
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
        });
    }

    /**
     * 声明 agent 的自有链成员（参数顺序即名单序）：桩按入口 mode 拆分——
     * {@code ownChainEntryIds} 被以具体模式调用时只回该模式成员，整链（null）
     * 回 KEY 名单＋CLI 名单，与服务实现的整链定义一致。
     */
    private void givenChainMembers(long agentId, List<AdminAiModelEntryEntity> members) {
        lenient()
                .when(chainPresetService.ownChainEntryIds(org.mockito.ArgumentMatchers.eq(agentId), any()))
                .thenAnswer(invocation -> {
                    String mode = invocation.getArgument(1);
                    return members.stream()
                            .filter(member -> mode == null
                                    || mode.equalsIgnoreCase(
                                            member.getMode() == null || member.getMode().isBlank()
                                                    ? "KEY"
                                                    : member.getMode().trim()))
                            .map(AdminAiModelEntryEntity::getId)
                            .toList();
                });
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenReturn(members);
    }

    private static AdminAiModelEntryEntity entry(long id, Long sourceAgentId, String spec, boolean isDefault) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(id);
        entry.setSourceAgentId(sourceAgentId);
        entry.setModelSpec(spec);
        entry.setApiKey("enc:key-" + id);
        entry.setEnabled(true);
        entry.setDefaultEntry(isDefault);
        return entry;
    }

    private static List<String> specs(List<AgentModelSelection> chain) {
        return chain.stream().map(AgentModelSelection::spec).toList();
    }

    // 嵌入类自 Issue #126 UoW-2 起改读登记：本类只委托 agent-core 共享服务，
    // 类的选择/合并/dedupe/物化语义由 AgentEmbeddingClassServiceImplTest 钉死；
    // 这里只钉委托本身（顺序即登记序，消费方取首元的口径在知识服务侧钉死）。
    // 单值接口 embeddingSelection 已于 UoW-5 退役删除。
    @Test
    void embeddingClassesForSpaceDelegateToRegistrationService() {
        List<AgentModelSelection> registered =
                List.of(
                        new AgentModelSelection(
                                "google:gemini-3-flash-preview", "key-5", null, "google:gemini-embedding-001"),
                        new AgentModelSelection("openai:chat", "key-9", null, "openai:text-embedding-3-small"));
        when(embeddingClassService.embeddingClassesForSpace(10L)).thenReturn(registered);

        assertThat(service.embeddingClassesForSpace(10L)).isSameAs(registered);
    }

    @Test
    void embeddingClassesForSpaceIsEmptyWhenNoRegisteredClasses() {
        when(embeddingClassService.embeddingClassesForSpace(10L)).thenReturn(List.of());

        assertThat(service.embeddingClassesForSpace(10L)).isEmpty();
    }

    @Test
    void chainStripsEmbeddingSpecFromCliEntry() {
        AdminAiModelEntryEntity cli = entry(2L, 10L, "openai:gpt", true);
        cli.setMode("CLI");
        cli.setEmbeddingSpec("google:gemini-embedding-001");
        givenChainMembers(10L, List.of(cli));

        List<AgentModelSelection> chain = service.selectionChain(10L);
        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).embeddingSpec()).isNull();
    }

    @Test
    void chainFollowsOwnChainOrderWithPrimaryFirst() {
        // 名单序即组链序：第一个默认作主用，其后按名单序备援
        givenChainMembers(
                10L,
                List.of(
                        entry(3L, 10L, "google:agent-default", true),
                        entry(4L, 10L, "openai:agent-alt", false),
                        entry(1L, null, "google:global-default", true),
                        entry(2L, null, "anthropic:global-alt", false)));

        List<AgentModelSelection> chain = service.selectionChain(10L);

        assertThat(specs(chain))
                .containsExactly(
                        "google:agent-default", "openai:agent-alt", "google:global-default", "anthropic:global-alt");
        assertThat(chain.get(0).apiKey()).isEqualTo("key-3");
    }

    @Test
    void currentSelectionReturnsChainHead() {
        givenChainMembers(
                10L,
                List.of(
                        entry(3L, 10L, "google:agent-default", true),
                        entry(1L, null, "google:global-default", true)));

        assertThat(service.currentSelection(10L).spec()).isEqualTo("google:agent-default");
    }

    @Test
    void chainFallsToLaterDefaultWhenOwnChainStartsWithNonDefault() {
        givenChainMembers(
                10L,
                List.of(
                        entry(4L, 10L, "openai:agent-alt", false),
                        entry(1L, null, "google:global-default", true),
                        entry(2L, null, "anthropic:global-alt", false)));

        List<AgentModelSelection> chain = service.selectionChain(10L);

        assertThat(specs(chain))
                .containsExactly("google:global-default", "openai:agent-alt", "anthropic:global-alt");
    }

    @Test
    void chainIsEmptyWithoutAnyDefault() {
        givenChainMembers(
                10L,
                List.of(
                        entry(4L, 10L, "openai:agent-alt", false),
                        entry(2L, null, "anthropic:global-alt", false)));

        assertThat(service.selectionChain(10L)).isEmpty();
        assertThat(service.currentSelection(10L)).isNull();
    }

    @Test
    void chainIsEmptyWhenAgentHasNoOwnChain() {
        lenient()
                .when(chainPresetService.ownChainEntryIds(
                        org.mockito.ArgumentMatchers.eq(10L), any()))
                .thenReturn(List.of());

        assertThat(service.selectionChain(10L)).isEmpty();
        assertThat(service.currentSelection(10L)).isNull();
    }

    @Test
    void chainIsEmptyWhenPrimaryKeyUndecryptable() {
        when(cipher.decrypt("enc:key-3")).thenReturn("");
        givenChainMembers(
                10L,
                List.of(
                        entry(3L, 10L, "google:agent-default", true),
                        entry(4L, 10L, "openai:agent-alt", false),
                        entry(1L, null, "google:global-default", true)));

        // 主用坏掉时备援不得顶上，整链不启用
        assertThat(service.selectionChain(10L)).isEmpty();
    }

    @Test
    void chainStaysInsideTheRequestedMode() {
        AdminAiModelEntryEntity cli = entry(2L, 10L, "openai:muse-wonder-1.0", true);
        cli.setMode("CLI");
        AdminAiModelEntryEntity key = entry(1L, null, "google:gemini-3-flash-preview", true);
        key.setMode("KEY");
        givenChainMembers(10L, List.of(cli, key));

        assertThat(specs(service.selectionChain(10L, "CLI"))).containsExactly("openai:muse-wonder-1.0");
        assertThat(specs(service.selectionChain(10L, "key"))).containsExactly("google:gemini-3-flash-preview");
    }

    @Test
    void chainRejectsUnknownMode() {
        assertThatThrownBy(() -> service.selectionChain(10L, "BOGUS")).isInstanceOf(BusinessException.class);
    }

    @Test
    void chainSkipsUndecryptableFallbacks() {
        when(cipher.decrypt("enc:key-4")).thenReturn("");
        givenChainMembers(
                10L,
                List.of(
                        entry(3L, 10L, "google:agent-default", true),
                        entry(4L, 10L, "openai:agent-alt", false),
                        entry(1L, null, "google:global-default", true)));

        assertThat(specs(service.selectionChain(10L)))
                .containsExactly("google:agent-default", "google:global-default");
    }

    @Test
    void chainSkipsDisabledAndMissingMembers() {
        AdminAiModelEntryEntity disabled = entry(4L, 10L, "openai:agent-alt", false);
        disabled.setEnabled(false);
        // 自有链含已删除成员 99：批量查询不返回它（@TableLogic），组链按缺失跳过；
        // 停用成员 4 在结果里但被启用过滤挡下
        lenient()
                .when(chainPresetService.ownChainEntryIds(
                        org.mockito.ArgumentMatchers.eq(10L), any()))
                .thenReturn(List.of(3L, 4L, 99L, 1L));
        when(modelEntryMapper.selectBatchIds(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                disabled,
                                entry(1L, null, "google:global-default", true)));

        assertThat(specs(service.selectionChain(10L)))
                .containsExactly("google:agent-default", "google:global-default");
    }

    @Test
    void normalizeModeDefaultsToKeyAndAcceptsCli() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(null)).isEqualTo("KEY");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(" ")).isEqualTo("KEY");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(" cli ")).isEqualTo("CLI");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode("key")).isEqualTo("KEY");
    }

    @Test
    void normalizeModeRejectsUnknown() {
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeMode("BOGUS"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void normalizeAliasBlankReturnsNull() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias(null)).isNull();
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias("   ")).isNull();
    }

    @Test
    void normalizeAliasTrimsAndLowercases() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias(" Gpt-4o_Mini ")).isEqualTo("gpt-4o_mini");
    }

    @Test
    void normalizeAliasRejectsInvalidFormat() {
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("A"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("-leading-dash"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("has space"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createEntryRejectsAliasAlreadyTakenByAnotherEntry() {
        when(modelEntryMapper.selectCount(any())).thenReturn(1L);

        AdminAiModelEntryWriteRequest request = new AdminAiModelEntryWriteRequest(
                "Display",
                "google:gemini-3-flash-preview",
                "sk-live-key",
                null,
                null,
                true,
                false,
                null,
                null,
                "taken-alias");

        assertThatThrownBy(() -> service.createEntry(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void createEntryStopsWritingAgentScope() {
        // 平台化后新建入口：agent_id 停写、无来源标注，不再校验任何 agent 存在性。
        ArgumentCaptor<AdminAiModelEntryEntity> captor = ArgumentCaptor.forClass(AdminAiModelEntryEntity.class);
        when(modelEntryMapper.insert(captor.capture()))
                .thenAnswer(invocation -> {
                    captor.getValue().setId(42L);
                    return 1;
                });
        AdminAiModelEntryEntity saved = entry(42L, null, "google:gemini-3-flash-preview", false);
        when(modelEntryMapper.selectById(42L)).thenReturn(saved);
        lenient().when(cipher.encrypt(any())).thenReturn("enc:stored");

        AdminAiModelEntryDto dto = service.createEntry(new AdminAiModelEntryWriteRequest(
                "Display",
                "google:gemini-3-flash-preview",
                "sk-live-key",
                null,
                null,
                true,
                false,
                null,
                "KEY",
                null));

        assertThat(captor.getValue().getAgentId()).isNull();
        assertThat(captor.getValue().getSourceAgentId()).isNull();
        assertThat(dto.sourceAgentId()).isNull();
    }
}
