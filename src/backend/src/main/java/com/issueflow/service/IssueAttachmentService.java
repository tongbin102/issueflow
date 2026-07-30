package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.resp.AttachmentVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.IssueAttachment;
import com.issueflow.mapper.IssueAttachmentMapper;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.FileUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 附件服务：上传（落盘+DB）、下载、预览（图片内联）、删除
 */
@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final IssueMapper issueMapper;
    private final FileUtil fileUtil;
    private final UserService userService;

    /**
     * 上传附件（仅问题创建者或 ADMIN）
     */
    @Transactional
    public List<AttachmentVO> upload(Long issueId, MultipartFile[] files, Long currentUser, String roleCode) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (!Constants.ROLE_ADMIN.equals(roleCode) && !Objects.equals(issue.getReporterId(), currentUser)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        List<AttachmentVO> result = new ArrayList<>();
        if (files == null || files.length == 0) {
            return result;
        }
        Map<Long, String> nameMap = userService.userNameMap();
        for (MultipartFile file : files) {
            FileUtil.StoredFile stored = fileUtil.store(file);
            IssueAttachment attachment = new IssueAttachment();
            attachment.setIssueId(issueId);
            attachment.setFileName(stored.fileName());
            attachment.setOriginalName(file.getOriginalFilename());
            attachment.setFilePath(stored.filePath());
            attachment.setFileSize(stored.fileSize());
            attachment.setContentType(stored.contentType());
            attachment.setUploaderId(currentUser);
            attachmentMapper.insert(attachment);
            result.add(toVO(attachment, nameMap));
        }
        return result;
    }

    /**
     * 下载 / 预览附件
     *
     * @param inline true 且为图片时以 inline 方式内联展示（预览）
     */
    public void download(Long id, HttpServletResponse response, boolean inline) throws IOException {
        IssueAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        File file = new File(attachment.getFilePath());
        if (!file.exists()) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        String contentType = attachment.getContentType() != null
                ? attachment.getContentType() : "application/octet-stream";
        response.setContentType(contentType);
        boolean isImage = fileUtil.isImage(contentType);
        String disposition = (inline && isImage) ? "inline" : "attachment";
        String encodedName = URLEncoder.encode(
                attachment.getOriginalName() != null ? attachment.getOriginalName() : attachment.getFileName(),
                StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", disposition + ";filename=\"" + encodedName + "\"");
        if (attachment.getFileSize() != null) {
            response.setContentLengthLong(attachment.getFileSize());
        }
        try (InputStream in = new FileInputStream(file); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    /**
     * 删除附件（逻辑删 + 删文件，仅问题创建者或 ADMIN）
     */
    @Transactional
    public void delete(Long id, Long currentUser, String roleCode) {
        IssueAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        Issue issue = issueMapper.selectById(attachment.getIssueId());
        boolean allowed = Constants.ROLE_ADMIN.equals(roleCode)
                || (issue != null && Objects.equals(issue.getReporterId(), currentUser));
        if (!allowed) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        attachmentMapper.deleteById(id);
        fileUtil.deleteFile(attachment.getFilePath());
    }

    private AttachmentVO toVO(IssueAttachment a, Map<Long, String> nameMap) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setIssueId(a.getIssueId());
        vo.setFileName(a.getFileName());
        vo.setOriginalName(a.getOriginalName());
        vo.setFilePath(a.getFilePath());
        vo.setFileSize(a.getFileSize());
        vo.setContentType(a.getContentType());
        vo.setUploaderId(a.getUploaderId());
        vo.setUploaderName(nameMap.get(a.getUploaderId()));
        vo.setImage(a.getContentType() != null && a.getContentType().startsWith("image/"));
        vo.setUrl("/api/attachments/" + a.getId() + "/download");
        vo.setPreviewUrl("/api/attachments/" + a.getId() + "/preview");
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }
}
