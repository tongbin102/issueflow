package com.issueflow.service.data.strategy;

import com.issueflow.entity.RestoreRecord;

import java.nio.file.Path;

/**
 * 数据库恢复策略（Phase10 数据管理）。
 *
 * <p>把备份包中解出的 SQL 文件导回当前库。这是整个系统<b>破坏性最强</b>的操作，
 * 实现类必须遵守以下硬约束：</p>
 * <ol>
 *   <li>调用前系统必须已进入只读期（{@code dm:readonly}），否则并发写会污染导入过程；</li>
 *   <li>导入完成后<b>必须</b>触发连接池换血（HikariCP {@code softEvictConnections}），
 *       否则旧连接持有的会话状态 / 预编译缓存会指向已被 DROP 的表定义；</li>
 *   <li>任何失败都不得吞异常 —— 上层需要据此决定是否用恢复前安全备份回退。</li>
 * </ol>
 */
public interface RestoreStrategy {

    /**
     * 当前运行环境是否支持该策略。
     *
     * @return true 可用
     */
    boolean isAvailable();

    /**
     * 策略名称，用于日志。
     *
     * @return 策略标识，如 {@code MYSQL_CLIENT}
     */
    String strategyName();

    /**
     * 执行恢复导入。
     *
     * @param sqlFile 已解包的 SQL 文件绝对路径，必须存在
     * @param record  恢复记录，过程中会回填 {@code affectedTables}
     * @throws Exception 导入失败
     */
    void restore(Path sqlFile, RestoreRecord record) throws Exception;
}
