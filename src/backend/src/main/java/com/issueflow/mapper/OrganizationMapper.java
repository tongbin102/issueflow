package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.Organization;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织 Mapper
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {
}
