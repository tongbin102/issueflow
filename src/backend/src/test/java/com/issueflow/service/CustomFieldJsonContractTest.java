package com.issueflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issueflow.dto.resp.IssueDetailVO;
import com.issueflow.util.DateTimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义字段 JSON 出参契约测试（BUG-1 回归，Round 2）。
 *
 * <p>背景：{@code IssueDetailVO.customFields} 声明为 {@code Map<String,Object>}，
 * VO 上逐字段的 {@code @JsonFormat} <b>不会</b>作用于 Map 的 value（泛型擦除）。
 * 若把 {@code LocalDateTime} 直接放进 Map，Jackson 会按全局默认输出带 'T' 的 ISO 串，
 * 与前端 {@code DynamicField.vue} 的 {@code valueFormat} 契约不符，
 * 且回填后原样提交会被后端 {@code toDate} 拒绝（BUG-1）。</p>
 *
 * <p>修复方案：{@code IssueService.extractFieldValue} 对 DATE/DATETIME 先经
 * {@link DateTimeUtils} 格式化为字符串再放入 Map。本类校验修复后的出参形态，
 * 并保留一条「反面哨兵」用例说明为什么必须格式化。</p>
 */
class CustomFieldJsonContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    private static IssueDetailVO voWith(String code, Object value) {
        Map<String, Object> customFields = new LinkedHashMap<>();
        customFields.put(code, value);
        IssueDetailVO vo = new IssueDetailVO();
        vo.setCustomFields(customFields);
        return vo;
    }

    @Test
    @DisplayName("DATE 出参为纯日期串 yyyy-MM-dd，与前端 valueFormat 一致")
    void dateValueSerializesAsPlainDate() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            // 模拟 extractFieldValue(DATE) 的产物
            Object out = DateTimeUtils.formatDate(LocalDateTime.of(2026, 8, 2, 0, 0, 0));
            String json = mapper.writeValueAsString(voWith("expect_date", out));

            assertThat(json).contains("\"expect_date\":\"2026-08-02\"");
            assertThat(json).doesNotContain("2026-08-02T");
        });
    }

    @Test
    @DisplayName("DATETIME 出参为空格分隔 yyyy-MM-dd HH:mm:ss，与前端 valueFormat 一致")
    void dateTimeValueSerializesWithSpaceSeparator() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            Object out = DateTimeUtils.formatDateTime(LocalDateTime.of(2026, 8, 2, 14, 30, 0));
            String json = mapper.writeValueAsString(voWith("deploy_at", out));

            assertThat(json).contains("\"deploy_at\":\"2026-08-02 14:30:00\"");
            assertThat(json).doesNotContain("2026-08-02T14:30:00");
        });
    }

    @Test
    @DisplayName("反面哨兵：若把 LocalDateTime 直接塞进 Map，仍会输出带 'T' 的串——说明格式化不可省略")
    void rawLocalDateTimeInMapStillBreaksContract() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            String json = mapper.writeValueAsString(
                    voWith("raw_date", LocalDateTime.of(2026, 8, 2, 0, 0, 0)));
            // 这正是 BUG-1 的根因形态，作为回归护栏保留
            assertThat(json).contains("\"raw_date\":\"2026-08-02T00:00:00\"");
        });
    }

    @Test
    @DisplayName("对照组：内置 closedAt 字段有 @JsonFormat，输出空格分隔（非 Map 值，不受擦除影响）")
    void builtInDateTimeFieldUsesJsonFormatPattern() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            IssueDetailVO vo = new IssueDetailVO();
            vo.setClosedAt(LocalDateTime.of(2026, 8, 2, 14, 30, 0));
            String json = mapper.writeValueAsString(vo);
            assertThat(json).contains("\"closedAt\":\"2026-08-02 14:30:00\"");
        });
    }

    @Test
    @DisplayName("TEXT / NUMBER 自定义字段出参形态稳定，未被本次修复影响")
    void textAndNumberValuesAreStable() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            Map<String, Object> customFields = new LinkedHashMap<>();
            customFields.put("remark", "hello");
            customFields.put("score", new BigDecimal("12.50"));
            IssueDetailVO vo = new IssueDetailVO();
            vo.setCustomFields(customFields);

            String json = mapper.writeValueAsString(vo);
            assertThat(json).contains("\"remark\":\"hello\"");
            assertThat(json).contains("\"score\":12.50");
        });
    }

    @Test
    @DisplayName("DateTimeUtils.formatDate/formatDateTime 对 null 返回 null，不产生 \"null\" 字符串")
    void formattersHandleNull() {
        assertThat(DateTimeUtils.formatDate(null)).isNull();
        assertThat(DateTimeUtils.formatDateTime(null)).isNull();
    }
}
