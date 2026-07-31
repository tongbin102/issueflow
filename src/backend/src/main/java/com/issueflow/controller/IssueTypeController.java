package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.IssueTypeReq;
import com.issueflow.dto.req.IssueTypeStatusReq;
import com.issueflow.dto.resp.IssueTypeOptionVO;
import com.issueflow.dto.resp.IssueTypeVO;
import com.issueflow.service.IssueTypeService;
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
 * 问题类型控制器：管理列表 / 下拉选项 / CRUD / 启停
 */
@RestController
@RequestMapping("/api/issue-types")
@RequiredArgsConstructor
public class IssueTypeController {

    private final IssueTypeService issueTypeService;

    /**
     * 管理列表（issue:type:list，含停用项与引用计数）
     */
    @GetMapping
    public Result<List<IssueTypeVO>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer enabled) {
        return Result.success(issueTypeService.list(keyword, enabled));
    }

    /**
     * 下拉选项（登录即可）。默认仅启用项；筛选场景传 includeDisabled=true 拿全量。
     */
    @GetMapping("/options")
    public Result<List<IssueTypeOptionVO>> options(
            @RequestParam(required = false, defaultValue = "false") Boolean includeDisabled) {
        return Result.success(issueTypeService.options(Boolean.TRUE.equals(includeDisabled)));
    }

    /**
     * 新增类型（issue:type:create）
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody IssueTypeReq req) {
        return Result.success(issueTypeService.create(req));
    }

    /**
     * 编辑类型（issue:type:update）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody IssueTypeReq req) {
        issueTypeService.update(id, req);
        return Result.success();
    }

    /**
     * 启停切换（issue:type:update）
     */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody IssueTypeStatusReq req) {
        issueTypeService.toggleStatus(id, req.getEnabled());
        return Result.success();
    }

    /**
     * 删除类型（issue:type:delete；被引用时业务异常阻断，提示改为停用）
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        issueTypeService.delete(id);
        return Result.success();
    }
}
