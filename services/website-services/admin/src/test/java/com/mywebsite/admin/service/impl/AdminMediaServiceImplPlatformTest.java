package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminMediaConstants;
import com.mywebsite.admin.constant.AdminMediaMessageKeys;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaDetailDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListQuery;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.entity.AdminMediaReferenceEntity;
import com.mywebsite.admin.mapper.AdminContentMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper.AdminMediaListRow;
import com.mywebsite.admin.mapper.AdminMediaReferenceMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.properties.AdminMediaProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListItemDto;
import com.mywebsite.infra.properties.StorageProperties;
import com.mywebsite.infra.service.ObjectStorageService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 媒体平台化（#212）服务层规则：可见性、引用幂等、删除拦截顺序、删站级联、列表视图分发。
 * Spring/JWT 层的端到端覆盖在 {@code AdminMediaPlatformMvcTest}。
 */
@ExtendWith(MockitoExtension.class)
class AdminMediaServiceImplPlatformTest {

    private static final long AGENT_A = 1L;
    private static final long AGENT_B = 2L;
    private static final long MEDIA_ID = 10L;

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

    private AdminMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setBucket("test");
        service = new AdminMediaServiceImpl(
                adminMediaMapper,
                adminMediaReferenceMapper,
                adminAgentMapper,
                adminContentMapper,
                objectStorageService,
                storageProperties,
                new AdminMediaProperties());
    }

    private void siteExists(long agentId) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(agentId);
        when(adminAgentMapper.selectById(agentId)).thenReturn(agent);
    }

    private AdminMediaEntity media(long ownerId, String scope) {
        AdminMediaEntity media = new AdminMediaEntity();
        media.setId(MEDIA_ID);
        media.setAgentId(ownerId);
        media.setScope(scope);
        media.setStatus(AdminMediaConstants.STATUS_READY);
        media.setBucket("test");
        media.setObjectKey("sites/" + ownerId + "/2026/01/x.jpg");
        return media;
    }

    // ---------- 可见规则 ----------

    @Test
    void getMedia_platformFileVisibleToOtherAgent() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(0L);

        AdminMediaDetailDto dto = service.getMedia(AGENT_B, MEDIA_ID);

        assertThat(dto.id()).isEqualTo(MEDIA_ID);
        assertThat(dto.scope()).isEqualTo("PLATFORM");
    }

    @Test
    void getMedia_agentFileInvisibleToOtherAgent() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "AGENT"));
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.getMedia(AGENT_B, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo(AdminMediaMessageKeys.MEDIA_NOT_FOUND);
                });
    }

    @Test
    void getMedia_referencedAgentFileVisibleToReferencingAgent() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "AGENT"));
        // requireVisibleMedia 的引用计数命中；toDetail 的总引用计数同 mock
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(1L);

        AdminMediaDetailDto dto = service.getMedia(AGENT_B, MEDIA_ID);

        assertThat(dto.id()).isEqualTo(MEDIA_ID);
        assertThat(dto.referenceCount()).isEqualTo(1L);
    }

    // ---------- reference ----------

    @Test
    void reference_platformFileInsertsReferenceRow() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(0L);

        service.referenceMedia(AGENT_B, MEDIA_ID);

        ArgumentCaptor<AdminMediaReferenceEntity> captor =
                ArgumentCaptor.forClass(AdminMediaReferenceEntity.class);
        verify(adminMediaReferenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getMediaId()).isEqualTo(MEDIA_ID);
        assertThat(captor.getValue().getAgentId()).isEqualTo(AGENT_B);
    }

    @Test
    void reference_ownFileIsIdempotentNoop() {
        siteExists(AGENT_A);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "AGENT"));

        service.referenceMedia(AGENT_A, MEDIA_ID);

        verify(adminMediaReferenceMapper, never()).insert(any(AdminMediaReferenceEntity.class));
    }

    @Test
    void reference_alreadyReferencedIsIdempotent() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(1L);

        service.referenceMedia(AGENT_B, MEDIA_ID);

        verify(adminMediaReferenceMapper, never()).insert(any(AdminMediaReferenceEntity.class));
    }

    @Test
    void reference_agentScopeFileOfOtherReturns404() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "AGENT"));

        assertThatThrownBy(() -> service.referenceMedia(AGENT_B, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(adminMediaReferenceMapper, never()).insert(any(AdminMediaReferenceEntity.class));
    }

    // ---------- unreference ----------

    @Test
    void unreference_existingReferenceDeletesRow() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminMediaReferenceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        service.unreferenceMedia(AGENT_B, MEDIA_ID);

        verify(adminMediaReferenceMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void unreference_ownFileReturns400() {
        siteExists(AGENT_A);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));

        assertThatThrownBy(() -> service.unreferenceMedia(AGENT_A, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo(AdminMediaMessageKeys.MEDIA_REFERENCE_OWN_FILE);
                });
    }

    @Test
    void unreference_withoutReferenceReturns404() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminMediaReferenceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.unreferenceMedia(AGENT_B, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    // ---------- setScope ----------

    @Test
    void updateScope_byOwnerUpdatesAndReturnsDetail() {
        siteExists(AGENT_A);
        AdminMediaEntity entity = media(AGENT_A, "AGENT");
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(entity);
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(0L);

        AdminMediaDetailDto dto = service.updateScope(AGENT_A, MEDIA_ID, "PLATFORM");

        assertThat(dto.scope()).isEqualTo("PLATFORM");
        verify(adminMediaMapper).updateById(entity);
    }

    @Test
    void updateScope_invalidValueReturns400() {
        siteExists(AGENT_A);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "AGENT"));

        assertThatThrownBy(() -> service.updateScope(AGENT_A, MEDIA_ID, "PUBLIC"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo(AdminMediaMessageKeys.MEDIA_SCOPE_INVALID);
                });
        verify(adminMediaMapper, never()).updateById(any(AdminMediaEntity.class));
    }

    @Test
    void updateScope_byNonOwnerReturns404() {
        siteExists(AGENT_B);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));

        assertThatThrownBy(() -> service.updateScope(AGENT_B, MEDIA_ID, "AGENT"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    // ---------- delete 拦截顺序 ----------

    @Test
    void delete_blockedByAgentReferencesAfterContentCheck() {
        siteExists(AGENT_A);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminContentMapper.selectCount(any())).thenReturn(0L);
        when(adminMediaReferenceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteMedia(AGENT_A, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
                    assertThat(ex.getMessageKey())
                            .isEqualTo(AdminMediaMessageKeys.MEDIA_REFERENCED_BY_AGENTS);
                });
        verify(adminMediaMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void delete_contentReferenceStillCheckedFirst() {
        siteExists(AGENT_A);
        when(adminMediaMapper.selectById(MEDIA_ID)).thenReturn(media(AGENT_A, "PLATFORM"));
        when(adminContentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteMedia(AGENT_A, MEDIA_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
                    assertThat(ex.getMessageKey())
                            .isEqualTo(AdminMediaMessageKeys.MEDIA_REFERENCED_BY_CONTENT);
                });
        verify(adminMediaReferenceMapper, never()).selectCount(any());
    }

    // ---------- 删站级联 ----------

    @Test
    void softDeleteByAgentId_clearsReferencesBothDirections() {
        AdminMediaEntity m1 = media(AGENT_A, "PLATFORM");
        AdminMediaEntity m2 = media(AGENT_A, "AGENT");
        m2.setId(11L);
        when(adminMediaMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1, m2));

        service.softDeleteByAgentId(AGENT_A);

        verify(adminMediaReferenceMapper).deleteByMediaIds(List.of(MEDIA_ID, 11L));
        verify(adminMediaReferenceMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<java.io.Serializable> deletedIds = ArgumentCaptor.forClass(java.io.Serializable.class);
        verify(adminMediaMapper, org.mockito.Mockito.times(2)).deleteById(deletedIds.capture());
        assertThat(deletedIds.getAllValues()).containsExactly(MEDIA_ID, 11L);
    }

    // ---------- 列表视图分发 ----------

    @Test
    void listMedia_platformViewUsesPlatformQueryAndMapsReferenced() {
        siteExists(AGENT_B);
        AdminMediaListRow row = new AdminMediaListRow(
                MEDIA_ID, AGENT_A, "a.jpg", "image/jpeg", "jpg", 4L, "READY", "PLATFORM",
                OffsetDateTime.now(), OffsetDateTime.now(), true);
        Page<AdminMediaListRow> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(adminMediaMapper.selectPlatformPage(any(), eq(AGENT_B), eq(null))).thenReturn(page);

        PageResult<AdminMediaListItemDto> result =
                service.listMedia(AGENT_B, new AdminMediaListQuery(1, 20, null, "platform"));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).referenced()).isTrue();
        assertThat(result.getRecords().get(0).scope()).isEqualTo("PLATFORM");
        verify(adminMediaMapper, never()).selectLibraryPage(any(), anyLong(), any());
    }

    @Test
    void listMedia_defaultViewUsesLibraryQuery() {
        siteExists(AGENT_B);
        Page<AdminMediaListRow> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(adminMediaMapper.selectLibraryPage(any(), eq(AGENT_B), eq(null))).thenReturn(page);

        service.listMedia(AGENT_B, new AdminMediaListQuery(null, null, null));

        verify(adminMediaMapper).selectLibraryPage(any(), eq(AGENT_B), eq(null));
    }

    @Test
    void listMedia_invalidViewReturns400() {
        siteExists(AGENT_B);

        assertThatThrownBy(() -> service.listMedia(AGENT_B, new AdminMediaListQuery(1, 20, null, "bogus")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo(AdminMediaMessageKeys.MEDIA_VIEW_INVALID);
                });
    }
}
