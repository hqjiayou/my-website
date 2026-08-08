package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.admin.entity.AdminSiteEntity;
import org.apache.ibatis.annotations.Mapper;

/** 映射平台 {@code site} 表；与 site-core {@code SiteMapper} 为同表双映射，admin 不依赖 site-core。 */
@Mapper
public interface AdminSiteMapper extends BaseMapper<AdminSiteEntity> {}
