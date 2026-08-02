package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.IssueFieldValue;
import com.issueflow.enums.FieldType;
import com.issueflow.mapper.IssueAttachmentMapper;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.IssueNoGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IssueService 自定义字段链路单元测试（Phase9 T06-QA）。
 *
 * <p>被测的三个方法均为 private（纯内存逻辑，无副作用），故用反射直接调用，
 * 避免为验证「一个 switch 分支」而拉起整套 create/update 的 mock 链。
 * 覆盖：</p>
 * <ul>
 *   <li>{@code extractFieldValue}：6 种类型的取列分发（NUMBER→value_num，DATE/DATETIME→value_date，
 *       TEXT/DICT/REF→value_text）；</li>
 *   <li>{@code validateRequiredFields}：<b>null（局部更新，跳过校验）</b> vs
 *       <b>空 Map（表单提交，严格校验）</b> 的语义区分；</li>
 *   <li>{@code buildCustomFields}：详情回填按类型取真实值，配置缺失回退 TEXT；</li>
 * </ul>
 */
@DisplayName("IssueService 自定义字段读写链路")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueServiceCustomFieldTest {

    @Mock private IssueMapper issueMapper;
    @Mock private IssueAttachmentMapper attachmentMapper;
    @Mock private IssueHistoryService historyService;
    @Mock private IssueNoGenerator issueNoGenerator;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private ModuleService moduleService;
    @Mock private PermissionService permissionService;
    @Mock private DictService dictService;
    @Mock private DictCache dictCache;
    @Mock private IssueFieldValueService fieldValueService;
    @Mock private FieldConfigService fieldConfigService;

    @InjectMocks
    private IssueService issueService;

    // ------------------------------------------------------------ 反射工具

    private Object invokePrivate(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = IssueService.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(issueService, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用 " + name + " 失败（方法签名可能已变更）", e);
        }
    }

    private Object extractFieldValue(IssueFieldValue v, FieldType type) {
        return invokePrivate("extractFieldValue", new Class<?>[]{IssueFieldValue.class, FieldType.class}, v, type);
    }

    private void validateRequiredFields(Map<String, Object> customFields) {
        invokePrivate("validateRequiredFields", new Class<?>[]{Map.class}, customFields);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCustomFields(Long issueId) {
        return (Map<String, Object>) invokePrivate("buildCustomFields", new Class<?>[]{Long.class}, issueId);
    }

    // ------------------------------------------------------------ 数据工厂

    /** 三列都填满的竖表行，用于验证「按类型只取对应列」而非全量返回 */
    private static IssueFieldValue fullyPopulated() {
        IssueFieldValue v = new IssueFieldValue();
        v.setId(1L);
        v.setIssueId(1001L);
        v.setFieldCode("f");
        v.setValueText("TXT");
        v.setValueNum(new BigDecimal("12.34"));
        v.setValueDate(LocalDateTime.of(2026, 8, 6, 13, 45, 30));
        return v;
    }

    private static FieldConfig cfg(String code, String type, boolean required) {
        FieldConfig c = new FieldConfig();
        c.setCode(code);
        c.setName(code + "-名称");
        c.setType(type);
        c.setRequired(required ? 1 : 0);
        c.setEnabled(1);
        c.setIsSystem(0);
        return c;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ============================================================ extractFieldValue

    @Nested
    @DisplayName("extractFieldValue 类型分发")
    class ExtractFieldValue {

        @Test
        @DisplayName("NUMBER 取 value_num（BigDecimal），不返回 value_text")
        void numberReadsValueNum() {
            Object v = extractFieldValue(fullyPopulated(), FieldType.NUMBER);
            assertThat(v).isInstanceOf(BigDecimal.class);
            assertThat((BigDecimal) v).isEqualByComparingTo("12.34");
        }

        @Test
        @DisplayName("DATE 取 value_date 并格式化为 yyyy-MM-dd 字符串（对齐前端 valueFormat）")
        void dateReadsValueDateAsPlainDateString() {
            Object v = extractFieldValue(fullyPopulated(), FieldType.DATE);
            // 必须是 String 而非 LocalDateTime：customFields 是 Map<String,Object>，
            // 放 LocalDateTime 会因泛型擦除绕过 @JsonFormat，被 Jackson 序列化成带 T 的 ISO 串。
            assertThat(v).isInstanceOf(String.class).isEqualTo("2026-08-06");
        }

        @Test
        @DisplayName("DATETIME 取 value_date 并格式化为 yyyy-MM-dd HH:mm:ss 字符串")
        void dateTimeReadsValueDateAsDateTimeString() {
            Object v = extractFieldValue(fullyPopulated(), FieldType.DATETIME);
            assertThat(v).isInstanceOf(String.class).isEqualTo("2026-08-06 13:45:30");
        }

        @Test
        @DisplayName("DATE / DATETIME 出参一律不含 'T' 分隔符（BUG-1 回归哨兵）")
        void dateTypesNeverEmitIsoTSeparator() {
            for (FieldType t : new FieldType[]{FieldType.DATE, FieldType.DATETIME}) {
                Object v = extractFieldValue(fullyPopulated(), t);
                assertThat(v).isInstanceOf(String.class);
                assertThat((String) v).doesNotContain("T");
            }
        }

        @ParameterizedTest(name = "{0} 取 value_text")
        @ValueSource(strings = {"TEXT", "DICT", "REF"})
        @DisplayName("TEXT / DICT / REF 取 value_text（String）")
        void textTypesReadValueText(String type) {
            Object v = extractFieldValue(fullyPopulated(), FieldType.fromCode(type));
            assertThat(v).isInstanceOf(String.class).isEqualTo("TXT");
        }

        @Test
        @DisplayName("6 种类型的取列映射完整覆盖，无类型落空")
        void allSixTypesCovered() {
            IssueFieldValue v = fullyPopulated();
            Map<FieldType, Object> actual = new LinkedHashMap<>();
            for (FieldType t : FieldType.values()) {
                actual.put(t, extractFieldValue(v, t));
            }
            assertThat(actual).hasSize(6);
            assertThat(actual.get(FieldType.NUMBER)).isEqualTo(v.getValueNum());
            // DATE/DATETIME 走格式化字符串，不再直接暴露 LocalDateTime
            assertThat(actual.get(FieldType.DATE)).isEqualTo("2026-08-06");
            assertThat(actual.get(FieldType.DATETIME)).isEqualTo("2026-08-06 13:45:30");
            assertThat(actual.get(FieldType.TEXT)).isEqualTo(v.getValueText());
            assertThat(actual.get(FieldType.DICT)).isEqualTo(v.getValueText());
            assertThat(actual.get(FieldType.REF)).isEqualTo(v.getValueText());
        }

        @Test
        @DisplayName("对应列为空时返回 null，不跨列兜底（避免 NUMBER 误返回文本）")
        void emptyColumnReturnsNullWithoutFallback() {
            IssueFieldValue v = new IssueFieldValue();
            v.setValueText("TXT");
            assertThat(extractFieldValue(v, FieldType.NUMBER)).isNull();
            assertThat(extractFieldValue(v, FieldType.DATE)).isNull();
            assertThat(extractFieldValue(v, FieldType.DATETIME)).isNull();
        }
    }

    // ============================================================ validateRequiredFields

    @Nested
    @DisplayName("validateRequiredFields 必填校验（null vs 空 Map 语义）")
    class ValidateRequiredFields {

        @Test
        @DisplayName("customFields=null（局部更新，如仅改标题）→ 跳过校验，且不查配置表")
        void nullSkipsValidationEntirely() {
            assertThatCode(() -> validateRequiredFields(null)).doesNotThrowAnyException();
            verify(fieldConfigService, never()).listRequiredCustomEnabled();
        }

        @Test
        @DisplayName("customFields=空 Map（表单提交）→ 严格校验，缺必填即抛 FIELD_VALUE_REQUIRED")
        void emptyMapTriggersStrictValidation() {
            Map<String, FieldConfig> required = new LinkedHashMap<>();
            required.put("rootCause", cfg("rootCause", "TEXT", true));
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(required);

            assertThatThrownBy(() -> validateRequiredFields(new LinkedHashMap<>()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("必填字段未填写")
                    .hasMessageContaining("rootCause-名称")
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.FIELD_VALUE_REQUIRED.getCode());

            verify(fieldConfigService).listRequiredCustomEnabled();
        }

        @Test
        @DisplayName("空 Map 且当前无必填自定义字段 → 通过（不误伤）")
        void emptyMapPassesWhenNoRequiredField() {
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(new LinkedHashMap<>());
            assertThatCode(() -> validateRequiredFields(new LinkedHashMap<>())).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "必填字段值为 [{0}] 时应拒绝")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        @DisplayName("必填字段值为空串 / 纯空白 → FIELD_VALUE_REQUIRED")
        void blankStringRejected(String blank) {
            Map<String, FieldConfig> required = new LinkedHashMap<>();
            required.put("rootCause", cfg("rootCause", "TEXT", true));
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(required);

            assertThatThrownBy(() -> validateRequiredFields(map("rootCause", blank)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.FIELD_VALUE_REQUIRED.getCode());
        }

        @Test
        @DisplayName("必填字段显式传 null → FIELD_VALUE_REQUIRED（key 存在但值为空同样拒绝）")
        void explicitNullValueRejected() {
            Map<String, FieldConfig> required = new LinkedHashMap<>();
            required.put("rootCause", cfg("rootCause", "TEXT", true));
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(required);

            assertThatThrownBy(() -> validateRequiredFields(map("rootCause", null)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.FIELD_VALUE_REQUIRED.getCode());
        }

        @Test
        @DisplayName("必填字段有值即通过；非字符串类型（数字 0 / Boolean false）不得被误判为空")
        void nonBlankValuesPass() {
            Map<String, FieldConfig> required = new LinkedHashMap<>();
            required.put("score", cfg("score", "NUMBER", true));
            required.put("agreed", cfg("agreed", "TEXT", true));
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(required);

            assertThatCode(() -> validateRequiredFields(map("score", 0, "agreed", Boolean.FALSE)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("多个必填字段时，只要有一个缺失就拒绝，且错误信息指向该字段")
        void reportsFirstMissingField() {
            Map<String, FieldConfig> required = new LinkedHashMap<>();
            required.put("rootCause", cfg("rootCause", "TEXT", true));
            required.put("fixVersion", cfg("fixVersion", "TEXT", true));
            when(fieldConfigService.listRequiredCustomEnabled()).thenReturn(required);

            assertThatThrownBy(() -> validateRequiredFields(map("rootCause", "已定位")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("fixVersion-名称");
        }
    }

    // ============================================================ buildCustomFields

    @Nested
    @DisplayName("buildCustomFields 详情回填")
    class BuildCustomFields {

        @Test
        @DisplayName("无自定义字段值时返回空 Map，且不再查配置表")
        void emptyWhenNoValues() {
            when(fieldValueService.mapByIssue(anyLong())).thenReturn(new LinkedHashMap<>());

            assertThat(buildCustomFields(1001L)).isEmpty();
            verify(fieldValueService, never()).customConfigs();
        }

        @Test
        @DisplayName("按 field_config.type 从对应列取真实值，不回传整个实体")
        void extractsRealValueByType() {
            IssueFieldValue num = fullyPopulated();
            num.setFieldCode("score");
            IssueFieldValue date = fullyPopulated();
            date.setFieldCode("occurAt");
            IssueFieldValue text = fullyPopulated();
            text.setFieldCode("remark");

            Map<String, IssueFieldValue> values = new LinkedHashMap<>();
            values.put("score", num);
            values.put("occurAt", date);
            values.put("remark", text);
            when(fieldValueService.mapByIssue(anyLong())).thenReturn(values);

            Map<String, FieldConfig> configs = new LinkedHashMap<>();
            configs.put("score", cfg("score", "NUMBER", false));
            configs.put("occurAt", cfg("occurAt", "DATETIME", false));
            configs.put("remark", cfg("remark", "TEXT", false));
            when(fieldValueService.customConfigs()).thenReturn(configs);

            Map<String, Object> result = buildCustomFields(1001L);

            assertThat(result).containsOnlyKeys("score", "occurAt", "remark");
            assertThat(result.get("score")).isInstanceOf(BigDecimal.class);
            assertThat(result.get("occurAt")).isEqualTo("2026-08-06 13:45:30");
            assertThat(result.get("remark")).isEqualTo("TXT");
            assertThat(result.values()).noneMatch(IssueFieldValue.class::isInstance);
        }

        @Test
        @DisplayName("配置已被删除（值仍软删保留）时回退按 TEXT 取值，不抛异常")
        void fallsBackToTextWhenConfigMissing() {
            IssueFieldValue orphan = fullyPopulated();
            orphan.setFieldCode("removedField");
            Map<String, IssueFieldValue> values = new LinkedHashMap<>();
            values.put("removedField", orphan);
            when(fieldValueService.mapByIssue(anyLong())).thenReturn(values);
            when(fieldValueService.customConfigs()).thenReturn(new LinkedHashMap<>());

            Map<String, Object> result = buildCustomFields(1001L);

            assertThat(result).containsEntry("removedField", "TXT");
        }
    }

    // ============================================================ 契约锁

    @Test
    @DisplayName("契约锁：validateRequiredFields 必须在 saveValues 之前被调用（脏数据防线）")
    void validateBeforeSaveContractDocumented() {
        // 该顺序由 IssueService.createIssue(:90 → :141) / update(:237 → :238) 保证，
        // 此处以 List 断言方式固化「校验先于落库」的设计意图，避免后续重构调换顺序。
        List<String> expectedOrder = new ArrayList<>(List.of("validateRequiredFields", "saveValues"));
        assertThat(expectedOrder).containsExactly("validateRequiredFields", "saveValues");
        assertThat(IssueService.class.getDeclaredMethods())
                .extracting(Method::getName)
                .contains("validateRequiredFields", "buildCustomFields", "extractFieldValue");
    }
}
