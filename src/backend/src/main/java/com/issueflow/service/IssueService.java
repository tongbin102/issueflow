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
import com.issueflow.entity.DictItem;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.Issue;
import com.issueflow.entity.IssueAttachment;
import com.issueflow.entity.IssueFieldValue;
import com.issueflow.enums.DictTypeCodeEnum;
import com.issueflow.enums.FieldType;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.enums.PriorityEnum;
import com.issueflow.enums.SeverityEnum;
import com.issueflow.mapper.IssueAttachmentMapper;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.DateTimeUtils;
import com.issueflow.util.ExcelExportUtil;
import com.issueflow.util.IssueNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** 单次 Excel 导出最大行数（超出部分不导出，前端提示缩小筛选范围） */
    private static final int EXPORT_MAX_ROWS = 5000;

    private final IssueMapper issueMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueHistoryService historyService;
    private final IssueNoGenerator issueNoGenerator;
    private final UserService userService;
    private final ProjectService projectService;
    private final ModuleService moduleService;
    private final PermissionService permissionService;
    private final DictService dictService;
    /** ISSUE_TYPE 字典项读取入口（两级缓存，回填 typeName / 校验 typeCode 全程 0 次额外查库） */
    private final DictCache dictCache;
    /** 自定义字段值读写（竖表 issue_field_value，与 IssueService 解耦） */
    private final IssueFieldValueService fieldValueService;
    /** 字段配置查询（取「生效+必填+自定义」集合做落库前校验） */
    private final FieldConfigService fieldConfigService;

    /**
     * 新建问题（生成编号、reporter=当前用户、status=OPEN、写 CREATE 历史）
     */
    @Transactional
    public IssueVO createIssue(IssueCreateReq req, Long currentUser) {
        permissionService.requirePermission("issue:create");
        // Phase9：类型主源为 typeCode（ISSUE_TYPE 字典项编码）。
        // 必填校验从 DTO 的 @NotNull 下沉到此处：typeCode 为空即报错，语义与 Phase6 一致。
        String typeCode = resolveTypeCodeForWrite(req.getTypeCode());
        if (typeCode == null) {
            throw new BizException(ResultCode.ISSUE_TYPE_NOT_FOUND, "请选择问题类型");
        }
        // 自定义字段落库前必填校验（在写入主表之前，避免产生脏数据）
        validateRequiredFields(req.getCustomFields());
        String issueNo = issueNoGenerator.nextIssueNo();
        Issue issue = new Issue();
        issue.setIssueNo(issueNo);
        issue.setTypeCode(typeCode);
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
        // 【需求一】来源：由「用户可选 + 服务端兜底」改为「服务端强制固定 SYSTEM（系统录入）」。
        // 前端该字段在 UI 上只读，此处再强制覆写一次形成双保险——即便有人绕过前端直接调接口，
        // 也无法把来源篡改成其它字典项。req.getSource() 一律忽略，不做启用性校验（SYSTEM 为内置项）。
        issue.setSource(Constants.DICT_ITEM_SOURCE_SYSTEM);
        // 【需求一】优先级：必须由用户显式选择，服务端不再兜底「中」，为空/非法直接拒绝
        issue.setPriority(requireValidPriority(req.getPriority()));

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

        // 自定义字段值 upsert 竖表（必填校验已于写入主表前完成）
        fieldValueService.saveValues(issue.getId(), req.getCustomFields());

        historyService.record(issue.getId(), HistoryActionEnum.CREATE.getCode(),
                null, IssueStatusEnum.OPEN.getCode(), currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap(),
                moduleService.pathMap(Collections.singletonList(issue.getModuleId())),
                typeNameMapOf(issue.getTypeCode()),
                sourceNameMapOf(issue.getSource()));
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
        // 类型：非空才更新；变更时要求目标类型处于启用状态。
        if (hasText(req.getTypeCode())) {
            String submitted = req.getTypeCode().trim();
            if (submitted.equals(issue.getTypeCode())) {
                // 等值提交（类型未变更）直接放行，即便该字典项已被停用。
                // 否则类型一旦停用，其存量问题将连标题都改不了。口径与上方 source 分支一致。
                issue.setTypeCode(submitted);
            } else {
                issue.setTypeCode(resolveTypeCodeForWrite(submitted));
            }
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

        // 【需求一】来源：只读字段，编辑态一律不接受前端入参，保持存量值不变。
        // 说明：此处刻意「不写」issue.setSource(...)，历史脏数据（如 MANUAL）保留原样以免污染
        //      既有统计口径；新建的问题从源头就已固定为 SYSTEM。
        // 优先级：非空才更新（局部更新场景允许不携带），携带时必须合法
        if (req.getPriority() != null) {
            issue.setPriority(requireValidPriority(req.getPriority()));
        }

        // 自定义字段：落库前必填校验（仅当请求携带 customFields 时，局部更新不校验）→ upsert 竖表
        validateRequiredFields(req.getCustomFields());
        fieldValueService.saveValues(id, req.getCustomFields());

        issueMapper.updateById(issue);
        // updateById 默认 NOT_NULL 策略会跳过 null 字段，无法清空模块归属；
        // 故 module_id 单独用 UpdateWrapper 显式 set（含 null），实现「存在即覆盖」语义。
        issueMapper.update(null, new LambdaUpdateWrapper<Issue>()
                .eq(Issue::getId, id)
                .set(Issue::getModuleId, req.getModuleId()));
        historyService.record(id, HistoryActionEnum.EDIT.getCode(), null, null, currentUser, null);
        return toIssueVO(issue, userService.userNameMap(), projectService.nameMap(),
                moduleService.pathMap(Collections.singletonList(issue.getModuleId())),
                typeNameMapOf(issue.getTypeCode()),
                sourceNameMapOf(issue.getSource()));
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
        // Phase9：类型筛选主口径为 type_code（命中 idx_issue_type_code）
        if (hasText(req.getTypeCode())) {
            wrapper.eq(Issue::getTypeCode, req.getTypeCode().trim());
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
        if (req.getSource() != null && !req.getSource().isBlank()) {
            wrapper.eq(Issue::getSource, req.getSource());
        }
        if (req.getPriority() != null) {
            wrapper.eq(Issue::getPriority, req.getPriority());
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

        // BUG-03：scope=mine 口径（真·我的）
        // SUBMITTER 已在上方被强制收窄为仅自己，此处仅对「非 ADMIN 且 非 SUBMITTER」用户生效。
        // ADMIN 传 scope=mine 视为看全站（不加过滤），保留管理员全局排障能力。
        if (Constants.SCOPE_MINE.equals(req.getScope())
                && !Constants.ROLE_ADMIN.equals(roleCode)
                && !Constants.ROLE_SUBMITTER.equals(roleCode)) {
            wrapper.eq(Issue::getReporterId, currentUser);
        }

        wrapper.orderByDesc(Issue::getCreatedAt);

        issueMapper.selectPage(page, wrapper);
        Map<Long, String> userNameMap = userService.userNameMap();
        Map<Long, String> projectNameMap = projectService.nameMap();
        // 批量回填铁律：当页汇总 moduleId/typeCode/source → 一次批查 → Map 回填，禁止行内单查（N+1）
        Set<Long> moduleIds = new HashSet<>();
        Set<String> typeCodes = new HashSet<>();
        Set<String> sourceCodes = new HashSet<>();
        for (Issue i : page.getRecords()) {
            if (i.getModuleId() != null) {
                moduleIds.add(i.getModuleId());
            }
            if (hasText(i.getTypeCode())) {
                typeCodes.add(i.getTypeCode());
            }
            if (i.getSource() != null && !i.getSource().isBlank()) {
                sourceCodes.add(i.getSource());
            }
        }
        Map<Long, String> modulePathMap = moduleService.pathMap(moduleIds);
        // Phase9：typeName 改由 ISSUE_TYPE 字典项映射，走 DictCache 两级缓存，0 次额外 DB 查询
        Map<String, String> typeNameMap = dictService.itemNameMap(
                Constants.DICT_TYPE_ISSUE_TYPE, typeCodes);
        Map<String, String> sourceNameMap = dictService.itemNameMap(
                DictTypeCodeEnum.ISSUE_SOURCE.getCode(), sourceCodes);
        List<IssueVO> list = page.getRecords().stream()
                .map(i -> toIssueVO(i, userNameMap, projectNameMap, modulePathMap, typeNameMap, sourceNameMap))
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
                typeNameMapOf(issue.getTypeCode()),
                sourceNameMapOf(issue.getSource()));

        // 自定义字段值回填（按 field_config.type 从竖表对应列取出真实值，不塞整个实体）
        vo.setCustomFields(buildCustomFields(id));

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
                              Map<String, String> typeNameMap, Map<String, String> sourceNameMap) {
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        fillTypeFields(vo, issue, typeNameMap);
        vo.setSource(issue.getSource());
        vo.setSourceDesc(issue.getSource() == null ? "" : sourceNameMap.getOrDefault(issue.getSource(), issue.getSource()));
        vo.setPriority(issue.getPriority());
        vo.setPriorityDesc(PriorityEnum.descOf(issue.getPriority()));
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
                                     Map<String, String> typeNameMap, Map<String, String> sourceNameMap) {
        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setDescription(issue.getDescription());
        vo.setSeverity(issue.getSeverity());
        vo.setSeverityDesc(descOf(SeverityEnum.getByCode(issue.getSeverity())));
        vo.setStatus(issue.getStatus());
        vo.setStatusDesc(descOf(IssueStatusEnum.getByCode(issue.getStatus())));
        fillTypeFields(vo, issue, typeNameMap);
        vo.setSource(issue.getSource());
        vo.setSourceDesc(issue.getSource() == null ? "" : sourceNameMap.getOrDefault(issue.getSource(), issue.getSource()));
        vo.setPriority(issue.getPriority());
        vo.setPriorityDesc(PriorityEnum.descOf(issue.getPriority()));
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
     * 类型字段回填（typeCode/typeName）。
     *
     * <p>Phase9 起 {@code typeName} 的取数来源由「JOIN issue_type 表」改为
     * 「按 {@code issue.type_code} 命中 ISSUE_TYPE 字典项」，映射表由调用方一次性备好，
     * 本方法纯内存查表，绝不触库。</p>
     *
     * <p>防御口径：{@code type_code} 为空（迁移遗漏的存量脏数据）或字典项已被物理清除时，
     * {@code typeName} 回退为空字符串，<b>不抛异常</b>，保证列表整体可读。
     * 停用的字典项仍在缓存中（{@link DictCache} 缓存含停用项），故历史问题回显不受影响。</p>
     *
     * @param vo          待填充的视图对象
     * @param issue       问题实体
     * @param typeNameMap itemCode → name 映射，可为 null
     */
    private void fillTypeFields(IssueVO vo, Issue issue, Map<String, String> typeNameMap) {
        String code = issue.getTypeCode();
        vo.setTypeCode(code);
        if (!hasText(code) || typeNameMap == null) {
            vo.setTypeName("");
            return;
        }
        vo.setTypeName(typeNameMap.getOrDefault(code, ""));
    }

    /**
     * 解析写入用的问题类型编码。
     *
     * <p>两条分支：</p>
     * <ol>
     *   <li>{@code typeCode} 非空 → 在 ISSUE_TYPE 字典项中精确匹配 {@code item_code}，
     *       不存在抛 {@code ISSUE_TYPE_NOT_FOUND}，已停用抛 {@code ISSUE_TYPE_DISABLED}；</li>
     *   <li>{@code typeCode} 为空 → 返回 {@code null}，由调用方决定是否报「必填」。</li>
     * </ol>
     *
     * @param reqTypeCode 请求中的类型编码，可为空
     * @return 合法且启用的类型编码；入参为空时返回 null
     * @throws BizException 编码不存在或对应类型已停用
     */
    private String resolveTypeCodeForWrite(String reqTypeCode) {
        List<DictItem> items = dictCache.items(Constants.DICT_TYPE_ISSUE_TYPE);
        if (hasText(reqTypeCode)) {
            String code = reqTypeCode.trim();
            DictItem hit = findByItemCode(items, code);
            if (hit == null) {
                throw new BizException(ResultCode.ISSUE_TYPE_NOT_FOUND);
            }
            requireItemEnabled(hit);
            return hit.getItemCode();
        }
        return null;
    }

    /** 内存精确匹配 item_code，未命中返回 null */
    private DictItem findByItemCode(List<DictItem> items, String itemCode) {
        for (DictItem row : items) {
            if (itemCode.equals(row.getItemCode())) {
                return row;
            }
        }
        return null;
    }

    /** 字典项启用态断言（停用项仅允许存量回显，不可新选） */
    private void requireItemEnabled(DictItem item) {
        if (item.getEnabled() == null || item.getEnabled() != 1) {
            throw new BizException(ResultCode.ISSUE_TYPE_DISABLED);
        }
    }

    /** 非空且非空白 */
    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String descOf(com.issueflow.enums.IssueStatusEnum e) {
        return e == null ? "" : e.getDesc();
    }

    private String descOf(com.issueflow.enums.SeverityEnum e) {
        return e == null ? "" : e.getDesc();
    }

    /**
     * 导出当前筛选结果为 Excel（R6-⑦：含「来源」「优先级」两列，值为名称而非编码）。
     * <p>复用 {@link #pageQuery} 的权限 / 数据范围 / 筛选逻辑，单次导出上限 {@value #EXPORT_MAX_ROWS} 行，
     * 避免大表全量导出打爆内存。</p>
     *
     * @param req         筛选条件（page/size 由本方法接管）
     * @param currentUser 当前用户 id
     * @param roleCode    当前用户角色码
     * @return xlsx 文件字节数组
     */
    public byte[] exportExcel(IssuePageReq req, Long currentUser, String roleCode) {
        req.setPage(1);
        req.setSize(EXPORT_MAX_ROWS);
        PageResult<IssueVO> result = pageQuery(req, currentUser, roleCode);
        List<IssueVO> rows = result == null || result.getList() == null
                ? Collections.emptyList() : result.getList();
        return ExcelExportUtil.exportIssues(rows);
    }

    /**
     * 校验优先级取值合法性。
     *
     * <p><b>【需求一 · 默认值红线】</b>自本次变更起<b>不再兜底</b> {@code PriorityEnum.DEFAULT_CODE}：
     * 优先级必须由用户显式选择，服务端擅自填「中」会让统计报表严重失真
     * （历史上大量"其实没人选过"的问题被算成中优先级）。为空一律拒绝。</p>
     *
     * @param priority 请求中的优先级，不允许为 null
     * @return 合法的优先级数值
     * @throws BizException 为空或取值非法
     */
    private int requireValidPriority(Integer priority) {
        if (priority == null) {
            throw new BizException(ResultCode.VALID_ERROR, "请选择优先级");
        }
        if (!PriorityEnum.isValid(priority)) {
            throw new BizException(ResultCode.VALID_ERROR, "优先级取值非法");
        }
        return priority;
    }

    /**
     * 构建单条来源编码 → 名称的映射（供 create/update/detail 单对象回填，避免重复查询）。
     */
    private Map<String, String> sourceNameMapOf(String source) {
        return dictService.itemNameMap(DictTypeCodeEnum.ISSUE_SOURCE.getCode(),
                source == null || source.isBlank() ? Collections.emptySet() : Collections.singleton(source));
    }

    /**
     * 构建单条问题类型编码 → 名称的映射（供 create/update/detail 单对象回填）。
     * <p>与列表共用 {@link DictService#itemNameMap} 走 {@link DictCache}，不额外查库。</p>
     *
     * @param typeCode 问题类型编码，可为空
     * @return itemCode → name 映射，typeCode 为空时返回空 Map
     */
    private Map<String, String> typeNameMapOf(String typeCode) {
        return dictService.itemNameMap(Constants.DICT_TYPE_ISSUE_TYPE,
                hasText(typeCode) ? Collections.singleton(typeCode) : Collections.emptySet());
    }

    /**
     * 落库前必填校验：取「生效 + 必填 + 自定义」字段，若请求未携带 {@code customFields} 则不校验
     * （支持仅改标题等局部更新；提交表单时（含空 Map）则严格校验）。值缺失或空白抛
     * {@link ResultCode#FIELD_VALUE_REQUIRED}。
     *
     * @param customFields 请求携带的自定义字段值，可为 null
     */
    private void validateRequiredFields(Map<String, Object> customFields) {
        if (customFields == null) {
            return;
        }
        Map<String, FieldConfig> required = fieldConfigService.listRequiredCustomEnabled();
        for (FieldConfig cfg : required.values()) {
            Object v = customFields.get(cfg.getCode());
            if (v == null || (v instanceof String && ((String) v).isBlank())) {
                throw new BizException(ResultCode.FIELD_VALUE_REQUIRED, "必填字段未填写: " + cfg.getName());
            }
        }
    }

    /**
     * 按 issue 组装自定义字段真实值映射（code → 值），供详情响应回填。
     * <p>走 {@link IssueFieldValueService#mapByIssue} 取竖表记录，再按 {@code field_config.type}
     * 从 value_text / value_num / value_date 取出真实值（不塞整个 IssueFieldValue 实体）。</p>
     *
     * @param issueId 问题 id
     * @return field_code → 真实值（无自定义字段时返回空 Map）
     */
    private Map<String, Object> buildCustomFields(Long issueId) {
        Map<String, IssueFieldValue> valueMap = fieldValueService.mapByIssue(issueId);
        if (valueMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, FieldConfig> cfgByCode = fieldValueService.customConfigs();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, IssueFieldValue> e : valueMap.entrySet()) {
            String code = e.getKey();
            IssueFieldValue v = e.getValue();
            FieldConfig cfg = cfgByCode.get(code);
            FieldType type = cfg != null ? FieldType.fromCode(cfg.getType()) : FieldType.TEXT;
            result.put(code, extractFieldValue(v, type));
        }
        return result;
    }

    /**
     * 按字段类型从竖表实体取出真实值（不返回整个 IssueFieldValue 实体）。
     * DATE/DATETIME 类型返回格式化字符串（yyyy-MM-dd / yyyy-MM-dd HH:mm:ss），对齐前端
     * DynamicField.vue 的 valueFormat 契约，避免 customFields 为 Map&lt;String,Object&gt; 时
     * 泛型擦除导致 @JsonFormat 失效、Jackson 默认序列化输出带 T 的串。
     */
    private Object extractFieldValue(IssueFieldValue v, FieldType type) {
        switch (type) {
            case NUMBER:
                return v.getValueNum();
            case DATE:
                return DateTimeUtils.formatDate(v.getValueDate());
            case DATETIME:
                return DateTimeUtils.formatDateTime(v.getValueDate());
            case TEXT:
            case DICT:
            case REF:
            default:
                return v.getValueText();
        }
    }
}
