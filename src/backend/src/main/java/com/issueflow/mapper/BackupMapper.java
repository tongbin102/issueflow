package com.issueflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 备份专用通用查询 Mapper。
 * <p><b>安全铁律</b>：所有 {@code table} 参数均使用 {@code ${}} 直接拼接，
 * 因此表名<b>只能</b>来自后端白名单常量 {@code BackupService.CORE_TABLES / ALL_TABLES}，
 * <b>绝不可</b>来自前端入参，否则构成 SQL 注入。</p>
 */
@Mapper
public interface BackupMapper {

    /**
     * 统计单表未删除行数（无 deleted 列的表退化为全表计数，由调用方选择方法）。
     *
     * @param table 白名单表名
     * @return 行数
     */
    @Select("SELECT COUNT(*) FROM ${table}")
    long countTable(@Param("table") String table);

    /**
     * 按主键游标分页读取单表数据（避免 OFFSET 深翻页与全量内存驻留）。
     *
     * @param table  白名单表名
     * @param lastId 上一批的最大 id，首批传 0
     * @param limit  批大小
     * @return 行数据列表，键为列名
     */
    @Select("SELECT * FROM ${table} WHERE id > #{lastId} ORDER BY id ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPageByCursor(@Param("table") String table,
                                                 @Param("lastId") long lastId,
                                                 @Param("limit") int limit);

    /**
     * 全表读取（<b>仅供无 {@code id} 主键的表兜底</b>）。
     *
     * <p>没有游标可用时只能一次性取回，因此调用方必须先用 {@link #countTable(String)}
     * 确认行数在安全范围内，否则会把整表拉进内存。</p>
     *
     * @param table 白名单表名
     * @return 行数据列表
     */
    @Select("SELECT * FROM ${table}")
    List<Map<String, Object>> selectAllRows(@Param("table") String table);

    /**
     * 读取单表列名（按序）。
     *
     * @param table 白名单表名
     * @return 列名列表
     */
    @Select("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{table} "
            + "AND EXTRA NOT LIKE '%GENERATED%' ORDER BY ORDINAL_POSITION")
    List<String> listColumns(@Param("table") String table);

    /**
     * 判断表是否存在（迁移脚本未执行时跳过该表，避免导出整体失败）。
     *
     * @param table 白名单表名
     * @return 存在数量，0 表示不存在
     */
    @Select("SELECT COUNT(*) FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{table}")
    int existsTable(@Param("table") String table);
}
