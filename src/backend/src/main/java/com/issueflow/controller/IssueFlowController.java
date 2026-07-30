package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.FlowConfigReq;
import com.issueflow.dto.req.StatusChangeReq;
import com.issueflow.dto.resp.IssueVO;
import com.issueflow.service.IssueFlowService;
import com.issueflow.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 状态流转控制器：状态变更 / 重开 / 流程开关配置
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IssueFlowController {

    private final IssueFlowService flowService;

    /**
     * 状态流转（按状态机规则校验角色与开关）
     */
    @PostMapping("/issues/{id}/status")
    public Result<IssueVO> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeReq req) {
        IssueVO vo = flowService.changeStatus(id, req.getToStatus(), req.getRemark(),
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode());
        return Result.success(vo);
    }

    /**
     * 重开问题（仅 ADMIN，且需 flow_reopen_enabled）
     */
    @PostMapping("/issues/{id}/reopen")
    public Result<IssueVO> reopen(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = (body == null) ? null : body.get("remark");
        IssueVO vo = flowService.reopen(id, remark,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode());
        return Result.success(vo);
    }

    /**
     * 读取流程开关配置（ADMIN）
     */
    @GetMapping("/flow/config")
    public Result<Map<String, Boolean>> getFlowConfig() {
        return Result.success(flowService.getFlowConfig());
    }

    /**
     * 写入流程开关配置（flow:config）
     */
    @PutMapping("/flow/config")
    public Result<Void> putFlowConfig(@Valid @RequestBody FlowConfigReq req) {
        flowService.updateFlowConfig(req);
        return Result.success();
    }
}
