package com.issueflow.controller;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.MenuReq;
import com.issueflow.dto.resp.MenuVO;
import com.issueflow.service.MenuService;
import com.issueflow.util.SecurityUtils;
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
 * 菜单控制器：列表 + CRUD（写操作仅 ADMIN）
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * 菜单列表（ADMIN）
     */
    @GetMapping
    public Result<List<MenuVO>> list() {
        requireAdmin();
        return Result.success(menuService.listAll());
    }

    /**
     * 新建菜单（ADMIN）
     */
    @PostMapping
    public Result<MenuVO> create(@Valid @RequestBody MenuReq req) {
        return Result.success(menuService.create(req));
    }

    /**
     * 编辑菜单（ADMIN）
     */
    @PutMapping("/{id}")
    public Result<MenuVO> update(@PathVariable Long id, @Valid @RequestBody MenuReq req) {
        return Result.success(menuService.update(id, req));
    }

    /**
     * 删除菜单（ADMIN，有子节点禁止）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    private void requireAdmin() {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }
}
