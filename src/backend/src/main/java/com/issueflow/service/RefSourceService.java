package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.fieldconfig.resp.RefOptionVO;
import com.issueflow.dto.fieldconfig.resp.RefSourceVO;
import com.issueflow.entity.RefSourceRegistry;
import com.issueflow.enums.RefQueryType;
import com.issueflow.mapper.RefSourceRegistryMapper;
import com.issueflow.util.SqlIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REF 引用源服务（ARCH §3.3，Q7）。
 * <p>核心安全约束：所有拼入动态 SQL 的标识符（table_name / label_field / value_field / order_field /
 * filter_field / parent_field）必须<b>先过 {@link SqlIdentifier#check} 正则</b>（再叠加启动期
 * information_schema 校验），值一律走 {@code #{} } 预编译。前端只传 {@code refSource} 编码，永不传表名。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefSourceService {

    private final RefSourceRegistryMapper registryMapper;

    /**
     * 取启用的引用源（白名单校验入口）。
     *
     * @param code 引用源编码
     * @return 引用源配置，未命中或已停用返回 null
     */
    public RefSourceRegistry getEnabled(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return registryMapper.selectOne(new LambdaQueryWrapper<RefSourceRegistry>()
                .eq(RefSourceRegistry::getCode, code).eq(RefSourceRegistry::getEnabled, 1));
    }

    /**
     * 配置页下拉：全部启用的引用源。
     */
    public List<RefSourceVO> listEnabled() {
        List<RefSourceRegistry> rows = registryMapper.selectList(new LambdaQueryWrapper<RefSourceRegistry>()
                .eq(RefSourceRegistry::getEnabled, 1).orderByAsc(RefSourceRegistry::getId));
        return rows.stream().map(r -> {
            RefSourceVO vo = new RefSourceVO();
            vo.setCode(r.getCode());
            vo.setName(r.getName());
            vo.setQueryType(r.getQueryType());
            vo.setDisplayType(RefQueryType.TREE.name().equalsIgnoreCase(r.getQueryType()) ? "tree" : "select");
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询引用候选项（flat 返回 list / tree 返回树）。
     *
     * @param refSource  白名单编码（必填）
     * @param parentValue 依赖源当前值（有值则按 filter_field 过滤）
     * @param keyword    模糊搜索（按 label_field LIKE）
     * @return 候选选项（flat 无 children；tree 递归 children）
     */
    public List<RefOptionVO> query(String refSource, String parentValue, String keyword) {
        RefSourceRegistry reg = getEnabled(refSource);
        if (reg == null) {
            throw new BizException(ResultCode.REF_SOURCE_NOT_ALLOWED);
        }

        // ① 所有标识符再过一次正则（防运维手工插入恶意注册行）
        String table = SqlIdentifier.check(reg.getTableName());
        String label = SqlIdentifier.check(reg.getLabelField());
        String value = SqlIdentifier.check(reg.getValueField());
        String order = resolveOrderField(reg, value);
        String filter = reg.getFilterField() == null ? null : SqlIdentifier.check(reg.getFilterField());
        String parent = reg.getParentField() == null ? null : SqlIdentifier.check(reg.getParentField());

        // ② 标识符用 ${} 拼接（已双校验），值一律 #{} 预编译
        List<Map<String, Object>> rows = selectOptionsWithOrderFallback(
                reg.getCode(), table, label, value, order, filter, parent, parentValue, keyword);

        if (RefQueryType.TREE.equals(RefQueryType.fromCode(reg.getQueryType()))) {
            return buildTree(rows);
        }
        return toFlat(rows);
    }

    /**
     * 解析排序列：为空 / 全空白时回退 {@code valueField}，绝不把空串拼进 {@code ORDER BY}。
     * <p>配置误录入常见两侧空白（如 {@code " sort "}），先 trim 再过正则，避免误判为非法标识符。</p>
     *
     * @param reg        引用源注册行
     * @param valueField 已校验的取值列（兜底排序列）
     * @return 已校验的排序列标识符
     */
    private String resolveOrderField(RefSourceRegistry reg, String valueField) {
        String raw = reg.getOrderField();
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return SqlIdentifier.check(valueField);
        }
        return SqlIdentifier.check(trimmed);
    }

    /**
     * 执行候选项查询；当 {@code order_field} 配置了目标表并不存在的列时，
     * MySQL 抛 {@code Unknown column 'xxx' in 'order clause'}，MyBatis 转成
     * {@link BadSqlGrammarException}。此时<b>降级为按 {@code valueField} 排序重试一次</b>，
     * 并记 error 日志暴露配置问题——避免一行脏配置把整个「新建问题」表单打成 500
     * （2026-08-06 线上缺陷：PROJECT 的 order_field 被种子写成了 project 表没有的 sort）。
     *
     * @param code        引用源编码（仅用于日志定位）
     * @param table       已校验的表名
     * @param label       已校验的展示列
     * @param value       已校验的取值列
     * @param order       已校验的排序列
     * @param filter      已校验的过滤列，可空
     * @param parent      已校验的树父列，可空
     * @param parentValue 依赖源当前值，可空
     * @param keyword     模糊搜索关键词，可空
     * @return 扁平结果行
     */
    private List<Map<String, Object>> selectOptionsWithOrderFallback(String code, String table, String label,
                                                                     String value, String order, String filter,
                                                                     String parent, String parentValue,
                                                                     String keyword) {
        try {
            return registryMapper.selectOptions(table, label, value, order, filter, parent, parentValue, keyword);
        } catch (BadSqlGrammarException ex) {
            if (Objects.equals(order, value)) {
                // 已经是兜底列仍失败，说明是表名/展示列等其他配置错误，交由全局异常处理
                throw ex;
            }
            log.error("引用源 [{}] 的 order_field=[{}] 在表 [{}] 中不可用，已降级按 [{}] 排序；请订正 ref_source_registry 配置",
                    code, order, table, value, ex);
            return registryMapper.selectOptions(table, label, value, value, filter, parent, parentValue, keyword);
        }
    }

    private List<RefOptionVO> toFlat(List<Map<String, Object>> rows) {
        List<RefOptionVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            RefOptionVO vo = new RefOptionVO();
            vo.setValue(row.get("value"));
            vo.setLabel(asString(row.get("label")));
            list.add(vo);
        }
        return list;
    }

    /**
     * 由扁平行（value/label/parent）构建树。parent 为 NULL/空表示根节点。
     */
    private List<RefOptionVO> buildTree(List<Map<String, Object>> rows) {
        List<RefOptionVO> roots = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return roots;
        }
        Map<Object, RefOptionVO> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object val = row.get("value");
            RefOptionVO node = new RefOptionVO();
            node.setValue(val);
            node.setLabel(asString(row.get("label")));
            node.setChildren(new ArrayList<>());
            nodeMap.put(val, node);
        }
        for (Map<String, Object> row : rows) {
            Object val = row.get("value");
            Object parentVal = row.get("parent");
            RefOptionVO node = nodeMap.get(val);
            if (parentVal == null || "".equals(String.valueOf(parentVal))) {
                roots.add(node);
            } else {
                RefOptionVO parentNode = nodeMap.get(parentVal);
                if (parentNode == null) {
                    // 父节点不在结果集（被过滤/删除），作为根兜底，避免孤儿丢失
                    parentNode = new RefOptionVO();
                    parentNode.setValue(parentVal);
                    parentNode.setLabel(String.valueOf(parentVal));
                    parentNode.setChildren(new ArrayList<>());
                    nodeMap.put(parentVal, parentNode);
                    roots.add(parentNode);
                }
                parentNode.getChildren().add(node);
            }
        }
        return roots;
    }

    private String asString(Object o) {
        return o == null ? "" : o.toString();
    }
}
