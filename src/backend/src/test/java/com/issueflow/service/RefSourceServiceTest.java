package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.fieldconfig.resp.RefOptionVO;
import com.issueflow.entity.RefSourceRegistry;
import com.issueflow.mapper.RefSourceRegistryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REF 引用源服务单元测试（Phase9 T06-QA）。
 *
 * <p>重点：<b>ref-options 白名单防线</b>。前端只允许传 {@code refSource} 编码，
 * 表名/列名一律由注册表提供并二次过正则；任何未注册 / 已停用 / 含注入载荷的入参
 * 都必须在触达 Mapper 之前被拒绝。</p>
 */
@DisplayName("RefSourceService REF 引用源（白名单 / 注入防线）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefSourceServiceTest {

    @Mock
    private RefSourceRegistryMapper registryMapper;

    @InjectMocks
    private RefSourceService refSourceService;

    private RefSourceRegistry registry(String code, String table, String label, String value,
                                       String queryType, String parent, String filter, String order) {
        RefSourceRegistry r = new RefSourceRegistry();
        r.setId(1L);
        r.setCode(code);
        r.setName(code);
        r.setTableName(table);
        r.setLabelField(label);
        r.setValueField(value);
        r.setQueryType(queryType);
        r.setParentField(parent);
        r.setFilterField(filter);
        r.setOrderField(order);
        r.setEnabled(1);
        return r;
    }

    private Map<String, Object> row(Object value, Object label, Object parent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        m.put("parent", parent);
        return m;
    }

    // ------------------------------------------------------------------ 白名单

    @ParameterizedTest(name = "refSource=\"{0}\" 未注册 → REF_SOURCE_NOT_ALLOWED")
    @ValueSource(strings = {
            "user; DROP TABLE user",
            "user; DROP TABLE user; --",
            "PROJECT' OR '1'='1",
            "issue_field_value",
            "UNKNOWN_SOURCE",
            "project"
    })
    @DisplayName("未在注册表命中的 refSource（含注入载荷）一律拒绝，且不触达 Mapper 查询")
    void queryRejectsUnregisteredRefSource(String refSource) {
        when(registryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> refSourceService.query(refSource, null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_NOT_ALLOWED.getCode());

        verify(registryMapper, never()).selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("refSource 为空/空白直接拒绝（getEnabled 短路，连注册表都不查）")
    void queryRejectsBlankRefSource(String refSource) {
        assertThatThrownBy(() -> refSourceService.query(refSource, null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_NOT_ALLOWED.getCode());
        verify(registryMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("停用的引用源等同未注册（selectOne 带 enabled=1 条件，返回 null → 拒绝）")
    void queryRejectsDisabledRefSource() {
        when(registryMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> refSourceService.query("PROJECT", null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_NOT_ALLOWED.getCode());
    }

    @Test
    @DisplayName("注册行被运维手工污染（table_name 含注入）时，二次正则拦截 → REF_SOURCE_ILLEGAL_IDENTIFIER")
    void queryRejectsPoisonedRegistryRow() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("EVIL", "user; DROP TABLE user", "username", "id", "flat", null, null, null));

        assertThatThrownBy(() -> refSourceService.query("EVIL", null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_ILLEGAL_IDENTIFIER.getCode());

        verify(registryMapper, never()).selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("注册行 label_field 被污染同样拦截（每个标识符都过正则，不只表名）")
    void queryRejectsPoisonedLabelField() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("EVIL2", "user", "username, (SELECT password FROM user)", "id",
                        "flat", null, null, null));

        assertThatThrownBy(() -> refSourceService.query("EVIL2", null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.REF_SOURCE_ILLEGAL_IDENTIFIER.getCode());
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("flat 引用源：标识符透传 Mapper，parentValue / keyword 作为「值」原样下传（走 #{} 预编译）")
    void queryFlatPassesCheckedIdentifiers() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("PROJECT", "project", "name", "id", "flat", null, null, "sort"));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1L, "商城", null));
        rows.add(row(2L, "官网", null));
        when(registryMapper.selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(rows);

        // keyword 里带引号：它是「值」不是「标识符」，不应被拒，必须原样交给 #{} 预编译
        List<RefOptionVO> options = refSourceService.query("PROJECT", null, "a' OR '1'='1");

        assertThat(options).hasSize(2);
        assertThat(options).extracting(RefOptionVO::getLabel).containsExactly("商城", "官网");
        assertThat(options).extracting(RefOptionVO::getValue).containsExactly(1L, 2L);
        assertThat(options).allSatisfy(o -> assertThat(o.getChildren()).isNull());

        ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> order = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(registryMapper).selectOptions(table.capture(), label.capture(), value.capture(),
                order.capture(), any(), any(), any(), keyword.capture());
        assertThat(table.getValue()).isEqualTo("project");
        assertThat(label.getValue()).isEqualTo("name");
        assertThat(value.getValue()).isEqualTo("id");
        assertThat(order.getValue()).isEqualTo("sort");
        assertThat(keyword.getValue()).isEqualTo("a' OR '1'='1");
    }

    @Test
    @DisplayName("order_field 为空时兜底为 value_field（不让 null 进 SQL）")
    void queryDefaultsOrderFieldToValueField() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("USER", "user", "username", "id", "flat", null, null, null));
        when(registryMapper.selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(new ArrayList<>());

        refSourceService.query("USER", null, null);

        verify(registryMapper).selectOptions(eq("user"), eq("username"), eq("id"), eq("id"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("tree 引用源：扁平行按 parent 组装成树，根节点 parent 为 NULL")
    void queryTreeBuildsHierarchy() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("MODULE", "module", "name", "id", "tree", "parent_id", "project_id", "sort"));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1L, "订单", null));
        rows.add(row(2L, "支付", 1L));
        rows.add(row(3L, "退款", 2L));
        when(registryMapper.selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(rows);

        List<RefOptionVO> tree = refSourceService.query("MODULE", "10", null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getLabel()).isEqualTo("订单");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getLabel()).isEqualTo("支付");
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getLabel()).isEqualTo("退款");
    }

    @Test
    @DisplayName("tree 引用源：父节点不在结果集时孤儿节点兜底提升为根，不丢数据")
    void queryTreeKeepsOrphans() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("MODULE", "module", "name", "id", "tree", "parent_id", "project_id", null));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(9L, "孤儿", 99L));
        when(registryMapper.selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(rows);

        List<RefOptionVO> tree = refSourceService.query("MODULE", null, null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getValue()).isEqualTo(99L);
        assertThat(tree.get(0).getChildren()).extracting(RefOptionVO::getLabel).containsExactly("孤儿");
    }

    @Test
    @DisplayName("Mapper 返回 null 时不抛 NPE，返回空列表")
    void queryHandlesNullRows() {
        when(registryMapper.selectOne(any()))
                .thenReturn(registry("PROJECT", "project", "name", "id", "flat", null, null, null));
        when(registryMapper.selectOptions(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(null);

        assertThat(refSourceService.query("PROJECT", null, null)).isEmpty();
    }

    @Test
    @DisplayName("getEnabled：code 为空/空白返回 null 而非查库")
    void getEnabledShortCircuitsOnBlank() {
        assertThat(refSourceService.getEnabled(null)).isNull();
        assertThat(refSourceService.getEnabled("")).isNull();
        assertThat(refSourceService.getEnabled("  ")).isNull();
        verify(registryMapper, never()).selectOne(any(Wrapper.class));
    }
}
