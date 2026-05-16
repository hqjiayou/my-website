package com.mywebsite.site.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.site.entity.SiteUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteUserMapper extends BaseMapper<SiteUserEntity> {}
