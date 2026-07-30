package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.IssueAttachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件 Mapper
 */
@Mapper
public interface IssueAttachmentMapper extends BaseMapper<IssueAttachment> {
}
