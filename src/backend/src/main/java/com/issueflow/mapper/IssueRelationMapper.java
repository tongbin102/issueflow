package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.IssueRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 问题关联 Mapper（自定义方法均显式处理逻辑删除 deleted=0）
 */
@Mapper
public interface IssueRelationMapper extends BaseMapper<IssueRelation> {

    /**
     * 查询某问题的全部前置边（issue_id = 当前）
     */
    @Select("SELECT * FROM issue_relation WHERE issue_id = #{issueId} AND deleted = 0")
    List<IssueRelation> selectByIssueId(@Param("issueId") Long issueId);

    /**
     * 查询以 related_id 为前置的所有后继 issue_id（用于反向推导后置 / BFS 防环）
     */
    @Select("SELECT issue_id FROM issue_relation WHERE related_id = #{relatedId} AND rel_type = 1 AND deleted = 0")
    List<Long> selectIssueIdsByRelatedId(@Param("relatedId") Long relatedId);

    /**
     * 逻辑删除某问题的全部前置边（整体替换时使用）
     */
    @Update("UPDATE issue_relation SET deleted = 1 WHERE issue_id = #{issueId} AND deleted = 0")
    void deleteByIssueId(@Param("issueId") Long issueId);

    /**
     * 逻辑删除以某问题为前置的全部后继边（整体替换后置时使用）
     */
    @Update("UPDATE issue_relation SET deleted = 1 WHERE related_id = #{relatedId} AND deleted = 0")
    void deleteByRelatedId(@Param("relatedId") Long relatedId);
}
