package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.mapper.SystemDataMapper;
import com.issueflow.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 数据初始化服务（R7）：单事务物理清空业务数据（先子后父），保留
 * role / permission / role_permission / menu / sys_config / flow_node / flow_transition 与 admin 账号。
 * <p>
 * 事务提交后：逐表重置 AUTO_INCREMENT（失败仅告警）→ 递归删除磁盘附件 → 失效角色权限 Redis 缓存。
 * 可通过 application.yml 的 system.data-reset.enabled=false 关闭本功能（默认开启）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemDataService {

    /** 必须输入的确认文本 */
    private static final String CONFIRM_TEXT = "RESET";

    /** AUTO_INCREMENT 重置白名单（与删除顺序一致，非用户输入） */
    private static final List<String> RESET_TABLES = List.of(
            "issue_attachment", "issue_history", "issue_relation", "issue",
            "tag", "module_dependency", "module", "project", "organization", "user");

    private final SystemDataMapper systemDataMapper;
    private final PermissionService permissionService;
    private final TransactionTemplate transactionTemplate;

    /** 功能开关（yml 可关闭，默认开启） */
    @Value("${system.data-reset.enabled:true}")
    private boolean resetEnabled;

    /** 附件存储根路径（与 FileUtil 同源配置） */
    @Value("${app.attachment-base-path:" + Constants.ATTACHMENT_BASE_PATH + "}")
    private String attachmentBasePath;

    /**
     * 执行数据初始化。
     *
     * @param confirmText 确认文本，必须为 RESET
     * @return 各表清理条数（LinkedHashMap，保持删除顺序）
     */
    public Map<String, Integer> resetData(String confirmText) {
        if (!resetEnabled) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "数据初始化功能已被系统配置关闭");
        }
        // 双重校验：角色码必须 ADMIN + 权限码 system:reset
        // M4 鉴权收口：原地内联判断收敛到 PermissionService#requireAdmin，语义与错误码完全不变
        permissionService.requireAdmin();
        permissionService.requirePermission("system:reset");
        if (!CONFIRM_TEXT.equals(confirmText == null ? null : confirmText.trim())) {
            throw new BizException(ResultCode.VALID_ERROR, "确认文本不正确，请输入 RESET");
        }

        // 单事务清库（先子后父，DELETE 不用 TRUNCATE），并收集各表清理条数
        Map<String, Integer> counts = transactionTemplate.execute(status -> {
            Map<String, Integer> deleted = new LinkedHashMap<>();
            deleted.put("issue_attachment", systemDataMapper.clearIssueAttachment());
            deleted.put("issue_history", systemDataMapper.clearIssueHistory());
            deleted.put("issue_relation", systemDataMapper.clearIssueRelation());
            deleted.put("issue", systemDataMapper.clearIssue());
            deleted.put("tag", systemDataMapper.clearTag());
            deleted.put("module_dependency", systemDataMapper.clearModuleDependency());
            deleted.put("module", systemDataMapper.clearModule());
            deleted.put("project", systemDataMapper.clearProject());
            deleted.put("organization", systemDataMapper.clearOrganization());
            deleted.put("user", systemDataMapper.clearUsersExceptAdmin());
            systemDataMapper.resetAdminLeader();
            return deleted;
        });
        if (counts == null) {
            counts = new LinkedHashMap<>();
        }
        log.info("data reset: business tables cleared by userId={}", SecurityUtils.getCurrentUserId());

        // 提交后逐表重置自增（DDL 隐式提交，失败仅告警不回滚业务结果）
        for (String table : RESET_TABLES) {
            try {
                systemDataMapper.resetAutoIncrement(table);
            } catch (Exception e) {
                log.warn("data reset: reset AUTO_INCREMENT failed for table {}: {}", table, e.getMessage());
            }
        }

        // 递归删除磁盘附件（保留根目录）
        deleteAttachmentFiles();

        // 失效全部角色权限 Redis 缓存（perm:role:*）
        // M5：改用 PermissionService#invalidateAll，按 key 前缀整体清理，
        // 不再依赖「先查全量角色再逐个 invalidate」——避免角色表已被改动时漏清理；
        // 该方法内部已吞异常并告警，此处无需再包 try/catch。
        permissionService.invalidateAll();
        log.info("data reset: completed, counts={}", counts);
        return counts;
    }

    /**
     * 递归删除附件根目录下所有文件与子目录（保留根目录本身；失败仅告警）
     */
    private void deleteAttachmentFiles() {
        Path base = Paths.get(attachmentBasePath);
        if (!Files.exists(base)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(base))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("data reset: delete attachment file failed {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("data reset: walk attachment dir failed {}: {}", base, e.getMessage());
        }
    }
}
