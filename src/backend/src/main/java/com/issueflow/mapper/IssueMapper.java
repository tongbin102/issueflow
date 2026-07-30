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
     * 统计当日已生成的问题编号数量（用于编号生成重试兜底）
     *
     * @param prefix 形如 IS-YYYYMMDD-
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM issue WHERE deleted = 0 AND issue_no LIKE CONCAT(#{prefix}, '%')")
    Long countByIssueNoPrefix(@Param("prefix") String prefix);

    /**
     * 状态分布
     */
    @Select("<script>"
            + "SELECT status AS status, COUNT(*) AS cnt FROM issue WHERE deleted = 0 "
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
     */
    @Select("<script>"
            + "SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM issue WHERE deleted = 0 "
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
     */
    @Select("<script>"
            + "SELECT severity AS severity, COUNT(*) AS cnt FROM issue WHERE deleted = 0 "
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
