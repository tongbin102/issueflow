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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

    /** 模块层级软上限（产品约定，超出即拒绝） */
    private static final int MAX_DEPTH = 10;

    /** 根节点的 parent_id 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    /** 模块路径分隔符 */
    private static final String PATH_SEPARATOR = " > ";

    /** 模块写操作复用的权限码 */
    private static final String PERM_MODULE_WRITE = "project:update";

    private final ModuleMapper moduleMapper;
    private final ModuleDependencyMapper moduleDependencyMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 查询某项目的完整模块树（仅登录即可访问）。
     *
     * <p>共两次查询：module 全量 + dependency IN 全量，随后内存组树并回填依赖。</p>
     *
     * @param projectId 项目 id，必填
     * @return 根级节点数组，children 递归，同级按 sort 升序
     */
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
        Map<Long, List<Module>> childrenMap = buildChildrenMap(modules);
        return buildNodes(ROOT_PARENT_ID, childrenMap, dependencyMap);
    }

    /**
     * 批量构建模块「父 &gt; 子 &gt; 孙」全路径映射，供 IssueService 回填 modulePath（禁止 N+1）。
     *
     * <p>流程：批查这些 module → 汇总涉及 projectId → 一次查这些项目的全量模块 → 内存向上拼路径。</p>
     *
     * @param moduleIds 模块 id 集合（允许包含 null / 重复，内部会清洗）
     * @return moduleId -> 全路径；查不到的 id 不出现在结果中
     */
    public Map<Long, String> pathMap(Collection<Long> moduleIds) {
        Map<Long, String> result = new HashMap<>();
        Set<Long> targetIds = cleanIds(moduleIds);
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
    // 写操作
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
        long parentId = normalizeParentId(req.getParentId());

        List<Module> modules = loadProjectModules(projectId);
        Map<Long, Module> byId = indexById(modules);
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
        if (depthOf(parentId, byId) + 1 > MAX_DEPTH) {
            throw new BizException(ResultCode.MODULE_DEPTH_EXCEEDED);
        }
        assertNameAvailable(modules, parentId, req.getName(), null);

        Module module = new Module();
        module.setProjectId(projectId);
        module.setParentId(parentId);
        module.setName(req.getName());
        module.setDescription(req.getDescription());
        module.setSort(nextSort(modules, parentId));
        moduleMapper.insert(module);

        return toNodeVO(module, new ArrayList<>());
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
        List<Module> modules = loadProjectModules(exist.getProjectId());
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
        List<ModuleBriefVO> dependencies = loadDependencies(Collections.singletonList(id), nameById)
                .getOrDefault(id, new ArrayList<>());
        return toNodeVO(exist, dependencies);
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
        long targetParentId = normalizeParentId(req.getTargetParentId());

        List<Module> modules = loadProjectModules(projectId);
        Map<Long, Module> byId = indexById(modules);
        Map<Long, List<Module>> childrenMap = buildChildrenMap(modules);

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
        Set<Long> selected = cleanIds(req.getIds());
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        if (selected.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请至少选择一个模块");
        }

        List<Module> modules = loadProjectModules(projectId);
        Map<Long, Module> byId = indexById(modules);
        Map<Long, List<Module>> childrenMap = buildChildrenMap(modules);
        assertAllInProject(selected, byId);

        Set<Long> scopeIds = new LinkedHashSet<>();
        List<String> blocked = new ArrayList<>();
        for (Long rootId : selected) {
            Set<Long> group = new LinkedHashSet<>();
            group.add(rootId);
            group.addAll(collectDescendantIds(rootId, childrenMap));
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
        Set<Long> selected = cleanIds(req.getIds());
        if (projectId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "projectId 不能为空");
        }
        if (selected.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请至少选择一个模块");
        }
        long targetParentId = normalizeParentId(req.getTargetParentId());

        List<Module> modules = loadProjectModules(projectId);
        Map<Long, Module> byId = indexById(modules);
        Map<Long, List<Module>> childrenMap = buildChildrenMap(modules);
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
            if (!hasSelectedAncestor(id, selected, byId)) {
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

        int sort = nextSort(modules, targetParentId);
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
        List<Module> modules = loadProjectModules(projectId);
        Map<Long, Module> byId = indexById(modules);

        // 保序去重（cleanIds 基于 LinkedHashSet，保留前端提交顺序）
        List<Long> targets = new ArrayList<>(cleanIds(req == null ? null : req.getDependsOnIds()));
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
        if (hasDependencyCycle(id, graph)) {
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
    // 内存组树工具（禁止逐层查库）
    // ------------------------------------------------------------------

    /**
     * 一次性加载某项目的全部未删除模块（同级按 sort 升序、id 升序兜底）。
     */
    private List<Module> loadProjectModules(Long projectId) {
        return moduleMapper.selectList(new LambdaQueryWrapper<Module>()
                .eq(Module::getProjectId, projectId)
                .eq(Module::getDeleted, 0)
                .orderByAsc(Module::getSort)
                .orderByAsc(Module::getId));
    }

    /**
     * 构建 parentId -> 有序子节点列表 映射（输入需已按 sort 排序）。
     */
    private Map<Long, List<Module>> buildChildrenMap(List<Module> modules) {
        Map<Long, List<Module>> map = new HashMap<>();
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            map.computeIfAbsent(pid, k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    /**
     * 内存收集某节点的全部子孙 id（不含自身），迭代实现，天然防御脏数据成环。
     */
    private Set<Long> collectDescendantIds(Long id, Map<Long, List<Module>> childrenMap) {
        Set<Long> result = new LinkedHashSet<>();
        if (id == null) {
            return result;
        }
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(id);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            List<Module> children = childrenMap.get(cur);
            if (children == null) {
                continue;
            }
            for (Module c : children) {
                if (result.add(c.getId())) {
                    stack.push(c.getId());
                }
            }
        }
        return result;
    }

    /**
     * 内存计算节点深度：根节点为 1，虚拟根（0）为 0。
     */
    private int depthOf(Long moduleId, Map<Long, Module> byId) {
        if (moduleId == null || moduleId == ROOT_PARENT_ID) {
            return 0;
        }
        int depth = 0;
        Long cur = moduleId;
        Set<Long> guard = new HashSet<>();
        while (cur != null && cur != ROOT_PARENT_ID && guard.add(cur)) {
            Module m = byId.get(cur);
            if (m == null) {
                break;
            }
            depth++;
            cur = m.getParentId();
        }
        return depth;
    }

    /**
     * 内存计算子树高度：叶子为 1。
     */
    private int subtreeHeight(Long id, Map<Long, List<Module>> childrenMap) {
        List<Module> children = childrenMap.get(id);
        if (children == null || children.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (Module c : children) {
            int h = subtreeHeight(c.getId(), childrenMap);
            if (h > max) {
                max = h;
            }
        }
        return max + 1;
    }

    /**
     * 依赖图 DFS 防环：从 startId 出发若可回到 startId 则判定成环。
     */
    private boolean hasDependencyCycle(Long startId, Map<Long, Set<Long>> graph) {
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        for (Long seed : graph.getOrDefault(startId, Collections.emptySet())) {
            if (Objects.equals(seed, startId)) {
                return true;
            }
            stack.push(seed);
        }
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (!visited.add(cur)) {
                continue;
            }
            for (Long next : graph.getOrDefault(cur, Collections.emptySet())) {
                if (Objects.equals(next, startId)) {
                    return true;
                }
                if (!visited.contains(next)) {
                    stack.push(next);
                }
            }
        }
        return false;
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
     * 实体 -> 节点 VO（children 由调用方填充）。
     */
    private ModuleNodeVO toNodeVO(Module m, List<ModuleBriefVO> dependencies) {
        ModuleNodeVO vo = new ModuleNodeVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setParentId(m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId());
        vo.setName(m.getName());
        vo.setDescription(m.getDescription());
        vo.setSort(m.getSort());
        vo.setDependencies(dependencies);
        vo.setDependencyCount(dependencies.size());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    /**
     * 批量加载 fromId -> 依赖列表（一次 IN 查询，禁止逐节点单查）。
     */
    private Map<Long, List<ModuleBriefVO>> loadDependencies(Collection<Long> fromIds, Map<Long, String> nameById) {
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
            if (pid == null || pid == ROOT_PARENT_ID) {
                break;
            }
            cur = byId.get(pid);
        }
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }

    /**
     * id -> 实体 映射。
     */
    private Map<Long, Module> indexById(List<Module> modules) {
        Map<Long, Module> byId = new HashMap<>();
        for (Module m : modules) {
            byId.put(m.getId(), m);
        }
        return byId;
    }

    /**
     * parentId 归一：null 视为根级 0。
     */
    private long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /**
     * 清洗 id 集合：去 null、去重（无序）。
     */
    private Set<Long> cleanIds(Collection<Long> ids) {
        Set<Long> set = new LinkedHashSet<>();
        if (ids == null) {
            return set;
        }
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return set;
    }

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
        Set<Long> descendants = collectDescendantIds(id, childrenMap);
        if (descendants.contains(targetParentId)) {
            throw new BizException(ResultCode.MODULE_MOVE_CYCLE);
        }
        if (depthOf(targetParentId, byId) + subtreeHeight(id, childrenMap) > MAX_DEPTH) {
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
     * 取某层级的下一个 sort 值（= 同级 max + 1，最小为 1）。
     */
    private int nextSort(List<Module> modules, long parentId) {
        int max = 0;
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == parentId && m.getSort() != null && m.getSort() > max) {
                max = m.getSort();
            }
        }
        return max + 1;
    }

    /**
     * 目标层级 sort 全量重排为 1..n：先按 orderedSiblingIds 给定顺序，
     * 未出现在列表中的同级节点按原 sort 顺序追加在后面。
     *
     * @param modules           内存中的项目全量模块（被移动节点的 parentId 已同步）
     * @param targetParentId    目标层级
     * @param orderedSiblingIds 前端给出的目标层完整有序 id 列表，可为空
     */
    private void reorderSiblings(List<Module> modules, long targetParentId, List<Long> orderedSiblingIds) {
        List<Module> siblings = new ArrayList<>();
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == targetParentId) {
                siblings.add(m);
            }
        }
        if (siblings.isEmpty()) {
            return;
        }
        Map<Long, Module> siblingById = new HashMap<>();
        for (Module m : siblings) {
            siblingById.put(m.getId(), m);
        }

        List<Module> ordered = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        if (orderedSiblingIds != null) {
            for (Long sid : orderedSiblingIds) {
                if (sid == null) {
                    continue;
                }
                Module m = siblingById.get(sid);
                if (m != null && used.add(sid)) {
                    ordered.add(m);
                }
            }
        }
        for (Module m : siblings) {
            if (used.add(m.getId())) {
                ordered.add(m);
            }
        }

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

    /**
     * 判断某节点的祖先链上是否存在被选中的节点（batch-move 只移「顶层被选节点」）。
     */
    private boolean hasSelectedAncestor(Long id, Set<Long> selected, Map<Long, Module> byId) {
        Module cur = byId.get(id);
        if (cur == null) {
            return false;
        }
        Set<Long> guard = new HashSet<>();
        guard.add(id);
        Long pid = cur.getParentId();
        while (pid != null && pid != ROOT_PARENT_ID && guard.add(pid)) {
            if (selected.contains(pid)) {
                return true;
            }
            Module parent = byId.get(pid);
            if (parent == null) {
                return false;
            }
            pid = parent.getParentId();
        }
        return false;
    }
}
