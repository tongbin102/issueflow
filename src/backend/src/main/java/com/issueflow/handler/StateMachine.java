package com.issueflow.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.Constants;
import com.issueflow.entity.FlowNode;
import com.issueflow.entity.FlowTransition;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.mapper.FlowNodeMapper;
import com.issueflow.mapper.FlowTransitionMapper;
import com.issueflow.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 问题状态机：规则读库驱动 + 运行时缓存 + 兜底硬编码默认。
 * <p>
 * 设计：启动与每次流程配置写操作后 {@link #reload()} 重建规则缓存；库中无任何流转时
 * 回退到 {@link #DEFAULT_TRANSITIONS}（原 6 条硬编码），保证流转永不断裂。
 * config_key 命中的流转仍叠加 SysConfig 开关判断，兼容旧的回退/重开开关。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class StateMachine {

    private final SysConfigService sysConfigService;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowTransitionMapper flowTransitionMapper;

    /** 单条转移规则（读库构建） */
    private record Transition(int from, int to, Set<String> roles,
                              String actionCode, String configKey, boolean remarkRequired) {
    }

    /** 运行时规则缓存（整体替换，volatile 可见性） */
    private volatile List<Transition> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 从库重建流转规则缓存；库空则回退硬编码默认（流转永不断裂）
     */
    public void reload() {
        List<FlowTransition> transitions = flowTransitionMapper.selectList(
                new LambdaQueryWrapper<FlowTransition>()
                        .eq(FlowTransition::getEnabled, 1).eq(FlowTransition::getDeleted, 0));
        Map<Long, Integer> statusMap = flowNodeMapper.selectList(
                        new LambdaQueryWrapper<FlowNode>()
                                .eq(FlowNode::getEnabled, 1).eq(FlowNode::getDeleted, 0))
                .stream().collect(Collectors.toMap(FlowNode::getId, FlowNode::getStatusCode));

        List<Transition> built = new ArrayList<>();
        for (FlowTransition t : transitions) {
            Integer from = statusMap.get(t.getFromNodeId());
            Integer to = statusMap.get(t.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            built.add(new Transition(from, to, parseRoles(t.getAllowRoles()),
                    t.getActionCode(), t.getConfigKey(),
                    t.getRemarkRequired() != null && t.getRemarkRequired() == 1));
        }
        if (built.isEmpty()) {
            this.rules = DEFAULT_TRANSITIONS;
        } else {
            this.rules = built;
        }
    }

    private Set<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /**
     * 判断状态流转是否被允许（单角色，兼容保留）
     */
    public boolean isAllowed(int from, int to, String roleCode) {
        return isAllowed(from, to,
                roleCode == null ? Collections.<String>emptyList() : Collections.singletonList(roleCode));
    }

    /**
     * 判断状态流转是否被允许（多角色，Phase8 W3 #11 新增）。
     *
     * <p>角色取<b>并集</b>语义：用户任一角色被该流转规则允许即放行。</p>
     *
     * @param from      源状态码
     * @param to        目标状态码
     * @param roleCodes 当前用户的全部角色码（null/空视为无权限）
     * @return 允许返回 true
     */
    public boolean isAllowed(int from, int to, Collection<String> roleCodes) {
        if (from == to) {
            return false;
        }
        Transition transition = find(from, to);
        if (transition == null) {
            return false;
        }
        if (transition.configKey() != null && !sysConfigService.isEnabled(transition.configKey())) {
            return false;
        }
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        for (String roleCode : roleCodes) {
            if (roleCode != null && transition.roles().contains(roleCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取转移对应的历史动作码（无匹配返回 null）
     */
    public String getActionCode(int from, int to) {
        Transition transition = find(from, to);
        return transition == null ? null : transition.actionCode();
    }

    /**
     * 该流转是否必填原因
     */
    public boolean isRemarkRequired(int from, int to) {
        Transition transition = find(from, to);
        return transition != null && transition.remarkRequired();
    }

    private Transition find(int from, int to) {
        for (Transition t : rules) {
            if (t.from() == from && t.to() == to) {
                return t;
            }
        }
        return null;
    }

    /** 硬编码默认流转（与原 6 条 TRANSITIONS 一致），库空时回退 */
    private static final List<Transition> DEFAULT_TRANSITIONS = List.of(
            new Transition(0, 1, Set.of(Constants.ROLE_DEVELOPER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.CLAIM.getCode(), null, false),
            new Transition(1, 2, Set.of(Constants.ROLE_DEVELOPER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.SUBMIT_FIX.getCode(), null, false),
            new Transition(2, 3, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.VERIFY_PASS.getCode(), null, false),
            new Transition(2, 1, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.VERIFY_REJECT.getCode(), Constants.CFG_FLOW_REJECT_ENABLED, true),
            new Transition(3, 4, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.CLOSE.getCode(), null, false),
            new Transition(4, 0, Set.of(Constants.ROLE_ADMIN),
                    HistoryActionEnum.REOPEN.getCode(), Constants.CFG_FLOW_REOPEN_ENABLED, false)
    );
}
