package com.mywebsite.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mywebsite.admin.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {}
