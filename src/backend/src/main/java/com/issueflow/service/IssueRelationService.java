package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.IssueRelationReq;
import com.issueflow.dto.resp.IssueRefVO;
import com.issueflow.dto.resp.IssueRelationVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.IssueRelation;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.mapper.IssueRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问题关联服务：查询关系、保存关系（含 BFS 防环）、关联下拉选项。
 *
 * 关联建模（仅存前置边）：issue_relation(issue_id=A, related_id=P, rel_type=1) ⇔ P 是 A 的前置。
 * 后置由反向查询推导：A 的后置 = 满足 (issue_id=S, related_id=A) 的 S。
 */
@Service
@RequiredArgsConstructor
public class IssueRelationService {

    private final IssueRelationMapper relationMapper;
    private final IssueMapper issueMapper;
    private final PermissionService permissionService;

    /**
     * 查询某问题的前置 / 后置列表
     */
    public IssueRelationVO getRelations(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        IssueRelationVO vo = new IssueRelationVO();

        // 前置：edges where issue_id = 当前
        List<IssueRelation> preds = relationMapper.selectByIssueId(issueId);
        List<IssueRefVO> predVos = preds.stream()
                .map(r -> toRef(issueMapper.selectById(r.getRelatedId())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 后置：edges where related_id = 当前（反向推导）
        List<Long> succIds = relationMapper.selectIssueIdsByRelatedId(issueId);
        List<IssueRefVO> succVos = succIds.stream()
                .map(id -> toRef(issueMapper.selectById(id)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        vo.setPredecessors(predVos);
        vo.setSuccessors(succVos);
        return vo;
    }

    /**
     * 关联问题下拉选项（排除指定问题自身）
     */
    public List<IssueRefVO> listOptions(Long excludeId) {
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>().eq(Issue::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(Issue::getId, excludeId);
        }
        wrapper.orderByDesc(Issue::getCreatedAt);
        return issueMapper.selectList(wrapper).stream()
                .map(this::toRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 保存关联关系（整体替换当前问题的前置/后置 + BFS 防环）。
     *
     * @param predecessorIds 前置问题 id 列表（P → 边 (issueId, P)）
     * @param successorIds   后置问题 id 列表（S → 边 (S, issueId)）
     * @param uid            当前用户 id（ADMIN 或提交人可操作）
     * @param roleCode       当前角色码
     */
    @Transactional
    public void saveRelations(Long issueId, List<Long> predecessorIds, List<Long> successorIds,
                              Long uid, String roleCode) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        boolean allowed = Constants.ROLE_ADMIN.equals(roleCode)
                || Objects.equals(issue.getReporterId(), uid);
        if (!allowed) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }

        List<Long> preds = predecessorIds == null ? Collections.emptyList() : predecessorIds;
        List<Long> succs = successorIds == null ? Collections.emptyList() : successorIds;

        // 展开待写边
        List<IssueRelation> edges = new ArrayList<>();
        for (Long p : preds) {
            edges.add(buildEdge(issueId, p));
        }
        for (Long s : succs) {
            edges.add(buildEdge(s, issueId));
        }

        // 逐边防环校验（自环 + BFS）
        for (IssueRelation e : edges) {
            if (wouldCreateCycle(e.getIssueId(), e.getRelatedId())) {
                throw new BizException(ResultCode.RELATION_CYCLE,
                        "关联存在环路：问题 " + e.getIssueId() + " 与前置 " + e.getRelatedId()
                                + " 不能形成循环依赖");
            }
        }

        // 整体替换：删除当前问题的全部前置边与后置边，再批量插入
        relationMapper.deleteByIssueId(issueId);
        relationMapper.deleteByRelatedId(issueId);
        LocalDateTime now = LocalDateTime.now();
        for (IssueRelation e : edges) {
            e.setRelType(1);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            relationMapper.insert(e);
        }
    }

    private IssueRelation buildEdge(Long issueId, Long relatedId) {
        IssueRelation e = new IssueRelation();
        e.setIssueId(issueId);
        e.setRelatedId(relatedId);
        e.setRelType(1);
        return e;
    }

    /**
     * 判断新增边 (A, Y)（Y 为 A 的前置）是否成环：
     * 从 A 沿"后继"方向（related_id=A 的 issue_id）BFS，若命中 Y 则成环。
     */
    private boolean wouldCreateCycle(Long a, Long y) {
        if (a.equals(y)) {
            return true;
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(a);
        visited.add(a);
        while (!queue.isEmpty()) {
            Long n = queue.poll();
            if (n.equals(y)) {
                return true;
            }
            for (Long succ : relationMapper.selectIssueIdsByRelatedId(n)) {
                if (visited.add(succ)) {
                    queue.add(succ);
                }
            }
        }
        return false;
    }

    private IssueRefVO toRef(Issue issue) {
        if (issue == null) {
            return null;
        }
        IssueRefVO vo = new IssueRefVO();
        vo.setId(issue.getId());
        vo.setIssueNo(issue.getIssueNo());
        vo.setTitle(issue.getTitle());
        vo.setStatus(issue.getStatus());
        return vo;
    }
}
