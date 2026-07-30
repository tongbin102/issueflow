package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块树节点视图对象（children 递归；同级按 sort 升序）
 */
@Data
public class ModuleNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属项目 id */
    private Long projectId;

    /** 父模块 id，0 = 根 */
    private Long parentId;

    private String name;

    private String description;

    /** 同级排序号 */
    private Integer sort;

    /** 依赖数量（= dependencies.size()，供树上「依赖 N」标签直接使用） */
    private Integer dependencyCount = 0;

    /** 本模块所依赖的模块列表（单向 A→B） */
    private List<ModuleBriefVO> dependencies = new ArrayList<>();

    /** 子模块列表 */
    private List<ModuleNodeVO> children = new ArrayList<>();
}
