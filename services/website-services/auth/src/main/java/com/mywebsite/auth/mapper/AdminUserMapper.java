package com.mywebsite.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.auth.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {}
