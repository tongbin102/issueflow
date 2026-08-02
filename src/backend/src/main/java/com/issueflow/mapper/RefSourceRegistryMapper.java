package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.RefSourceRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * REF 引用源白名单 Mapper（对应表 ref_source_registry）
 * <p>唯一允许的 {@code ${}} 动态标识符拼接收口（SQL 注入边界，见 ARCH §7.5）：
 * 所有标识符（tableName/labelField/...）在 {@code RefSourceService} 调用前已通过
 * {@code SqlIdentifier.check()} 正则校验 + 启动期 {@code information_schema} 校验，
 * 全部来自封闭白名单；值一律以 {@code #{} } 预编译占位符传入。</p>
 */
@Mapper
public interface RefSourceRegistryMapper extends BaseMapper<RefSourceRegistry> {

    /**
     * 按引用源动态取候选（flat 列表 / tree 的扁平行）。
     * <p>结果列：{@code value}（取值列）、{@code label}（展示列）、{@code parent}（树父列，flat 为 NULL）。</p>
     *
     * @param tableName   已校验的表名
     * @param labelField  已校验的展示列
     * @param valueField  已校验的取值列
     * @param orderField  已校验的排序列，可空（空时按 valueField 升序）
     * @param filterField 已校验的依赖过滤列，可空
     * @param parentField 已校验的树父列，可空
     * @param parentValue 依赖源当前值（仅 filterField 非空时拼接 WHERE）
     * @param keyword     模糊搜索关键词（按 labelField LIKE，可空）
     * @return 扁平结果行（value/label/parent）
     */
    @Select("<script>"
            + "SELECT ${valueField} AS `value`, ${labelField} AS `label`, "
            + "<if test='parentField != null'>${parentField} AS `parent`</if>"
            + "<if test='parentField == null'>NULL AS `parent`</if>"
            + " FROM ${tableName} WHERE deleted = 0 "
            + "<if test='filterField != null and parentValue != null'> AND ${filterField} = #{parentValue} </if>"
            + "<if test='keyword != null and keyword != \"\"'> AND ${labelField} LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<choose>"
            + "  <when test='orderField != null'> ORDER BY ${orderField} ASC </when>"
            + "  <otherwise> ORDER BY ${valueField} ASC </otherwise>"
            + "</choose>"
            + "</script>")
    List<Map<String, Object>> selectOptions(@Param("tableName") String tableName,
                                            @Param("labelField") String labelField,
                                            @Param("valueField") String valueField,
                                            @Param("orderField") String orderField,
                                            @Param("filterField") String filterField,
                                            @Param("parentField") String parentField,
                                            @Param("parentValue") String parentValue,
                                            @Param("keyword") String keyword);
}
