package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.IssueFieldValue;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.IssueFieldValueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自定义字段值服务单元测试（Phase9 T06-QA）。
 *
 * <p>覆盖 {@code saveValues} 的四条核心契约：
 * ① 只处理自定义字段（is_system=0，配置表按 is_system=0 过滤，未命中的 key 静默跳过）；
 * ② 空值走软删（{@code deleteById}）而非插入；
 * ③ upsert 幂等（先软删旧值再插入，连续两次保存结果一致）；
 * ④ 按 {@code field_config.type} 落到正确的列（value_num / value_date / value_text）。</p>
 */
@DisplayName("IssueFieldValueService 自定义字段值读写")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueFieldValueServiceTest {

    private static final Long ISSUE_ID = 1001L;

    @Mock
    private IssueFieldValueMapper valueMapper;

    @Mock
    private FieldConfigMapper configMapper;

    @InjectMocks
    private IssueFieldValueService service;

    /** 构造一个自定义字段配置（is_system=0，enabled=1） */
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

    private static IssueFieldValue existing(Long id, String code) {
        IssueFieldValue v = new IssueFieldValue();
        v.setId(id);
        v.setIssueId(ISSUE_ID);
        v.setFieldCode(code);
        return v;
    }

    /** 让 configMapper.selectList 返回给定的自定义字段配置集合 */
    private void givenCustomConfigs(FieldConfig... configs) {
        when(configMapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(configs)));
    }

    /** 单值 Map 便捷构造（保留插入顺序） */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @BeforeEach
    void setUp() {
        when(valueMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    // =================================================================== 类型分发

    @Nested
    @DisplayName("按 field_config.type 落列")
    class TypeDispatch {

        @Test
        @DisplayName("NUMBER → value_num 落 BigDecimal（value_date 必须为空）")
        void numberGoesToValueNum() {
            givenCustomConfigs(cfg("score", "NUMBER"));

            service.saveValues(ISSUE_ID, map("score", 87.5));

            IssueFieldValue saved = captureInsert();
            assertThat(saved.getValueNum()).isEqualByComparingTo(new BigDecimal("87.5"));
            assertThat(saved.getValueDate()).isNull();
            assertThat(saved.getFieldCode()).isEqualTo("score");
            assertThat(saved.getIssueId()).isEqualTo(ISSUE_ID);
        }

        @Test
        @DisplayName("NUMBER：字符串数字可解析；非数值抛 VALID_ERROR 且不落库")
        void numberParsesStringAndRejectsGarbage() {
            givenCustomConfigs(cfg("score", "NUMBER"));

            service.saveValues(ISSUE_ID, map("score", " 42 "));
            assertThat(captureInsert().getValueNum()).isEqualByComparingTo(new BigDecimal("42"));

            assertThatThrownBy(() -> service.saveValues(ISSUE_ID, map("score", "abc")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("字段值非数值");
        }

        @Test
        @DisplayName("DATE → value_date 取 00:00:00（value_num / value_text 均为空）")
        void dateGoesToValueDateAtStartOfDay() {
            givenCustomConfigs(cfg("onlineDate", "DATE"));

            service.saveValues(ISSUE_ID, map("onlineDate", "2026-08-06"));

            IssueFieldValue saved = captureInsert();
            assertThat(saved.getValueDate()).isEqualTo(LocalDateTime.of(2026, 8, 6, 0, 0, 0));
            assertThat(saved.getValueNum()).isNull();
            assertThat(saved.getValueText()).isNull();
        }

        @Test
        @DisplayName("DATETIME → value_date 保留时分秒，兼容前端 'yyyy-MM-dd HH:mm:ss' 空格分隔")
        void dateTimeAcceptsSpaceSeparatedFormat() {
            givenCustomConfigs(cfg("occurAt", "DATETIME"));

            service.saveValues(ISSUE_ID, map("occurAt", "2026-08-06 13:45:30"));

            IssueFieldValue saved = captureInsert();
            assertThat(saved.getValueDate()).isEqualTo(LocalDateTime.of(2026, 8, 6, 13, 45, 30));
            assertThat(saved.getValueNum()).isNull();
        }

        @Test
        @DisplayName("DATETIME：同时兼容 ISO 'T' 分隔与纯日期（后端回显再提交场景）")
        void dateTimeAcceptsIsoAndDateOnly() {
            givenCustomConfigs(cfg("occurAt", "DATETIME"));

            service.saveValues(ISSUE_ID, map("occurAt", "2026-08-06T13:45:30"));
            assertThat(captureInsert().getValueDate()).isEqualTo(LocalDateTime.of(2026, 8, 6, 13, 45, 30));

            service.saveValues(ISSUE_ID, map("occurAt", "2026-08-06"));
            assertThat(captureLastInsert().getValueDate()).isEqualTo(LocalDateTime.of(2026, 8, 6, 0, 0));
        }

        @Test
        @DisplayName("DATE / DATETIME 非法格式抛 VALID_ERROR，提示含期望格式")
        void badDateFormatRejected() {
            givenCustomConfigs(cfg("onlineDate", "DATE"), cfg("occurAt", "DATETIME"));

            assertThatThrownBy(() -> service.saveValues(ISSUE_ID, map("onlineDate", "2026/08/06")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("yyyy-MM-dd");
            assertThatThrownBy(() -> service.saveValues(ISSUE_ID, map("occurAt", "not-a-date")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("yyyy-MM-dd HH:mm:ss");
        }

        @Test
        @DisplayName("TEXT / DICT / REF → value_text（含多选逗号拼接）")
        void textDictRefGoToValueText() {
            givenCustomConfigs(cfg("remark", "TEXT"), cfg("channel", "DICT"), cfg("ownerId", "REF"));

            service.saveValues(ISSUE_ID, map(
                    "remark", "线上偶现",
                    "channel", "A,B,C",
                    "ownerId", 7L));

            List<IssueFieldValue> saved = captureAllInserts();
            assertThat(saved).hasSize(3);
            assertThat(saved).allSatisfy(v -> {
                assertThat(v.getValueNum()).isNull();
                assertThat(v.getValueDate()).isNull();
            });
            assertThat(saved).extracting(IssueFieldValue::getValueText)
                    .containsExactly("线上偶现", "A,B,C", "7");
        }
    }

    // =================================================================== 自定义字段边界

    @Nested
    @DisplayName("作用域与空值语义")
    class ScopeAndBlank {

        @Test
        @DisplayName("内置字段 / 未注册的 key 静默跳过：既不插入也不软删")
        void unknownOrSystemFieldsSkipped() {
            // 配置表按 is_system=0 过滤，故内置字段 title 根本不在返回集里
            givenCustomConfigs(cfg("remark", "TEXT"));

            service.saveValues(ISSUE_ID, map("title", "改标题", "notExists", "x"));

            verify(valueMapper, never()).insert(any(IssueFieldValue.class));
            verify(valueMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("停用字段（enabled=0）只读：不接受新值、不软删旧值、不报错（Q5）")
        void disabledFieldSilentlyIgnored() {
            FieldConfig disabled = cfg("legacy", "TEXT");
            disabled.setEnabled(0);
            givenCustomConfigs(disabled);

            assertThatCode(() -> service.saveValues(ISSUE_ID, map("legacy", "新值"))).doesNotThrowAnyException();
            verify(valueMapper, never()).insert(any(IssueFieldValue.class));
            verify(valueMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("值为 null / 空串 / 纯空白 → 软删旧值，绝不插入新行")
        void blankValueSoftDeletesOnly() {
            givenCustomConfigs(cfg("remark", "TEXT"), cfg("score", "NUMBER"), cfg("onlineDate", "DATE"));
            when(valueMapper.selectList(any()))
                    .thenReturn(new ArrayList<>(List.of(existing(501L, "remark"))));

            Map<String, Object> values = map("remark", null, "score", "", "onlineDate", "   ");
            service.saveValues(ISSUE_ID, values);

            verify(valueMapper, never()).insert(any(IssueFieldValue.class));
            // 三个字段都走软删分支（旧值存在的才真正 deleteById）
            verify(valueMapper, times(3)).deleteById(anyLong());
        }

        @Test
        @DisplayName("软删走 deleteById（逻辑删除），不做物理 delete")
        void softDeleteUsesLogicalDelete() {
            givenCustomConfigs(cfg("remark", "TEXT"));
            when(valueMapper.selectList(any()))
                    .thenReturn(new ArrayList<>(List.of(existing(777L, "remark"))));

            service.saveValues(ISSUE_ID, map("remark", null));

            verify(valueMapper).deleteById(777L);
            verify(valueMapper, never()).delete(any());
            verify(valueMapper, never()).insert(any(IssueFieldValue.class));
        }

        @Test
        @DisplayName("customFields 为 null 或空 Map → 直接返回，不查配置、不写库")
        void nullOrEmptyMapIsNoOp() {
            service.saveValues(ISSUE_ID, null);
            service.saveValues(ISSUE_ID, new LinkedHashMap<>());

            verify(configMapper, never()).selectList(any());
            verify(valueMapper, never()).insert(any(IssueFieldValue.class));
            verify(valueMapper, never()).deleteById(anyLong());
        }
    }

    // =================================================================== upsert 幂等

    @Nested
    @DisplayName("upsert 幂等")
    class Upsert {

        @Test
        @DisplayName("有旧值时先软删再插入（保证 (issue_id, field_code) 条件唯一不冲突）")
        void upsertDeletesBeforeInsert() {
            givenCustomConfigs(cfg("remark", "TEXT"));
            when(valueMapper.selectList(any()))
                    .thenReturn(new ArrayList<>(List.of(existing(601L, "remark"))));

            service.saveValues(ISSUE_ID, map("remark", "v2"));

            verify(valueMapper).deleteById(601L);
            assertThat(captureInsert().getValueText()).isEqualTo("v2");
        }

        @Test
        @DisplayName("同一 issue 连续保存两次同值，落库实体完全一致（幂等）")
        void repeatedSaveIsIdempotent() {
            givenCustomConfigs(cfg("remark", "TEXT"), cfg("score", "NUMBER"), cfg("onlineDate", "DATE"));
            Map<String, Object> payload = map("remark", "同值", "score", 3, "onlineDate", "2026-08-06");

            service.saveValues(ISSUE_ID, payload);
            service.saveValues(ISSUE_ID, payload);

            List<IssueFieldValue> all = captureAllInserts();
            assertThat(all).hasSize(6);
            for (int i = 0; i < 3; i++) {
                assertThat(signature(all.get(i + 3)))
                        .as("第 %d 个字段两次保存结果应完全一致", i)
                        .isEqualTo(signature(all.get(i)));
            }
        }

        /** 落库实体的可比较签名（BigDecimal 用 toPlainString 规避 scale 干扰） */
        private String signature(IssueFieldValue v) {
            return v.getIssueId() + "|" + v.getFieldCode() + "|" + v.getValueText() + "|"
                    + (v.getValueNum() == null ? "-" : v.getValueNum().stripTrailingZeros().toPlainString())
                    + "|" + v.getValueDate();
        }
    }

    // =================================================================== 读取

    @Nested
    @DisplayName("读取")
    class Read {

        @Test
        @DisplayName("mapByIssue 以 field_code 为 key，保持 listByIssue 的顺序")
        void mapByIssueKeepsOrder() {
            IssueFieldValue a = existing(1L, "aCode");
            a.setValueText("A");
            IssueFieldValue b = existing(2L, "bCode");
            b.setValueText("B");
            when(valueMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(a, b)));

            Map<String, IssueFieldValue> m = service.mapByIssue(ISSUE_ID);

            assertThat(m).containsOnlyKeys("aCode", "bCode");
            assertThat(m.keySet()).containsExactly("aCode", "bCode");
            assertThat(m.get("aCode").getValueText()).isEqualTo("A");
        }

        @Test
        @DisplayName("customConfigs 返回 code→配置，含停用项（供详情回显历史值）")
        void customConfigsIncludeDisabled() {
            FieldConfig disabled = cfg("legacy", "TEXT");
            disabled.setEnabled(0);
            givenCustomConfigs(cfg("remark", "TEXT"), disabled);

            Map<String, FieldConfig> configs = service.customConfigs();

            assertThat(configs).containsOnlyKeys("remark", "legacy");
            assertThat(configs.get("legacy").getEnabled()).isZero();
        }
    }

    // =================================================================== helpers

    private IssueFieldValue captureInsert() {
        ArgumentCaptor<IssueFieldValue> captor = ArgumentCaptor.forClass(IssueFieldValue.class);
        verify(valueMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    private IssueFieldValue captureLastInsert() {
        return captureInsert();
    }

    private List<IssueFieldValue> captureAllInserts() {
        ArgumentCaptor<IssueFieldValue> captor = ArgumentCaptor.forClass(IssueFieldValue.class);
        verify(valueMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getAllValues();
    }
}
