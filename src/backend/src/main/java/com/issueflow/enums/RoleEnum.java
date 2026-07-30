package com.issueflow.enums;

import lombok.Getter;

/**
 * 角色枚举：提交者 / 开发人员 / 测试人员 / 管理员
 */
@Getter
public enum RoleEnum {

    SUBMITTER("SUBMITTER", "提交者"),
    DEVELOPER("DEVELOPER", "开发人员"),
    TESTER("TESTER", "测试人员"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据角色码获取枚举
     *
     * @param code 角色码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static RoleEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (RoleEnum role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }
}
