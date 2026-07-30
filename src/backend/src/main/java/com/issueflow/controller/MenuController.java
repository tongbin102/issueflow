package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.MenuReq;
import com.issueflow.dto.resp.MenuNodeVO;
import com.issueflow.dto.resp.MenuVO;
import com.issueflow.service.MenuService;
import com.issueflow.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单控制器：列表（按端过滤）+ 侧栏树（按端）+ CRUD（写操作走权限码鉴权）
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final PermissionService permissionService;

    /**
     * 菜单列表（可选按端过滤；menu:list）
     */
    @GetMapping
    public Result<List<MenuVO>> list(@RequestParam(required = false) Integer type) {
        permissionService.requirePermission("menu:list");
        return Result.success(menuService.listByType(type));
    }

    /**
     * 侧栏菜单树（按端；仅需登录，供前端动态渲染）
     */
    @GetMapping("/sidebar")
    public Result<List<MenuNodeVO>> sidebar(@RequestParam Integer type) {
        return Result.success(menuService.listSidebarTree(type));
    }

    /**
     * 新建菜单（menu:create）
     */
    @PostMapping
    public Result<MenuVO> create(@Valid @RequestBody MenuReq req) {
        return Result.success(menuService.create(req));
    }

    /**
     * 编辑菜单（menu:update）
     */
    @PutMapping("/{id}")
    public Result<MenuVO> update(@PathVariable Long id, @Valid @RequestBody MenuReq req) {
        return Result.success(menuService.update(id, req));
    }

    /**
     * 删除菜单（menu:delete，有子节点禁止）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}
