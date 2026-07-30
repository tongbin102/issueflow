package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.FlowConfigReq;
import com.issueflow.dto.resp.IssueVO;
import com.issueflow.entity.Issue;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.enums.SeverityEnum;
import com.issueflow.handler.StateMachine;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 状态流转服务：状态变更 + 重开 + 流程开关配置
 */
@Service
@RequiredArgsConstructor
public class IssueFlowService {

    private final IssueMapper issueMapper;
    private final IssueHistoryService historyService;
    private final StateMachine stateMachine;
    private final SysConfigService sysConfigService;
    private final UserService userService;

    /**
     * 状态流转（按状态机规则校验角色与开关）
     */
    @Transactional
    public IssueVO changeStatus(Long id, Integer toStatus, String remark, Long operatorId, String roleCode) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        int from = issue.getStatus();
        if (toStatus == null) {
            throw new BizException(ResultCode.VALID_ERROR, "目标状态不能为空");
        }
        if (!stateMachine.isAllowed(from, toStatus, roleCode)) {
            throw new BizException(ResultCode.STATUS_TRANSITION_DENIED);
        }
        // 验证回退必须填写原因
        if (from == IssueStatusEnum.PENDING_VERIFY.getCode()
                && toStatus == IssueStatusEnum.IN_PROGRESS.getCode()
                && (remark == null || remark.isBlank())) {
            throw new BizException(ResultCode.VALID_ERROR, "回退必须填写原因");
        }
        issue.setStatus(toStatus);
        if (toStatus == IssueStatusEnum.CLOSED.getCode()) {
            issue.setClosedAt(LocalDateTime.now());
        }
        issueMapper.updateById(issue);

        HistoryActionEnum action = stateMachine.getAction(from, toStatus);
        historyService.record(id, action == null ? null : action.getCode(), from, toStatus, operatorId, remark);
        return toIssueVO(issue);
    }

    /**
     * 重开（仅 ADMIN，且需 flow_reopen_enabled 开启；仅已关闭问题可重开）
     */
    @Transactional
    public IssueVO reopen(Long id, String remark, Long operatorId, String roleCode) {
        if (!Constants.ROLE_ADMIN.equals(roleCode)) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        if (!sysConfigService.isEnabled(Constants.CFG_FLOW_REOPEN_ENABLED)) {
            throw new BizException(ResultCode.STATUS_TRANSITION_DENIED, "重开功能未启用");
        }
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (!Objects.equals(issue.getStatus(), IssueStatusEnum.CLOSED.getCode())) {
            throw new BizException(ResultCode.STATUS_TRANSITION_DENIED, "仅已关闭的问题可重开");
        }
        int from = issue.getStatus();
        issue.setStatus(IssueStatusEnum.OPEN.getCode());
        issue.setClosedAt(null);
        issueMapper.updateById(issue);
        historyService.record(id, HistoryActionEnum.REOPEN.getCode(), from,
                IssueStatusEnum.OPEN.getCode(), operatorId, remark);
        return toIssueVO(issue);
    }

    /**
     * 读取流程开关配置
     */
    public Map<String, Boolean> getFlowConfig() {
        return sysConfigService.getFlowConfig();
    }

    /**
     * 写入流程开关配置
     */
    @Transactional
    public void updateFlowConfig(FlowConfigReq req) {
        sysConfigService.setFlowConfig(req.getRejectEnabled(), req.getReopenEnabled());
    }

    private IssueVO toIssueVO(Issue issue) {
        Map<Long, String> nameMap = userService.userNameMap();
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setSeverity(issue.getSeverity());
        SeverityEnum severityEnum = SeverityEnum.getByCode(issue.getSeverity());
        vo.setSeverityDesc(severityEnum == null ? "" : severityEnum.getDesc());
        vo.setStatus(issue.getStatus());
        IssueStatusEnum statusEnum = IssueStatusEnum.getByCode(issue.getStatus());
        vo.setStatusDesc(statusEnum == null ? "" : statusEnum.getDesc());
        vo.setTags(issue.getTags());
        vo.setEnvAppVersion(issue.getEnvAppVersion());
        vo.setReporterId(issue.getReporterId());
        vo.setReporterName(nameMap.get(issue.getReporterId()));
        vo.setAssigneeId(issue.getAssigneeId());
        vo.setAssigneeName(nameMap.get(issue.getAssigneeId()));
        vo.setClosedAt(issue.getClosedAt());
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }
}
