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

    /**
     * 判断给定角色码是否为管理员（2026-08-01 魔法值收敛新增）。
     *
     * <p>等价于历史写法 {@code Constants.ROLE_ADMIN.equals(roleCode)}，
     * 语义完全一致（null-safe，null 返回 false）。</p>
     *
     * @param roleCode 角色码，允许 null
     * @return 是 ADMIN 返回 true
     */
    public static boolean isAdmin(String roleCode) {
        return ADMIN.code.equals(roleCode);
    }

    /**
     * 判断给定角色码是否为提交者（2026-08-01 魔法值收敛新增）。
     *
     * @param roleCode 角色码，允许 null
     * @return 是 SUBMITTER 返回 true
     */
    public static boolean isSubmitter(String roleCode) {
        return SUBMITTER.code.equals(roleCode);
    }

    /**
     * 当前枚举的角色码是否与给定字符串相等（null-safe）。
     *
     * @param roleCode 角色码，允许 null
     * @return 相等返回 true
     */
    public boolean matches(String roleCode) {
        return this.code.equals(roleCode);
    }
}
