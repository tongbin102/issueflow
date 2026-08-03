package com.issueflow.enums;

/**
 * 备份来源（Phase10 数据管理）。
 *
 * <p>用于区分备份记录的产生方式，前端列表按此维度做筛选。</p>
 */
public enum BackupSourceEnum {

    /** 管理员在「数据管理」页手动触发 */
    MANUAL("MANUAL", "手动备份"),
    /** 定时任务自动触发 */
    AUTO("AUTO", "自动备份"),
    /** 由外部上传的备份包（仅登记，不由本系统生成） */
    UPLOAD("UPLOAD", "上传导入"),
    /** 恢复前系统自动生成的安全备份，用于回退 */
    PRE_RESTORE("PRE_RESTORE", "恢复前安全备份");

    private final String code;
    private final String desc;

    BackupSourceEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按编码获取枚举。
     *
     * @param code 编码，可为 null
     * @return 匹配的枚举；未匹配返回 null
     */
    public static BackupSourceEnum of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (BackupSourceEnum e : values()) {
            if (e.code.equals(normalized)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 校验编码是否合法。
     *
     * @param code 编码
     * @return true 合法
     */
    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
