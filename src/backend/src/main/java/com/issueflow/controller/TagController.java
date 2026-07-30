package com.issueflow.controller;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.Tag;
import com.issueflow.service.TagService;
import com.issueflow.util.SecurityUtils;
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
        requireAdmin();
        return Result.success(tagService.create(tag));
    }

    /**
     * 更新标签（ADMIN）
     */
    @PutMapping
    public Result<Tag> update(@RequestBody Tag tag) {
        requireAdmin();
        return Result.success(tagService.update(tag));
    }

    /**
     * 删除标签（ADMIN，逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        tagService.delete(id);
        return Result.success();
    }

    private void requireAdmin() {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }
}
