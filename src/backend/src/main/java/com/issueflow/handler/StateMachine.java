package com.issueflow.handler;

import com.issueflow.common.Constants;
import com.issueflow.enums.HistoryActionEnum;
import com.issueflow.enums.IssueStatusEnum;
import com.issueflow.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 问题状态机：定义状态转移规则、角色约束与流程开关
 * <p>
 * 转移表（见架构 4.3）：
 * OPEN→IN_PROGRESS (D/A)、IN_PROGRESS→PENDING_VERIFY (D/A)、
 * PENDING_VERIFY→VERIFIED (T/A)、PENDING_VERIFY→IN_PROGRESS (T/A 且需 flow_reject_enabled)、
 * VERIFIED→CLOSED (T/A)、CLOSED→OPEN (A 且需 flow_reopen_enabled)
 * </p>
 */
@Component
@RequiredArgsConstructor
public class StateMachine {

    private final SysConfigService sysConfigService;

    /** 单条转移定义 */
    private record Transition(int from, int to, Set<String> roles,
                              HistoryActionEnum action, String configKey, boolean remarkRequired) {
    }

    private static final int OPEN = IssueStatusEnum.OPEN.getCode();
    private static final int IN_PROGRESS = IssueStatusEnum.IN_PROGRESS.getCode();
    private static final int PENDING_VERIFY = IssueStatusEnum.PENDING_VERIFY.getCode();
    private static final int VERIFIED = IssueStatusEnum.VERIFIED.getCode();
    private static final int CLOSED = IssueStatusEnum.CLOSED.getCode();

    private static final List<Transition> TRANSITIONS = List.of(
            new Transition(OPEN, IN_PROGRESS, Set.of(Constants.ROLE_DEVELOPER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.CLAIM, null, false),
            new Transition(IN_PROGRESS, PENDING_VERIFY, Set.of(Constants.ROLE_DEVELOPER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.SUBMIT_FIX, null, false),
            new Transition(PENDING_VERIFY, VERIFIED, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.VERIFY_PASS, null, false),
            new Transition(PENDING_VERIFY, IN_PROGRESS, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.VERIFY_REJECT, Constants.CFG_FLOW_REJECT_ENABLED, true),
            new Transition(VERIFIED, CLOSED, Set.of(Constants.ROLE_TESTER, Constants.ROLE_ADMIN),
                    HistoryActionEnum.CLOSE, null, false),
            new Transition(CLOSED, OPEN, Set.of(Constants.ROLE_ADMIN),
                    HistoryActionEnum.REOPEN, Constants.CFG_FLOW_REOPEN_ENABLED, false)
    );

    /**
     * 判断状态流转是否被允许
     *
     * @param from     源状态 code
     * @param to       目标状态 code
     * @param roleCode 当前角色码
     * @return 允许返回 true
     */
    public boolean isAllowed(int from, int to, String roleCode) {
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
        return roleCode != null && transition.roles().contains(roleCode);
    }

    /**
     * 获取转移对应的历史动作枚举（无匹配返回 null）
     */
    public HistoryActionEnum getAction(int from, int to) {
        Transition transition = find(from, to);
        return transition == null ? null : transition.action();
    }

    private Transition find(int from, int to) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.to() == to)
                .findFirst()
                .orElse(null);
    }
}
