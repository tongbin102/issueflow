package com.issueflow.enums;

import lombok.Getter;

/**
 * 问题状态枚举
 * OPEN=0 待处理 / IN_PROGRESS=1 处理中 / PENDING_VERIFY=2 待验证 / VERIFIED=3 验证通过 / CLOSED=4 已关闭
 */
@Getter
public enum IssueStatusEnum {

    OPEN(0, "待处理"),
    IN_PROGRESS(1, "处理中"),
    PENDING_VERIFY(2, "待验证"),
    VERIFIED(3, "验证通过"),
    CLOSED(4, "已关闭");

    private final Integer code;
    private final String desc;

    IssueStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取枚举
     */
    public static IssueStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IssueStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
