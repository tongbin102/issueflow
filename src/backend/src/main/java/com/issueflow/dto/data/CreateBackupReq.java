package com.issueflow.dto.data;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建备份请求（Phase10 数据管理）。
 *
 * <p>对应 {@code POST /api/admin/data-management/backups}。</p>
 *
 * <p><b>类型取值必须与 {@link com.issueflow.enums.BackupTypeEnum} 逐字对齐</b>：
 * {@code FULL} / {@code DB_ONLY} / {@code CONFIG_ONLY}。
 * {@code includeConfig} 是给前端「全量备份是否附带配置」勾选框用的语法糖，
 * 最终会由 {@link #resolveType()} 归一成唯一的类型编码，
 * 避免出现「type=DB_ONLY 却又 includeConfig=true」这种自相矛盾的入参。</p>
 */
@Data
public class CreateBackupReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 备份名称（写入 remark 备注），可空；为空时由后端按时间生成文件名 */
    @Size(max = 200, message = "备份名称长度不能超过 200")
    private String name = "";

    /** 备份类型：FULL / DB_ONLY / CONFIG_ONLY，默认 FULL */
    @Pattern(regexp = "FULL|DB_ONLY|CONFIG_ONLY", message = "备份类型仅支持 FULL / DB_ONLY / CONFIG_ONLY")
    private String type = "FULL";

    /**
     * 是否附带配置文件快照。
     *
     * <p>仅在 {@code type=FULL} 时有意义：取消勾选则降级为 {@code DB_ONLY}。
     * {@code type=CONFIG_ONLY} 时本字段被忽略。</p>
     */
    private Boolean includeConfig = Boolean.TRUE;

    /**
     * 归一出最终的备份类型编码。
     *
     * <p>归一规则：</p>
     * <ul>
     *   <li>{@code CONFIG_ONLY} → 原样返回（本就只有配置）；</li>
     *   <li>{@code FULL} + {@code includeConfig=false} → 降级为 {@code DB_ONLY}；</li>
     *   <li>其余 → 原样返回。</li>
     * </ul>
     *
     * @return 备份类型编码，非空
     */
    public String resolveType() {
        String raw = type == null || type.trim().isEmpty() ? "FULL" : type.trim().toUpperCase();
        if ("CONFIG_ONLY".equals(raw)) {
            return raw;
        }
        if ("FULL".equals(raw) && Boolean.FALSE.equals(includeConfig)) {
            return "DB_ONLY";
        }
        return raw;
    }

    /**
     * 取安全的备注文本（去首尾空白，null 转空串）。
     *
     * @return 备注，非空
     */
    public String safeName() {
        return name == null ? "" : name.trim();
    }
}
