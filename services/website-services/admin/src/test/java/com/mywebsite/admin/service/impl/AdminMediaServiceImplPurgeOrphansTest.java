package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.constant.AdminMediaConstants;
import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.mapper.AdminContentMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper;
import com.mywebsite.admin.mapper.AdminMediaReferenceMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.properties.AdminMediaProperties;
import com.mywebsite.infra.properties.StorageProperties;
import com.mywebsite.infra.service.ObjectStorageService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * orphan 扫尾：对象 remove 成功后打 {@code object_purged_at}，保证 batch 能越过前 N 条推进。
 */
@ExtendWith(MockitoExtension.class)
class AdminMediaServiceImplPurgeOrphansTest {

    @Mock
    private AdminMediaMapper adminMediaMapper;

    @Mock
    private AdminMediaReferenceMapper adminMediaReferenceMapper;

    @Mock
    private AdminAgentMapper adminAgentMapper;

    @Mock
    private AdminContentMapper adminContentMapper;

    @Mock
    private ObjectStorageService objectStorageService;

    private AdminMediaProperties mediaProperties;
    private AdminMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        mediaProperties = new AdminMediaProperties();
        mediaProperties.setOrphanRetention(Duration.ofHours(1));
        mediaProperties.setOrphanStaleUpload(Duration.ofHours(1));
        mediaProperties.setOrphanBatchSize(2);
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setBucket("test");
        service = new AdminMediaServiceImpl(
                adminMediaMapper,
                adminMediaReferenceMapper,
                adminAgentMapper,
                adminContentMapper,
                objectStorageService,
                storageProperties,
                mediaProperties);
    }

    @Test
    void purgeOrphans_marksSoftDeletedAfterSuccessfulRemove() {
        AdminMediaEntity softDeleted = softDeleted(10L, "k-10");
        when(adminMediaMapper.selectOrphanCandidates(any(), any(), eq(2))).thenReturn(List.of(softDeleted));
        when(adminMediaMapper.markObjectPurged(eq(10L), any())).thenReturn(1);

        service.purgeOrphans();

        verify(objectStorageService).remove("b", "k-10");
        verify(adminMediaMapper).markObjectPurged(eq(10L), any(OffsetDateTime.class));
        verify(adminMediaMapper, never()).deleteById(anyLong());
    }

    @Test
    void purgeOrphans_skipsMarkWhenRemoveFails() {
        AdminMediaEntity softDeleted = softDeleted(11L, "k-11");
        when(adminMediaMapper.selectOrphanCandidates(any(), any(), eq(2))).thenReturn(List.of(softDeleted));
        doThrow(new IllegalStateException("minio down"))
                .when(objectStorageService)
                .remove(anyString(), anyString());

        service.purgeOrphans();

        verify(adminMediaMapper, never()).markObjectPurged(anyLong(), any());
        verify(adminMediaMapper, never()).deleteById(anyLong());
    }

    @Test
    void purgeOrphans_softDeletesStaleFailedAfterPurgeMark() {
        AdminMediaEntity failed = staleFailed(20L, "k-20");
        when(adminMediaMapper.selectOrphanCandidates(any(), any(), eq(2))).thenReturn(List.of(failed));
        when(adminMediaMapper.markObjectPurged(eq(20L), any())).thenReturn(1);

        service.purgeOrphans();

        verify(adminMediaMapper).markObjectPurged(eq(20L), any(OffsetDateTime.class));
        verify(adminMediaMapper).deleteById(20L);
    }

    @Test
    void purgeOrphans_advancesBeyondBatchSizeAcrossRuns() {
        // 模拟 DB：已打标行不再被 selectOrphanCandidates 返回
        List<AdminMediaEntity> pending = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            pending.add(softDeleted(id, "k-" + id));
        }
        AtomicInteger calls = new AtomicInteger();
        when(adminMediaMapper.selectOrphanCandidates(any(), any(), eq(2))).thenAnswer(inv -> {
            calls.incrementAndGet();
            int limit = inv.getArgument(2);
            return pending.stream().limit(limit).toList();
        });
        when(adminMediaMapper.markObjectPurged(anyLong(), any())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            pending.removeIf(m -> m.getId().equals(id));
            return 1;
        });

        service.purgeOrphans();
        service.purgeOrphans();
        service.purgeOrphans();

        assertThat(pending).isEmpty();
        assertThat(calls.get()).isEqualTo(3);
        ArgumentCaptor<Long> purgedIds = ArgumentCaptor.forClass(Long.class);
        verify(adminMediaMapper, times(5)).markObjectPurged(purgedIds.capture(), any());
        assertThat(purgedIds.getAllValues()).containsExactly(1L, 2L, 3L, 4L, 5L);
        verify(objectStorageService, times(5)).remove(eq("b"), anyString());
    }

    private static AdminMediaEntity softDeleted(long id, String objectKey) {
        AdminMediaEntity entity = base(id, objectKey);
        entity.setStatus(AdminMediaConstants.STATUS_READY);
        entity.setDeleted(true);
        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        return entity;
    }

    private static AdminMediaEntity staleFailed(long id, String objectKey) {
        AdminMediaEntity entity = base(id, objectKey);
        entity.setStatus(AdminMediaConstants.STATUS_FAILED);
        entity.setDeleted(false);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        return entity;
    }

    private static AdminMediaEntity base(long id, String objectKey) {
        AdminMediaEntity entity = new AdminMediaEntity();
        entity.setId(id);
        entity.setAgentId(1L);
        entity.setBucket("b");
        entity.setObjectKey(objectKey);
        entity.setOriginalFilename("a.jpg");
        entity.setContentType("image/jpeg");
        entity.setExtension("jpg");
        entity.setFileSize(4L);
        entity.setVisibility(AdminMediaConstants.VISIBILITY_PRIVATE);
        return entity;
    }
}
