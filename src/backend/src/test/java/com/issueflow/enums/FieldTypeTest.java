package com.issueflow.enums;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 字段类型枚举单元测试（Phase9 T06-QA）。
 *
 * <p>覆盖：6 种合法类型解析、大小写/空白容错、非法输入兜底行为、{@code isValid} 判定。
 * 该枚举是「字段类型唯一真源」，与前端 {@code utils/fieldControls.js} 的 FIELD_TYPES
 * 必须逐字对齐，故此处同时锁定枚举成员集合，防止后续增删类型时前后端失配。</p>
 */
@DisplayName("FieldType 字段类型枚举")
class FieldTypeTest {

    @Test
    @DisplayName("枚举成员恰为 6 种且顺序稳定（与前端 FIELD_TYPES 契约）")
    void enumMembersAreExactlySix() {
        assertThat(FieldType.values())
                .extracting(Enum::name)
                .containsExactly("TEXT", "NUMBER", "DATE", "DATETIME", "DICT", "REF");
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") = {1}")
    @CsvSource({
            "TEXT,TEXT",
            "NUMBER,NUMBER",
            "DATE,DATE",
            "DATETIME,DATETIME",
            "DICT,DICT",
            "REF,REF"
    })
    @DisplayName("6 种合法类型均可正确解析")
    void fromCodeParsesAllSixTypes(String code, FieldType expected) {
        assertThat(FieldType.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 归一化为 DATETIME")
    @ValueSource(strings = {"datetime", "DateTime", "  DATETIME  ", "\tdatetime\n"})
    @DisplayName("大小写与首尾空白均被归一化")
    void fromCodeIsCaseInsensitiveAndTrims(String code) {
        assertThat(FieldType.fromCode(code)).isEqualTo(FieldType.DATETIME);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("null / 空 / 空白抛 VALID_ERROR「字段类型不能为空」")
    void fromCodeRejectsBlank(String code) {
        assertThatThrownBy(() -> FieldType.fromCode(code))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("字段类型不能为空")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.VALID_ERROR.getCode());
    }

    @ParameterizedTest(name = "非法类型 \"{0}\" 被拒")
    @ValueSource(strings = {"BOOLEAN", "text2", "TEXTAREA", "SELECT", "'; DROP TABLE field_config; --"})
    @DisplayName("非法类型抛 VALID_ERROR 且回显原始入参，不静默兜底")
    void fromCodeRejectsUnknown(String code) {
        assertThatThrownBy(() -> FieldType.fromCode(code))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法字段类型")
                .hasMessageContaining(code);
    }

    @Test
    @DisplayName("isValid 与 fromCode 判定一致，且不抛异常")
    void isValidMatchesFromCode() {
        for (FieldType t : FieldType.values()) {
            assertThat(FieldType.isValid(t.name())).isTrue();
            assertThat(FieldType.isValid(t.name().toLowerCase())).isTrue();
        }
        assertThat(FieldType.isValid(null)).isFalse();
        assertThat(FieldType.isValid("")).isFalse();
        assertThat(FieldType.isValid("   ")).isFalse();
        assertThat(FieldType.isValid("BOOLEAN")).isFalse();
    }
}
