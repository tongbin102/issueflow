package com.issueflow.service;

import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.IssueFieldValue;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.IssueFieldValueMapper;
import com.issueflow.util.DateTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自定义字段日期「详情出参 → 表单回填 → 原样回传」闭环回归测试（BUG-1，Round 2）。
 *
 * <p>前端 {@code IssueForm.applyInitial()} 直接 {@code model[code] = cf[code]} 不做归一化，
 * 用户若不重新选择日期，提交时回传的就是详情接口原样吐出的字符串。
 * 因此「后端能否吃回自己吐出的值」是这条链路的硬性要求。</p>
 *
 * <p>本类用 {@link DateTimeUtils} 生成与 {@code extractFieldValue} 完全一致的出参，
 * 再喂回 {@code saveValues}，形成真实闭环；同时覆盖历史脏数据（带 'T'）的兼容性。</p>
 */
@DisplayName("自定义字段日期闭环（详情出参 → 原样回传）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomFieldRoundTripTest {

    private static final Long ISSUE_ID = 2002L;
    private static final LocalDateTime STORED_DATE = LocalDateTime.of(2026, 8, 2, 0, 0, 0);
    private static final LocalDateTime STORED_DATETIME = LocalDateTime.of(2026, 8, 2, 14, 30, 0);

    @Mock
    private IssueFieldValueMapper valueMapper;

    @Mock
    private FieldConfigMapper configMapper;

    @InjectMocks
    private IssueFieldValueService service;

    private static FieldConfig cfg(String code, String type) {
        FieldConfig c = new FieldConfig();
        c.setId((long) code.hashCode());
        c.setCode(code);
        c.setName(code);
        c.setType(type);
        c.setIsSystem(0);
        c.setEnabled(1);
        c.setTypeScope("GLOBAL");
        return c;
    }

    private void givenCustomConfigs(FieldConfig... configs) {
        when(configMapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(configs)));
    }

    private static Map<String, Object> map(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    /** 捕获落库实体 */
    private IssueFieldValue captureInserted() {
        ArgumentCaptor<IssueFieldValue> captor = ArgumentCaptor.forClass(IssueFieldValue.class);
        verify(valueMapper).insert(captor.capture());
        return captor.getValue();
    }

    @BeforeEach
    void setUp() {
        when(valueMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    // ------------------------------------------------------------------ DATE

    @Test
    @DisplayName("DATE 闭环：详情出参 yyyy-MM-dd 原样回传可保存，且日期值不漂移")
    void dateRoundTripIsClosed() {
        givenCustomConfigs(cfg("expect_date", "DATE"));
        String detailOut = DateTimeUtils.formatDate(STORED_DATE);
        assertThat(detailOut).isEqualTo("2026-08-02");

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("expect_date", detailOut)))
                .doesNotThrowAnyException();

        // 存回去的值必须与原始存量一致（当天零点），否则闭环会逐次漂移
        assertThat(captureInserted().getValueDate()).isEqualTo(STORED_DATE);
    }

    @ParameterizedTest(name = "DATE 接受 [{0}]")
    @ValueSource(strings = {
            "2026-08-02",              // 修复后的标准出参
            "2026-08-02T00:00:00",     // 历史脏数据 / 旧前端缓存（BUG-1 原触发形态）
            "2026-08-02 00:00:00",     // 空格分隔变体
            "2026-08-02T13:45:30"      // 带时间的 DATE 值，应截断到日期
    })
    @DisplayName("DATE 兼容多种入参形态，一律归一化到当天零点")
    void dateAcceptsAllVariantsAndNormalizes(String raw) {
        givenCustomConfigs(cfg("expect_date", "DATE"));

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("expect_date", raw)))
                .doesNotThrowAnyException();

        IssueFieldValue saved = captureInserted();
        assertThat(saved.getValueDate()).isEqualTo(STORED_DATE);
        assertThat(saved.getValueDate().getHour()).isZero();
    }

    // -------------------------------------------------------------- DATETIME

    @Test
    @DisplayName("DATETIME 闭环：详情出参 yyyy-MM-dd HH:mm:ss 原样回传可保存，时分秒不丢")
    void dateTimeRoundTripIsClosed() {
        givenCustomConfigs(cfg("deploy_at", "DATETIME"));
        String detailOut = DateTimeUtils.formatDateTime(STORED_DATETIME);
        assertThat(detailOut).isEqualTo("2026-08-02 14:30:00");

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("deploy_at", detailOut)))
                .doesNotThrowAnyException();

        assertThat(captureInserted().getValueDate()).isEqualTo(STORED_DATETIME);
    }

    @ParameterizedTest(name = "DATETIME 接受 [{0}]")
    @ValueSource(strings = {
            "2026-08-02 14:30:00",     // 修复后的标准出参
            "2026-08-02T14:30:00"      // 历史脏数据 / ISO 形态
    })
    @DisplayName("DATETIME 兼容空格与 'T' 两种分隔符，时分秒保持一致")
    void dateTimeAcceptsBothSeparators(String raw) {
        givenCustomConfigs(cfg("deploy_at", "DATETIME"));

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("deploy_at", raw)))
                .doesNotThrowAnyException();

        assertThat(captureInserted().getValueDate()).isEqualTo(STORED_DATETIME);
    }

    @Test
    @DisplayName("DATETIME 收到纯日期串时回退到当天零点，不抛异常")
    void dateTimeAcceptsDateOnly() {
        givenCustomConfigs(cfg("deploy_at", "DATETIME"));

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("deploy_at", "2026-08-02")))
                .doesNotThrowAnyException();

        assertThat(captureInserted().getValueDate()).isEqualTo(STORED_DATE);
    }

    // ------------------------------------------------------------ 非法输入仍需拦截

    @ParameterizedTest(name = "DATE 拒绝非法值 [{0}]")
    @ValueSource(strings = {"2026-13-45", "not-a-date", "2026/08/02"})
    @DisplayName("兜底放宽后，真正的非法日期仍必须被拒绝（不能因兼容而失守）")
    void dateStillRejectsGarbage(String raw) {
        givenCustomConfigs(cfg("expect_date", "DATE"));

        assertThatCode(() -> service.saveValues(ISSUE_ID, map("expect_date", raw)))
                .isInstanceOf(com.issueflow.common.BizException.class);
    }
}
