package com.issueflow.service.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.entity.BackupRecord;
import com.issueflow.enums.BackupSourceEnum;
import com.issueflow.enums.TaskStatusEnum;
import com.issueflow.mapper.BackupRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 备份保留策略执行器（Phase10 数据管理）。
 *
 * <p>每次备份成功后调用，按 {@code sys_config} 的保留策略清理旧备份：</p>
 * <ol>
 *   <li><b>按天数</b>：{@code defaultDays} 之前创建的备份一律清理；</li>
 *   <li><b>按份数</b>：剩余备份若仍超过 {@code maxCopies}，从最旧的开始删到达标。</li>
 * </ol>
 *
 * <p><b>关于 0 值语义</b>：与 {@code sys_config} 的种子注释保持一致 ——
 * {@code data.management.backup.retain.count} / {@code retain.days} 取 <b>0（或负数）表示「不限制」</b>，
 * 即<b>跳过</b>对应维度的淘汰，而不是「全部清理」。
 * 正常配置入口由 {@code DataManagementConfigDTO} 的 {@code @Min(1)} 兜住，
 * 这里的判空只针对历史脏数据 / 人工直改库表的场景做防御，
 * 避免把「不限制」误解成「一份不留」而清空全部历史备份。</p>
 *
 * <p><b>三条不删红线</b>（安全兜底，优先级高于任何保留策略）：</p>
 * <ul>
 *   <li>{@code source=PRE_RESTORE} 的恢复前安全备份 —— 它是恢复翻车时的唯一退路；</li>
 *   <li>状态非终态（PENDING / RUNNING）的记录 —— 正在写的文件不能删；</li>
 *   <li>清理后至少保留 1 份成功备份 —— 绝不允许把系统清成「零备份」状态。</li>
 * </ul>
 *
 * <p><b>关于 sizeLimitMB</b>：该配置项对应 {@code data.management.upload.max.size.mb}，
 * 语义是<b>单个上传包的体积上限</b>，不是备份目录的总容量配额
 * （现有 {@code sys_config} 并未定义总容量键）。
 * 把上传上限当成总容量来清理会导致「传了个 512MB 的包就把历史备份全删光」，
 * 因此本执行器<b>刻意不使用</b>该项，只按份数与天数清理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionPolicyExecutor {

    private final BackupRecordMapper backupRecordMapper;
    private final DataManagementConfigService configService;

    /**
     * 执行一次保留策略清理。
     *
     * <p>清理失败不抛异常 —— 备份本身已经成功，不能因为清理旧文件出错
     * 就把整个备份任务标记为失败，那会误导管理员以为没备份成功。</p>
     *
     * @return 实际清理掉的备份份数
     */
    public int apply() {
        try {
            return doApply();
        } catch (Exception e) {
            log.warn("[RetentionPolicy] 清理过程异常，已忽略: {}", e.getClass().getSimpleName(), e);
            return 0;
        }
    }

    /**
     * 清理主流程。
     *
     * @return 清理份数
     */
    private int doApply() {
        DataManagementConfigDTO config = configService.getConfig();
        int maxCopies = config.getMaxCopies() == null ? 20 : config.getMaxCopies();
        int retainDays = config.getDefaultDays() == null ? 30 : config.getDefaultDays();

        // 只考虑「可清理」的候选：终态成功 + 非恢复前安全备份，按时间从旧到新
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<BackupRecord>()
                .eq(BackupRecord::getStatus, TaskStatusEnum.SUCCESS.getCode())
                .ne(BackupRecord::getSource, BackupSourceEnum.PRE_RESTORE.getCode())
                .orderByAsc(BackupRecord::getCreatedAt);
        List<BackupRecord> candidates = backupRecordMapper.selectList(wrapper);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        List<BackupRecord> toDelete = new ArrayList<>();

        // 第一轮：按天数淘汰。retainDays <= 0 表示「不限制保留天数」，直接跳过本轮。
        if (retainDays > 0) {
            LocalDateTime deadline = LocalDateTime.now().minusDays(retainDays);
            for (BackupRecord record : candidates) {
                LocalDateTime createdAt = record.getCreatedAt();
                if (createdAt != null && createdAt.isBefore(deadline)) {
                    toDelete.add(record);
                }
            }
        } else {
            log.debug("[RetentionPolicy] retainDays={} 视为不限制，跳过天数淘汰", retainDays);
        }

        // 第二轮：按份数淘汰（在天数淘汰之后仍然超量的部分，从最旧开始）。
        // maxCopies <= 0 表示「不限制保留份数」，直接跳过本轮。
        int remaining = candidates.size() - toDelete.size();
        if (maxCopies <= 0) {
            log.debug("[RetentionPolicy] maxCopies={} 视为不限制，跳过份数淘汰", maxCopies);
        } else if (remaining > maxCopies) {
            int overflow = remaining - maxCopies;
            for (BackupRecord record : candidates) {
                if (overflow <= 0) {
                    break;
                }
                if (toDelete.contains(record)) {
                    continue;
                }
                toDelete.add(record);
                overflow--;
            }
        }

        // 兜底红线：至少保留一份成功备份
        if (toDelete.size() >= candidates.size() && !candidates.isEmpty()) {
            BackupRecord newest = candidates.get(candidates.size() - 1);
            toDelete.remove(newest);
            log.info("[RetentionPolicy] 触发「至少保留一份」兜底，保留最新备份 id={}", newest.getId());
        }

        int deleted = 0;
        for (BackupRecord record : toDelete) {
            if (removeOne(record)) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("[RetentionPolicy] 清理完成 deleted={} maxCopies={} retainDays={}",
                    deleted, maxCopies, retainDays);
        }
        return deleted;
    }

    /**
     * 删除单条备份（先删文件再逻辑删记录）。
     *
     * <p>顺序不能反：先删记录再删文件，若删文件失败就会留下无主孤儿文件，
     * 永远没人再去清理它。先删文件的话，即便记录删除失败，
     * 下一轮清理仍会重新命中这条记录并重试。</p>
     *
     * @param record 备份记录
     * @return true 删除成功
     */
    public boolean removeOne(BackupRecord record) {
        if (record == null || record.getId() == null) {
            return false;
        }
        try {
            deleteFileQuietly(record);
            backupRecordMapper.deleteById(record.getId());
            return true;
        } catch (Exception e) {
            log.warn("[RetentionPolicy] 删除备份失败 id={}: {}", record.getId(), e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 删除备份文件（不存在视为已删成功）。
     *
     * @param record 备份记录
     */
    public void deleteFileQuietly(BackupRecord record) {
        String relative = record.getFilePath();
        if (relative == null || relative.trim().isEmpty()) {
            return;
        }
        try {
            Path root = configService.getBackupRoot();
            Path target = root.resolve(relative).normalize();
            // 防越界：拼出来的路径必须仍在备份根目录内，杜绝 filePath 被污染成 ../../ 后误删系统文件
            if (!target.startsWith(root)) {
                log.warn("[RetentionPolicy] 备份路径越界，拒绝删除 id={}", record.getId());
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[RetentionPolicy] 备份文件删除失败 id={}: {}", record.getId(), e.getClass().getSimpleName());
        }
    }
}
