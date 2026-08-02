package com.issueflow.enums;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;

/**
 * 字段类型（唯一真源，ARCH §7.1）。
 * <p>数据库 {@code field_config.type} 存枚举 name 字符串（与 dict 风格一致，不存序号）。
 * 前端 {@code fieldControls.js} 的常量与 i18n key 必须与本枚举逐字对齐。</p>
 */
public enum FieldType {

    /** 文本（单行/多行） */
    TEXT,
    /** 数值 */
    NUMBER,
    /** 日期 */
    DATE,
    /** 日期时间 */
    DATETIME,
    /** 字典项（来自 dict 表） */
    DICT,
    /** 引用（来自 ref_source_registry 白名单） */
    REF;

    /**
     * 按枚举名解析（忽略大小写）。
     *
     * @param code 类型字符串（如 "TEXT"）
     * @return 匹配枚举
     * @throws BizException 非法类型
     */
    public static FieldType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "字段类型不能为空");
        }
        try {
            return FieldType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.VALID_ERROR, "非法字段类型: " + code);
        }
    }

    /**
     * 判断是否合法类型字符串。
     *
     * @param code 类型字符串
     * @return true 为合法
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (FieldType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
