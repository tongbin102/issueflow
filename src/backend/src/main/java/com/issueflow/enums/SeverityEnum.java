package com.issueflow.enums;

import lombok.Getter;

/**
 * 缺陷严重等级枚举
 * FATAL=0 致命 / SERIOUS=1 严重 / NORMAL=2 一般 / MINOR=3 轻微
 */
@Getter
public enum SeverityEnum {

    FATAL(0, "致命"),
    SERIOUS(1, "严重"),
    NORMAL(2, "一般"),
    MINOR(3, "轻微");

    private final Integer code;
    private final String desc;

    SeverityEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据等级码获取枚举
     */
    public static SeverityEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SeverityEnum severity : values()) {
            if (severity.code.equals(code)) {
                return severity;
            }
        }
        return null;
    }
}
