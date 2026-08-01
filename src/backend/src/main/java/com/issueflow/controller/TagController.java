package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.entity.Tag;
import com.issueflow.service.PermissionService;
import com.issueflow.service.TagService;
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
 * 标签控制器：标签字典管理（写操作仅 ADMIN，读任意登录用户）
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 鉴权统一入口（M4，2026-08-01）：替代本类此前的私有 requireAdmin 副本。
     */
    private final PermissionService permissionService;

    /**
     * 标签列表
     */
    @GetMapping
    public Result<List<Tag>> list() {
        return Result.success(tagService.list());
    }

    /**
     * 新增标签（ADMIN）
     */
    @PostMapping
    public Result<Tag> create(@RequestBody Tag tag) {
        permissionService.requireAdmin();
        return Result.success(tagService.create(tag));
    }

    /**
     * 更新标签（ADMIN）
     */
    @PutMapping
    public Result<Tag> update(@RequestBody Tag tag) {
        permissionService.requireAdmin();
        return Result.success(tagService.update(tag));
    }

    /**
     * 删除标签（ADMIN，逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.requireAdmin();
        tagService.delete(id);
        return Result.success();
    }
}
