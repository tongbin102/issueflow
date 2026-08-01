package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.Issue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问题 Mapper（含看板聚合统计 SQL）
 */
@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 取当日已使用的最大问题编号序号。
     * <p>关键：<b>不过滤 deleted</b>。逻辑删除行仍占用唯一索引 uk_issue_no，
     * 若只统计未删除行会使序号回退、生成已被软删行占用的编号而触发 DuplicateKeyException。
     * 故软删行必须参与计算，以保证序号单调自增。</p>
     *
     * @param prefix 形如 IS-YYYYMMDD-
     * @return 最大序号（无匹配行时返回 0）
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(issue_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
            + "FROM issue WHERE issue_no LIKE CONCAT(#{prefix}, '%')")
    Long maxSeqByIssueNoPrefix(@Param("prefix") String prefix);

    /**
     * 状态分布
     *
     * <p>BUG-01：聚合列别名必须为 {@code count}（原为 {@code cnt}），与前端
     * {@code d.count} 的读取口径一致，否则看板全部统计恒为 0。
     * {@code count} 在 MySQL 中非保留字，但为规避 sql_mode 差异一律用反引号包裹。</p>
     */
    @Select("<script>"
            + "SELECT status AS status, COUNT(*) AS `count` FROM issue WHERE deleted = 0 "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "GROUP BY status"
            + "</script>")
    List<Map<String, Object>> statusDistribution(@Param("reporterId") Long reporterId,
                                                 @Param("version") String version,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * 每日创建趋势
     *
     * <p>BUG-01：聚合列别名 {@code cnt} → {@code count}，与前端 TrendChart 的 {@code d.count} 对齐。</p>
     */
    @Select("<script>"
            + "SELECT DATE(created_at) AS day, COUNT(*) AS `count` FROM issue WHERE deleted = 0 "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "GROUP BY DATE(created_at) ORDER BY day"
            + "</script>")
    List<Map<String, Object>> trendByDay(@Param("reporterId") Long reporterId,
                                         @Param("version") String version,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * 平均解决周期（小时），仅已关闭且有 closed_at
     */
    @Select("<script>"
            + "SELECT AVG(TIMESTAMPDIFF(HOUR, created_at, closed_at)) FROM issue WHERE deleted = 0 AND status = 4 AND closed_at IS NOT NULL "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "</script>")
    BigDecimal avgResolveCycle(@Param("reporterId") Long reporterId,
                               @Param("version") String version,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /**
     * 符合条件的问题总数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM issue WHERE deleted = 0 "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "</script>")
    Long countTotal(@Param("reporterId") Long reporterId,
                    @Param("version") String version,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    /**
     * 符合条件且已关闭的问题数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM issue WHERE deleted = 0 AND status = 4 "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "</script>")
    Long countClosed(@Param("reporterId") Long reporterId,
                     @Param("version") String version,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    /**
     * 严重等级占比
     *
     * <p>BUG-01：聚合列别名 {@code cnt} → {@code count}，与前端 DistributionChart 的 {@code d.count} 对齐。</p>
     */
    @Select("<script>"
            + "SELECT severity AS severity, COUNT(*) AS `count` FROM issue WHERE deleted = 0 "
            + "<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>"
            + "<if test='version != null and version != \"\"'> AND env_app_version = #{version} </if>"
            + "<if test='start != null'> AND created_at &gt;= #{start} </if>"
            + "<if test='end != null'> AND created_at &lt;= #{end} </if>"
            + "GROUP BY severity"
            + "</script>")
    List<Map<String, Object>> severityRatio(@Param("reporterId") Long reporterId,
                                            @Param("version") String version,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
