package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.RolePermissionReq;
import com.issueflow.dto.req.RoleReq;
import com.issueflow.dto.resp.RoleVO;
import com.issueflow.service.PermissionService;
import com.issueflow.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器（/api/roles）：CRUD + 权限分配 + 缓存刷新。
 * 注：原 UserController 的 GET /api/roles 列表已迁移至此。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    /**
     * 角色列表
     */
    @GetMapping
    public Result<List<RoleVO>> list() {
        permissionService.requirePermission("role:list");
        return Result.success(roleService.list());
    }

    /**
     * 新建角色（码不可与内置重复）
     */
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleReq req) {
        permissionService.requirePermission("role:create");
        return Result.success(roleService.create(req));
    }

    /**
     * 编辑角色（内置角色不可改码）
     */
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleReq req) {
        permissionService.requirePermission("role:update");
        return Result.success(roleService.update(id, req));
    }

    /**
     * 删除角色（内置角色受保护）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.requirePermission("role:delete");
        roleService.delete(id);
        return Result.success();
    }

    /**
     * 获取角色已分配权限码集合
     */
    @GetMapping("/{id}/permissions")
    public Result<List<String>> getPermissions(@PathVariable Long id) {
        permissionService.requirePermission("role:assign");
        return Result.success(roleService.getPermissions(id));
    }

    /**
     * 分配角色权限（整体替换）
     */
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionReq req) {
        permissionService.requirePermission("role:assign");
        roleService.assignPermissions(id, req);
        return Result.success();
    }

    /**
     * 强制刷新全部角色权限缓存
     */
    @PostMapping("/permissions/refresh")
    public Result<Void> refresh() {
        permissionService.requirePermission("role:assign");
        permissionService.refreshAll();
        return Result.success();
    }
}
