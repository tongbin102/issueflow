package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.IssueFieldValue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问题自定义字段值 Mapper（对应表 issue_field_value，竖表）
 */
@Mapper
public interface IssueFieldValueMapper extends BaseMapper<IssueFieldValue> {
}
