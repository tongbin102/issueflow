package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.ModuleBatchReq;
import com.issueflow.dto.req.ModuleDependencyReq;
import com.issueflow.dto.req.ModuleMoveReq;
import com.issueflow.dto.req.ModuleReq;
import com.issueflow.dto.resp.ModuleBriefVO;
import com.issueflow.dto.resp.ModuleNodeVO;
import com.issueflow.service.ModuleService;
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
 * 模块控制器（瘦 Controller，业务逻辑全部在 ModuleService）。
 *
 * <p>权限：写操作复用 {@code project:update}（在 Service 内统一校验，ADMIN 放行）；
 * {@code GET /tree} 仅需登录。</p>
 */
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    /**
     * 查询项目模块树（仅需登录）
     */
    @GetMapping("/tree")
    public Result<List<ModuleNodeVO>> tree(@RequestParam Long projectId) {
        return Result.success(moduleService.tree(projectId));
    }

    /**
     * 新建模块（project:update）
     */
    @PostMapping
    public Result<ModuleNodeVO> create(@Valid @RequestBody ModuleReq req) {
        return Result.success(moduleService.create(req));
    }

    /**
     * 编辑模块名称 / 描述（project:update）
     */
    @PutMapping("/{id}")
    public Result<ModuleNodeVO> update(@PathVariable Long id, @Valid @RequestBody ModuleReq req) {
        return Result.success(moduleService.update(id, req));
    }

    /**
     * 删除模块（project:update，级联软删子孙）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        moduleService.delete(id);
        return Result.success();
    }

    /**
     * 移动模块（project:update，即拖即存）
     */
    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @RequestBody ModuleMoveReq req) {
        moduleService.move(id, req);
        return Result.success();
    }

    /**
     * 批量删除模块（project:update，整体原子阻断）
     */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody ModuleBatchReq req) {
        moduleService.batchDelete(req);
        return Result.success();
    }

    /**
     * 批量移动模块（project:update）
     */
    @PostMapping("/batch-move")
    public Result<Void> batchMove(@Valid @RequestBody ModuleBatchReq req) {
        moduleService.batchMove(req);
        return Result.success();
    }

    /**
     * 设置模块依赖（project:update，全量替换 + 防环）
     */
    @PutMapping("/{id}/dependencies")
    public Result<List<ModuleBriefVO>> setDependencies(@PathVariable Long id,
                                                       @RequestBody ModuleDependencyReq req) {
        return Result.success(moduleService.setDependencies(id, req));
    }
}
