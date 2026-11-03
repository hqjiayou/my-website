package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.entity.AgentEmbeddingClassEntity;
import com.mywebsite.agent.entity.AgentEmbeddingClassMaterializationEntity;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMapper;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMaterializationMapper;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 登记制读取与首次读物化（Issue #126 UoW-2）。物化用例的期望类序按旧推算
 * 语义（站点＋全站一次扫描、站点优先 dedupe）手工推导后钉死，证明按 scope
 * 分别物化＋合并读与旧结果逐字一致。
 */
@ExtendWith(MockitoExtension.class)
class AgentEmbeddingClassServiceImplTest {

    @Mock
    private AgentEmbeddingClassMapper registrationMapper;

    @Mock
    private AgentEmbeddingClassMaterializationMapper materializationMapper;

    @Mock
    private AgentAiModelEntryMapper modelEntryMapper;

    @Mock
    private ModelEntryCipher cipher;

    private AgentEmbeddingClassServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new AgentEmbeddingClassServiceImpl(
                        registrationMapper, materializationMapper, modelEntryMapper, cipher);
        // enc:key-<id> 解出 key-<id>；enc:bad-<id> 模拟解不出（返回 null）。
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            if (raw == null || !raw.startsWith("enc:")) {
                return raw;
            }
            String plain = raw.substring(4);
            return plain.startsWith("bad") ? null : plain;
        });
    }

    // ------------------------------------------------------------------
    // 登记读取
    // ------------------------------------------------------------------

    @Test
    void readsRegistrationsSiteFirstThenGlobalWithCrossScopeDedupeAndCap() {
        givenMaterialized();
        Map<Long, AgentAiModelEntryEntity> entries = new HashMap<>();
        entries.put(1L, entry(1, 10L, "google:chat", "google-gla:gemini-embedding-001"));
        entries.put(2L, entry(2, 10L, "openai:chat", "openai:text-embedding-3-small"));
        entries.put(3L, entry(3, null, "google:chat", "google:gemini-embedding-001"));
        entries.put(4L, entry(4, null, "openai:chat", "openai:text-embedding-3-large"));
        entries.put(5L, entry(5, null, "openai:chat", "openai:embed-extra"));
        entries.put(6L, entry(6, null, "openai:chat", "openai:embed-dropped"));
        givenEntries(entries);
        when(registrationMapper.selectList(any()))
                .thenReturn(List.of(row(101, 10L, 1L, 0), row(102, 10L, 2L, 1)))
                .thenReturn(
                        List.of(
                                row(103, null, 3L, 0),
                                row(104, null, 4L, 1),
                                row(105, null, 5L, 2),
                                row(106, null, 6L, 3)));

        List<AgentModelSelection> classes = service.embeddingClasses(10L);

        // 站点行在前；全站的 google: 类与站点 google-gla: 同类、站点胜出被压掉；
        // 合并后上限 4，全站最后一类落选。元素保留登记行引用入口的原始 spec 文本。
        assertThat(classes).extracting(AgentModelSelection::embeddingSpec)
                .containsExactly(
                        "google-gla:gemini-embedding-001",
                        "openai:text-embedding-3-small",
                        "openai:text-embedding-3-large",
                        "openai:embed-extra");
        assertThat(classes.get(0).spec()).isEqualTo("google:chat");
        assertThat(classes.get(0).apiKey()).isEqualTo("key-1");
        // marker 已在：不物化、不走旧扫描。
        verify(registrationMapper, never()).insert(any(AgentEmbeddingClassEntity.class));
        verify(materializationMapper, never()).insert(any(AgentEmbeddingClassMaterializationEntity.class));
        verify(modelEntryMapper, never()).selectList(any());
    }

    @Test
    void skipsRowsWhoseReferencedEntryIsUnusable() {
        givenMaterialized();
        Map<Long, AgentAiModelEntryEntity> entries = new HashMap<>();
        entries.put(1L, entry(1, 10L, "google:chat", "google:embed-ok"));
        AgentAiModelEntryEntity disabled = entry(2, 10L, "google:chat", "google:embed-disabled");
        disabled.setEnabled(false);
        entries.put(2L, disabled);
        AgentAiModelEntryEntity cli = entry(3, 10L, "openai:cli", "openai:embed-cli");
        cli.setMode("CLI");
        entries.put(3L, cli);
        entries.put(4L, entry(4, 10L, "google:chat", "  "));
        AgentAiModelEntryEntity badKey = entry(5, 10L, "google:chat", "google:embed-bad-key");
        badKey.setApiKey("enc:bad-5");
        entries.put(5L, badKey);
        givenEntries(entries);
        // entry 99 不在批量回读结果里（不存在/已软删）。
        when(registrationMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                row(101, 10L, 99L, 0),
                                row(102, 10L, 2L, 1),
                                row(103, 10L, 3L, 2),
                                row(104, 10L, 4L, 3),
                                row(105, 10L, 5L, 4),
                                row(106, 10L, 1L, 5)))
                .thenReturn(List.of());

        List<AgentModelSelection> classes = service.embeddingClasses(10L);

        assertThat(classes).extracting(AgentModelSelection::embeddingSpec)
                .containsExactly("google:embed-ok");
    }

    @Test
    void materializedScopeWithNoRegistrationsStaysEmpty() {
        givenMaterialized();
        when(registrationMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.embeddingClasses(10L)).isEmpty();

        // 人工删光登记后 marker 仍在：不重新物化、不复活。
        verify(registrationMapper, never()).insert(any(AgentEmbeddingClassEntity.class));
        verify(materializationMapper, never()).insert(any(AgentEmbeddingClassMaterializationEntity.class));
        verify(modelEntryMapper, never()).selectList(any());
    }

    // ------------------------------------------------------------------
    // 首次读物化
    // ------------------------------------------------------------------

    @Test
    void materializesBothScopesOnFirstReadWithLegacyWinners() {
        when(materializationMapper.selectById(any())).thenReturn(null);
        // 旧扫描序（默认在前、id 升序）下两侧的启用入口。
        AgentAiModelEntryEntity siteGoogle = entry(11, 10L, "google:chat-a", "google-gla:gemini-embedding-001");
        siteGoogle.setDefaultEntry(true);
        siteGoogle.setBaseUrl(" https://embed.example.com ");
        AgentAiModelEntryEntity siteGoogleAlias = entry(12, 10L, "google:chat-b", "google:gemini-embedding-001");
        AgentAiModelEntryEntity siteCli = entry(13, 10L, "openai:cli", "openai:cli-embed");
        siteCli.setMode("CLI");
        AgentAiModelEntryEntity siteBadKey = entry(14, 10L, "openai:chat-c", "openai:text-embedding-3-small");
        siteBadKey.setApiKey("enc:bad-14");
        AgentAiModelEntryEntity siteOpenAi = entry(15, 10L, "openai:chat-d", "openai:text-embedding-3-small");
        AgentAiModelEntryEntity globalGoogle = entry(21, null, "google:chat-e", "google:gemini-embedding-001");
        AgentAiModelEntryEntity globalLarge = entry(22, null, "openai:chat-f", "openai:text-embedding-3-large");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(List.of(siteGoogle, siteGoogleAlias, siteCli, siteBadKey, siteOpenAi))
                .thenReturn(List.of(globalGoogle, globalLarge));

        List<AgentEmbeddingClassEntity> insertedRows = captureRegistrationInserts();
        List<AgentEmbeddingClassMaterializationEntity> insertedMarkers = captureMarkerInserts();
        givenEntries(
                Map.of(
                        11L, siteGoogle,
                        15L, siteOpenAi,
                        21L, globalGoogle,
                        22L, globalLarge));
        // 物化后的读取就返回刚写入的行（按 scope 分两次调用）。
        AtomicInteger readCalls = new AtomicInteger();
        when(registrationMapper.selectList(any()))
                .thenAnswer(
                        invocation -> {
                            int call = readCalls.getAndIncrement();
                            return insertedRows.stream()
                                    .filter(
                                            row ->
                                                    call == 0
                                                            ? Long.valueOf(10L).equals(row.getAgentId())
                                                            : row.getAgentId() == null)
                                    .toList();
                        });

        List<AgentModelSelection> classes = service.embeddingClasses(10L);

        // 与旧推算对同一批入口的输出逐字一致：站点 google-gla 胜出（全站 google:
        // 被压掉）、坏 key 的入口不占类位（同类由 15 号胜出）、CLI 不进。
        assertThat(classes).extracting(AgentModelSelection::embeddingSpec)
                .containsExactly(
                        "google-gla:gemini-embedding-001",
                        "openai:text-embedding-3-small",
                        "openai:text-embedding-3-large");
        assertThat(classes.get(0).apiKey()).isEqualTo("key-11");
        assertThat(classes.get(0).baseUrl()).isEqualTo("https://embed.example.com");
        // 物化行：站点 scope 胜者 11、15；全站 scope 胜者 21、22；顺序即类序。
        assertThat(insertedRows)
                .extracting(AgentEmbeddingClassEntity::getEntryId)
                .containsExactly(11L, 15L, 21L, 22L);
        assertThat(insertedRows)
                .extracting(AgentEmbeddingClassEntity::getSortOrder)
                .containsExactly(0, 1, 0, 1);
        assertThat(insertedRows)
                .extracting(AgentEmbeddingClassEntity::getAgentId)
                .containsExactly(10L, 10L, null, null);
        assertThat(insertedMarkers)
                .extracting(AgentEmbeddingClassMaterializationEntity::getScopeKey)
                .containsExactly("AGENT:10", "GLOBAL");
    }

    @Test
    void materializationCapsAtFourClassesPerScope() {
        when(materializationMapper.selectById("AGENT:10")).thenReturn(null);
        when(materializationMapper.selectById("GLOBAL"))
                .thenReturn(new AgentEmbeddingClassMaterializationEntity());
        List<AgentAiModelEntryEntity> siteEntries = new ArrayList<>();
        Map<Long, AgentAiModelEntryEntity> entries = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            AgentAiModelEntryEntity entry = entry(10L + i, 10L, "openai:chat", "openai:embed-" + i);
            siteEntries.add(entry);
            entries.put(entry.getId(), entry);
        }
        when(modelEntryMapper.selectList(any())).thenReturn(siteEntries);
        List<AgentEmbeddingClassEntity> insertedRows = captureRegistrationInserts();
        givenEntries(entries);
        when(registrationMapper.selectList(any()))
                .thenAnswer(
                        invocation ->
                                insertedRows.stream()
                                        .filter(row -> Long.valueOf(10L).equals(row.getAgentId()))
                                        .toList())
                .thenReturn(List.of());

        List<AgentModelSelection> classes = service.embeddingClasses(10L);

        assertThat(insertedRows).hasSize(4);
        assertThat(classes).extracting(AgentModelSelection::embeddingSpec)
                .containsExactly("openai:embed-1", "openai:embed-2", "openai:embed-3", "openai:embed-4");
        // 全站 marker 已在，不扫全站。
        verify(modelEntryMapper, org.mockito.Mockito.times(1)).selectList(any());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private void givenMaterialized() {
        when(materializationMapper.selectById(any()))
                .thenReturn(new AgentEmbeddingClassMaterializationEntity());
    }

    private void givenEntries(Map<Long, AgentAiModelEntryEntity> entries) {
        lenient().when(modelEntryMapper.selectBatchIds(any()))
                .thenAnswer(
                        invocation -> {
                            Collection<?> ids = invocation.getArgument(0);
                            List<AgentAiModelEntryEntity> found = new ArrayList<>();
                            for (Object id : ids) {
                                AgentAiModelEntryEntity entry = entries.get(id);
                                if (entry != null) {
                                    found.add(entry);
                                }
                            }
                            return found;
                        });
    }

    private List<AgentEmbeddingClassEntity> captureRegistrationInserts() {
        List<AgentEmbeddingClassEntity> inserted = new ArrayList<>();
        lenient().when(registrationMapper.insert(any(AgentEmbeddingClassEntity.class)))
                .thenAnswer(
                        invocation -> {
                            inserted.add(invocation.getArgument(0));
                            return 1;
                        });
        return inserted;
    }

    private List<AgentEmbeddingClassMaterializationEntity> captureMarkerInserts() {
        List<AgentEmbeddingClassMaterializationEntity> inserted = new ArrayList<>();
        lenient().when(materializationMapper.insert(any(AgentEmbeddingClassMaterializationEntity.class)))
                .thenAnswer(
                        invocation -> {
                            inserted.add(invocation.getArgument(0));
                            return 1;
                        });
        return inserted;
    }

    private static AgentAiModelEntryEntity entry(long id, Long agentId, String modelSpec, String embeddingSpec) {
        AgentAiModelEntryEntity entry = new AgentAiModelEntryEntity();
        entry.setId(id);
        entry.setAgentId(agentId);
        entry.setModelSpec(modelSpec);
        entry.setApiKey("enc:key-" + id);
        entry.setEmbeddingSpec(embeddingSpec);
        entry.setEnabled(true);
        entry.setDefaultEntry(false);
        entry.setMode("KEY");
        return entry;
    }

    private static AgentEmbeddingClassEntity row(long id, Long agentId, long entryId, int sortOrder) {
        AgentEmbeddingClassEntity row = new AgentEmbeddingClassEntity();
        row.setId(id);
        row.setAgentId(agentId);
        row.setEntryId(entryId);
        row.setEnabled(true);
        row.setSortOrder(sortOrder);
        return row;
    }
}
