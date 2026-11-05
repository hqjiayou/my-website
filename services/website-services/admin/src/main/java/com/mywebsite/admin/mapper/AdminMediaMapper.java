package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.entity.AdminMediaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 映射 {@code media_file}。 */
@Mapper
public interface AdminMediaMapper extends BaseMapper<AdminMediaEntity> {

    /**
     * 列表投影行：媒体列加 {@code referenced}（该行是否经 {@code media_reference} 引用可见，
     * 而非来源站自有）。
     */
    record AdminMediaListRow(
            Long id,
            Long agentId,
            String originalFilename,
            String contentType,
            String extension,
            Long fileSize,
            String status,
            String scope,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            boolean referenced) {}

    /**
     * 本站库视图分页：来源站文件＋本站已引用文件，一条 SQL 分页（total 由分页拦截器同查）。
     */
    @Select(
            """
            <script>
            SELECT m.id AS id, m.site_id AS agent_id, m.original_filename AS original_filename,
                   m.content_type AS content_type, m.extension AS extension,
                   m.file_size AS file_size, m.status AS status, m.scope AS scope,
                   m.created_at AS created_at, m.updated_at AS updated_at,
                   (m.site_id &lt;&gt; #{agentId}) AS referenced
            FROM media_file m
            WHERE m.deleted = FALSE
              AND (m.site_id = #{agentId}
                   OR EXISTS (SELECT 1 FROM media_reference r
                              WHERE r.media_id = m.id AND r.agent_id = #{agentId}))
              <if test="contentTypePrefix != null and contentTypePrefix != ''">
              AND m.content_type LIKE CONCAT(#{contentTypePrefix}, '%')
              </if>
            ORDER BY m.id DESC
            </script>
            """)
    Page<AdminMediaListRow> selectLibraryPage(
            Page<AdminMediaListRow> page,
            @Param("agentId") long agentId,
            @Param("contentTypePrefix") String contentTypePrefix);

    /** 平台库视图分页：全部 {@code scope='PLATFORM'} 文件；{@code referenced} 表示本站已引用。 */
    @Select(
            """
            <script>
            SELECT m.id AS id, m.site_id AS agent_id, m.original_filename AS original_filename,
                   m.content_type AS content_type, m.extension AS extension,
                   m.file_size AS file_size, m.status AS status, m.scope AS scope,
                   m.created_at AS created_at, m.updated_at AS updated_at,
                   EXISTS (SELECT 1 FROM media_reference r
                           WHERE r.media_id = m.id AND r.agent_id = #{agentId}) AS referenced
            FROM media_file m
            WHERE m.deleted = FALSE
              AND m.scope = 'PLATFORM'
              <if test="contentTypePrefix != null and contentTypePrefix != ''">
              AND m.content_type LIKE CONCAT(#{contentTypePrefix}, '%')
              </if>
            ORDER BY m.id DESC
            </script>
            """)
    Page<AdminMediaListRow> selectPlatformPage(
            Page<AdminMediaListRow> page,
            @Param("agentId") long agentId,
            @Param("contentTypePrefix") String contentTypePrefix);


    /**
     * Orphan 扫尾：绕过 {@code @TableLogic}，包含已软删行与超时 FAILED/UPLOADING；
     * 排除对象已清理（{@code object_purged_at}）的行，保证 batch 能向前推进。
     */
    @Select(
            """
            SELECT id, site_id, bucket, object_key, original_filename, content_type, extension,
                   file_size, sha256, visibility, status, object_purged_at,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM media_file
            WHERE object_purged_at IS NULL
              AND ((deleted = TRUE
                    AND COALESCE(deleted_at, updated_at, created_at) < #{deletedBefore})
                OR (deleted = FALSE AND status IN ('FAILED', 'UPLOADING')
                    AND COALESCE(updated_at, created_at) < #{staleBefore}))
            ORDER BY id
            LIMIT #{limit}
            """)
    List<AdminMediaEntity> selectOrphanCandidates(
            @Param("deletedBefore") OffsetDateTime deletedBefore,
            @Param("staleBefore") OffsetDateTime staleBefore,
            @Param("limit") int limit);

    /**
     * 对象 remove 成功后打标；绕过 {@code @TableLogic}，软删行也可更新。
     * 不物理删：{@code site_content.cover_media_id} 为 {@code ON DELETE RESTRICT}。
     */
    @Update(
            """
            UPDATE media_file
            SET object_purged_at = #{purgedAt}, updated_at = #{purgedAt}
            WHERE id = #{id} AND object_purged_at IS NULL
            """)
    int markObjectPurged(@Param("id") long id, @Param("purgedAt") OffsetDateTime purgedAt);
}
