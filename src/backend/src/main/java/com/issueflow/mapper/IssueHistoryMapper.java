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
import java.util.Map;

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

    /**
     * 个人中心「活动记录」：查询本人的问题操作历史（含问题编号与标题）。
     *
     * <p>{@code operatorId} 一律由 {@code SecurityUtils.getCurrentUserId()} 传入，
     * <b>永不来自请求入参</b>，从签名层面杜绝越权查看他人动态（ARCH §3.8 越权设计）。</p>
     *
     * @param operatorId 操作人 id（当前登录用户）
     * @param start      起始时间，可空
     * @param end        结束时间，可空
     * @param limit      最多返回条数（两路归并分页时取 page*size）
     * @return 每行含 action / fromStatus / toStatus / remark / createdAt / issueId / issueNo / issueTitle
     */
    @Select("<script>"
            + "SELECT h.action AS action, h.from_status AS fromStatus, h.to_status AS toStatus, "
            + "h.remark AS remark, h.created_at AS createdAt, h.issue_id AS issueId, "
            + "i.issue_no AS issueNo, i.title AS issueTitle "
            + "FROM issue_history h LEFT JOIN issue i ON i.id = h.issue_id "
            + "WHERE h.deleted = 0 AND h.operator_id = #{operatorId} "
            + "<if test='start != null'> AND h.created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND h.created_at &lt;= #{end} </if>"
            + "ORDER BY h.created_at DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectMyActivities(@Param("operatorId") Long operatorId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("limit") int limit);
}
