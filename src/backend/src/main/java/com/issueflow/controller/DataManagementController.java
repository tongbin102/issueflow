package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.data.BackupDetailVO;
import com.issueflow.dto.data.BackupListVO;
import com.issueflow.dto.data.CreateBackupReq;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.dto.data.RestoreReq;
import com.issueflow.dto.data.TaskProgressDTO;
import com.issueflow.dto.data.UploadRestoreReq;
import com.issueflow.service.DataManagementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据管理（备份 / 恢复）接口（Phase10）。
 *
 * <p>路径前缀 {@code /api/admin/data}，与
 * {@link com.issueflow.common.Constants#DATA_MANAGEMENT_API_PREFIX} 保持一致 ——
 * {@code ReadOnlyGuardInterceptor} 正是按此前缀放行，
 * 否则恢复期间连「查询恢复进度」都会被自己的只读闸门挡死。</p>
 *
 * <p><b>权限设计</b>：七个细粒度权限码，与
 * {@code scripts/V20260803_data_management.sql} 种子数据一一对应：</p>
 * <ul>
 *   <li>{@code system:data:view} —— 查看列表 / 详情 / 进度 / 配置</li>
 *   <li>{@code system:data:backup} —— 发起备份</li>
 *   <li>{@code system:data:download} —— 下载备份包</li>
 *   <li>{@code system:data:delete} —— 删除备份</li>
 *   <li>{@code system:data:upload} —— 上传备份包</li>
 *   <li>{@code system:data:restore} —— 执行恢复</li>
 *   <li>{@code system:data:config} —— 修改保留策略</li>
 * </ul>
 * 下载 / 删除 / 恢复之所以各自独立成码，是因为它们的风险等级完全不同：
 * 能看列表不代表能把整库数据下载带走，更不代表能把生产库整个覆盖掉。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataManagementController {

    private final DataManagementService dataManagementService;

    /**
     * 发起手动备份。
     *
     * @param req 备份参数
     * @return 任务初始进度
     */
    @PostMapping("/backups")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<TaskProgressDTO> createBackup(@Valid @RequestBody(required = false) CreateBackupReq req) {
        return Result.success(dataManagementService.createBackup(req));
    }

    /**
     * 分页查询备份列表。
     *
     * @param page       页码，从 1 开始
     * @param size       每页条数，上限 100
     * @param backupType 类型过滤：FULL / DB_ONLY / CONFIG_ONLY
     * @param source     来源过滤：MANUAL / AUTO / UPLOAD / PRE_RESTORE
     * @param status     状态过滤：PENDING / RUNNING / SUCCESS / FAILED / CANCELED
     * @param keyword    文件名或备注模糊匹配
     * @return 分页结果
     */
    @GetMapping("/backups")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<PageResult<BackupListVO>> listBackups(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String backupType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(dataManagementService.listBackups(
                page, size, backupType, source, status, keyword));
    }

    /**
     * 查询备份详情（恢复确认弹窗用）。
     *
     * @param id 备份记录 id
     * @return 详情
     */
    @GetMapping("/backups/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<BackupDetailVO> getBackupDetail(@PathVariable Long id) {
        return Result.success(dataManagementService.getBackupDetail(id));
    }

    /**
     * 下载备份文件。
     *
     * <p>直接写 HTTP 响应流，不返回 {@code Result} 包装
     * —— 前端用 blob 方式接收。</p>
     *
     * @param id       备份记录 id
     * @param response HTTP 响应
     */
    @GetMapping("/backups/{id}/download")
    @PreAuthorize("hasAuthority('ADMIN')")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        dataManagementService.download(id, response);
    }

    /**
     * 删除备份（记录 + 磁盘文件）。
     *
     * @param id 备份记录 id
     * @return 空结果
     */
    @DeleteMapping("/backups/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> deleteBackup(@PathVariable Long id) {
        dataManagementService.deleteBackup(id);
        return Result.success();
    }

    /**
     * 从指定备份恢复数据。
     *
     * @param id  备份记录 id
     * @param req 恢复参数
     * @return 任务初始进度
     */
    @PostMapping("/backups/{id}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<TaskProgressDTO> restore(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) RestoreReq req) {
        return Result.success(dataManagementService.restore(id, req));
    }

    /**
     * 上传备份包并（可选）立即恢复。
     *
     * <p>需要 {@code system:data:upload}；当 {@code restoreNow=true} 时，
     * Service 内部会再走一遍恢复流程，因此调用者<b>还应当</b>具备
     * {@code system:data:restore}。这里用 {@code and} 组合表达式显式要求两者，
     * 避免「只有上传权限的人靠 restoreNow 参数越权覆盖整库」这种提权路径。</p>
     *
     * @param file 上传的 zip 备份包
     * @param req  上传参数
     * @return 任务初始进度
     */
    @PostMapping("/backups/upload")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<TaskProgressDTO> uploadAndRestore(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "meta", required = false) @Valid UploadRestoreReq req) {
        return Result.success(dataManagementService.uploadAndRestore(file, req));
    }

    /**
     * 读取数据管理配置（保留份数 / 天数 / 上传上限）。
     *
     * @return 配置
     */
    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<DataManagementConfigDTO> getConfig() {
        return Result.success(dataManagementService.getConfig());
    }

    /**
     * 更新数据管理配置。
     *
     * @param dto 新配置
     * @return 落库后的最新配置
     */
    @PutMapping("/config")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<DataManagementConfigDTO> updateConfig(
            @Valid @RequestBody DataManagementConfigDTO dto) {
        return Result.success(dataManagementService.updateConfig(dto));
    }

    /**
     * 查询任务进度（前端轮询）。
     *
     * @param taskId 任务号
     * @return 进度
     */
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<TaskProgressDTO> getTaskProgress(@PathVariable String taskId) {
        return Result.success(dataManagementService.getTaskProgress(taskId));
    }
}
