package com.issueflow.util;

import com.issueflow.common.Constants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 安全工具：从 SecurityContext 获取当前登录用户/角色
 * JwtAuthenticationFilter 将 userId 置于 principal，全部 roleCode 置于 authorities。
 *
 * <p>Phase8 W3 #11：升级为多角色。新代码请优先用
 * {@link #getCurrentRoleCodes()}（全部角色）或 {@link #hasRole(String)}（是否持有某角色）；
 * {@link #getCurrentRoleCode()} 兼容保留，返回「主角色」。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 id
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number) {
            return ((Number) principal).longValue();
        }
        if (principal instanceof String) {
            return Long.valueOf((String) principal);
        }
        return null;
    }

    /**
     * 获取当前登录用户的全部角色码（Phase8 W3 #11 新增）。
     *
     * @return 角色码列表，未登录/无角色时为空列表，永不为 null
     */
    public static List<String> getCurrentRoleCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String code = authority.getAuthority();
            if (code != null && !code.isBlank() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * 当前登录用户是否持有指定角色（Phase8 W3 #11 新增）。
     *
     * @param roleCode 角色码（为 null 返回 false）
     * @return 持有返回 true
     */
    public static boolean hasRole(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        return getCurrentRoleCodes().contains(roleCode);
    }

    /**
     * 获取当前登录用户「主角色」码（兼容既有按单角色判定的业务逻辑）。
     *
     * <p>多角色下的取值优先级，保证升级后既有行为不退化：</p>
     * <ol>
     *   <li>持有 ADMIN → 返回 ADMIN（各处 {@code ROLE_ADMIN.equals(...)} 的放行判定不受角色顺序影响）；</li>
     *   <li>否则返回首个非 SUBMITTER 角色（SUBMITTER 是「仅看自己」的数据收窄角色，
     *       兼具其他角色时不应被收窄）；</li>
     *   <li>否则返回首个角色（即纯 SUBMITTER 用户）。</li>
     * </ol>
     *
     * @return 主角色码，未登录/无角色返回 null
     */
    public static String getCurrentRoleCode() {
        List<String> codes = getCurrentRoleCodes();
        if (codes.isEmpty()) {
            return null;
        }
        if (codes.contains(Constants.ROLE_ADMIN)) {
            return Constants.ROLE_ADMIN;
        }
        for (String code : codes) {
            if (!Constants.ROLE_SUBMITTER.equals(code)) {
                return code;
            }
        }
        return codes.get(0);
    }
}
