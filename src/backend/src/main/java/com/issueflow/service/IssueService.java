package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.issueflow.entity.IssueType;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ModuleService moduleService;
    private final PermissionService permissionService;
    private final IssueTypeService issueTypeService;

    /**
     * 新建问题（生成编号、reporter=当前用户、status=OPEN、写 CREATE 历史）
     */
    @Transactional
    public IssueVO createIssue(IssueCreateReq req, Long currentUser) {
        permissionService.requirePermission("issue:create");
        // Phase6：类型必填且必须启用（停用类型仅存量展示，不可新选）
        issueTypeService.requireEnabled(req.getTypeId());
        String issueNo = issueNoGenerator.nextIssueNo();
        Issue issue = new Issue();
        issue.setIssueNo(issueNo);
        issue.setTypeId(req.getTypeId());
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
        // R5-1：模块归属校验（moduleId 为空时直接放行）
        moduleService.assertModuleBelongsToProject(req.getModuleId(), req.getProjectId());
        issue.setModuleId(req.getModuleId());

        // 插入冲突（唯一索引兜底）最多重试 3 次；每次重新生成编号，
        // 因 maxSeq 随并发插入自增，重试才真正有效。3 次仍失败则抛受控业务异常，避免裸奔成 500。
        boolean inserted = false;
        for (int attempt = 0; attempt < 3 && !inserted; attempt++) {
            try {
                issueMapper.insert(issue);
                inserted = true;
            } catch (DuplicateKeyException e) {
                issue.setId(null);
                issue.setIssueNo(issueNoGenerator.nextIssueNo());
            }
        }
        if (!inserted) {
            throw new BizException(ResultCode.SYSTEM_ERROR);
        }

        historyService.record(issue.getId(), HistoryActionEnum.CREATE.getCode(),
                null, IssueStatusEnum.OPEN.getCode(), currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap(),
                moduleService.pathMap(Collections.singletonList(issue.getModuleId())),
                issueTypeService.nameMap(Collections.singletonList(issue.getTypeId())));
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
        // 类型：非空才更新；变更时同样要求目标类型处于启用状态
        if (req.getTypeId() != null) {
            issueTypeService.requireEnabled(req.getTypeId());
            issue.setTypeId(req.getTypeId());
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
        // moduleId 语义：存在即覆盖（null = 清空），与 projectId 的「非空才更新」不同。
        // 校验以更新后最终生效的 projectId 为准。
        moduleService.assertModuleBelongsToProject(req.getModuleId(), issue.getProjectId());
        issue.setModuleId(req.getModuleId());

        issueMapper.updateById(issue);
        // updateById 默认 NOT_NULL 策略会跳过 null 字段，无法清空模块归属；
        // 故 module_id 单独用 UpdateWrapper 显式 set（含 null），实现「存在即覆盖」语义。
        issueMapper.update(null, new LambdaUpdateWrapper<Issue>()
                .eq(Issue::getId, id)
                .set(Issue::getModuleId, req.getModuleId()));
        historyService.record(id, HistoryActionEnum.EDIT.getCode(), null, null, currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap(),
                moduleService.pathMap(Collections.singletonList(issue.getModuleId())),
                issueTypeService.nameMap(Collections.singletonList(issue.getTypeId())));
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
        if (req.getTypeId() != null) {
            wrapper.eq(Issue::getTypeId, req.getTypeId());
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
        // 批量回填铁律：当页汇总 moduleId/typeId → 一次批查 → Map 回填，禁止行内单查（N+1）
        Set<Long> moduleIds = new HashSet<>();
        Set<Long> typeIds = new HashSet<>();
        for (Issue i : page.getRecords()) {
            if (i.getModuleId() != null) {
                moduleIds.add(i.getModuleId());
            }
            if (i.getTypeId() != null) {
                typeIds.add(i.getTypeId());
            }
        }
        Map<Long, String> modulePathMap = moduleService.pathMap(moduleIds);
        Map<Long, IssueType> typeMap = issueTypeService.nameMap(typeIds);
        List<IssueVO> list = page.getRecords().stream()
                .map(i -> toIssueVO(i, userNameMap, projectNameMap, modulePathMap, typeMap))
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
        IssueDetailVO vo = toDetailVO(issue, userNameMap,
                moduleService.pathMap(Collections.singletonList(issue.getModuleId())),
                issueTypeService.nameMap(Collections.singletonList(issue.getTypeId())));

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

    private IssueVO toIssueVO(Issue issue, Map<Long, String> userNameMap,
                              Map<Long, String> projectNameMap, Map<Long, String> modulePathMap,
                              Map<Long, IssueType> typeMap) {
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        fillTypeFields(vo, issue, typeMap);
        vo.setTags(issue.getTags());
        vo.setEnvAppVersion(issue.getEnvAppVersion());
        vo.setReporterId(issue.getReporterId());
        vo.setReporterName(userNameMap.get(issue.getReporterId()));
        vo.setAssigneeId(issue.getAssigneeId());
        vo.setAssigneeName(userNameMap.get(issue.getAssigneeId()));
        vo.setProjectId(issue.getProjectId());
        vo.setProjectName(projectNameMap.get(issue.getProjectId()));
        vo.setModuleId(issue.getModuleId());
        vo.setModulePath(issue.getModuleId() == null ? null : modulePathMap.get(issue.getModuleId()));
        vo.setClosedAt(issue.getClosedAt());
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }

    private IssueDetailVO toDetailVO(Issue issue, Map<Long, String> userNameMap,
                                     Map<Long, String> modulePathMap,
                                     Map<Long, IssueType> typeMap) {
        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setDescription(issue.getDescription());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        fillTypeFields(vo, issue, typeMap);
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
        vo.setModuleId(issue.getModuleId());
        vo.setModulePath(issue.getModuleId() == null ? null : modulePathMap.get(issue.getModuleId()));
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

    /**
     * 类型三字段回填（typeId/typeName/typeCode）；停用/已删类型查不到时仅回 id，前端显「—」
     */
    private void fillTypeFields(IssueVO vo, Issue issue, Map<Long, IssueType> typeMap) {
        vo.setTypeId(issue.getTypeId());
        if (issue.getTypeId() != null && typeMap != null) {
            IssueType type = typeMap.get(issue.getTypeId());
            if (type != null) {
                vo.setTypeName(type.getName());
                vo.setTypeCode(type.getCode());
            }
        }
    }

    private String descOf(com.issueflow.enums.IssueStatusEnum e) {
        return e == null ? "" : e.getDesc();
    }

    private String descOf(com.issueflow.enums.SeverityEnum e) {
        return e == null ? "" : e.getDesc();
    }
}
