package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.FileConfigReq;
import com.issueflow.dto.resp.FileConfigVO;
import com.issueflow.entity.FileConfig;
import com.issueflow.entity.FileRecord;
import com.issueflow.mapper.FileConfigMapper;
import com.issueflow.mapper.FileRecordMapper;
import com.issueflow.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件存储配置服务（Phase 7）。
 *
 * <p>{@code file_config} 全局唯一一行，由 SQL 种子保证存在。采用<b>独立表</b>而非
 * {@code sys_config} 的 {@code file.*} 键（本期与 ARCH §3.7 的差异，见交付说明）：
 * 保证「文件配置」只有一个真源，避免两处存储不一致。</p>
 *
 * <p>{@link #current()} 带 30s 本地缓存 —— 上传是高频路径，不能每次都查库；
 * 但缓存必须在 {@link #updateConfig(FileConfigReq)} 后立刻失效，
 * 以满足「改小上限后<b>不重启</b>即刻生效」的验收标准。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileConfigService {

    private final FileConfigMapper fileConfigMapper;
    private final FileRecordMapper fileRecordMapper;
    private final PermissionService permissionService;

    /** 30s 本地缓存 */
    private volatile FileConfig cached;

    /** 缓存写入时间戳（毫秒） */
    private volatile long cachedAt = 0L;

    // ============================ 读取 ============================

    /**
     * 读取唯一的文件配置行；库内无行时返回内存默认（不落库，落库由 SQL 种子保证）。
     *
     * @return 配置实体，永不为 null
     */
    public FileConfig getConfig() {
        List<FileConfig> all = fileConfigMapper.selectList(null);
        if (all != null && !all.isEmpty()) {
            return all.get(0);
        }
        return defaultConfig();
    }

    /**
     * 带 30s 缓存的配置读取（上传 / 下载热路径专用）。
     *
     * @return 配置实体，永不为 null
     */
    public FileConfig current() {
        FileConfig snapshot = cached;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - cachedAt < Constants.FILE_CONFIG_CACHE_MILLIS) {
            return snapshot;
        }
        FileConfig fresh = getConfig();
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    /**
     * 失效本地缓存（配置写入后必须调用）。
     */
    public void evictCache() {
        cached = null;
        cachedAt = 0L;
    }

    /**
     * 文件配置视图（含占用统计与可写探测）。
     *
     * @return 视图对象
     */
    public FileConfigVO getConfigVO() {
        permissionService.requirePermission("file:config");
        FileConfig cfg = current();
        FileConfigVO vo = new FileConfigVO();
        vo.setStorageRoot(cfg.getStorageRoot());
        vo.setMaxSizeMb(cfg.getMaxSizeMb());
        vo.setAllowedExts(cfg.getAllowedExts());
        vo.setStorageType(cfg.getStorageType());
        Long used = fileRecordMapper.sumSize();
        vo.setUsedSize(used == null ? 0L : used);
        Long count = fileRecordMapper.selectCount(new LambdaQueryWrapper<FileRecord>());
        vo.setFileCount(count == null ? 0L : count);
        vo.setWritable(FileUtil.isWritable(cfg.getStorageRoot()));
        return vo;
    }

    // ============================ 写入 ============================

    /**
     * 按请求体保存配置（权限 {@code file:config}）。
     *
     * <p>保存后立即 evict 本地缓存，对新上传即时生效；
     * 修改存储根<b>不迁移历史文件</b>，历史文件仍按其入库时的路径读取。</p>
     *
     * @param req 配置请求
     * @return 保存后的视图对象
     */
    @Transactional
    public FileConfigVO updateConfig(FileConfigReq req) {
        permissionService.requirePermission("file:config");
        String storageRoot = req.getStorageRoot() == null ? "" : req.getStorageRoot().trim();
        if (!isAbsolutePath(storageRoot)) {
            throw new BizException(ResultCode.VALID_ERROR, "存储根目录必须是绝对路径");
        }
        String allowedExts = normalizeExts(req.getAllowedExts());
        if (allowedExts.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "允许的扩展名不能为空");
        }

        FileConfig entity = new FileConfig();
        entity.setStorageRoot(storageRoot);
        entity.setMaxSizeMb(req.getMaxSizeMb());
        entity.setAllowedExts(allowedExts);
        entity.setStorageType(req.getStorageType() == null || req.getStorageType().isBlank()
                ? Constants.STORAGE_TYPE_LOCAL : req.getStorageType().trim().toUpperCase());
        updateConfig(entity);
        evictCache();

        if (!FileUtil.isWritable(storageRoot)) {
            log.warn("[FileConfig] storageRoot={} 当前不可写，新上传将失败，请检查目录权限", storageRoot);
        }
        return getConfigVO();
    }

    /**
     * 保存配置实体：库内已有则覆盖首行，否则插入。
     *
     * @param cfg 配置实体
     * @return 落库后的实体
     */
    @Transactional
    public FileConfig updateConfig(FileConfig cfg) {
        List<FileConfig> all = fileConfigMapper.selectList(null);
        if (all != null && !all.isEmpty()) {
            FileConfig existing = all.get(0);
            existing.setStorageRoot(cfg.getStorageRoot());
            existing.setMaxSizeMb(cfg.getMaxSizeMb());
            existing.setAllowedExts(cfg.getAllowedExts());
            existing.setStorageType(cfg.getStorageType());
            fileConfigMapper.updateById(existing);
            evictCache();
            return existing;
        }
        fileConfigMapper.insert(cfg);
        evictCache();
        return cfg;
    }

    // ============================ 校验与派生值 ============================

    /**
     * 上传前校验（大小 + 扩展名），不通过直接抛业务异常。
     *
     * @param file 上传文件
     * @throws BizException 文件为空 / 超限 / 扩展名不允许
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "文件内容为空");
        }
        long max = maxBytes();
        if (max > 0 && file.getSize() > max) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件大小超过上限 " + current().getMaxSizeMb() + "MB");
        }
        String ext = FileUtil.extensionOf(file.getOriginalFilename());
        Set<String> allowed = allowedExtSet();
        if (!allowed.isEmpty() && !allowed.contains(ext)) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "不允许的文件类型：" + (ext.isEmpty() ? "(无扩展名)" : ext));
        }
    }

    /**
     * 当前存储根目录。
     *
     * @return 绝对路径
     */
    public String storageRoot() {
        String root = current().getStorageRoot();
        return (root == null || root.isBlank()) ? Constants.DEFAULT_FILE_STORAGE_ROOT : root;
    }

    /**
     * 当前单文件字节上限。
     *
     * @return 字节数；配置缺失时按默认 10MB
     */
    public long maxBytes() {
        Integer mb = current().getMaxSizeMb();
        int value = (mb == null || mb <= 0) ? Constants.DEFAULT_FILE_MAX_SIZE_MB : mb;
        return (long) value * 1024L * 1024L;
    }

    /**
     * 当前允许的扩展名集合（小写、不含点）。
     *
     * @return 扩展名集合；配置为空时返回空集合（表示不限制）
     */
    public Set<String> allowedExtSet() {
        String raw = current().getAllowedExts();
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",")) {
            String ext = item.trim().toLowerCase();
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty()) {
                result.add(ext);
            }
        }
        return result;
    }

    // ============================ 私有方法 ============================

    /**
     * 构造内存默认配置（库内无行时兜底，不落库）。
     *
     * @return 默认配置
     */
    private FileConfig defaultConfig() {
        FileConfig def = new FileConfig();
        def.setStorageRoot(Constants.DEFAULT_FILE_STORAGE_ROOT);
        def.setMaxSizeMb(Constants.DEFAULT_FILE_MAX_SIZE_MB);
        def.setAllowedExts(Constants.DEFAULT_FILE_ALLOWED_EXTS);
        def.setStorageType(Constants.DEFAULT_FILE_STORAGE_TYPE);
        return def;
    }

    /**
     * 判断是否绝对路径（同时兼容 Linux {@code /data} 与 Windows {@code D:\data}）。
     *
     * @param path 路径
     * @return true 为绝对路径
     */
    private boolean isAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }
        return path.length() >= 3 && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':' && (path.charAt(2) == '\\' || path.charAt(2) == '/');
    }

    /**
     * 归一化扩展名串：小写、去点、去空、去重。
     *
     * @param raw 原始串
     * @return 归一化后的逗号分隔串
     */
    private String normalizeExts(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(raw.split(",")).forEach(item -> {
            String ext = item.trim().toLowerCase();
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty()) {
                set.add(ext);
            }
        });
        return String.join(",", set);
    }
}
