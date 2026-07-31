package com.issueflow.enums;

/**
 * 系统预设字典类型编码（Phase 7 新增）
 * <p>ISSUE_PRIORITY / ISSUE_SEVERITY 为只读枚举镜像，仅供字典页展示与改名，
 * 业务取值仍走 {@link PriorityEnum} / {@link SeverityEnum}。</p>
 */
public enum DictTypeCodeEnum {

    /** 问题来源（业务真实使用） */
    ISSUE_SOURCE("ISSUE_SOURCE", "问题来源", false),
    /** 问题状态（枚举镜像） */
    ISSUE_STATUS("ISSUE_STATUS", "问题状态", true),
    /** 优先级（枚举镜像，预留） */
    ISSUE_PRIORITY("ISSUE_PRIORITY", "优先级", true),
    /** 严重等级（枚举镜像，预留） */
    ISSUE_SEVERITY("ISSUE_SEVERITY", "严重等级", true);

    private final String code;
    private final String desc;
    /** true 表示该类型是系统枚举镜像，页面只读提示且不允许新增选项 */
    private final boolean mirror;

    DictTypeCodeEnum(String code, String desc, boolean mirror) {
        this.code = code;
        this.desc = desc;
        this.mirror = mirror;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isMirror() {
        return mirror;
    }

    /**
     * 按 code 查枚举。
     *
     * @param code 类型编码
     * @return 匹配项，未匹配返回 null
     */
    public static DictTypeCodeEnum of(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (DictTypeCodeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 判断该类型编码是否为系统枚举镜像。
     *
     * @param code 类型编码
     * @return true 为镜像类型
     */
    public static boolean isMirrorType(String code) {
        DictTypeCodeEnum e = of(code);
        return e != null && e.isMirror();
    }
}
