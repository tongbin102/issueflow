package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.dto.resp.IssueHistoryVO;
import com.issueflow.entity.IssueHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作历史 Mapper（含联表查询操作人姓名 + 分页）
 */
@Mapper
public interface IssueHistoryMapper extends BaseMapper<IssueHistory> {

    /**
     * 查询某问题的全部历史（时间倒序，含操作人姓名）
     */
    @Select("SELECT h.id AS id, h.issue_id AS issueId, h.action AS action, h.from_status AS fromStatus, "
            + "h.to_status AS toStatus, h.operator_id AS operatorId, h.remark AS remark, h.created_at AS createdAt, "
            + "COALESCE(u.real_name, u.username) AS operatorName "
            + "FROM issue_history h LEFT JOIN user u ON u.id = h.operator_id "
            + "WHERE h.deleted = 0 AND h.issue_id = #{issueId} ORDER BY h.created_at DESC")
    List<IssueHistoryVO> selectByIssue(@Param("issueId") Long issueId);

    /**
     * 分页查询历史（按问题 + 操作人 + 时间范围过滤，含操作人姓名）
     */
    @Select("<script>"
            + "SELECT h.id AS id, h.issue_id AS issueId, h.action AS action, h.from_status AS fromStatus, "
            + "h.to_status AS toStatus, h.operator_id AS operatorId, h.remark AS remark, h.created_at AS createdAt, "
            + "COALESCE(u.real_name, u.username) AS operatorName "
            + "FROM issue_history h LEFT JOIN user u ON u.id = h.operator_id "
            + "WHERE h.deleted = 0 "
            + "<if test='issueId != null'> AND h.issue_id = #{issueId} </if>"
            + "<if test='operatorId != null'> AND h.operator_id = #{operatorId} </if>"
            + "<if test='start != null'> AND h.created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND h.created_at &lt;= #{end} </if>"
            + "ORDER BY h.created_at DESC"
            + "</script>")
    List<IssueHistoryVO> selectPage(Page<IssueHistoryVO> page,
                                    @Param("issueId") Long issueId,
                                    @Param("operatorId") Long operatorId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);
}
