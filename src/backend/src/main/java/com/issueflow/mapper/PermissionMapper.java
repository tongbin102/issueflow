package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限目录 Mapper
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
