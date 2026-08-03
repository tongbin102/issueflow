package com.issueflow.service;

import com.issueflow.common.PageResult;
import com.issueflow.dto.data.BackupDetailVO;
import com.issueflow.dto.data.BackupListVO;
import com.issueflow.dto.data.CreateBackupReq;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.dto.data.RestoreReq;
import com.issueflow.dto.data.TaskProgressDTO;
import com.issueflow.dto.data.UploadRestoreReq;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据管理（备份 / 恢复）服务接口（Phase10）。
 *
 * <p>对应「系统设置 → 数据管理」页的全部能力。所有会改变系统状态的方法
 * 都受全局互斥锁保护：同一时刻集群内只允许一个备份或恢复任务在跑。</p>
 */
public interface DataManagementService {

    /**
     * 发起一次手动备份（异步执行）。
     *
     * @param req 备份参数，不可为 null
     * @return 任务初始进度，前端据 {@code taskId} 轮询
     */
    TaskProgressDTO createBackup(CreateBackupReq req);

    /**
     * 分页查询备份列表。
     *
     * @param page       页码，从 1 开始
     * @param size       每页条数
     * @param backupType 备份类型过滤，空表示不过滤
     * @param source     来源过滤，空表示不过滤
     * @param status     状态过滤，空表示不过滤
     * @param keyword    文件名 / 备注模糊匹配，空表示不过滤
     * @return 分页结果
     */
    PageResult<BackupListVO> listBackups(long page, long size, String backupType,
                                         String source, String status, String keyword);

    /**
     * 查询备份详情（恢复前确认弹窗使用）。
     *
     * @param id 备份记录 id
     * @return 详情
     */
    BackupDetailVO getBackupDetail(Long id);

    /**
     * 下载备份文件（流式写出，不暴露服务器路径）。
     *
     * @param id       备份记录 id
     * @param response HTTP 响应
     */
    void download(Long id, HttpServletResponse response);

    /**
     * 删除备份（同时删除磁盘文件与记录）。
     *
     * @param id 备份记录 id
     */
    void deleteBackup(Long id);

    /**
     * 从已有备份恢复（异步执行）。
     *
     * @param id  备份记录 id
     * @param req 恢复参数
     * @return 任务初始进度
     */
    TaskProgressDTO restore(Long id, RestoreReq req);

    /**
     * 上传备份包并（可选）立即恢复。
     *
     * @param file 上传的 zip 文件
     * @param req  上传参数
     * @return 任务初始进度；仅登记不恢复时返回已完成状态的登记结果
     */
    TaskProgressDTO uploadAndRestore(MultipartFile file, UploadRestoreReq req);

    /**
     * 读取数据管理配置。
     *
     * @return 配置
     */
    DataManagementConfigDTO getConfig();

    /**
     * 更新数据管理配置。
     *
     * @param dto 新配置
     * @return 落库后的最新配置
     */
    DataManagementConfigDTO updateConfig(DataManagementConfigDTO dto);

    /**
     * 查询任务进度。
     *
     * @param taskId 任务号
     * @return 进度
     */
    TaskProgressDTO getTaskProgress(String taskId);
}
