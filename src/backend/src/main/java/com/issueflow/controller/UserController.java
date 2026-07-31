package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.req.UserReq;
import com.issueflow.dto.resp.UserBriefVO;
import com.issueflow.dto.resp.UserVO;
import com.issueflow.service.UserService;
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
 * 用户控制器：用户增删改查（写操作仅 ADMIN；角色列表已迁移至 RoleController）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户分页列表（ADMIN）
     */
    @GetMapping("/users")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.pageUsers(page, size));
    }

    /**
     * 新增用户（user:create）
     */
    @PostMapping("/users")
    public Result<UserVO> create(@Valid @RequestBody UserReq req) {
        return Result.success(userService.createUser(req));
    }

    /**
     * 编辑用户（user:update）
     */
    @PutMapping("/users/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserReq req) {
        return Result.success(userService.updateUser(id, req));
    }

    /**
     * 删除用户（user:delete，逻辑删除）
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 查询用户已分配的全部角色码（user:list，Phase8 W3 #11 新增，供编辑回显）
     */
    @GetMapping("/users/{id}/roles")
    public Result<List<String>> roles(@PathVariable Long id) {
        return Result.success(userService.listUserRoleCodes(id));
    }

    /**
     * 用户下拉选项（仅登录，无 requirePermission）：负责人/成员选择用。
     * 返回 status=1 & deleted=0 的用户，按 real_name/username 模糊匹配，上限 100。
     */
    @GetMapping("/users/options")
    public Result<List<UserBriefVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(userService.listUserOptions(keyword));
    }
}
