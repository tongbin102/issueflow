package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.FlowNodePositionReq;
import com.issueflow.dto.req.FlowNodeReq;
import com.issueflow.dto.req.FlowTransitionReq;
import com.issueflow.dto.resp.FlowGraphVO;
import com.issueflow.dto.resp.FlowNodeVO;
import com.issueflow.dto.resp.FlowTransitionVO;
import com.issueflow.entity.FlowNode;
import com.issueflow.entity.FlowTransition;
import com.issueflow.entity.Issue;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.handler.StateMachine;
import com.issueflow.mapper.FlowNodeMapper;
import com.issueflow.mapper.FlowTransitionMapper;
import com.issueflow.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流程定义服务（R2）：流程节点 / 流转规则 CRUD + 节点坐标持久化 + 流程图查询。
 * <p>
 * 数据模型与 DDL 保持一致：flow_transition 以 from_node_id / to_node_id 关联 flow_node，
 * flow_node.status_code 与 IssueStatusEnum(0-4) 一一对应且唯一。
 * 写操作成功后显式调用 {@link StateMachine#reload()}，配置即时生效。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class FlowDefinitionService {

    /** 节点可绑定的状态码范围（不开放 >=5 的扩展位） */
    private static final int STATUS_MIN = 0;
    private static final int STATUS_MAX = 4;

    private final FlowNodeMapper flowNodeMapper;
    private final FlowTransitionMapper flowTransitionMapper;
    private final IssueMapper issueMapper;
    private final StateMachine stateMachine;
    private final PermissionService permissionService;

    /**
     * 流程图（节点 + 流转边），供可视化画布与双列表渲染
     */
    public FlowGraphVO getGraph() {
        permissionService.requirePermission("flow:view", "flow:config");
        List<FlowNode> nodes = listNodes();
        Map<Long, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        List<FlowTransition> transitions = flowTransitionMapper.selectList(
                new LambdaQueryWrapper<FlowTransition>()
                        .eq(FlowTransition::getDeleted, 0)
                        .orderByAsc(FlowTransition::getSort)
                        .orderByAsc(FlowTransition::getId));

        FlowGraphVO graph = new FlowGraphVO();
        List<FlowNodeVO> nodeVOs = new ArrayList<>();
        for (FlowNode node : nodes) {
            nodeVOs.add(toNodeVO(node));
        }
        List<FlowTransitionVO> transitionVOs = new ArrayList<>();
        for (FlowTransition transition : transitions) {
            transitionVOs.add(toTransitionVO(transition, nodeMap));
        }
        graph.setNodes(nodeVOs);
        graph.setTransitions(transitionVOs);
        return graph;
    }

    /**
     * 新建流程节点（状态码仅允许 0-4 且未被占用）
     */
    @Transactional
    public FlowNodeVO createNode(FlowNodeReq req) {
        permissionService.requirePermission("flow:config");
        assertStatusCodeValid(req.getStatusCode());
        assertStatusCodeUnused(req.getStatusCode(), null);
        FlowNode node = new FlowNode();
        applyNodeReq(node, req, true);
        flowNodeMapper.insert(node);
        stateMachine.reload();
        return toNodeVO(node);
    }

    /**
     * 编辑流程节点
     */
    @Transactional
    public FlowNodeVO updateNode(Long id, FlowNodeReq req) {
        permissionService.requirePermission("flow:config");
        FlowNode exist = flowNodeMapper.selectById(id);
        if (exist == null || Objects.equals(exist.getDeleted(), 1)) {
            throw new BizException(ResultCode.FLOW_NODE_NOT_FOUND);
        }
        assertStatusCodeValid(req.getStatusCode());
        assertStatusCodeUnused(req.getStatusCode(), id);
        applyNodeReq(exist, req, false);
        flowNodeMapper.updateById(exist);
        stateMachine.reload();
        return toNodeVO(exist);
    }

    /**
     * 删除流程节点（仍被流转规则引用、或该状态下有存量问题则禁止）
     */
    @Transactional
    public void deleteNode(Long id) {
        permissionService.requirePermission("flow:config");
        FlowNode exist = flowNodeMapper.selectById(id);
        if (exist == null || Objects.equals(exist.getDeleted(), 1)) {
            throw new BizException(ResultCode.FLOW_NODE_NOT_FOUND);
        }
        long transitionCount = flowTransitionMapper.selectCount(new LambdaQueryWrapper<FlowTransition>()
                .eq(FlowTransition::getDeleted, 0)
                .and(w -> w.eq(FlowTransition::getFromNodeId, id)
                        .or().eq(FlowTransition::getToNodeId, id)));
        if (transitionCount > 0) {
            throw new BizException(ResultCode.FLOW_NODE_HAS_TRANSITION);
        }
        if (exist.getStatusCode() != null) {
            long issueCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getStatus, exist.getStatusCode())
                    .eq(Issue::getDeleted, 0));
            if (issueCount > 0) {
                throw new BizException(ResultCode.FLOW_NODE_HAS_ISSUE);
            }
        }
        // status_code 唯一索引包含逻辑删除行，必须物理 DELETE，否则同状态码节点无法重建
        flowNodeMapper.physicalDeleteById(id);
        stateMachine.reload();
    }

    /**
     * 批量保存节点坐标（画布拖拽后持久化，不影响流转规则，无需 reload）
     */
    @Transactional
    public void updateNodePositions(FlowNodePositionReq req) {
        permissionService.requirePermission("flow:config");
        for (FlowNodePositionReq.PositionItem item : req.getPositions()) {
            FlowNode node = flowNodeMapper.selectById(item.getId());
            if (node == null) {
                continue;
            }
            if (item.getPosX() != null) {
                node.setPosX(item.getPosX());
            }
            if (item.getPosY() != null) {
                node.setPosY(item.getPosY());
            }
            flowNodeMapper.updateById(node);
        }
    }

    /**
     * 新建流转规则（源/目标必须为已存在节点，且 from→to 不可重复）
     */
    @Transactional
    public FlowTransitionVO createTransition(FlowTransitionReq req) {
        permissionService.requirePermission("flow:config");
        assertTransitionValid(req);
        assertTransitionUnused(req.getFromNodeId(), req.getToNodeId(), null);
        FlowTransition transition = new FlowTransition();
        applyTransitionReq(transition, req, true);
        flowTransitionMapper.insert(transition);
        stateMachine.reload();
        return toTransitionVO(transition, nodeMapOf(transition));
    }

    /**
     * 编辑流转规则
     */
    @Transactional
    public FlowTransitionVO updateTransition(Long id, FlowTransitionReq req) {
        permissionService.requirePermission("flow:config");
        FlowTransition exist = flowTransitionMapper.selectById(id);
        if (exist == null || Objects.equals(exist.getDeleted(), 1)) {
            throw new BizException(ResultCode.FLOW_TRANSITION_NOT_FOUND);
        }
        assertTransitionValid(req);
        assertTransitionUnused(req.getFromNodeId(), req.getToNodeId(), id);
        applyTransitionReq(exist, req, false);
        flowTransitionMapper.updateById(exist);
        stateMachine.reload();
        return toTransitionVO(exist, nodeMapOf(exist));
    }

    /**
     * 删除流转规则
     */
    @Transactional
    public void deleteTransition(Long id) {
        permissionService.requirePermission("flow:config");
        FlowTransition exist = flowTransitionMapper.selectById(id);
        if (exist == null || Objects.equals(exist.getDeleted(), 1)) {
            throw new BizException(ResultCode.FLOW_TRANSITION_NOT_FOUND);
        }
        // (from_node_id,to_node_id) 唯一索引包含逻辑删除行，必须物理 DELETE
        flowTransitionMapper.physicalDeleteById(id);
        stateMachine.reload();
    }

    /**
     * 恢复默认流程（物理清空后重建 5 节点 + 6 流转，与 DDL 种子一致）
     */
    @Transactional
    public FlowGraphVO resetDefault() {
        permissionService.requirePermission("flow:config");
        flowTransitionMapper.physicalDeleteAll();
        flowNodeMapper.physicalDeleteAll();

        Map<Integer, Long> idByStatus = new HashMap<>();
        Object[][] nodeSeeds = {
                {"待处理", "OPEN", 0, 1, "#909399", 120, 80, 0, "问题创建后待处理"},
                {"处理中", "IN_PROGRESS", 1, 2, "#409EFF", 320, 80, 1, "开发人员处理中"},
                {"待验证", "PENDING_VERIFY", 2, 2, "#E6A23C", 520, 80, 2, "提交修复待测试验证"},
                {"验证通过", "VERIFIED", 3, 2, "#67C23A", 720, 80, 3, "测试验证通过"},
                {"已关闭", "CLOSED", 4, 3, "#909399", 920, 80, 4, "问题已关闭"}
        };
        for (Object[] seed : nodeSeeds) {
            FlowNode node = new FlowNode();
            node.setName((String) seed[0]);
            node.setCode((String) seed[1]);
            node.setStatusCode((Integer) seed[2]);
            node.setNodeType((Integer) seed[3]);
            node.setColor((String) seed[4]);
            node.setPosX((Integer) seed[5]);
            node.setPosY((Integer) seed[6]);
            node.setSort((Integer) seed[7]);
            node.setDescription((String) seed[8]);
            node.setEnabled(1);
            flowNodeMapper.insert(node);
            idByStatus.put(node.getStatusCode(), node.getId());
        }

        Object[][] transitionSeeds = {
                {0, 1, "CLAIM", "认领", "DEVELOPER,ADMIN", 0, null, 1},
                {1, 2, "SUBMIT_FIX", "提交修复", "DEVELOPER,ADMIN", 0, null, 2},
                {2, 3, "VERIFY_PASS", "验证通过", "TESTER,ADMIN", 0, null, 3},
                {2, 1, "VERIFY_REJECT", "验证回退", "TESTER,ADMIN", 1, "flow_reject_enabled", 4},
                {3, 4, "CLOSE", "关闭", "TESTER,ADMIN", 0, null, 5},
                {4, 0, "REOPEN", "重开", "ADMIN", 0, "flow_reopen_enabled", 6}
        };
        for (Object[] seed : transitionSeeds) {
            FlowTransition transition = new FlowTransition();
            transition.setFromNodeId(idByStatus.get((Integer) seed[0]));
            transition.setToNodeId(idByStatus.get((Integer) seed[1]));
            transition.setActionCode((String) seed[2]);
            transition.setActionName((String) seed[3]);
            transition.setAllowRoles((String) seed[4]);
            transition.setRemarkRequired((Integer) seed[5]);
            transition.setConfigKey((String) seed[6]);
            transition.setEnabled(1);
            transition.setSort((Integer) seed[7]);
            flowTransitionMapper.insert(transition);
        }
        stateMachine.reload();
        return getGraph();
    }

    // ------------------------------------------------------------------
    // 私有工具
    // ------------------------------------------------------------------

    private List<FlowNode> listNodes() {
        return flowNodeMapper.selectList(new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getDeleted, 0)
                .orderByAsc(FlowNode::getSort)
                .orderByAsc(FlowNode::getId));
    }

    private Map<Long, FlowNode> nodeMapOf(FlowTransition transition) {
        Map<Long, FlowNode> map = new HashMap<>();
        FlowNode from = flowNodeMapper.selectById(transition.getFromNodeId());
        FlowNode to = flowNodeMapper.selectById(transition.getToNodeId());
        if (from != null) {
            map.put(from.getId(), from);
        }
        if (to != null) {
            map.put(to.getId(), to);
        }
        return map;
    }

    private void assertStatusCodeValid(Integer statusCode) {
        if (statusCode == null || statusCode < STATUS_MIN || statusCode > STATUS_MAX
                || IssueStatusEnum.getByCode(statusCode) == null) {
            throw new BizException(ResultCode.FLOW_NODE_STATUS_INVALID);
        }
    }

    private void assertStatusCodeUnused(Integer statusCode, Long excludeId) {
        LambdaQueryWrapper<FlowNode> wrapper = new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getStatusCode, statusCode)
                .eq(FlowNode::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(FlowNode::getId, excludeId);
        }
        if (flowNodeMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.FLOW_NODE_STATUS_DUPLICATE);
        }
    }

    private void assertTransitionValid(FlowTransitionReq req) {
        if (Objects.equals(req.getFromNodeId(), req.getToNodeId())) {
            throw new BizException(ResultCode.VALID_ERROR, "源节点与目标节点不能相同");
        }
        FlowNode from = flowNodeMapper.selectById(req.getFromNodeId());
        FlowNode to = flowNodeMapper.selectById(req.getToNodeId());
        if (from == null || Objects.equals(from.getDeleted(), 1)
                || to == null || Objects.equals(to.getDeleted(), 1)) {
            throw new BizException(ResultCode.FLOW_NODE_NOT_FOUND);
        }
    }

    private void assertTransitionUnused(Long fromNodeId, Long toNodeId, Long excludeId) {
        LambdaQueryWrapper<FlowTransition> wrapper = new LambdaQueryWrapper<FlowTransition>()
                .eq(FlowTransition::getFromNodeId, fromNodeId)
                .eq(FlowTransition::getToNodeId, toNodeId)
                .eq(FlowTransition::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(FlowTransition::getId, excludeId);
        }
        if (flowTransitionMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.FLOW_TRANSITION_DUPLICATE);
        }
    }

    private void applyNodeReq(FlowNode node, FlowNodeReq req, boolean create) {
        node.setName(req.getName());
        node.setCode(blankToNull(req.getCode()));
        node.setStatusCode(req.getStatusCode());
        node.setNodeType(req.getNodeType() == null ? (create ? 2 : node.getNodeType()) : req.getNodeType());
        node.setColor(blankToNull(req.getColor()));
        node.setDescription(blankToNull(req.getDescription()));
        if (req.getPosX() != null || create) {
            node.setPosX(req.getPosX() == null ? 0 : req.getPosX());
        }
        if (req.getPosY() != null || create) {
            node.setPosY(req.getPosY() == null ? 0 : req.getPosY());
        }
        if (req.getSort() != null || create) {
            node.setSort(req.getSort() == null ? 0 : req.getSort());
        }
        if (req.getEnabled() != null || create) {
            node.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        }
    }

    private void applyTransitionReq(FlowTransition transition, FlowTransitionReq req, boolean create) {
        transition.setFromNodeId(req.getFromNodeId());
        transition.setToNodeId(req.getToNodeId());
        transition.setActionCode(req.getActionCode() == null ? null : req.getActionCode().trim());
        transition.setActionName(blankToNull(req.getActionName()));
        transition.setAllowRoles(normalizeRoles(req.getAllowRoles()));
        transition.setConfigKey(blankToNull(req.getConfigKey()));
        transition.setRemarkRequired(req.getRemarkRequired() == null
                ? (create ? 0 : transition.getRemarkRequired()) : req.getRemarkRequired());
        if (req.getEnabled() != null || create) {
            transition.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        }
        if (req.getSort() != null || create) {
            transition.setSort(req.getSort() == null ? 0 : req.getSort());
        }
    }

    /** 角色串归一化：去空白、去空项，逗号分隔 */
    private String normalizeRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (String role : roles.split(",")) {
            String trimmed = role.trim();
            if (!trimmed.isEmpty() && !items.contains(trimmed)) {
                items.add(trimmed);
            }
        }
        return items.isEmpty() ? null : String.join(",", items);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private FlowNodeVO toNodeVO(FlowNode node) {
        FlowNodeVO vo = new FlowNodeVO();
        vo.setId(node.getId());
        vo.setName(node.getName());
        vo.setCode(node.getCode());
        vo.setStatusCode(node.getStatusCode());
        IssueStatusEnum statusEnum = IssueStatusEnum.getByCode(node.getStatusCode());
        vo.setStatusDesc(statusEnum == null ? "" : statusEnum.getDesc());
        vo.setNodeType(node.getNodeType());
        vo.setColor(node.getColor());
        vo.setPosX(node.getPosX());
        vo.setPosY(node.getPosY());
        vo.setSort(node.getSort());
        vo.setDescription(node.getDescription());
        vo.setEnabled(node.getEnabled());
        vo.setCreatedAt(node.getCreatedAt());
        vo.setUpdatedAt(node.getUpdatedAt());
        return vo;
    }

    private FlowTransitionVO toTransitionVO(FlowTransition transition, Map<Long, FlowNode> nodeMap) {
        FlowTransitionVO vo = new FlowTransitionVO();
        vo.setId(transition.getId());
        vo.setFromNodeId(transition.getFromNodeId());
        vo.setToNodeId(transition.getToNodeId());
        FlowNode from = nodeMap.get(transition.getFromNodeId());
        FlowNode to = nodeMap.get(transition.getToNodeId());
        vo.setFromStatusCode(from == null ? null : from.getStatusCode());
        vo.setToStatusCode(to == null ? null : to.getStatusCode());
        vo.setFromName(from == null ? null : from.getName());
        vo.setToName(to == null ? null : to.getName());
        vo.setActionCode(transition.getActionCode());
        vo.setActionName(transition.getActionName());
        vo.setAllowRoles(transition.getAllowRoles());
        vo.setRemarkRequired(transition.getRemarkRequired());
        vo.setConfigKey(transition.getConfigKey());
        vo.setEnabled(transition.getEnabled());
        vo.setSort(transition.getSort());
        return vo;
    }
}
