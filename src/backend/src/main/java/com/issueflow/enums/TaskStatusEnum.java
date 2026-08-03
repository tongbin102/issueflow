package com.issueflow.enums;

/**
 * 数据管理任务状态（备份 / 恢复共用，Phase10）。
 *
 * <p>状态流转：{@code PENDING → RUNNING → SUCCESS | FAILED | CANCELED}。
 * 终态一旦写入不可逆转。</p>
 */
public enum TaskStatusEnum {

    /** 已受理，尚未进入线程池执行 */
    PENDING("PENDING", "排队中"),
    /** 执行中 */
    RUNNING("RUNNING", "执行中"),
    /** 成功（终态） */
    SUCCESS("SUCCESS", "成功"),
    /** 失败（终态） */
    FAILED("FAILED", "失败"),
    /** 已取消 / 超时中断（终态） */
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String desc;

    TaskStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按编码获取枚举。
     *
     * @param code 编码，可为 null
     * @return 匹配的枚举；未匹配返回 null
     */
    public static TaskStatusEnum of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (TaskStatusEnum e : values()) {
            if (e.code.equals(normalized)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 校验编码是否合法。
     *
     * @param code 编码
     * @return true 合法
     */
    public static boolean isValid(String code) {
        return of(code) != null;
    }

    /**
     * 是否终态（不可再变更）。
     *
     * @return true 终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }
}
