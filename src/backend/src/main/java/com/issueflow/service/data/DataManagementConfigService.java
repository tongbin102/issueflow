package com.issueflow.service.data;

import com.issueflow.common.Constants;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据管理热配置读写（Phase10）。
 *
 * <p>只负责 {@code sys_config} 表中 {@code data.management.*} 这几个
 * <b>管理员可在页面改、改完立即生效</b>的键；需重启才生效的运维级参数
 * 在 {@link com.issueflow.config.DataManagementProperties} 里，两者互不越界。</p>
 *
 * <p><b>读取一律走「取值 → 解析失败回落默认」</b>：sys_config 是纯文本表，
 * 手工改库写进一个 {@code "abc"} 是完全可能的，
 * 此时宁可用默认值继续跑，也不能让备份任务因为解析异常整个崩掉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataManagementConfigService {

    private final SysConfigService sysConfigService;
    private final DataManagementProperties properties;

    /**
     * 解析备份根目录。
     *
     * <p>优先取 {@code sys_config} 的 {@code data.management.backup.dir}（管理员可改），
     * 缺失时回落到 {@code application.yml} 的 {@code backup-dir}。
     * 统一在此处收口，避免各处各写一份解析逻辑导致「备份写到 A、清理扫 B」。</p>
     *
     * @return 备份根目录绝对路径
     */
    public Path getBackupRoot() {
        String configured = sysConfigService.getConfig(Constants.CFG_DM_BACKUP_DIR);
        String dir = configured == null || configured.trim().isEmpty()
                ? properties.getBackupDir()
                : configured.trim();
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * 读取当前保留策略配置。
     *
     * @return 配置对象，字段恒非 null（缺失项回落默认值）
     */
    public DataManagementConfigDTO getConfig() {
        DataManagementConfigDTO dto = new DataManagementConfigDTO();
        dto.setMaxCopies(readInt(Constants.CFG_DM_RETAIN_COUNT, Constants.DM_DEFAULT_RETAIN_COUNT));
        dto.setDefaultDays(readInt(Constants.CFG_DM_RETAIN_DAYS, Constants.DM_DEFAULT_RETAIN_DAYS));
        dto.setSizeLimitMB(readInt(Constants.CFG_DM_UPLOAD_MAX_MB, Constants.DM_DEFAULT_UPLOAD_MAX_MB));
        return dto;
    }

    /**
     * 更新保留策略配置。
     *
     * <p>入参已由 {@code @Valid} 完成上下限校验，此处直接落库。</p>
     *
     * @param dto 新配置，不可为 null
     * @return 落库后的最新配置（回读一次，保证前端拿到的是真实状态）
     */
    public DataManagementConfigDTO updateConfig(DataManagementConfigDTO dto) {
        if (dto == null) {
            return getConfig();
        }
        if (dto.getMaxCopies() != null) {
            sysConfigService.setConfig(Constants.CFG_DM_RETAIN_COUNT, String.valueOf(dto.getMaxCopies()));
        }
        if (dto.getDefaultDays() != null) {
            sysConfigService.setConfig(Constants.CFG_DM_RETAIN_DAYS, String.valueOf(dto.getDefaultDays()));
        }
        if (dto.getSizeLimitMB() != null) {
            sysConfigService.setConfig(Constants.CFG_DM_UPLOAD_MAX_MB, String.valueOf(dto.getSizeLimitMB()));
        }
        log.info("[DataManagementConfig] 保留策略已更新 maxCopies={} days={} uploadMb={}",
                dto.getMaxCopies(), dto.getDefaultDays(), dto.getSizeLimitMB());
        return getConfig();
    }

    /**
     * 恢复前是否强制自动预备份。
     *
     * <p>缺省为 true —— 安全策略的默认值必须是「更安全的那一侧」。</p>
     *
     * @return true 需要预备份
     */
    public boolean isPreBackupEnabled() {
        String raw = sysConfigService.getConfig(Constants.CFG_DM_PRE_BACKUP_ENABLED);
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        String normalized = raw.trim().toLowerCase();
        return !"false".equals(normalized) && !"0".equals(normalized);
    }

    /**
     * 单个任务超时秒数。
     *
     * @param fallback 配置缺失时的回落值
     * @return 超时秒数，恒大于 0
     */
    public int getTaskTimeoutSeconds(int fallback) {
        int value = readInt(Constants.CFG_DM_TASK_TIMEOUT, fallback);
        return value > 0 ? value : fallback;
    }

    /**
     * 上传体积上限（字节）。
     *
     * @return 字节数
     */
    public long getUploadMaxBytes() {
        int mb = readInt(Constants.CFG_DM_UPLOAD_MAX_MB, Constants.DM_DEFAULT_UPLOAD_MAX_MB);
        return (long) mb * 1024L * 1024L;
    }

    /**
     * 读取整型配置，解析失败回落默认值。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private int readInt(String key, int defaultValue) {
        String raw = sysConfigService.getConfig(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[DataManagementConfig] 配置 {} 值非法（{}），回落默认 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }
}
