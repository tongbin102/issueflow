package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.IssueType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问题类型 Mapper
 */
@Mapper
public interface IssueTypeMapper extends BaseMapper<IssueType> {
}
