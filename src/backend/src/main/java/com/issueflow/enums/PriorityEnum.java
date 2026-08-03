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

    /**
     * 默认优先级 = 中。
     *
     * <p><b>【需求一 · 默认值红线】已废弃</b>：优先级必须由用户显式选择，
     * <b>严禁</b>在表单初值、DTO 缺省、Service 兜底等任何「写入路径」上使用本常量，
     * 否则会把「没人选过」的问题统计成中优先级，造成报表失真。
     * 仅保留给历史数据的展示端兜底，新代码请勿引用。</p>
     *
     * @deprecated 写入路径禁止使用，见需求一默认值红线
     */
    @Deprecated
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
