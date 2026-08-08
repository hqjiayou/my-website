package com.mywebsite.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.auth.entity.SiteUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteUserMapper extends BaseMapper<SiteUserEntity> {}
