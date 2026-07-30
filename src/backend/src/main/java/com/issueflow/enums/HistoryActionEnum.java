package com.issueflow.enums;

import lombok.Getter;

/**
 * 操作历史动作枚举
 * CREATE 新建 / CLAIM 认领 / SUBMIT_FIX 提交修复 / VERIFY_PASS 验证通过
 * VERIFY_REJECT 验证回退 / CLOSE 关闭 / REOPEN 重开 / EDIT 编辑
 */
@Getter
public enum HistoryActionEnum {

    CREATE("CREATE", "新建"),
    CLAIM("CLAIM", "认领"),
    SUBMIT_FIX("SUBMIT_FIX", "提交修复"),
    VERIFY_PASS("VERIFY_PASS", "验证通过"),
    VERIFY_REJECT("VERIFY_REJECT", "验证回退"),
    CLOSE("CLOSE", "关闭"),
    REOPEN("REOPEN", "重开"),
    EDIT("EDIT", "编辑");

    private final String code;
    private final String desc;

    HistoryActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据动作码获取枚举
     */
    public static HistoryActionEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (HistoryActionEnum action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        return null;
    }
}
