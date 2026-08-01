package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.Permission;
import com.issueflow.entity.Role;
import com.issueflow.mapper.PermissionMapper;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.RolePermissionMapper;
import com.issueflow.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务：鉴权助手 + 角色权限缓存（Redis）+ 内存 code/id 映射预热。
 *
 * 设计要点：
 * 1. requirePermission：ADMIN 首判放行；其余取该角色权限集与入参做 OR 语意判定（存在交集即放行）。
 * 2. 权限集缓存于 Redis，key = perm:role:{roleId}，value 为逗号分隔权限码字符串；变更即失效。
 * 3. @PostConstruct init() 预热 roleCode→roleId 与 permissionCode→permissionId 内存映射，避免硬编码 ID。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;

    /** roleCode -> roleId 内存映射（@PostConstruct 预热） */
    private final Map<String, Long> roleCodeToId = new ConcurrentHashMap<>();

    /** permissionCode -> permissionId 内存映射（@PostConstruct 预热） */
    private final Map<String, Long> permCodeToId = new ConcurrentHashMap<>();

    /**
     * 启动预热：加载角色/权限映射并预填各角色权限缓存。
     */
    @PostConstruct
    public void init() {
        refreshAll();
    }

    /**
     * 刷新全部内存映射 + 预热所有角色权限缓存（新增/删除角色后调用，确保新角色可即时查询）。
     *
     * <p><b>M5 修复（2026-08-01）</b>：此前本方法先加载映射再调用 {@link #getPermissions(Long)}，
     * 而 {@code getPermissions} 命中 Redis 即直接返回——导致 <b>refreshAll 实际并不会刷新
     * 已存在的缓存</b>，角色权限变更后旧值可能长期滞留（写后失效缺口）。
     * 现改为「<b>先全量失效、再从 DB 预热</b>」，保证调用后缓存与 DB 强一致。</p>
     */
    public void refreshAll() {
        loadRoleCodeMap();
        loadPermCodeMap();
        // 先失效：避免命中旧缓存导致预热成为空操作
        invalidateAll();
        for (Long roleId : roleCodeToId.values()) {
            getPermissions(roleId);
        }
    }

    /**
     * 失效<b>全部</b>角色权限缓存（M5 新增）。
     *
     * <p>适用于「影响面跨角色」的写操作：角色增删、权限体系调整、菜单/权限批量变更、
     * 系统数据初始化等。单角色变更请优先用 {@link #invalidate(Long)} 以减小抖动。</p>
     *
     * <p>失败不抛出：缓存清理属尽力而为，异常仅吞掉以免阻断主业务事务
     * （下次读取时 {@link #getPermissions(Long)} 会自然回源 DB）。</p>
     */
    public void invalidateAll() {
        try {
            Set<String> keys = redisTemplate.keys(Constants.REDIS_PERM_ROLE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // 缓存清理失败不影响主流程：读取路径会回源 DB 并重建缓存
            log.warn("invalidateAll: clear role permission cache failed: {}", e.getMessage());
        }
    }

    private void loadRoleCodeMap() {
        roleCodeToId.clear();
        List<Role> roles = roleMapper.selectList(null);
        for (Role r : roles) {
            roleCodeToId.put(r.getCode(), r.getId());
        }
    }

    private void loadPermCodeMap() {
        permCodeToId.clear();
        List<Permission> perms = permissionMapper.selectList(null);
        for (Permission p : perms) {
            permCodeToId.put(p.getCode(), p.getId());
        }
    }

    /**
     * 角色码 -> 角色 id（内存映射优先，DB 兜底并回填映射）。
     */
    private Long resolveRoleId(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        Long id = roleCodeToId.get(roleCode);
        if (id == null) {
            Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode));
            if (role != null) {
                id = role.getId();
                roleCodeToId.put(roleCode, id);
            }
        }
        return id;
    }

    /**
     * 取当前登录用户的全部角色 id（Phase8 W3 #11：由单角色升级为多角色）。
     */
    private List<Long> currentRoleIds() {
        List<String> roleCodes = SecurityUtils.getCurrentRoleCodes();
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(roleCodes.size());
        for (String roleCode : roleCodes) {
            Long id = resolveRoleId(roleCode);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 鉴权：持有 ADMIN 直接放行；其余按<b>全部角色权限并集</b>与入参做 OR 语意判定。
     *
     * <p>Phase8 W3 #11：多角色下权限取并集——用户任一角色拥有该权限即放行。</p>
     *
     * @param required 允许的权限码（任一命中即放行）
     */
    public void requirePermission(String... required) {
        if (SecurityUtils.hasRole(Constants.ROLE_ADMIN)) {
            return;
        }
        if (required == null || required.length == 0) {
            return;
        }
        Set<String> owned = new HashSet<>();
        for (Long roleId : currentRoleIds()) {
            owned.addAll(getPermissions(roleId));
        }
        for (String perm : required) {
            if (owned.contains(perm)) {
                return;
            }
        }
        throw new BizException(ResultCode.PERMISSION_DENIED);
    }

    /**
     * 统一「必须是管理员」门禁（M4 鉴权收口，2026-08-01）。
     *
     * <p>收敛此前散落在 Controller / Service 中的重复写法
     * {@code if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) throw ...}。</p>
     *
     * <p><b>行为与原写法严格等价</b>：{@code SecurityUtils.getCurrentRoleCode()} 在持有 ADMIN 时
     * 必定返回 ADMIN（见其取值优先级第 1 条），故
     * {@code ROLE_ADMIN.equals(getCurrentRoleCode())} ⟺ {@code hasRole(ADMIN)}；
     * 抛出的异常类型与错误码（{@link ResultCode#PERMISSION_DENIED}）也保持不变，
     * 前端契约零影响。</p>
     *
     * @throws BizException 当前登录用户不持有 ADMIN 角色
     */
    public void requireAdmin() {
        if (!SecurityUtils.hasRole(Constants.ROLE_ADMIN)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }

    /**
     * 取角色权限码集合：Redis 优先，DB 兜底并写回缓存。
     */
    public Set<String> getPermissions(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        String key = Constants.REDIS_PERM_ROLE_PREFIX + roleId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return parseCodes(cached);
        }
        List<String> codes = rolePermissionMapper.selectPermissionCodesByRoleId(roleId);
        Set<String> set = new HashSet<>(codes);
        redisTemplate.opsForValue().set(key, String.join(",", codes));
        return set;
    }

    /**
     * 失效某角色权限缓存（角色权限变更后调用）。
     */
    public void invalidate(Long roleId) {
        if (roleId == null) {
            return;
        }
        redisTemplate.delete(Constants.REDIS_PERM_ROLE_PREFIX + roleId);
    }

    /**
     * 解析权限码 -> 权限 id（供 RoleService 分配权限时批量转换；内存映射优先，DB 兜底）。
     */
    public Long resolvePermissionId(String code) {
        Long id = permCodeToId.get(code);
        if (id == null) {
            Permission p = permissionMapper.selectOne(
                    new LambdaQueryWrapper<Permission>().eq(Permission::getCode, code));
            if (p != null) {
                id = p.getId();
                permCodeToId.put(code, id);
            }
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseCodes(Object cached) {
        Set<String> set = new HashSet<>();
        String raw = cached instanceof String
                ? (String) cached
                : (cached == null ? "" : cached.toString());
        if (raw != null && !raw.isEmpty() && !"null".equals(raw)) {
            for (String s : raw.split(",")) {
                if (s != null && !s.trim().isEmpty()) {
                    set.add(s.trim());
                }
            }
        }
        return set;
    }
}
