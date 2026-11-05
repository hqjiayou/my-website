package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.admin.entity.AdminMediaReferenceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 映射 {@code media_reference}。 */
@Mapper
public interface AdminMediaReferenceMapper extends BaseMapper<AdminMediaReferenceEntity> {

    /** 删站级联：清掉指向给定文件集合的全部引用行（文件随来源站软删，引用不得残留阻塞/悬挂）。 */
    @Delete(
            """
            <script>
            DELETE FROM media_reference
            WHERE media_id IN
            <foreach collection="mediaIds" item="mediaId" open="(" separator="," close=")">
                #{mediaId}
            </foreach>
            </script>
            """)
    int deleteByMediaIds(@Param("mediaIds") java.util.List<Long> mediaIds);
}
