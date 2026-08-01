package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.ModuleBatchReq;
import com.issueflow.dto.req.ModuleDependencyReq;
import com.issueflow.dto.req.ModuleMoveReq;
import com.issueflow.dto.req.ModuleReq;
import com.issueflow.dto.resp.ModuleBriefVO;
import com.issueflow.dto.resp.ModuleNodeVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.Module;
import com.issueflow.entity.ModuleDependency;
import com.issueflow.entity.Project;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.mapper.ModuleDependencyMapper;
import com.issueflow.mapper.ModuleMapper;
import com.issueflow.mapper.ProjectMapper;
import com.issueflow.util.ModuleTreeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 模块业务服务（Phase 4 R1 / R5）。
 *
 * <p>核心约定：</p>
 * <ol>
 *   <li>邻接表存储（parent_id 自引用，0 = 根），所有「查子孙 / 深度 / 高度」<b>一律一次全量查该项目模块后内存组树计算</b>，禁止逐层查库。</li>
 *   <li>层级软上限 10 层（{@link #MAX_DEPTH}）。</li>
 *   <li>模块软删（含级联子孙）；依赖表因唯一索引采用物理清空重建。</li>
 *   <li>写操作复用 {@code project:update} 权限，读树仅需登录。</li>
 * </ol>
 *
 * <p><b>2026-08-01 拆分说明（F1，纯结构调整、行为不变）</b>：原 907 行单体按职责拆为三层——</p>
 * <ul>
 *   <li>本类：<b>编排 + 写事务入口</b>，保留 create/update/delete/move/batchDelete/batchMove/setDependencies
 *       及其 {@code @Transactional}，以及写前的各项业务校验；</li>
 *   <li>{@link ModuleQueryService}：只读查询与树装配（tree/pathMap 及其私有装配逻辑），
 *       以 <b>bean 注入</b>方式调用（非同类自调用，事务代理正常）；</li>
 *   <li>{@link ModuleTreeSupport}：无状态 static 纯计算工具（组树/子孙/深度/高度/排序规划/依赖防环）。</li>
 * </ul>
 * <p>本类 <b>10 个 public 方法签名与语义完全不变</b>，{@code ModuleController} 与
 * {@code IssueService} 等调用方零改动。</p>
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

    /** 模块层级软上限（产品约定，超出即拒绝） */
    private static final int MAX_DEPTH = 10;

    /** 根节点的 parent_id 约定值（与 {@link ModuleTreeSupport#ROOT_PARENT_ID} 同源，避免双份字面量） */
    private static final long ROOT_PARENT_ID = ModuleTreeSupport.ROOT_PARENT_ID;

    /** 模块写操作复用的权限码 */
    private static final String PERM_MODULE_WRITE = "project:update";

    private final ModuleMapper moduleMapper;
    private final ModuleDependencyMapper moduleDependencyMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;
    private final ModuleQueryService moduleQueryService;

    // ------------------------------------------------------------------
    // 查询（委托 ModuleQueryService，public API 保持不变）
    // ------------------------------------------------------------------

    /**
     * 查询某项目的完整模块树（仅登录即可访问）。
     *
     * @param projectId 项目 id，必填
     * @return 根级节点数组，children 递归，同级按 sort 升序
     */
    public List<ModuleNodeVO> tree(Long projectId) {
        return moduleQueryService.tree(projectId);
    }

    /**
     * 批量构建模块「父 &gt; 子 &gt; 孙」全路径映射，供 IssueService 回填 modulePath（禁止 N+1）。
     *
     * @param moduleIds 模块 id 集合（允许包含 null / 重复，内部会清洗）
     * @return moduleId -&gt; 全路径；查不到的 id 不出现在结果中
     */
    public Map<Long, String> pathMap(Collection<Long> moduleIds) {
        return moduleQueryService.pathMap(moduleIds);
    }

    /**
     * R5-1 归属校验：moduleId 非空时，模块须存在、未删，且与问题最终生效的 projectId 属于同一项目。
     *
     * @param moduleId  模块 id，为 null 时直接放行
     * @param projectId 问题最终生效的项目 id
     */
    public void assertModuleBelongsToProject(Long moduleId, Long projectId) {
        if (moduleId == null) {
            return;
        }
        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new BizException(ResultCode.MODULE_NOT_FOUND);
        }
        if (projectId == null || !Objects.equals(module.getProjectId(), projectId)) {
            throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH);
        }
    }

    // ------------------------------------------------------------------
    // 写操作（事务边界一律保留在本类）
    // ------------------------------------------------------------------

    /**
     * 新建模块：父存在性 / 同项目 / 同级重名 / 深度 ≤10 校验；sort = 同级 max + 1。
     *
     * @param req 请求体（projectId 必填，parentId 空或 0 表示根级）
     * @return 新节点（children 为空）
     */
    @Transactional
    public ModuleNodeVO create(ModuleReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Long projectId = req.getProjectId();
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        long parentId = ModuleTreeSupport.normalizeParentId(req.getParentId());

        List<Module> modules = moduleQueryService.loadProjectModules(projectId);
        Map<Long, Module> byId = ModuleTreeSupport.indexById(modules);
        if (parentId != ROOT_PARENT_ID) {
            Module parent = byId.get(parentId);
            if (parent == null) {
                Module raw = moduleMapper.selectById(parentId);
                if (raw == null) {
                    throw new BizException(ResultCode.MODULE_NOT_FOUND, "父模块不存在");
                }
                throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH, "父模块不属于当前项目");
            }
        }
        if (ModuleTreeSupport.depthOf(parentId, byId) + 1 > MAX_DEPTH) {
            throw new BizException(ResultCode.MODULE_DEPTH_EXCEEDED);
        }
        assertNameAvailable(modules, parentId, req.getName(), null);

        Module module = new Module();
        module.setProjectId(projectId);
        module.setParentId(parentId);
        module.setName(req.getName());
        module.setDescription(req.getDescription());
        module.setSort(ModuleTreeSupport.nextSort(modules, parentId));
        moduleMapper.insert(module);

        return moduleQueryService.toNodeVO(module, new ArrayList<>());
    }

    /**
     * 编辑模块：仅更新 name / description，同级重名校验（排除自身）。
     *
     * @param id  模块 id
     * @param req 请求体
     * @return 更新后的节点（含依赖列表，children 为空）
     */
    @Transactional
    public ModuleNodeVO update(Long id, ModuleReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Module exist = moduleMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.MODULE_NOT_FOUND);
        }
        List<Module> modules = moduleQueryService.loadProjectModules(exist.getProjectId());
        assertNameAvailable(modules, exist.getParentId() == null ? ROOT_PARENT_ID : exist.getParentId(),
                req.getName(), id);

        exist.setName(req.getName());
        exist.setDescription(req.getDescription());
        moduleMapper.updateById(exist);

        Map<Long, String> nameById = new HashMap<>();
        for (Module m : modules) {
            nameById.put(m.getId(), m.getName());
        }
        nameById.put(exist.getId(), exist.getName());
        List<ModuleBriefVO> dependencies = moduleQueryService
                .loadDependencies(Collections.singletonList(id), nameById)
                .getOrDefault(id, new ArrayList<>());
        return moduleQueryService.toNodeVO(exist, dependencies);
    }

    /**
     * 删除单个模块（= batchDelete 的单元素特例）：级联软删子孙 + R5-2 关联问题校验 + 物理清依赖边。
     *
     * @param id 模块 id
     */
    @Transactional
    public void delete(Long id) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Module exist = moduleMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.MODULE_NOT_FOUND);
        }
        ModuleBatchReq req = new ModuleBatchReq();
        req.setProjectId(exist.getProjectId());
        req.setIds(Collections.singletonList(id));
        batchDelete(req);
    }

    /**
     * 移动模块（Q3 即拖即存）：防环 + 深度校验 + 同级重名校验 + 改 parent_id + 目标层 sort 全量重排。
     *
     * @param id  被移动的模块 id
     * @param req 目标父级与目标层完整有序 id 列表
     */
    @Transactional
    public void move(Long id, ModuleMoveReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Module module = moduleMapper.selectById(id);
        if (module == null) {
            throw new BizException(ResultCode.MODULE_NOT_FOUND);
        }
        Long projectId = module.getProjectId();
        long targetParentId = ModuleTreeSupport.normalizeParentId(req.getTargetParentId());

        List<Module> modules = moduleQueryService.loadProjectModules(projectId);
        Map<Long, Module> byId = ModuleTreeSupport.indexById(modules);
        Map<Long, List<Module>> childrenMap = ModuleTreeSupport.buildChildrenMap(modules);

        if (targetParentId != ROOT_PARENT_ID) {
            Module target = byId.get(targetParentId);
            if (target == null) {
                Module raw = moduleMapper.selectById(targetParentId);
                if (raw == null) {
                    throw new BizException(ResultCode.MODULE_NOT_FOUND, "目标父模块不存在");
                }
                throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH, "目标父模块不属于当前项目");
            }
        }
        assertMovable(id, targetParentId, byId, childrenMap);
        assertNameAvailable(modules, targetParentId, module.getName(), id);

        module.setParentId(targetParentId);
        moduleMapper.updateById(module);

        // 内存中同步 parentId，便于计算目标层的最终成员集合
        Module inMemory = byId.get(id);
        if (inMemory != null) {
            inMemory.setParentId(targetParentId);
        }
        reorderSiblings(modules, targetParentId, req.getOrderedSiblingIds());
    }

    /**
     * 批量删除（Q5 整体原子阻断 + Q6 级联软删子孙）。
     *
     * <p>任一所选根节点（含其子孙）下存在未删除的关联问题，则<b>整体拒绝</b>，
     * message 携带「模块名(N)」明细；全部为 0 才批量软删并物理清理依赖边。</p>
     *
     * @param req 项目 id + 所选模块 id 列表
     */
    @Transactional
    public void batchDelete(ModuleBatchReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Long projectId = req.getProjectId();
        Set<Long> selected = ModuleTreeSupport.cleanIds(req.getIds());
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        if (selected.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请至少选择一个模块");
        }

        List<Module> modules = moduleQueryService.loadProjectModules(projectId);
        Map<Long, Module> byId = ModuleTreeSupport.indexById(modules);
        Map<Long, List<Module>> childrenMap = ModuleTreeSupport.buildChildrenMap(modules);
        assertAllInProject(selected, byId);

        Set<Long> scopeIds = new LinkedHashSet<>();
        List<String> blocked = new ArrayList<>();
        for (Long rootId : selected) {
            Set<Long> group = new LinkedHashSet<>();
            group.add(rootId);
            group.addAll(ModuleTreeSupport.collectDescendantIds(rootId, childrenMap));
            scopeIds.addAll(group);

            long count = countIssues(group);
            if (count > 0) {
                Module m = byId.get(rootId);
                blocked.add((m == null ? rootId.toString() : m.getName()) + "(" + count + ")");
            }
        }
        if (!blocked.isEmpty()) {
            throw new BizException(ResultCode.MODULE_HAS_ISSUES,
                    "以下模块（含子模块）存在关联问题，无法删除：" + String.join("、", blocked));
        }

        List<Long> scopeList = new ArrayList<>(scopeIds);
        moduleMapper.deleteBatchIds(scopeList);
        moduleDependencyMapper.deletePhysicalByModuleIds(scopeList);
    }

    /**
     * 批量移动：同项目内；目标父不得为所选节点自身或其子孙；
     * 所选集合中「其祖先也被选中」的节点会被忽略（子孙随祖先移动）；逐个挂到目标层末尾。
     *
     * @param req 项目 id + 所选模块 id 列表 + 目标父 id
     */
    @Transactional
    public void batchMove(ModuleBatchReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Long projectId = req.getProjectId();
        Set<Long> selected = ModuleTreeSupport.cleanIds(req.getIds());
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        if (selected.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请至少选择一个模块");
        }
        long targetParentId = ModuleTreeSupport.normalizeParentId(req.getTargetParentId());

        List<Module> modules = moduleQueryService.loadProjectModules(projectId);
        Map<Long, Module> byId = ModuleTreeSupport.indexById(modules);
        Map<Long, List<Module>> childrenMap = ModuleTreeSupport.buildChildrenMap(modules);
        assertAllInProject(selected, byId);

        if (targetParentId != ROOT_PARENT_ID && byId.get(targetParentId) == null) {
            Module raw = moduleMapper.selectById(targetParentId);
            if (raw == null) {
                throw new BizException(ResultCode.MODULE_NOT_FOUND, "目标父模块不存在");
            }
            throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH, "目标父模块不属于当前项目");
        }

        // 仅移动「顶层被选节点」：其祖先也在所选集合内的节点直接忽略
        List<Long> topLevel = new ArrayList<>();
        for (Long id : selected) {
            if (!ModuleTreeSupport.hasSelectedAncestor(id, selected, byId)) {
                topLevel.add(id);
            }
        }
        if (topLevel.isEmpty()) {
            return;
        }
        for (Long id : topLevel) {
            assertMovable(id, targetParentId, byId, childrenMap);
        }
        // 同级重名：与目标层既有节点比对，同时组内互相比对
        Set<String> occupied = new HashSet<>();
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == targetParentId && !selected.contains(m.getId())) {
                occupied.add(m.getName());
            }
        }
        for (Long id : topLevel) {
            Module m = byId.get(id);
            if (m == null) {
                continue;
            }
            if (!occupied.add(m.getName())) {
                throw new BizException(ResultCode.MODULE_NAME_DUPLICATE,
                        "目标父级下已存在同名模块：" + m.getName());
            }
        }

        int sort = ModuleTreeSupport.nextSort(modules, targetParentId);
        for (Long id : topLevel) {
            Module m = byId.get(id);
            if (m == null) {
                continue;
            }
            Module update = new Module();
            update.setId(id);
            update.setParentId(targetParentId);
            update.setSort(sort++);
            moduleMapper.updateById(update);
        }
    }

    /**
     * 设置模块依赖（全量替换）：同项目 / 非自身校验 + DFS 防环 + 物理清空重建。
     *
     * @param id  依赖方模块 id（A）
     * @param req 被依赖模块 id 列表（空 / null = 清空）
     * @return 替换后的依赖列表
     */
    @Transactional
    public List<ModuleBriefVO> setDependencies(Long id, ModuleDependencyReq req) {
        permissionService.requirePermission(PERM_MODULE_WRITE);
        Module module = moduleMapper.selectById(id);
        if (module == null) {
            throw new BizException(ResultCode.MODULE_NOT_FOUND);
        }
        Long projectId = module.getProjectId();
        List<Module> modules = moduleQueryService.loadProjectModules(projectId);
        Map<Long, Module> byId = ModuleTreeSupport.indexById(modules);

        // 保序去重（cleanIds 基于 LinkedHashSet，保留前端提交顺序）
        List<Long> targets = new ArrayList<>(
                ModuleTreeSupport.cleanIds(req == null ? null : req.getDependsOnIds()));
        for (Long toId : targets) {
            if (Objects.equals(toId, id)) {
                throw new BizException(ResultCode.MODULE_DEPENDENCY_CYCLE, "模块不能依赖自身");
            }
            if (byId.get(toId) == null) {
                Module raw = moduleMapper.selectById(toId);
                if (raw == null) {
                    throw new BizException(ResultCode.MODULE_NOT_FOUND, "被依赖模块不存在：" + toId);
                }
                throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH, "被依赖模块不属于当前项目");
            }
        }

        // 构图：项目内现有依赖边（排除本模块旧边）+ 本次新边
        Map<Long, Set<Long>> graph = new HashMap<>();
        if (!modules.isEmpty()) {
            List<Long> allIds = new ArrayList<>(byId.keySet());
            List<ModuleDependency> edges = moduleDependencyMapper.selectList(
                    new LambdaQueryWrapper<ModuleDependency>()
                            .in(ModuleDependency::getFromModuleId, allIds)
                            .eq(ModuleDependency::getDeleted, 0));
            for (ModuleDependency e : edges) {
                if (Objects.equals(e.getFromModuleId(), id)) {
                    continue;
                }
                graph.computeIfAbsent(e.getFromModuleId(), k -> new HashSet<>()).add(e.getToModuleId());
            }
        }
        graph.put(id, new HashSet<>(targets));
        if (ModuleTreeSupport.hasDependencyCycle(id, graph)) {
            throw new BizException(ResultCode.MODULE_DEPENDENCY_CYCLE);
        }

        moduleDependencyMapper.deletePhysicalByFromId(id);
        List<ModuleBriefVO> result = new ArrayList<>();
        for (Long toId : targets) {
            ModuleDependency edge = new ModuleDependency();
            edge.setFromModuleId(id);
            edge.setToModuleId(toId);
            edge.setDeleted(0);
            moduleDependencyMapper.insert(edge);

            Module to = byId.get(toId);
            ModuleBriefVO brief = new ModuleBriefVO();
            brief.setId(toId);
            brief.setName(to == null ? null : to.getName());
            result.add(brief);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 私有辅助（写前校验与落库，均依赖 ModuleTreeSupport 做纯计算）
    // ------------------------------------------------------------------

    /**
     * 校验所选 id 全部属于当前项目且未删除。
     */
    private void assertAllInProject(Collection<Long> ids, Map<Long, Module> byId) {
        for (Long id : ids) {
            if (byId.get(id) == null) {
                Module raw = moduleMapper.selectById(id);
                if (raw == null) {
                    throw new BizException(ResultCode.MODULE_NOT_FOUND, "模块不存在：" + id);
                }
                throw new BizException(ResultCode.MODULE_PROJECT_MISMATCH, "模块不属于当前项目：" + id);
            }
        }
    }

    /**
     * 移动可行性校验：防环（目标 ∈ 自身 ∪ 子孙）+ 深度上限（目标深度 + 子树高度 ≤ 10）。
     */
    private void assertMovable(Long id, long targetParentId,
                               Map<Long, Module> byId,
                               Map<Long, List<Module>> childrenMap) {
        if (targetParentId == id) {
            throw new BizException(ResultCode.MODULE_MOVE_CYCLE);
        }
        Set<Long> descendants = ModuleTreeSupport.collectDescendantIds(id, childrenMap);
        if (descendants.contains(targetParentId)) {
            throw new BizException(ResultCode.MODULE_MOVE_CYCLE);
        }
        if (ModuleTreeSupport.depthOf(targetParentId, byId)
                + ModuleTreeSupport.subtreeHeight(id, childrenMap) > MAX_DEPTH) {
            throw new BizException(ResultCode.MODULE_DEPTH_EXCEEDED);
        }
    }

    /**
     * 同级重名校验（excludeId 为自身时排除）。
     */
    private void assertNameAvailable(List<Module> modules, long parentId, String name, Long excludeId) {
        if (name == null) {
            return;
        }
        for (Module m : modules) {
            if (excludeId != null && Objects.equals(m.getId(), excludeId)) {
                continue;
            }
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == parentId && name.equals(m.getName())) {
                throw new BizException(ResultCode.MODULE_NAME_DUPLICATE);
            }
        }
    }

    /**
     * 目标层级 sort 全量重排为 1..n。
     *
     * <p>排列顺序由纯函数 {@link ModuleTreeSupport#planSiblingOrder} 计算，
     * 本方法只负责按结果落库（写操作不外迁，保持事务边界在本类）。</p>
     *
     * @param modules           内存中的项目全量模块（被移动节点的 parentId 已同步）
     * @param targetParentId    目标层级
     * @param orderedSiblingIds 前端给出的目标层完整有序 id 列表，可为空
     */
    private void reorderSiblings(List<Module> modules, long targetParentId, List<Long> orderedSiblingIds) {
        List<Module> ordered =
                ModuleTreeSupport.planSiblingOrder(modules, targetParentId, orderedSiblingIds);
        int seq = 1;
        for (Module m : ordered) {
            int newSort = seq++;
            if (m.getSort() != null && m.getSort() == newSort) {
                continue;
            }
            Module update = new Module();
            update.setId(m.getId());
            update.setSort(newSort);
            moduleMapper.updateById(update);
            m.setSort(newSort);
        }
    }

    /**
     * 统计一批模块下未删除的关联问题数（一次 IN 查询）。
     */
    private long countIssues(Collection<Long> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) {
            return 0L;
        }
        Long count = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .in(Issue::getModuleId, scopeIds)
                .eq(Issue::getDeleted, 0));
        return count == null ? 0L : count;
    }
}
