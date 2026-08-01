package com.issueflow.util;

import com.issueflow.entity.Module;

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
 * 模块树纯计算工具（2026-08-01 由 {@code ModuleService} 拆出，F1-a）。
 *
 * <p><b>无状态 static 工具类</b>：不依赖 Spring 容器、不访问数据库，全部为纯函数，
 * 可直接单元测试。所有「查子孙 / 深度 / 高度 / 组树 / 排序」计算<b>一律在内存完成</b>，
 * 严禁在本类内做任何逐层查库。</p>
 *
 * <p><b>本次拆分为纯结构调整，所有方法实现逐行搬迁自 {@code ModuleService}，
 * 算法与边界行为（含对脏数据成环的 guard 防御）完全未改。</b></p>
 *
 * <p>注意：本类引用的 {@link Module} 是 {@code com.issueflow.entity.Module}，
 * 与 {@code java.lang.Module} 同名，必须保留显式 import。</p>
 */
public final class ModuleTreeSupport {

    /** 根节点的 parent_id 约定值（邻接表中 0 表示根） */
    public static final long ROOT_PARENT_ID = 0L;

    private ModuleTreeSupport() {
    }

    /**
     * 构建 parentId -&gt; 有序子节点列表 映射（输入需已按 sort 排序）。
     *
     * @param modules 项目全量模块（已排序）
     * @return parentId 到子节点列表的映射，永不为 null
     */
    public static Map<Long, List<Module>> buildChildrenMap(List<Module> modules) {
        Map<Long, List<Module>> map = new HashMap<>();
        if (modules == null) {
            return map;
        }
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            map.computeIfAbsent(pid, k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    /**
     * 内存收集某节点的全部子孙 id（不含自身），迭代实现，天然防御脏数据成环。
     *
     * @param id          起始节点 id，为 null 返回空集合
     * @param childrenMap 由 {@link #buildChildrenMap(List)} 生成的映射
     * @return 子孙 id 有序集合（LinkedHashSet），永不为 null
     */
    public static Set<Long> collectDescendantIds(Long id, Map<Long, List<Module>> childrenMap) {
        Set<Long> result = new LinkedHashSet<>();
        if (id == null || childrenMap == null) {
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
     *
     * @param moduleId 节点 id
     * @param byId     由 {@link #indexById(List)} 生成的映射
     * @return 深度值
     */
    public static int depthOf(Long moduleId, Map<Long, Module> byId) {
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
     *
     * @param id          子树根 id
     * @param childrenMap 由 {@link #buildChildrenMap(List)} 生成的映射
     * @return 高度值，最小为 1
     */
    public static int subtreeHeight(Long id, Map<Long, List<Module>> childrenMap) {
        List<Module> children = childrenMap == null ? null : childrenMap.get(id);
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
     * id -&gt; 实体 映射。
     *
     * @param modules 模块列表
     * @return id 到实体的映射，永不为 null
     */
    public static Map<Long, Module> indexById(List<Module> modules) {
        Map<Long, Module> byId = new HashMap<>();
        if (modules == null) {
            return byId;
        }
        for (Module m : modules) {
            byId.put(m.getId(), m);
        }
        return byId;
    }

    /**
     * parentId 归一：null 视为根级 0。
     *
     * @param parentId 原始父 id，允许 null
     * @return 归一后的 long 值
     */
    public static long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /**
     * 清洗 id 集合：去 null、去重（保序，基于 LinkedHashSet）。
     *
     * @param ids 原始 id 集合，允许 null
     * @return 清洗后的有序集合，永不为 null
     */
    public static Set<Long> cleanIds(Collection<Long> ids) {
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
     * 判断某节点的祖先链上是否存在被选中的节点（batch-move 只移「顶层被选节点」）。
     *
     * @param id       待判定节点 id
     * @param selected 被选中的节点 id 集合
     * @param byId     id 到实体的映射
     * @return 祖先链上存在被选中节点返回 true
     */
    public static boolean hasSelectedAncestor(Long id, Set<Long> selected, Map<Long, Module> byId) {
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

    /**
     * 取某层级的下一个 sort 值（= 同级 max + 1，最小为 1）。
     *
     * @param modules  项目全量模块
     * @param parentId 目标层级的父 id
     * @return 下一个可用 sort 值
     */
    public static int nextSort(List<Module> modules, long parentId) {
        int max = 0;
        if (modules == null) {
            return 1;
        }
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == parentId && m.getSort() != null && m.getSort() > max) {
                max = m.getSort();
            }
        }
        return max + 1;
    }

    /**
     * 计算目标层级的最终排列顺序（纯函数，<b>不落库</b>）。
     *
     * <p>先按 {@code orderedSiblingIds} 给定顺序排列，未出现在该列表中的同级节点
     * 按原 sort 顺序追加在后面。调用方拿到结果后按下标 1..n 落 sort。</p>
     *
     * <p><b>拆分说明</b>：架构文档将 {@code reorderSiblings} 整体归入本工具类，
     * 但其原实现内含 {@code moduleMapper.updateById} 落库副作用，与「无状态 static 纯函数」
     * 定位冲突。故此处仅抽出<b>纯排序计算</b>部分，落库循环保留在
     * {@code ModuleService#reorderSiblings} 内（写操作与事务边界不外迁）。</p>
     *
     * @param modules           内存中的项目全量模块（被移动节点的 parentId 应已同步）
     * @param targetParentId    目标层级
     * @param orderedSiblingIds 前端给出的目标层完整有序 id 列表，可为 null
     * @return 目标层级按最终顺序排列的节点列表；该层无节点时返回空列表
     */
    public static List<Module> planSiblingOrder(List<Module> modules,
                                                long targetParentId,
                                                List<Long> orderedSiblingIds) {
        List<Module> siblings = new ArrayList<>();
        if (modules == null) {
            return siblings;
        }
        for (Module m : modules) {
            long pid = m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId();
            if (pid == targetParentId) {
                siblings.add(m);
            }
        }
        if (siblings.isEmpty()) {
            return new ArrayList<>();
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
        return ordered;
    }

    /**
     * 依赖图 DFS 防环：从 startId 出发若可回到 startId 则判定成环。
     *
     * @param startId 起点模块 id
     * @param graph   fromId -&gt; toId 集合 的依赖图
     * @return 成环返回 true
     */
    public static boolean hasDependencyCycle(Long startId, Map<Long, Set<Long>> graph) {
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
}
