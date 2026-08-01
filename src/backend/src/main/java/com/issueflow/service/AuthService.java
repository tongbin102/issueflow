package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.LoginReq;
import com.issueflow.dto.resp.LoginVO;
import com.issueflow.dto.resp.UserVO;
import com.issueflow.entity.Role;
import com.issueflow.entity.User;
import com.issueflow.enums.EnableStatusEnum;
import com.issueflow.mapper.RoleMapper;
import com.issueflow.security.JwtUtil;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录 / 登出 / 当前用户
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final RoleMapper roleMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LoginLogService loginLogService;

    /**
     * 登录：校验账号密码 -> BCrypt 校验 -> 签发 token
     */
    public LoginVO login(LoginReq req) {
        User user = userService.selectByUsername(req.getUsername());
        // 语义与历史写法严格一致：账号不存在 / 状态未知 / 状态为「停用」一律按登录失败处理
        if (user == null || user.getStatus() == null || EnableStatusEnum.isDisabled(user.getStatus())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // Phase8 W3 #11：多角色 —— 取用户全部角色码签发 token，主角色（role_id 对应）置于首位
        Role role = roleMapper.selectById(user.getRoleId());
        String primaryRoleCode = role == null ? null : role.getCode();
        List<String> roles = orderPrimaryFirst(userService.resolveRoleCodes(user), primaryRoleCode);
        String token = jwtUtil.generate(user.getId(), roles);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserInfo(userService.getUserVO(user));
        vo.setRoles(roles);
        // 记录登录日志（异步写入，失败不影响登录主流程）
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            loginLogService.record(user.getId(), user.getUsername(), true, null,
                    getClientIp(request), request.getHeader("User-Agent"));
        } catch (Exception ignored) {
            // 登录日志写入失败不影响主流程
        }
        return vo;
    }

    /**
     * 登出：将当前 token 的 jti 加入 Redis 黑名单，TTL=剩余有效期
     */
    public void logout() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                String jti = jwtUtil.getJti(token);
                Date expiration = jwtUtil.getExpiration(token);
                long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
                if (ttlSeconds > 0) {
                    redisTemplate.opsForValue().set(
                            Constants.REDIS_JWT_BLACKLIST_PREFIX + jti,
                            "1",
                            ttlSeconds,
                            TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
                // 无效 token 忽略
            }
        }
        // 清空当前线程上下文
        SecurityContextHolder.clearContext();
    }

    /**
     * 当前登录用户信息
     */
    public LoginVO info() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        // Phase8 W3 #11：多角色 —— 优先取 token 解析出的角色集合，缺失时回落数据库
        List<String> roles = SecurityUtils.getCurrentRoleCodes();
        if (roles.isEmpty()) {
            roles = userService.resolveRoleCodes(user);
        }

        LoginVO vo = new LoginVO();
        vo.setToken(null);
        vo.setUserInfo(userService.getUserVO(user));
        vo.setRoles(roles);
        return vo;
    }

    /**
     * 将主角色码调整到列表首位（不改变其余角色的相对顺序）。
     *
     * @param roles           全部角色码
     * @param primaryRoleCode 主角色码（为 null 或不在列表中时原样返回）
     * @return 主角色置首的角色码列表，永不为 null
     */
    private List<String> orderPrimaryFirst(List<String> roles, String primaryRoleCode) {
        if (roles == null || roles.isEmpty()) {
            return primaryRoleCode == null
                    ? Collections.emptyList() : Collections.singletonList(primaryRoleCode);
        }
        if (primaryRoleCode == null || !roles.contains(primaryRoleCode)) {
            return roles;
        }
        List<String> ordered = new ArrayList<>(roles.size());
        ordered.add(primaryRoleCode);
        for (String code : roles) {
            if (!primaryRoleCode.equals(code)) {
                ordered.add(code);
            }
        }
        return ordered;
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(','));
        }
        return ip;
    }
}
