package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.FieldConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段配置 Mapper（对应表 field_config）
 */
@Mapper
public interface FieldConfigMapper extends BaseMapper<FieldConfig> {
}
