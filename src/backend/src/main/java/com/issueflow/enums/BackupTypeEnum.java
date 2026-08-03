package com.issueflow.enums;

/**
 * 备份类型（Phase10 数据管理）。
 *
 * <p>决定备份包内含哪些内容：</p>
 * <ul>
 *   <li>{@link #FULL} —— db/issueflow_db.sql + config/*（脱敏） + manifest.json</li>
 *   <li>{@link #DB_ONLY} —— 仅 db/issueflow_db.sql + manifest.json</li>
 *   <li>{@link #CONFIG_ONLY} —— 仅 config/*（脱敏） + manifest.json</li>
 * </ul>
 */
public enum BackupTypeEnum {

    /** 全量：数据库 + 配置 */
    FULL("FULL", "全量备份"),
    /** 仅数据库 */
    DB_ONLY("DB_ONLY", "仅数据库"),
    /** 仅配置文件 */
    CONFIG_ONLY("CONFIG_ONLY", "仅配置");

    private final String code;
    private final String desc;

    BackupTypeEnum(String code, String desc) {
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
    public static BackupTypeEnum of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (BackupTypeEnum e : values()) {
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

    /**
     * 是否需要导出数据库。
     *
     * @return true 需要
     */
    public boolean includeDb() {
        return this == FULL || this == DB_ONLY;
    }

    /**
     * 是否需要导出配置文件。
     *
     * @return true 需要
     */
    public boolean includeConfig() {
        return this == FULL || this == CONFIG_ONLY;
    }
}
