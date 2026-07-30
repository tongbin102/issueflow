package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.req.HistoryQueryReq;
import com.issueflow.dto.req.IssueCreateReq;
import com.issueflow.dto.req.IssuePageReq;
import com.issueflow.dto.req.IssueUpdateReq;
import com.issueflow.dto.resp.IssueDetailVO;
import com.issueflow.dto.resp.IssueHistoryVO;
import com.issueflow.dto.resp.IssueVO;
import com.issueflow.service.IssueAttachmentService;
import com.issueflow.service.IssueHistoryService;
import com.issueflow.service.IssueService;
import com.issueflow.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 问题控制器：CRUD + 分页筛选 + 详情 + 历史
 */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueHistoryService historyService;
    private final IssueAttachmentService attachmentService;

    /**
     * 新建问题（multipart，可附附件）
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<IssueVO> create(@RequestPart("issue") @Valid IssueCreateReq req,
                                  @RequestPart(value = "files", required = false) MultipartFile[] files) {
        Long uid = SecurityUtils.getCurrentUserId();
        IssueVO vo = issueService.createIssue(req, uid);
        if (files != null && files.length > 0) {
            attachmentService.upload(vo.getId(), files, uid, SecurityUtils.getCurrentRoleCode());
        }
        return Result.success(vo);
    }

    /**
     * 编辑问题（创建者 / ADMIN）
     */
    @PutMapping("/{id}")
    public Result<IssueVO> update(@PathVariable Long id, @Valid @RequestBody IssueUpdateReq req) {
        return Result.success(issueService.update(id, req,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode()));
    }

    /**
     * 删除问题（创建者 / ADMIN，逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        issueService.delete(id, SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode());
        return Result.success();
    }

    /**
     * 问题详情（含附件 + 最近历史）
     */
    @GetMapping("/{id}")
    public Result<IssueDetailVO> detail(@PathVariable Long id) {
        return Result.success(issueService.detail(id,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode()));
    }

    /**
     * 分页筛选（SUBMITTER 仅查自己；其余查全部）
     */
    @GetMapping
    public Result<PageResult<IssueVO>> page(IssuePageReq req) {
        return Result.success(issueService.pageQuery(req,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode()));
    }

    /**
     * 某问题的操作历史（分页 + 操作人/时间范围筛选）
     */
    @GetMapping("/{id}/history")
    public Result<PageResult<IssueHistoryVO>> history(
            @PathVariable Long id, HistoryQueryReq req) {
        return Result.success(historyService.queryPageByIssue(id, req));
    }
}
