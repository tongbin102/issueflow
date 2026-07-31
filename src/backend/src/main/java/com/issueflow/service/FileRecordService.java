package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.FilePageReq;
import com.issueflow.dto.resp.FileRecordVO;
import com.issueflow.entity.FileRecord;
import com.issueflow.entity.Issue;
import com.issueflow.mapper.FileRecordMapper;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.FileUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一文件记录服务（Phase 7）。
 *
 * <p>与 {@code issue_attachment} <b>并存</b>：问题详情仍读 issue_attachment（零回归），
 * {@code file_record} 提供后台统一文件视图。</p>
 *
 * <p>安全约束（ARCH §7.8）：</p>
 * <ul>
 *   <li>下载 / 预览一律后端读流回传，<b>绝不</b>把绝对路径暴露给前端；</li>
 *   <li>路径拼装统一走 {@link FileUtil#resolveSafe(String, String)} 做穿越断言；</li>
 *   <li>{@link #store} 为<b>内部方法不做权限校验</b>（头像上传属「登录即可」），
 *       对外的手工上传入口 {@link #uploadManual} 才校验 {@code file:upload}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileRecordService {

    private final FileRecordMapper fileRecordMapper;
    private final FileConfigService fileConfigService;
    private final PermissionService permissionService;
    private final UserService userService;
    private final IssueMapper issueMapper;
    private final FileUtil fileUtil;

    // ============================ 写入 ============================

    /**
     * 落库一条文件记录。
     *
     * @param record 文件记录
     */
    public void save(FileRecord record) {
        fileRecordMapper.insert(record);
    }

    /**
     * 存储文件并落库（<b>内部方法，不做权限校验</b>）。
     *
     * <p>供手工上传、头像上传、问题附件双写共用。校验规则来自运行期
     * {@code file_config}，改配置后无需重启即刻生效。</p>
     *
     * @param file       上传文件
     * @param bizType    业务类型 ISSUE / AVATAR / MANUAL
     * @param bizId      关联业务 id，可为 null
     * @param uploaderId 上传人 user.id
     * @return 落库后的文件记录
     */
    @Transactional
    public FileRecord store(MultipartFile file, String bizType, Long bizId, Long uploaderId) {
        fileConfigService.validate(file);
        FileUtil.StoredObject stored = fileUtil.storeTo(file,
                fileConfigService.storageRoot(),
                fileConfigService.maxBytes(),
                fileConfigService.allowedExtSet());

        FileRecord record = new FileRecord();
        record.setFileName(stored.fileName());
        record.setOriginalName(file.getOriginalFilename());
        record.setRelativePath(stored.relativePath());
        record.setFilePath(stored.absolutePath());
        record.setFileSize(stored.fileSize());
        record.setContentType(stored.contentType());
        record.setExt(stored.ext());
        record.setBizType(bizType == null || bizType.isBlank() ? Constants.BIZ_TYPE_MANUAL : bizType);
        record.setBizId(bizId);
        record.setUploaderId(uploaderId);
        record.setStorageType(Constants.STORAGE_TYPE_LOCAL);
        fileRecordMapper.insert(record);
        return record;
    }

    /**
     * 后台手工上传（权限 {@code file:upload}，{@code bizType='MANUAL'}）。
     *
     * @param file       上传文件
     * @param uploaderId 上传人 id
     * @return 文件视图对象
     */
    @Transactional
    public FileRecordVO uploadManual(MultipartFile file, Long uploaderId) {
        permissionService.requirePermission("file:upload");
        FileRecord record = store(file, Constants.BIZ_TYPE_MANUAL, null, uploaderId);
        return toVO(record, userService.userNameMap(), new HashMap<>());
    }

    // ============================ 查询 ============================

    /**
     * 按 id 查询（不存在抛 404）。
     *
     * @param id 文件 id
     * @return 文件记录
     */
    public FileRecord requireById(Long id) {
        FileRecord record = id == null ? null : fileRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return record;
    }

    /**
     * 按相对路径查询文件记录（头像展示端点用：{@code user.avatar} 存的就是相对路径）。
     *
     * @param relativePath 相对路径
     * @return 文件记录；未找到返回 null
     */
    public FileRecord findByRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return fileRecordMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getRelativePath, relativePath)
                .orderByDesc(FileRecord::getId)
                .last("LIMIT 1"));
    }

    /**
     * 文件列表分页（权限 {@code file:list}）。
     *
     * <p>{@code uploaderName} 与 {@code bizRef} 均为<b>批量回填</b>：
     * 上传人取一次全量 name map，业务引用对本页 ISSUE 类 bizId 做<b>一条 IN 查询</b>，
     * 严禁每行一次查询（ARCH §7.7 硬指标）。</p>
     *
     * @param req 分页与筛选请求
     * @return 分页结果
     */
    public PageResult<FileRecordVO> pageQuery(FilePageReq req) {
        permissionService.requirePermission("file:list");
        int pageNum = (req.getPage() == null || req.getPage() < 1) ? Constants.DEFAULT_PAGE : req.getPage();
        int size = (req.getSize() == null || req.getSize() < 1) ? Constants.DEFAULT_SIZE : req.getSize();

        Page<FileRecord> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<FileRecord> wrapper = new LambdaQueryWrapper<>();
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String kw = req.getKeyword().trim();
            wrapper.and(q -> q.like(FileRecord::getOriginalName, kw)
                    .or().like(FileRecord::getFileName, kw));
        }
        if (req.getExt() != null && !req.getExt().isBlank()) {
            wrapper.eq(FileRecord::getExt, req.getExt().trim().toLowerCase());
        }
        if (req.getBizType() != null && !req.getBizType().isBlank()) {
            wrapper.eq(FileRecord::getBizType, req.getBizType().trim().toUpperCase());
        }
        LocalDateTime start = parseStart(req.getStartDate());
        if (start != null) {
            wrapper.ge(FileRecord::getCreatedAt, start);
        }
        LocalDateTime end = parseEnd(req.getEndDate());
        if (end != null) {
            wrapper.le(FileRecord::getCreatedAt, end);
        }
        wrapper.orderByDesc(FileRecord::getCreatedAt).orderByDesc(FileRecord::getId);
        fileRecordMapper.selectPage(page, wrapper);

        List<FileRecord> rows = page.getRecords();
        Map<Long, String> userNameMap = userService.userNameMap();
        Map<Long, String> issueNoMap = issueNoMapOf(rows);
        List<FileRecordVO> list = new ArrayList<>(rows.size());
        for (FileRecord row : rows) {
            list.add(toVO(row, userNameMap, issueNoMap));
        }
        return PageResult.of(list, page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 按业务类型 + 业务 id 查询关联文件。
     *
     * @param bizType 业务类型
     * @param bizId   业务 id
     * @return 文件记录列表
     */
    public List<FileRecord> listByBiz(String bizType, Long bizId) {
        return fileRecordMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getBizType, bizType).eq(FileRecord::getBizId, bizId));
    }

    /**
     * 全部未删除文件的占用字节数。
     *
     * @return 字节数
     */
    public long totalSize() {
        Long sum = fileRecordMapper.sumSize();
        return sum == null ? 0L : sum;
    }

    // ============================ 下载 / 预览 ============================

    /**
     * 下载 / 预览文件（权限 {@code file:list}）。
     *
     * @param id       文件 id
     * @param response HTTP 响应
     * @param inline   true 且为图片时以 inline 内联展示，否则 attachment 下载
     * @throws IOException 读写流失败
     */
    public void download(Long id, HttpServletResponse response, boolean inline) throws IOException {
        permissionService.requirePermission("file:list");
        FileRecord record = requireById(id);
        if (inline && !isPreviewable(record)) {
            throw new BizException(ResultCode.VALID_ERROR, "该文件类型不支持在线预览");
        }
        writeStream(record, response, inline);
    }

    /**
     * 直接把文件写入响应流（<b>内部方法，不做权限校验</b>，供头像端点复用）。
     *
     * @param record   文件记录
     * @param response HTTP 响应
     * @param inline   是否内联
     * @throws IOException 读写流失败
     */
    public void writeStream(FileRecord record, HttpServletResponse response, boolean inline)
            throws IOException {
        Path path = resolvePath(record);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BizException(ResultCode.NOT_FOUND, "物理文件不存在或已被清理");
        }
        String contentType = (record.getContentType() == null || record.getContentType().isBlank())
                ? "application/octet-stream" : record.getContentType();
        response.setContentType(contentType);
        String displayName = (record.getOriginalName() == null || record.getOriginalName().isBlank())
                ? record.getFileName() : record.getOriginalName();
        String encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = inline ? "inline" : "attachment";
        response.setHeader("Content-Disposition",
                disposition + ";filename=\"" + encodedName + "\";filename*=UTF-8''" + encodedName);
        if (record.getFileSize() != null && record.getFileSize() > 0) {
            response.setContentLengthLong(record.getFileSize());
        }
        try (InputStream in = new FileInputStream(path.toFile());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    /**
     * 解析文件的物理路径。
     *
     * <p>优先用 {@code relative_path} + 当前存储根（迁移存储根后仍可定位），
     * 回落到存量 {@code file_path} 绝对路径（issue_attachment 回灌行没有相对路径）。</p>
     *
     * @param record 文件记录
     * @return 物理路径
     */
    public Path resolvePath(FileRecord record) {
        if (record.getRelativePath() != null && !record.getRelativePath().isBlank()) {
            return FileUtil.resolveSafe(fileConfigService.storageRoot(), record.getRelativePath());
        }
        if (record.getFilePath() != null && !record.getFilePath().isBlank()) {
            // 存量绝对路径由后端自己写入，不含用户输入，直接使用但仍做归一化
            return Paths.get(record.getFilePath()).toAbsolutePath().normalize();
        }
        throw new BizException(ResultCode.NOT_FOUND, "文件路径缺失，无法定位物理文件");
    }

    // ============================ 删除 ============================

    /**
     * 删除文件（权限 {@code file:delete}）：软删记录 + 物理删除。
     *
     * <p><b>物理删除失败只记 warn 不回滚</b>（ARCH T6 要点 4）—— 若因文件被占用而回滚，
     * 会陷入「记录删不掉、文件也删不掉」的死循环；此时返回提示由人工清理。</p>
     *
     * @param id 文件 id
     * @return 结果提示语
     */
    @Transactional
    public String delete(Long id) {
        permissionService.requirePermission("file:delete");
        FileRecord record = requireById(id);
        fileRecordMapper.deleteById(id);
        boolean physicalOk = deletePhysical(record);
        return physicalOk ? "删除成功"
                : "记录已删除，物理文件清理失败，请人工检查：" + record.getFileName();
    }

    /**
     * 物理回收「已软删且早于指定时间」的文件（内置清理任务调用）。
     *
     * @param before 截止时间
     * @param limit  单次最多处理条数
     * @return 实际清理条数
     */
    @Transactional
    public int purgeSoftDeletedBefore(LocalDateTime before, int limit) {
        if (before == null || limit <= 0) {
            return 0;
        }
        List<FileRecord> rows = fileRecordMapper.selectSoftDeletedBefore(before, limit);
        int purged = 0;
        for (FileRecord row : rows) {
            deletePhysical(row);
            purged += fileRecordMapper.hardDeleteById(row.getId());
        }
        return purged;
    }

    /**
     * 删除物理文件。
     *
     * @param record 文件记录
     * @return true 删除成功或文件本就不存在
     */
    private boolean deletePhysical(FileRecord record) {
        try {
            Path path = resolvePath(record);
            Files.deleteIfExists(path);
            return true;
        } catch (Exception e) {
            log.warn("[FileRecord] physical delete failed, id={}, file={}, msg={}",
                    record.getId(), record.getFileName(), e.getMessage());
            return false;
        }
    }

    // ============================ 转换与回填 ============================

    /**
     * 实体转 VO。
     *
     * @param row         文件记录
     * @param userNameMap 用户 id → 展示名（批量回填）
     * @param issueNoMap  问题 id → 问题编号（批量回填）
     * @return 视图对象
     */
    public FileRecordVO toVO(FileRecord row, Map<Long, String> userNameMap, Map<Long, String> issueNoMap) {
        FileRecordVO vo = new FileRecordVO();
        vo.setId(row.getId());
        vo.setOriginalName(row.getOriginalName() == null ? row.getFileName() : row.getOriginalName());
        vo.setExt(row.getExt());
        vo.setContentType(row.getContentType());
        vo.setFileSize(row.getFileSize());
        vo.setUploaderName(row.getUploaderId() == null ? ""
                : userNameMap.getOrDefault(row.getUploaderId(), ""));
        vo.setBizType(row.getBizType());
        vo.setBizId(row.getBizId());
        if (Constants.BIZ_TYPE_ISSUE.equals(row.getBizType()) && row.getBizId() != null) {
            vo.setBizRef(issueNoMap.getOrDefault(row.getBizId(), ""));
        } else {
            vo.setBizRef("");
        }
        vo.setRelativePath(row.getRelativePath());
        vo.setPreviewable(isPreviewable(row));
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    /**
     * 本页 ISSUE 类文件的问题编号批量回填（一条 IN 查询，禁 N+1）。
     *
     * @param rows 本页文件记录
     * @return 问题 id → issue_no
     */
    private Map<Long, String> issueNoMapOf(List<FileRecord> rows) {
        Map<Long, String> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<Long> issueIds = new HashSet<>();
        for (FileRecord row : rows) {
            if (Constants.BIZ_TYPE_ISSUE.equals(row.getBizType()) && row.getBizId() != null) {
                issueIds.add(row.getBizId());
            }
        }
        if (issueIds.isEmpty()) {
            return result;
        }
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().select(Issue::getId, Issue::getIssueNo)
                        .in(Issue::getId, issueIds));
        return issues.stream().filter(i -> i.getIssueNo() != null)
                .collect(Collectors.toMap(Issue::getId, Issue::getIssueNo, (a, b) -> a));
    }

    /**
     * 是否可在线预览（图片类）。
     *
     * @param row 文件记录
     * @return true 可预览
     */
    private boolean isPreviewable(FileRecord row) {
        if (row.getContentType() != null && row.getContentType().startsWith("image/")) {
            return true;
        }
        return row.getExt() != null && Constants.PREVIEWABLE_EXTS.contains(row.getExt().toLowerCase());
    }

    /**
     * 解析起始日期为当天 00:00:00。
     *
     * @param date yyyy-MM-dd，可空
     * @return 起始时间，解析失败返回 null
     */
    private LocalDateTime parseStart(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atStartOfDay();
    }

    /**
     * 解析结束日期为当天 23:59:59。
     *
     * @param date yyyy-MM-dd，可空
     * @return 结束时间，解析失败返回 null
     */
    private LocalDateTime parseEnd(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atTime(LocalTime.MAX);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
