package com.issueflow.controller;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.ProjectReq;
import com.issueflow.dto.resp.ProjectOptionVO;
import com.issueflow.dto.resp.ProjectVO;
import com.issueflow.service.ProjectService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目控制器：CRUD + 分页（写操作仅 ADMIN）；GET /options 仅需登录
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 项目分页列表（ADMIN）
     */
    @GetMapping
    public Result<PageResult<ProjectVO>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return Result.success(projectService.pageProjects(page, size));
    }

    /**
     * 项目下拉选项（仅需登录，任意角色可调用）
     */
    @GetMapping("/options")
    public Result<List<ProjectOptionVO>> options() {
        return Result.success(projectService.listOptions());
    }

    /**
     * 新建项目（ADMIN）
     */
    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectReq req) {
        return Result.success(projectService.createProject(req));
    }

    /**
     * 编辑项目（ADMIN）
     */
    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectReq req) {
        return Result.success(projectService.updateProject(id, req));
    }

    /**
     * 删除项目（ADMIN，逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }

    private void requireAdmin() {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }
}
