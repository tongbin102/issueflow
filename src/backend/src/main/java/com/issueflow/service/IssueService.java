package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.IssueCreateReq;
import com.issueflow.dto.req.IssuePageReq;
import com.issueflow.dto.req.IssueUpdateReq;
import com.issueflow.dto.resp.AttachmentVO;
import com.issueflow.dto.resp.IssueDetailVO;
import com.issueflow.dto.resp.IssueHistoryVO;
import com.issueflow.dto.resp.IssueVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.IssueAttachment;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.enums.SeverityEnum;
import com.issueflow.mapper.IssueAttachmentMapper;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.DateTimeUtils;
import com.issueflow.util.IssueNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 问题业务服务：CRUD + 分页筛选 + 权限/数据范围控制
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueHistoryService historyService;
    private final IssueNoGenerator issueNoGenerator;
    private final UserService userService;
    private final ProjectService projectService;
    private final PermissionService permissionService;

    /**
     * 新建问题（生成编号、reporter=当前用户、status=OPEN、写 CREATE 历史）
     */
    @Transactional
    public IssueVO createIssue(IssueCreateReq req, Long currentUser) {
        permissionService.requirePermission("issue:create");
        String issueNo = issueNoGenerator.nextIssueNo();
        Issue issue = new Issue();
        issue.setIssueNo(issueNo);
        issue.setTitle(req.getTitle());
        issue.setDescription(req.getDescription());
        issue.setSeverity(req.getSeverity() == null ? SeverityEnum.NORMAL.getCode() : req.getSeverity());
        issue.setTags(req.getTags());
        issue.setReproduceSteps(req.getReproduceSteps());
        issue.setEnvOs(req.getEnvOs());
        issue.setEnvBrowser(req.getEnvBrowser());
        issue.setEnvAppVersion(req.getEnvAppVersion());
        issue.setEnvDevice(req.getEnvDevice());
        issue.setStatus(IssueStatusEnum.OPEN.getCode());
        issue.setReporterId(currentUser);
        issue.setAssigneeId(req.getAssigneeId());
        issue.setProjectId(req.getProjectId());

        // 插入冲突（唯一索引兜底）重试一次
        try {
            issueMapper.insert(issue);
        } catch (DuplicateKeyException e) {
            issue.setId(null);
            issue.setIssueNo(issueNoGenerator.nextIssueNo());
            issueMapper.insert(issue);
        }

        historyService.record(issue.getId(), HistoryActionEnum.CREATE.getCode(),
                null, IssueStatusEnum.OPEN.getCode(), currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap());
    }

    /**
     * 编辑问题（创建者或 ADMIN 可操作；仅更新非空字段；写 EDIT 历史）
     */
    @Transactional
    public IssueVO update(Long id, IssueUpdateReq req, Long currentUser, String roleCode) {
        permissionService.requirePermission("issue:update");
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (!Objects.equals(issue.getReporterId(), currentUser) && !Constants.ROLE_ADMIN.equals(roleCode)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        if (req.getTitle() != null) {
            issue.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            issue.setDescription(req.getDescription());
        }
        if (req.getSeverity() != null) {
            issue.setSeverity(req.getSeverity());
        }
        if (req.getTags() != null) {
            issue.setTags(req.getTags());
        }
        if (req.getReproduceSteps() != null) {
            issue.setReproduceSteps(req.getReproduceSteps());
        }
        if (req.getEnvOs() != null) {
            issue.setEnvOs(req.getEnvOs());
        }
        if (req.getEnvBrowser() != null) {
            issue.setEnvBrowser(req.getEnvBrowser());
        }
        if (req.getEnvAppVersion() != null) {
            issue.setEnvAppVersion(req.getEnvAppVersion());
        }
        if (req.getEnvDevice() != null) {
            issue.setEnvDevice(req.getEnvDevice());
        }
        if (req.getAssigneeId() != null) {
            issue.setAssigneeId(req.getAssigneeId());
        }
        if (req.getProjectId() != null) {
            issue.setProjectId(req.getProjectId());
        }
        issueMapper.updateById(issue);
        historyService.record(id, HistoryActionEnum.EDIT.getCode(), null, null, currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap());
    }

    /**
     * 逻辑删除问题（创建者或 ADMIN；级联逻辑删附件与历史）
     */
    @Transactional
    public void delete(Long id, Long currentUser, String roleCode) {
        permissionService.requirePermission("issue:delete");
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (!Objects.equals(issue.getReporterId(), currentUser) && !Constants.ROLE_ADMIN.equals(roleCode)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        issueMapper.deleteById(id);
        // 级联逻辑删除附件与历史，保留关联可追溯
        attachmentMapper.delete(new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, id));
        historyService.deleteByIssue(id);
    }

    /**
     * 分页查询（按角色数据范围 + 多条件筛选）
     */
    public PageResult<IssueVO> pageQuery(IssuePageReq req, Long currentUser, String roleCode) {
        permissionService.requirePermission("issue:list");
        int pageNum = req.getPage() == null ? 1 : req.getPage();
        int size = req.getSize() == null ? 10 : req.getSize();
        Page<Issue> page = new Page<>(pageNum, size);

        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        if (req.getStatus() != null) {
            wrapper.eq(Issue::getStatus, req.getStatus());
        }
        if (req.getSeverity() != null) {
            wrapper.eq(Issue::getSeverity, req.getSeverity());
        }
        if (req.getTag() != null && !req.getTag().isBlank()) {
            wrapper.like(Issue::getTags, req.getTag());
        }
        if (req.getVersion() != null && !req.getVersion().isBlank()) {
            wrapper.eq(Issue::getEnvAppVersion, req.getVersion());
        }
        if (req.getAssigneeId() != null) {
            wrapper.eq(Issue::getAssigneeId, req.getAssigneeId());
        }
        if (req.getReporterId() != null) {
            wrapper.eq(Issue::getReporterId, req.getReporterId());
        }
        if (req.getProjectId() != null) {
            wrapper.eq(Issue::getProjectId, req.getProjectId());
        }
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword();
            wrapper.and(q -> q.like(Issue::getTitle, keyword).or().like(Issue::getDescription, keyword));
        }
        if (req.getStartDate() != null && !req.getStartDate().isBlank()) {
            LocalDateTime start = DateTimeUtils.parseDate(req.getStartDate(), true);
            if (start != null) {
                wrapper.ge(Issue::getCreatedAt, start);
            }
        }
        if (req.getEndDate() != null && !req.getEndDate().isBlank()) {
            LocalDateTime end = DateTimeUtils.parseDate(req.getEndDate(), false);
            if (end != null) {
                wrapper.le(Issue::getCreatedAt, end);
            }
        }

        // 数据范围：SUBMITTER 仅查自己提交的问题
        if (Constants.ROLE_SUBMITTER.equals(roleCode)) {
            wrapper.eq(Issue::getReporterId, currentUser);
        }
        wrapper.orderByDesc(Issue::getCreatedAt);

        issueMapper.selectPage(page, wrapper);
        Map<Long, String> userNameMap = userService.userNameMap();
        Map<Long, String> projectNameMap = projectService.nameMap();
        List<IssueVO> list = page.getRecords().stream()
                .map(i -> toIssueVO(i, userNameMap, projectNameMap))
                .collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 详情（含附件列表 + 最近历史；SUBMITTER 仅可看自己的问题）
     */
    public IssueDetailVO detail(Long id, Long currentUser, String roleCode) {
        permissionService.requirePermission("issue:list");
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (Constants.ROLE_SUBMITTER.equals(roleCode) && !Objects.equals(issue.getReporterId(), currentUser)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        Map<Long, String> userNameMap = userService.userNameMap();
        IssueDetailVO vo = toDetailVO(issue, userNameMap);

        List<IssueAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>()
                        .eq(IssueAttachment::getIssueId, id)
                        .orderByDesc(IssueAttachment::getCreatedAt));
        vo.setAttachments(attachments.stream().map(a -> toAttachmentVO(a, userNameMap)).collect(Collectors.toList()));

        List<IssueHistoryVO> history = historyService.queryByIssue(id);
        if (history.size() > 20) {
            history = history.subList(0, 20);
        }
        vo.setRecentHistory(history);
        return vo;
    }

    private IssueVO toIssueVO(Issue issue, Map<Long, String> userNameMap, Map<Long, String> projectNameMap) {
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        vo.setTags(issue.getTags());
        vo.setEnvAppVersion(issue.getEnvAppVersion());
        vo.setReporterId(issue.getReporterId());
        vo.setReporterName(userNameMap.get(issue.getReporterId()));
        vo.setAssigneeId(issue.getAssigneeId());
        vo.setAssigneeName(userNameMap.get(issue.getAssigneeId()));
        vo.setProjectId(issue.getProjectId());
        vo.setProjectName(projectNameMap.get(issue.getProjectId()));
        vo.setClosedAt(issue.getClosedAt());
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }

    private IssueDetailVO toDetailVO(Issue issue, Map<Long, String> userNameMap) {
        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setDescription(issue.getDescription());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        vo.setTags(issue.getTags());
        vo.setReproduceSteps(issue.getReproduceSteps());
        vo.setEnvOs(issue.getEnvOs());
        vo.setEnvBrowser(issue.getEnvBrowser());
        vo.setEnvAppVersion(issue.getEnvAppVersion());
        vo.setEnvDevice(issue.getEnvDevice());
        vo.setReporterId(issue.getReporterId());
        vo.setReporterName(userNameMap.get(issue.getReporterId()));
        vo.setAssigneeId(issue.getAssigneeId());
        vo.setAssigneeName(userNameMap.get(issue.getAssigneeId()));
        vo.setProjectId(issue.getProjectId());
        vo.setProjectName(projectService.nameMap().get(issue.getProjectId()));
        vo.setClosedAt(issue.getClosedAt());
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }

    private AttachmentVO toAttachmentVO(IssueAttachment a, Map<Long, String> userNameMap) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setIssueId(a.getIssueId());
        vo.setFileName(a.getFileName());
        vo.setOriginalName(a.getOriginalName());
        vo.setFilePath(a.getFilePath());
        vo.setFileSize(a.getFileSize());
        vo.setContentType(a.getContentType());
        vo.setUploaderId(a.getUploaderId());
        vo.setUploaderName(userNameMap.get(a.getUploaderId()));
        vo.setImage(a.getContentType() != null && a.getContentType().startsWith("image/"));
        vo.setUrl("/api/attachments/" + a.getId() + "/download");
        vo.setPreviewUrl("/api/attachments/" + a.getId() + "/preview");
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    private String descOf(com.issueflow.enums.IssueStatusEnum e) {
        return e == null ? "" : e.getDesc();
    }

    private String descOf(com.issueflow.enums.SeverityEnum e) {
        return e == null ? "" : e.getDesc();
    }
}
