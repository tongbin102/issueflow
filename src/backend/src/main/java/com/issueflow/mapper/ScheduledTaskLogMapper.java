package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.ScheduledTaskLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定时任务执行日志 Mapper
 */
@Mapper
public interface ScheduledTaskLogMapper extends BaseMapper<ScheduledTaskLog> {

    /**
     * 滚动裁剪：仅保留某任务最近 {@code keep} 条执行日志（ARCH §1.3.3，默认 200）。
     *
     * <p>子查询套一层派生表是 MySQL 的强制要求 —— 同一条语句里不能直接
     * {@code DELETE ... WHERE id NOT IN (SELECT ... FROM 同表)}，否则报 1093。</p>
     *
     * @param taskId 任务 id
     * @param keep   保留条数
     * @return 删除行数
     */
    @Delete("DELETE FROM scheduled_task_log WHERE task_id = #{taskId} AND id NOT IN ("
            + "SELECT id FROM (SELECT id FROM scheduled_task_log WHERE task_id = #{taskId} "
            + "ORDER BY id DESC LIMIT #{keep}) AS keep_ids)")
    int trimByTask(@Param("taskId") Long taskId, @Param("keep") int keep);
}
