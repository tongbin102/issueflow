package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FieldSection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段区域 Mapper（对应表 field_section）
 */
@Mapper
public interface FieldSectionMapper extends BaseMapper<FieldSection> {
}
