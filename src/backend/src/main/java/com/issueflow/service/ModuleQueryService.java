package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.resp.ModuleBriefVO;
import com.issueflow.dto.resp.ModuleNodeVO;
import com.issueflow.entity.Module;
import com.issueflow.entity.ModuleDependency;
import com.issueflow.mapper.ModuleDependencyMapper;
import com.issueflow.mapper.ModuleMapper;
import com.issueflow.util.ModuleTreeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块只读查询与树装配服务（2026-08-01 由 {@code ModuleService} 拆出，F1-b）。
 *
 * <p><b>职责边界</b>：只做查询与内存组树，<b>不含任何写操作</b>。
 * 所有写命令（create/update/delete/move/batchDelete/batchMove/setDependencies）
 * 及其 {@code @Transactional} 事务边界一律保留在 {@link ModuleService}。</p>
 *
 * <p><b>事务说明</b>：仅两个对外入口 {@link #tree(Long)} / {@link #pathMap(Collection)}
 * 标注 {@code @Transactional(readOnly = true)}。包级可见的复用方法刻意不加事务注解——
 * 它们会被 {@link ModuleService} 的写事务方法调用，若标注 readOnly 虽然在
 * {@code REQUIRED} 传播下会被忽略（加入既有事务），但不标注语义更清晰、零歧义。</p>
 *
 * <p><b>避免自调用失效</b>：{@link ModuleService} 通过<b>注入本 bean</b> 调用只读逻辑，
 * 而非同类自调用，因此代理与事务语义均正常。</p>
 *
 * <p>本次拆分为纯结构调整，方法实现逐行搬迁，查询语句与返回结构完全未改。</p>
 */
@Service
@RequiredArgsConstructor
public class ModuleQueryService {

    /** 模块路径分隔符 */
    private static final String PATH_SEPARATOR = " > ";

    private final ModuleMapper moduleMapper;
    private final ModuleDependencyMapper moduleDependencyMapper;

    /**
     * 查询某项目的完整模块树（仅登录即可访问）。
     *
     * <p>共两次查询：module 全量 + dependency IN 全量，随后内存组树并回填依赖。</p>
     *
     * @param projectId 项目 id，必填
     * @return 根级节点数组，children 递归，同级按 sort 升序
     */
    @Transactional(readOnly = true)
    public List<ModuleNodeVO> tree(Long projectId) {
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        List<Module> modules = loadProjectModules(projectId);
        if (modules.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nameById = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (Module m : modules) {
            nameById.put(m.getId(), m.getName());
            ids.add(m.getId());
        }
        Map<Long, List<ModuleBriefVO>> dependencyMap = loadDependencies(ids, nameById);
        Map<Long, List<Module>> childrenMap = ModuleTreeSupport.buildChildrenMap(modules);
        return buildNodes(ModuleTreeSupport.ROOT_PARENT_ID, childrenMap, dependencyMap);
    }

    /**
     * 批量构建模块「父 &gt; 子 &gt; 孙」全路径映射，供 IssueService 回填 modulePath（禁止 N+1）。
     *
     * <p>流程：批查这些 module → 汇总涉及 projectId → 一次查这些项目的全量模块 → 内存向上拼路径。</p>
     *
     * @param moduleIds 模块 id 集合（允许包含 null / 重复，内部会清洗）
     * @return moduleId -&gt; 全路径；查不到的 id 不出现在结果中
     */
    @Transactional(readOnly = true)
    public Map<Long, String> pathMap(Collection<Long> moduleIds) {
        Map<Long, String> result = new HashMap<>();
        Set<Long> targetIds = ModuleTreeSupport.cleanIds(moduleIds);
        if (targetIds.isEmpty()) {
            return result;
        }
        List<Module> targets = moduleMapper.selectBatchIds(targetIds);
        if (targets.isEmpty()) {
            return result;
        }
        Set<Long> projectIds = new HashSet<>();
        for (Module m : targets) {
            if (m.getProjectId() != null) {
                projectIds.add(m.getProjectId());
            }
        }
        if (projectIds.isEmpty()) {
            return result;
        }
        List<Module> all = moduleMapper.selectList(new LambdaQueryWrapper<Module>()
                .in(Module::getProjectId, projectIds)
                .eq(Module::getDeleted, 0));
        Map<Long, Module> byId = new HashMap<>();
        for (Module m : all) {
            byId.put(m.getId(), m);
        }
        for (Module target : targets) {
            String path = buildPath(target.getId(), byId);
            if (path != null) {
                result.put(target.getId(), path);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 包级复用（供同包的 ModuleService 写命令编排使用，不对外暴露）
    // ------------------------------------------------------------------

    /**
     * 一次性加载某项目的全部未删除模块（同级按 sort 升序、id 升序兜底）。
     *
     * @param projectId 项目 id
     * @return 模块列表，永不为 null
     */
    List<Module> loadProjectModules(Long projectId) {
        return moduleMapper.selectList(new LambdaQueryWrapper<Module>()
                .eq(Module::getProjectId, projectId)
                .eq(Module::getDeleted, 0)
                .orderByAsc(Module::getSort)
                .orderByAsc(Module::getId));
    }

    /**
     * 批量加载 fromId -&gt; 依赖列表（一次 IN 查询，禁止逐节点单查）。
     *
     * @param fromIds  依赖方模块 id 集合
     * @param nameById 模块 id -&gt; 名称 映射，用于回填被依赖模块名
     * @return fromId 到依赖简要信息列表的映射，永不为 null
     */
    Map<Long, List<ModuleBriefVO>> loadDependencies(Collection<Long> fromIds, Map<Long, String> nameById) {
        Map<Long, List<ModuleBriefVO>> map = new HashMap<>();
        if (fromIds == null || fromIds.isEmpty()) {
            return map;
        }
        List<ModuleDependency> edges = moduleDependencyMapper.selectList(
                new LambdaQueryWrapper<ModuleDependency>()
                        .in(ModuleDependency::getFromModuleId, fromIds)
                        .eq(ModuleDependency::getDeleted, 0)
                        .orderByAsc(ModuleDependency::getId));
        for (ModuleDependency e : edges) {
            String name = nameById.get(e.getToModuleId());
            if (name == null) {
                // 被依赖模块已不存在（理论上删除时已物理清理），跳过脏边
                continue;
            }
            ModuleBriefVO brief = new ModuleBriefVO();
            brief.setId(e.getToModuleId());
            brief.setName(name);
            map.computeIfAbsent(e.getFromModuleId(), k -> new ArrayList<>()).add(brief);
        }
        return map;
    }

    /**
     * 实体 -&gt; 节点 VO（children 由调用方填充）。
     *
     * @param m            模块实体
     * @param dependencies 该模块的依赖列表
     * @return 节点 VO
     */
    ModuleNodeVO toNodeVO(Module m, List<ModuleBriefVO> dependencies) {
        ModuleNodeVO vo = new ModuleNodeVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setParentId(m.getParentId() == null ? ModuleTreeSupport.ROOT_PARENT_ID : m.getParentId());
        vo.setName(m.getName());
        vo.setDescription(m.getDescription());
        vo.setSort(m.getSort());
        vo.setDependencies(dependencies);
        vo.setDependencyCount(dependencies.size());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    /**
     * 递归构建 VO 树。
     */
    private List<ModuleNodeVO> buildNodes(long parentId,
                                          Map<Long, List<Module>> childrenMap,
                                          Map<Long, List<ModuleBriefVO>> dependencyMap) {
        List<ModuleNodeVO> nodes = new ArrayList<>();
        List<Module> children = childrenMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return nodes;
        }
        for (Module m : children) {
            ModuleNodeVO vo = toNodeVO(m, dependencyMap.getOrDefault(m.getId(), new ArrayList<>()));
            vo.setChildren(buildNodes(m.getId(), childrenMap, dependencyMap));
            nodes.add(vo);
        }
        return nodes;
    }

    /**
     * 向上拼接「父 &gt; 子 &gt; 孙」全路径；节点缺失返回 null。
     */
    private String buildPath(Long moduleId, Map<Long, Module> byId) {
        Module cur = byId.get(moduleId);
        if (cur == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        Set<Long> guard = new HashSet<>();
        while (cur != null && guard.add(cur.getId())) {
            names.add(cur.getName());
            Long pid = cur.getParentId();
            if (pid == null || pid == ModuleTreeSupport.ROOT_PARENT_ID) {
                break;
            }
            cur = byId.get(pid);
        }
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }
}
