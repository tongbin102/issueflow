package com.issueflow.service.data.strategy;

import com.issueflow.entity.BackupRecord;

import java.io.OutputStream;

/**
 * 数据库导出策略（Phase10 数据管理）。
 *
 * <p>把整库导出为可直接被 {@code mysql} 客户端执行的 SQL 文本流。
 * 实现类需保证输出的 SQL 是自包含的（含建表语句 + 数据），
 * 且字符集固定 utf8mb4，避免恢复到不同环境时中文乱码。</p>
 *
 * <p>目前有两个实现：</p>
 * <ul>
 *   <li>{@link MysqldumpStrategy} —— 首选，调用 mysqldump，一致性与性能最好；</li>
 *   <li>{@link JdbcExportStrategy} —— 回落，mysqldump 不在镜像里时用纯 JDBC 导出。</li>
 * </ul>
 *
 * <p><b>约定</b>：实现类<b>不负责</b>关闭传入的 {@code OutputStream}
 * —— 该流是 zip 条目流，由 {@code BackupArchiveService} 统一收尾。</p>
 */
public interface DumpStrategy {

    /**
     * 当前运行环境是否支持该策略。
     *
     * @return true 可用
     */
    boolean isAvailable();

    /**
     * 策略名称，用于日志与 manifest 记录。
     *
     * @return 策略标识，如 {@code MYSQLDUMP}
     */
    String strategyName();

    /**
     * 执行导出。
     *
     * <p>实现类应在导出过程中通过 {@code TaskProgressStore} 更新
     * {@code record.getTaskId()} 对应的进度（DUMP_DB 阶段区间内）。</p>
     *
     * @param record 备份记录，导出过程中会回填 {@code tableCount} / {@code dbName}
     * @param out    目标输出流（zip 条目流），实现类不得关闭
     * @return 写出的字节数
     * @throws Exception 导出失败，调用方负责脱敏后落库
     */
    long dump(BackupRecord record, OutputStream out) throws Exception;
}
