package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
