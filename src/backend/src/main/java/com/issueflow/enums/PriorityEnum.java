package com.issueflow.enums;

/**
 * 问题优先级（Phase 7 新增，决策 B：固定枚举，仿 SeverityEnum）
 * <p>字典中的 ISSUE_PRIORITY 类型仅为只读镜像，业务读取一律走本枚举。</p>
 */
public enum PriorityEnum {

    /** 高 */
    HIGH(0, "高"),
    /** 中（默认） */
    MEDIUM(1, "中"),
    /** 低 */
    LOW(2, "低");

    private final int code;
    private final String desc;

    PriorityEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 默认优先级 = 中 */
    public static final int DEFAULT_CODE = 1;

    /**
     * 按数值获取枚举。
     *
     * @param code 数值，可为 null
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PriorityEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PriorityEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }

    /**
     * 按数值取中文描述。
     *
     * @param code 数值，可为 null
     * @return 描述；未匹配返回空串
     */
    public static String descOf(Integer code) {
        PriorityEnum e = of(code);
        return e == null ? "" : e.getDesc();
    }

    /**
     * 校验数值是否合法。
     *
     * @param code 数值
     * @return true 合法
     */
    public static boolean isValid(Integer code) {
        return of(code) != null;
    }
}
