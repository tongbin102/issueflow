package com.issueflow.util;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQL 标识符白名单校验单元测试（Phase9 T06-QA，SQL 注入第一道防线）。
 *
 * <p>{@code SqlIdentifier} 是全项目唯一允许把标识符拼进 {@code ${}} 的关卡
 * （{@code RefSourceRegistryMapper.selectOptions}）。一旦此处被绕过，
 * {@code FROM ${tableName}} 即可被注入任意 SQL，故本类以攻击载荷为主用例。</p>
 */
@DisplayName("SqlIdentifier SQL 标识符校验")
class SqlIdentifierTest {

    @ParameterizedTest(name = "合法标识符 \"{0}\" 原样放行")
    @ValueSource(strings = {"issue", "issue_field_value", "_tmp", "a", "T1", "project_id",
            "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcd"})
    @DisplayName("合法标识符原样返回（字母/下划线开头，1~64 位）")
    void checkAcceptsLegalIdentifiers(String id) {
        assertThat(SqlIdentifier.check(id)).isEqualTo(id);
    }

    @ParameterizedTest(name = "注入载荷 \"{0}\" 必须被拒")
    @ValueSource(strings = {
            "user; DROP TABLE user",
            "user;DROP TABLE user;--",
            "user WHERE 1=1",
            "1=1 OR 1=1",
            "user'",
            "user\"",
            "user`",
            "user--",
            "user/*x*/",
            "user)",
            "(SELECT 1)",
            "db.user",
            "user table",
            "用户表",
            "9user",
            "-user",
            " user",
            "user ",
            "UNION SELECT password FROM user"
    })
    @DisplayName("含空格/分号/引号/注释/点号/非 ASCII/数字开头的标识符一律拒绝")
    void checkRejectsInjectionPayloads(String id) {
        assertThatThrownBy(() -> SqlIdentifier.check(id))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_ILLEGAL_IDENTIFIER.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null / 空串拒绝")
    void checkRejectsNullAndEmpty(String id) {
        assertThatThrownBy(() -> SqlIdentifier.check(id)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超长（65 位）标识符拒绝，边界 64 位放行")
    void checkEnforcesLengthBoundary() {
        String len64 = "a".repeat(64);
        String len65 = "a".repeat(65);
        assertThat(SqlIdentifier.check(len64)).isEqualTo(len64);
        assertThatThrownBy(() -> SqlIdentifier.check(len65)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("checkOrDefault：入参为空回落兜底值；入参非法仍抛异常（不静默降级）")
    void checkOrDefaultFallsBackOnlyWhenBlank() {
        assertThat(SqlIdentifier.checkOrDefault(null, "id")).isEqualTo("id");
        assertThat(SqlIdentifier.checkOrDefault("  ", "id")).isEqualTo("id");
        assertThat(SqlIdentifier.checkOrDefault("sort", "id")).isEqualTo("sort");
        assertThatThrownBy(() -> SqlIdentifier.checkOrDefault("id; DROP TABLE user", "id"))
                .isInstanceOf(BizException.class);
        // 兜底值本身非法也必须炸，避免脏兜底进 SQL
        assertThatThrownBy(() -> SqlIdentifier.checkOrDefault(null, "id;--"))
                .isInstanceOf(BizException.class);
    }
}
