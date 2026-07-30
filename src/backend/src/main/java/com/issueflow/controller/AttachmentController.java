package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.resp.AttachmentVO;
import com.issueflow.service.IssueAttachmentService;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 附件控制器：上传 / 下载 / 预览 / 删除
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final IssueAttachmentService attachmentService;

    /**
     * 上传附件到指定问题（创建者 / ADMIN）
     */
    @PostMapping("/issues/{id}/attachments")
    public Result<List<AttachmentVO>> upload(@PathVariable Long id,
                                            @RequestParam("files") MultipartFile[] files) {
        return Result.success(attachmentService.upload(id, files,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode()));
    }

    /**
     * 下载附件（非图片走附件下载）
     */
    @GetMapping("/attachments/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        attachmentService.download(id, response, false);
    }

    /**
     * 预览附件（图片以 inline 方式内联展示）
     */
    @GetMapping("/attachments/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        attachmentService.download(id, response, true);
    }

    /**
     * 删除附件（创建者 / ADMIN，逻辑删 + 删文件）
     */
    @DeleteMapping("/attachments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id, SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode());
        return Result.success();
    }
}
