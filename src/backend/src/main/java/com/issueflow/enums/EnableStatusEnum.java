package com.issueflow.enums;

import lombok.Getter;

/**
 * 通用启用状态枚举（2026-08-01 魔法值收敛）。
 *
 * <p>收敛 user / project / dict_item / issue_type / scheduled_task 等表中
 * 语义一致的 {@code status == 0 / 1} 裸整型判断。</p>
 *
 * <p><b>取值口径与库中 code 严格一致</b>：{@code 0 = 停用}，{@code 1 = 启用}。
 * 与 {@link IssueStatusEnum}（问题流转状态 0..4）是<b>不同维度</b>，切勿混用。</p>
 */
@Getter
public enum EnableStatusEnum {

    /** 停用 / 禁用 */
    DISABLED(0, "停用"),

    /** 启用 / 正常 */
    ENABLED(1, "启用");

    private final Integer code;
    private final String desc;

    EnableStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取枚举。
     *
     * @param code 状态码，允许 null
     * @return 匹配的枚举，未匹配（含 null）返回 null
     */
    public static EnableStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (EnableStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 是否为「启用」状态。
     *
     * @param code 状态码，允许 null
     * @return code 等于 1 返回 true；null 或其它值返回 false
     */
    public static boolean isEnabled(Integer code) {
        return ENABLED.code.equals(code);
    }

    /**
     * 是否为「停用」状态。
     *
     * @param code 状态码，允许 null
     * @return code 等于 0 返回 true；null 或其它值返回 false
     */
    public static boolean isDisabled(Integer code) {
        return DISABLED.code.equals(code);
    }
}
