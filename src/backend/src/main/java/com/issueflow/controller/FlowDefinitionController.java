package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.FlowNodePositionReq;
import com.issueflow.dto.req.FlowNodeReq;
import com.issueflow.dto.req.FlowTransitionReq;
import com.issueflow.dto.resp.FlowGraphVO;
import com.issueflow.dto.resp.FlowNodeVO;
import com.issueflow.dto.resp.FlowTransitionVO;
import com.issueflow.service.FlowDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程定义控制器（R2）：流程图查询 + 节点/流转 CRUD + 坐标持久化
 */
@RestController
@RequestMapping("/api/flow/definition")
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final FlowDefinitionService flowDefinitionService;

    /**
     * 流程图（节点 + 流转边）
     */
    @GetMapping("/graph")
    public Result<FlowGraphVO> graph() {
        return Result.success(flowDefinitionService.getGraph());
    }

    /**
     * 新建流程节点
     */
    @PostMapping("/nodes")
    public Result<FlowNodeVO> createNode(@Valid @RequestBody FlowNodeReq req) {
        return Result.success(flowDefinitionService.createNode(req));
    }

    /**
     * 批量保存节点坐标（须在 /nodes/{id} 之前无歧义：字面量路径优先匹配）
     */
    @PutMapping("/nodes/positions")
    public Result<Void> updateNodePositions(@Valid @RequestBody FlowNodePositionReq req) {
        flowDefinitionService.updateNodePositions(req);
        return Result.success();
    }

    /**
     * 编辑流程节点
     */
    @PutMapping("/nodes/{id}")
    public Result<FlowNodeVO> updateNode(@PathVariable Long id, @Valid @RequestBody FlowNodeReq req) {
        return Result.success(flowDefinitionService.updateNode(id, req));
    }

    /**
     * 删除流程节点
     */
    @DeleteMapping("/nodes/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) {
        flowDefinitionService.deleteNode(id);
        return Result.success();
    }

    /**
     * 新建流转规则
     */
    @PostMapping("/transitions")
    public Result<FlowTransitionVO> createTransition(@Valid @RequestBody FlowTransitionReq req) {
        return Result.success(flowDefinitionService.createTransition(req));
    }

    /**
     * 编辑流转规则
     */
    @PutMapping("/transitions/{id}")
    public Result<FlowTransitionVO> updateTransition(@PathVariable Long id,
                                                     @Valid @RequestBody FlowTransitionReq req) {
        return Result.success(flowDefinitionService.updateTransition(id, req));
    }

    /**
     * 删除流转规则
     */
    @DeleteMapping("/transitions/{id}")
    public Result<Void> deleteTransition(@PathVariable Long id) {
        flowDefinitionService.deleteTransition(id);
        return Result.success();
    }

    /**
     * 恢复默认流程：清空两表 → 重灌 5 节点 + 6 条默认流转（P1）
     */
    @PostMapping("/reset-default")
    public Result<FlowGraphVO> resetDefault() {
        return Result.success(flowDefinitionService.resetDefault());
    }
}
