package com.mywebsite.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentListDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicSiteDto;
import com.mywebsite.site.entity.SiteContentEntity;
import com.mywebsite.site.entity.SiteEntity;
import com.mywebsite.site.mapper.SiteContentMapper;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.service.impl.SitePublicServiceImpl;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SitePublicServiceTest {

    @Mock
    private SiteMapper siteMapper;

    @Mock
    private SiteContentMapper siteContentMapper;

    private SitePublicServiceImpl service;

    @BeforeAll
    static void initLambdaCache() {
        initTable(AbstractAuditableEntity.class);
        initTable(SiteEntity.class);
        initTable(SiteContentEntity.class);
    }

    private static void initTable(Class<?> entity) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(entity.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new SitePublicServiceImpl(siteMapper, siteContentMapper);
    }

    @Test
    void listContentsReturnsOnlyPublishedRowsForThatSite() {
        when(siteMapper.selectOne(any())).thenReturn(site(10L, "demo", "ACTIVE"));
        when(siteContentMapper.selectList(any()))
                .thenReturn(List.of(
                        content(1L, 10L, SiteContentEntity.STATUS_PUBLISHED, "published", 2),
                        content(2L, 10L, "DRAFT", "draft", 1),
                        content(3L, 99L, SiteContentEntity.STATUS_PUBLISHED, "other-site", 0)));

        SitePublicContentListDto list = service.listContents(" demo ");

        assertThat(list.items()).extracting(item -> item.title()).containsExactly("published");
        assertThat(list.items().getFirst().id()).isEqualTo(1L);
        assertThat(list.items().getFirst().coverMediaId()).isEqualTo(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<SiteContentEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(siteContentMapper).selectList(captor.capture());
        assertPublishedListQuery(captor.getValue(), 10L);
    }

    @Test
    void contentOnAnotherSiteIsNotFound() {
        when(siteMapper.selectOne(any())).thenReturn(site(10L, "demo", "ACTIVE"));
        when(siteContentMapper.selectOne(any()))
                .thenReturn(content(5L, 99L, SiteContentEntity.STATUS_PUBLISHED, "other", 0));

        assertNotFound(() -> service.getContent("demo", 5L));
    }

    @Test
    void unpublishedContentIsNotFound() {
        when(siteMapper.selectOne(any())).thenReturn(site(10L, "demo", "ACTIVE"));
        when(siteContentMapper.selectOne(any())).thenReturn(content(5L, 10L, "HIDDEN", "hidden", 0));

        assertNotFound(() -> service.getContent("demo", 5L));
    }

    @Test
    void missingSiteIsNotFound() {
        when(siteMapper.selectOne(any())).thenReturn(null);

        assertNotFound(() -> service.getSite("missing"));
        assertNotFound(() -> service.listContents("missing"));
        assertNotFound(() -> service.getContent("missing", 1L));
        verifyNoInteractions(siteContentMapper);
    }

    @Test
    void inactiveSiteIsNotFound() {
        when(siteMapper.selectOne(any())).thenReturn(site(10L, "demo", "DISABLED"));

        assertNotFound(() -> service.getSite("demo"));
        verifyNoInteractions(siteContentMapper);
    }

    @Test
    void publishedDetailIncludesBody() {
        when(siteMapper.selectOne(any())).thenReturn(site(10L, "demo", " active "));
        when(siteContentMapper.selectOne(any()))
                .thenReturn(content(5L, 10L, SiteContentEntity.STATUS_PUBLISHED, "title", 0));

        var detail = service.getContent("demo", 5L);

        assertThat(detail.body()).isEqualTo("body-5");
        assertThat(detail.title()).isEqualTo("title");
        SitePublicSiteDto site = service.getSite("demo");
        assertThat(site.siteCode()).isEqualTo("demo");
        assertThat(site.siteName()).isEqualTo("Demo");
        assertThat(site.siteType()).isEqualTo("CAR");
        assertThat(site.description()).isEqualTo("desc");
    }

    private static void assertPublishedListQuery(LambdaQueryWrapper<SiteContentEntity> wrapper, long siteId) {
        // LambdaQueryWrapper 的参数对在生成 SQL 段时才物化，必须先触发 getSqlSegment 再断言参数。
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(siteId, SiteContentEntity.STATUS_PUBLISHED);
        assertThat(sql).contains("sort_order asc");
        assertThat(sql).contains("id desc");
    }

    private static void assertNotFound(org.junit.jupiter.api.function.Executable executable) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    private static SiteEntity site(long id, String code, String status) {
        SiteEntity site = new SiteEntity();
        site.setId(id);
        site.setSiteCode(code);
        site.setSiteName("Demo");
        site.setSiteType("CAR");
        site.setStatus(status);
        site.setDescription("desc");
        return site;
    }

    private static SiteContentEntity content(long id, long siteId, String status, String title, int sortOrder) {
        SiteContentEntity content = new SiteContentEntity();
        content.setId(id);
        content.setSiteId(siteId);
        content.setStatus(status);
        content.setTitle(title);
        content.setType("NEWS");
        content.setBody("body-" + id);
        content.setSortOrder(sortOrder);
        content.setCoverMediaId(7L);
        content.setPublishedAt(OffsetDateTime.parse("2026-01-02T03:04:05Z"));
        return content;
    }
}
