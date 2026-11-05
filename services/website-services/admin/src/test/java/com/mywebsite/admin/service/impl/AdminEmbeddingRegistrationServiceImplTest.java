package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassDto;
import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassSaveRequest;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.agent.entity.AgentEmbeddingClassEntity;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 嵌入类登记服务（Issue #126 UoW-1）：整表保存的资格/判重/上限校验、按数组序落
 * sort_order、旧行整表替换、双 scope 的查询条件（全站走 IS NULL）、读时从入口
 * 解析 spec/类键（google-gla 规范化）。
 *
 * <p>登记 Mapper 用内存假实现驱动（insert/delete/selectList 作用于同一份列表），
 * 使「保存后再读」的往返断言成立；scope 过滤本身由 wrapper 表达，假实现按
 * base_id 字段模拟同一过滤口径。
 */
class AdminEmbeddingRegistrationServiceImplTest {

    private final List<AgentEmbeddingClassEntity> store = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(100);
    private final Map<Long, AdminAiModelEntryEntity> entries = new HashMap<>();

    private AdminEmbeddingRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        // 单测无 MyBatis 会话：手工注册实体 TableInfo，wrapper 才能物化 SQL 段供假实现解析 scope 条件。
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                AgentEmbeddingClassEntity.class);
        AgentEmbeddingClassMapper registrationMapper = mock(AgentEmbeddingClassMapper.class);
        when(registrationMapper.selectList(any())).thenAnswer(invocation -> {
            // 按 wrapper 的 scope 条件过滤，与真实 MyBatis 行为一致，否则整表替换会
            // 误伤另一 scope 的行：SQL 段含 IS NULL＝全站 scope，否则取 eq 的 Long 参数。
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> wrapper =
                    invocation.getArgument(0);
            java.util.stream.Stream<AgentEmbeddingClassEntity> stream = store.stream();
            if (wrapper.getSqlSegment().contains("IS NULL")) {
                stream = stream.filter(row -> row.getBaseId() == null);
            } else {
                Long baseIdParam = wrapper.getParamNameValuePairs().values().stream()
                        .filter(Long.class::isInstance)
                        .map(Long.class::cast)
                        .findFirst()
                        .orElse(null);
                stream = stream.filter(row -> baseIdParam != null && baseIdParam.equals(row.getBaseId()));
            }
            return stream.sorted(Comparator.comparing(AgentEmbeddingClassEntity::getSortOrder)
                            .thenComparing(AgentEmbeddingClassEntity::getId))
                    .toList();
        });
        when(registrationMapper.insert(any(AgentEmbeddingClassEntity.class))).thenAnswer(invocation -> {
            AgentEmbeddingClassEntity row = invocation.getArgument(0);
            row.setId(idSeq.incrementAndGet());
            store.add(row);
            return 1;
        });
        when(registrationMapper.updateById(any(AgentEmbeddingClassEntity.class))).thenReturn(1);
        when(registrationMapper.deleteById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            store.removeIf(row -> row.getId() == id);
            return 1;
        });

        AdminAiModelEntryMapper modelEntryMapper = mock(AdminAiModelEntryMapper.class);
        when(modelEntryMapper.selectById(anyLong()))
                .thenAnswer(invocation -> entries.get((Long) invocation.getArgument(0)));
        when(modelEntryMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(entries::get).filter(java.util.Objects::nonNull).toList();
        });

        AdminAiKnowledgeBaseMapper knowledgeBaseMapper = mock(AdminAiKnowledgeBaseMapper.class);
        when(knowledgeBaseMapper.selectById(anyLong())).thenAnswer(invocation -> {
            AdminAiKnowledgeBaseEntity base = new AdminAiKnowledgeBaseEntity();
            base.setId(invocation.getArgument(0));
            return base;
        });

        service = new AdminEmbeddingRegistrationServiceImpl(
                registrationMapper, modelEntryMapper, knowledgeBaseMapper);
    }

    private AdminAiModelEntryEntity entry(long id, String mode, String embeddingSpec) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(id);
        entry.setDisplayName("entry-" + id);
        entry.setMode(mode);
        entry.setEmbeddingSpec(embeddingSpec);
        entry.setEnabled(true);
        entries.put(id, entry);
        return entry;
    }

    private static AdminEmbeddingClassSaveRequest save(Long... entryIds) {
        return new AdminEmbeddingClassSaveRequest(
                java.util.Arrays.stream(entryIds)
                        .map(id -> new AdminEmbeddingClassSaveRequest.Item(id, null))
                        .toList());
    }

    @Test
    void replaceSavesInArrayOrderAndListResolvesSpecAndClassNameFromEntry() {
        entry(1L, "KEY", "google-gla:gemini-embedding-001");
        entry(2L, "KEY", "openai:text-embedding-3-small");

        List<AdminEmbeddingClassDto> saved = service.replaceRegistrations(7L, save(2L, 1L));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).entryId()).isEqualTo(2L);
        assertThat(saved.get(0).sortOrder()).isEqualTo(0);
        assertThat(saved.get(0).embeddingSpec()).isEqualTo("openai:text-embedding-3-small");
        assertThat(saved.get(0).className()).isEqualTo("openai:text-embedding-3-small");
        assertThat(saved.get(0).entryDisplayName()).isEqualTo("entry-2");
        assertThat(saved.get(0).enabled()).isTrue();
        // google-gla: 在类键里并入 google:（与消费侧同一规范化口径）。
        assertThat(saved.get(1).className()).isEqualTo("google:gemini-embedding-001");
        assertThat(saved.get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    void replaceIsWholeTableReplacementNotAppend() {
        entry(1L, "KEY", "openai:a");
        entry(2L, "KEY", "openai:b");
        service.replaceRegistrations(null, save(1L));

        List<AdminEmbeddingClassDto> after = service.replaceRegistrations(null, save(2L));

        assertThat(after).extracting(AdminEmbeddingClassDto::entryId).containsExactly(2L);
        assertThat(store).hasSize(1);
    }

    @Test
    void replaceWithEmptyListClearsTheScope() {
        entry(1L, "KEY", "openai:a");
        service.replaceRegistrations(3L, save(1L));

        List<AdminEmbeddingClassDto> after =
                service.replaceRegistrations(3L, new AdminEmbeddingClassSaveRequest(List.of()));

        assertThat(after).isEmpty();
        assertThat(store).isEmpty();
    }

    @Test
    void replaceRejectsMoreThanFourClasses() {
        for (long i = 1; i <= 5; i++) {
            entry(i, "KEY", "openai:m" + i);
        }
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(1L, 2L, 3L, 4L, 5L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
        assertThat(store).isEmpty();
    }

    @Test
    void replaceRejectsTheSameEntryTwice() {
        entry(1L, "KEY", "openai:a");
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(1L, 1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void replaceRejectsTwoEntriesSharingOneNormalizedClassKey() {
        entry(1L, "KEY", "google:text-embedding-004");
        entry(2L, "KEY", "google-gla:text-embedding-004");
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(1L, 2L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
        assertThat(store).isEmpty();
    }

    @Test
    void replaceRejectsCliEntry() {
        entry(1L, "CLI", null);
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void replaceRejectsKeyEntryWithoutEmbeddingSpec() {
        entry(1L, "KEY", "  ");
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void replaceRejectsUnknownEntry() {
        assertThatThrownBy(() -> service.replaceRegistrations(null, save(999L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void listOfGlobalScopeAndSpaceScopeAreSeparateRows() {
        entry(1L, "KEY", "openai:a");
        entry(2L, "KEY", "openai:b");
        service.replaceRegistrations(null, save(1L));
        service.replaceRegistrations(9L, save(2L));
        // 两 scope 互不影响：各读各的，行上的 base_id 如实落 scope。
        assertThat(service.listRegistrations(null))
                .extracting(AdminEmbeddingClassDto::entryId)
                .containsExactly(1L);
        assertThat(service.listRegistrations(9L))
                .extracting(AdminEmbeddingClassDto::entryId)
                .containsExactly(2L);
        assertThat(store).extracting(AgentEmbeddingClassEntity::getBaseId).containsExactly(null, 9L);
    }

    @Test
    void listRejectsUnknownKnowledgeBase() {
        // scope 指向不存在的空间时 404；全站 scope（null）不做存在性校验。
        AdminAiKnowledgeBaseMapper emptyBaseMapper = mock(AdminAiKnowledgeBaseMapper.class);
        when(emptyBaseMapper.selectById(anyLong())).thenReturn(null);
        AdminEmbeddingRegistrationServiceImpl strictService = new AdminEmbeddingRegistrationServiceImpl(
                mock(AgentEmbeddingClassMapper.class),
                mock(AdminAiModelEntryMapper.class),
                emptyBaseMapper);
        assertThatThrownBy(() -> strictService.listRegistrations(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> strictService.replaceRegistrations(404L, save()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void listShowsDanglingRegistrationWhenEntryWasDeleted() {
        entry(1L, "KEY", "openai:a");
        service.replaceRegistrations(null, save(1L));
        entries.remove(1L);

        List<AdminEmbeddingClassDto> listed = service.listRegistrations(null);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).entryDisplayName()).isNull();
        assertThat(listed.get(0).embeddingSpec()).isNull();
        assertThat(listed.get(0).className()).isNull();
    }
}
