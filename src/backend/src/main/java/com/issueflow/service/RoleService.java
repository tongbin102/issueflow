package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.RolePermissionReq;
import com.issueflow.dto.req.RoleReq;
import com.issueflow.dto.resp.RoleVO;
import com.issueflow.entity.Role;
import com.issueflow.entity.RolePermission;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务：CRUD + 权限分配（整体替换）+ 内置角色保护。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionService permissionService;

    /**
     * 角色列表（按 id 升序）
     */
    public List<RoleVO> list() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        return roles.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 新建角色（码不可与内置重复；新建后刷新内存映射以便即时鉴权）
     */
    public RoleVO create(RoleReq req) {
        if (Constants.BUILTIN_ROLE_CODES.contains(req.getCode())) {
            throw new BizException(ResultCode.ROLE_CODE_DUPLICATE);
        }
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, req.getCode())) > 0) {
            throw new BizException(ResultCode.ROLE_CODE_DUPLICATE);
        }
        Role role = new Role();
        role.setCode(req.getCode());
        role.setName(req.getName());
        role.setDescription(req.getDescription());
        role.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        permissionService.refreshAll();
        return toVO(role);
    }

    /**
     * 编辑角色（内置角色禁止改码；此处仅允许修改名称/描述）
     */
    public RoleVO update(Long id, RoleReq req) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        role.setName(req.getName());
        role.setDescription(req.getDescription());
        roleMapper.updateById(role);
        return toVO(role);
    }

    /**
     * 删除角色（内置角色受保护；删除后清理权限映射与缓存）
     */
    public void delete(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (Constants.BUILTIN_ROLE_CODES.contains(role.getCode())) {
            throw new BizException(ResultCode.ROLE_BUILTIN_PROTECTED);
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.deleteByRoleId(id);
        permissionService.invalidate(id);
        permissionService.refreshAll();
    }

    /**
     * 获取角色已分配权限码集合
     */
    public List<String> getPermissions(Long id) {
        return new ArrayList<>(permissionService.getPermissions(id));
    }

    /**
     * 分配权限（整体替换：先删后插 + 失效缓存）
     */
    @Transactional
    public void assignPermissions(Long id, List<String> codes) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        rolePermissionMapper.deleteByRoleId(id);
        if (codes != null && !codes.isEmpty()) {
            List<RolePermission> batch = new ArrayList<>();
            for (String code : codes) {
                Long permId = permissionService.resolvePermissionId(code);
                if (permId != null) {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(id);
                    rp.setPermissionId(permId);
                    rp.setCreatedAt(LocalDateTime.now());
                    batch.add(rp);
                }
            }
            if (!batch.isEmpty()) {
                rolePermissionMapper.insertBatch(batch);
            }
        }
        permissionService.invalidate(id);
    }

    private RoleVO toVO(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        int count = rolePermissionMapper.selectPermissionCodesByRoleId(role.getId()).size();
        vo.setPermissionCount(count);
        vo.setBuiltin(Constants.BUILTIN_ROLE_CODES.contains(role.getCode()));
        return vo;
    }

    /**
     * 兼容旧调用：从请求体分配权限
     */
    public void assignPermissions(Long id, RolePermissionReq req) {
        assignPermissions(id, req == null ? null : req.getPermissionCodes());
    }
}
