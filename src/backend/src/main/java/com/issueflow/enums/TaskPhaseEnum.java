package com.issueflow.enums;

/**
 * 数据管理任务阶段（Phase10）。
 *
 * <p>用于前端进度条的阶段文案与百分比锚点。备份与恢复共用一套枚举，
 * 各自只走属于自己的子集：</p>
 * <ul>
 *   <li>备份：INIT → LOCK → DUMP_DB → DUMP_CONFIG → PACKAGE → CHECKSUM → PERSIST → DONE</li>
 *   <li>恢复：INIT → LOCK → VALIDATE → PRE_BACKUP → UNPACK → IMPORT_DB → REFRESH_CACHE → DONE</li>
 * </ul>
 *
 * <p>{@code weight} 为该阶段完成时对应的进度百分比锚点，
 * 由 {@code TaskProgressStore} 直接写入 Redis，前端无需自行推算。</p>
 */
public enum TaskPhaseEnum {

    /** 初始化：参数校验、生成 taskId */
    INIT("INIT", "初始化", 2),
    /** 抢占全局互斥锁 */
    LOCK("LOCK", "获取任务锁", 5),

    // ---------- 备份专用 ----------
    /** 导出数据库（mysqldump） */
    DUMP_DB("DUMP_DB", "导出数据库", 45),
    /** 导出配置文件（脱敏后） */
    DUMP_CONFIG("DUMP_CONFIG", "导出配置", 60),
    /** 打包 zip */
    PACKAGE("PACKAGE", "打包归档", 80),
    /** 计算 SHA-256 校验和 */
    CHECKSUM("CHECKSUM", "计算校验和", 90),
    /** 落库备份记录 + 清理过期备份 */
    PERSIST("PERSIST", "登记备份记录", 97),

    // ---------- 恢复专用 ----------
    /** 校验备份包结构与校验和 */
    VALIDATE("VALIDATE", "校验备份包", 15),
    /** 恢复前自动安全备份 */
    PRE_BACKUP("PRE_BACKUP", "生成安全备份", 40),
    /** 解包 */
    UNPACK("UNPACK", "解包备份文件", 50),
    /** 导入数据库 */
    IMPORT_DB("IMPORT_DB", "导入数据库", 85),
    /** 刷新缓存 / 解除只读 */
    REFRESH_CACHE("REFRESH_CACHE", "刷新缓存", 95),

    /** 完成 */
    DONE("DONE", "完成", 100);

    private final String code;
    private final String desc;
    private final int weight;

    TaskPhaseEnum(String code, String desc, int weight) {
        this.code = code;
        this.desc = desc;
        this.weight = weight;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 该阶段完成时对应的进度百分比锚点。
     *
     * @return 0-100
     */
    public int getWeight() {
        return weight;
    }

    /**
     * 按编码获取枚举。
     *
     * @param code 编码，可为 null
     * @return 匹配的枚举；未匹配返回 null
     */
    public static TaskPhaseEnum of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (TaskPhaseEnum e : values()) {
            if (e.code.equals(normalized)) {
                return e;
            }
        }
        return null;
    }
}
