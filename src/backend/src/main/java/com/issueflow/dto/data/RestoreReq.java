package com.issueflow.dto.data;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 恢复请求（Phase10 数据管理）。
 *
 * <p>对应 {@code POST /api/admin/data-management/backups/{id}/restore}。
 * 备份 id 走路径参数，本体只承载恢复策略选项。</p>
 *
 * <p><b>二次确认由前端负责</b>（弹窗要求手抄备份名），后端不重复校验确认串 ——
 * 但后端<b>必须</b>独立执行：预备份 → 只读拦截 → 换血，
 * 这三步是数据安全的兜底，绝不能因为「前端已经确认过了」而省略。</p>
 */
@Data
public class RestoreReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否在恢复前自动生成安全备份。
     *
     * <p>默认 true。即便前端传 false，若 {@code sys_config} 的
     * {@code data.management.restore.pre.backup.enabled} 为 true，
     * 仍以系统配置为准强制预备份 —— 系统级安全策略优先于单次请求偏好。</p>
     */
    private Boolean preBackup = Boolean.TRUE;

    /** 恢复备注，记录到 restore_record，便于事后追溯 */
    @Size(max = 500, message = "恢复备注长度不能超过 500")
    private String remark = "";

    /**
     * 取安全的备注文本。
     *
     * @return 备注，非空
     */
    public String safeRemark() {
        return remark == null ? "" : remark.trim();
    }
}
