package com.issueflow.util;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;

import java.util.regex.Pattern;

/**
 * SQL 标识符正则校验单一出口（ARCH §7.5）。
 * <p>全项目<b>唯一</b>允许将标识符拼入 {@code ${}} 的位置是 {@code RefSourceRegistryMapper}；
 * 所有待拼接的标识符（表名/列名）必须经本方法校验后，方可进入动态 SQL。</p>
 *
 * <p>校验规则：首字符为字母或下划线，后续为字母/数字/下划线，长度 1~64。</p>
 */
public final class SqlIdentifier {

    private SqlIdentifier() {
    }

    /** 允许标识符：^[A-Za-z_][A-Za-z0-9_]{0,63}$ */
    private static final Pattern P = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    /**
     * 校验标识符合法；不合法（含 {@code null}、空、含特殊字符、超长）直接抛业务异常。
     *
     * @param id 待校验标识符
     * @return 原值（校验通过）
     * @throws BizException 非法标识符
     */
    public static String check(String id) {
        if (id == null || !P.matcher(id).matches()) {
            throw new BizException(ResultCode.REF_SOURCE_ILLEGAL_IDENTIFIER);
        }
        return id;
    }

    /**
     * 校验标识符；为空时返回兜底值（不让 {@code null} 进入 SQL）。
     *
     * @param id          待校验标识符（可空）
     * @param defaultVal 兜底值（必须合法）
     * @return 原值（校验通过）或兜底值
     * @throws BizException 兜底值非法
     */
    public static String checkOrDefault(String id, String defaultVal) {
        if (id == null || id.isBlank()) {
            return check(defaultVal);
        }
        return check(id);
    }
}
