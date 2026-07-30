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
     */
    public void refreshAll() {
        loadRoleCodeMap();
        loadPermCodeMap();
        for (Long roleId : roleCodeToId.values()) {
            getPermissions(roleId);
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
     * 取当前登录用户的角色 id（基于 JWT 携带的 roleCode 解析；兜底查库）。
     */
    private Long currentRoleId() {
        String roleCode = SecurityUtils.getCurrentRoleCode();
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
     * 鉴权：ADMIN 直接放行；其余按权限码集合 OR 语意判定。
     *
     * @param required 允许的权限码（任一命中即放行）
     */
    public void requirePermission(String... required) {
        String roleCode = SecurityUtils.getCurrentRoleCode();
        if (Constants.ROLE_ADMIN.equals(roleCode)) {
            return;
        }
        if (required == null || required.length == 0) {
            return;
        }
        Set<String> owned = getPermissions(currentRoleId());
        for (String perm : required) {
            if (owned.contains(perm)) {
                return;
            }
        }
        throw new BizException(ResultCode.PERMISSION_DENIED);
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
