package com.issueflow.dto.data;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 上传恢复请求（Phase10 数据管理）。
 *
 * <p>对应 {@code POST /api/admin/data-management/backups/upload}，
 * 以 {@code multipart/form-data} 提交：文件走 {@code MultipartFile file} 参数，
 * 本 DTO 承载随文件一起提交的表单字段。</p>
 *
 * <p><b>校验分工</b>：文件大小 / 后缀 / zip 结构 / manifest 完整性
 * 全部在 Service 层校验（见 {@code DataManagementServiceImpl#uploadAndRestore}），
 * 本 DTO 只做轻量的字符串长度约束。</p>
 */
@Data
public class UploadRestoreReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上传备份的展示名称，为空时取上传文件的原始文件名 */
    @Size(max = 200, message = "备份名称长度不能超过 200")
    private String name = "";

    /**
     * 是否在恢复前自动生成安全备份，默认 true。
     *
     * <p>与 {@link RestoreReq#getPreBackup()} 同义：系统配置开启时强制生效。</p>
     */
    private Boolean preBackup = Boolean.TRUE;

    /**
     * 是否上传后立即触发恢复。
     *
     * <p>false 表示只把包登记入库（{@code source=UPLOAD}）不执行恢复，
     * 管理员可稍后在列表里再点「恢复」—— 给谨慎的运维留一步缓冲。</p>
     */
    private Boolean restoreNow = Boolean.TRUE;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark = "";

    /**
     * 取安全的名称文本。
     *
     * @return 名称，非空
     */
    public String safeName() {
        return name == null ? "" : name.trim();
    }

    /**
     * 取安全的备注文本。
     *
     * @return 备注，非空
     */
    public String safeRemark() {
        return remark == null ? "" : remark.trim();
    }
}
