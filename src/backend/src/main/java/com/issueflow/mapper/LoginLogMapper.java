package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.LoginLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 登录日志 Mapper
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 物理删除早于指定时间的登录日志（内置清理任务调用）。
     *
     * <p>日志类数据无还原价值，直接物理删除避免软删行持续占用空间与索引。</p>
     *
     * @param before 截止时间（比较 login_at）
     * @return 删除行数
     */
    @Delete("DELETE FROM login_log WHERE login_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
