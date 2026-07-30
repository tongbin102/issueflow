package com.issueflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.issueflow.entity.ModuleDependency;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模块依赖 Mapper。
 *
 * <p>本表唯一索引 uk(from_module_id,to_module_id) 与逻辑删除相斥（软删后重建同边会撞唯一键），
 * 因此下列两个方法使用原生 {@code @Delete} 物理删除，<b>刻意绕过 BaseEntity 上的 @TableLogic</b>，
 * 这是全项目「软删」约定的唯一例外（见增量设计 Phase 4 §1.1 / §9 约定 4）。</p>
 */
@Mapper
public interface ModuleDependencyMapper extends BaseMapper<ModuleDependency> {

    /**
     * 物理删除某模块作为「依赖方」的全部依赖边（含 deleted=1 残留），用于全量替换前清空。
     *
     * @param fromId 依赖方模块 id
     * @return 受影响行数
     */
    @Delete("DELETE FROM `module_dependency` WHERE `from_module_id` = #{fromId}")
    int deletePhysicalByFromId(@Param("fromId") Long fromId);

    /**
     * 物理删除一批模块相关的全部依赖边（作为 from 或 to 均清理），用于模块删除时的关系清理。
     *
     * @param ids 模块 id 列表，调用方保证非空
     * @return 受影响行数
     */
    @Delete("<script>"
            + "DELETE FROM `module_dependency` WHERE "
            + "`from_module_id` IN "
            + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + " OR `to_module_id` IN "
            + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    int deletePhysicalByModuleIds(@Param("ids") List<Long> ids);
}
