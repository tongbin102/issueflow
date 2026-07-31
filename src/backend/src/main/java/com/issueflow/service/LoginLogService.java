package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.PageResult;
import com.issueflow.config.AsyncConfig;
import com.issueflow.dto.resp.LoginLogVO;
import com.issueflow.entity.LoginLog;
import com.issueflow.mapper.LoginLogMapper;
import com.issueflow.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志服务（Phase 7）。
 *
 * <p>成功与失败均记录（PRD Q9 方案 B）。埋点走 {@code @Async} 专用线程池，
 * 且方法内部整体 try/catch —— <b>日志写入失败绝不允许影响登录本身</b>。</p>
 *
 * <p><b>异步生效前提</b>：{@link #record} 必须由<b>其他 Bean</b>（AuthService）调用。
 * 同类内部自调用会绕过 Spring 代理导致 {@code @Async} 静默失效，
 * 因此本类不提供任何「同步方法内部转调 record」的写法。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    /** IP 列长度上限 */
    private static final int IP_MAX_LENGTH = 64;

    private final LoginLogMapper loginLogMapper;

    // ============================ 埋点写入 ============================

    /**
     * 异步记录一次登录尝试。
     *
     * <p>IP / UA 由调用方在 HTTP 线程上先行提取后传入 —— 异步线程里
     * {@code RequestContextHolder} 已失效，取不到 request。</p>
     *
     * @param userId     用户 id，用户不存在时传 null
     * @param username   登录名（冗余存储，便于失败场景追溯）
     * @param success    是否成功
     * @param failReason 失败原因（成功时传 null）
     * @param ip         客户端 IP
     * @param userAgent  原始 User-Agent
     */
    @Async(AsyncConfig.LOGIN_LOG_EXECUTOR)
    public void record(Long userId, String username, boolean success,
                       String failReason, String ip, String userAgent) {
        try {
            LoginLog entity = new LoginLog();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setIp(truncateIp(ip));
            entity.setUserAgent(UserAgentParser.truncate(userAgent));
            entity.setBrowser(UserAgentParser.parseBrowser(userAgent));
            entity.setOs(UserAgentParser.parseOs(userAgent));
            entity.setSuccess(success ? 1 : 0);
            entity.setFailReason(failReason);
            entity.setLoginAt(LocalDateTime.now());
            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[LoginLog] record failed, username={}, msg={}", username, e.getMessage());
        }
    }

    /**
     * 直接落库一条日志（供数据修复 / 测试使用）。
     *
     * @param entity 日志实体
     */
    public void save(LoginLog entity) {
        loginLogMapper.insert(entity);
    }

    /**
     * 从请求中解析客户端真实 IP。
     *
     * <p>优先级：{@code X-Forwarded-For} 首段 → {@code X-Real-IP} → {@code getRemoteAddr()}。
     * XFF 可能是 {@code client, proxy1, proxy2} 形式，必须取<b>首段</b>。</p>
     *
     * @param request HTTP 请求，可为 null
     * @return 客户端 IP，无法解析时返回 {@code unknown}
     */
    public static String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (isValidIp(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (isValidIp(realIp)) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "unknown" : remote;
    }

    private static boolean isValidIp(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private String truncateIp(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() <= IP_MAX_LENGTH ? ip : ip.substring(0, IP_MAX_LENGTH);
    }

    // ============================ 查询 ============================

    /**
     * 分页查询当前用户自己的登录日志（个人中心「账户安全 / 活动记录」）。
     *
     * @param userId  当前登录用户 id（<b>不接受外部入参</b>，由 Service 调用方从 SecurityContext 取）
     * @param pageNum 页码，从 1 开始
     * @param size    每页大小
     * @return 分页结果
     */
    public PageResult<LoginLogVO> pageMine(Long userId, int pageNum, int size) {
        Page<LoginLog> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getLoginAt)
                .orderByDesc(LoginLog::getId);
        loginLogMapper.selectPage(page, wrapper);
        List<LoginLogVO> list = new ArrayList<>(page.getRecords().size());
        for (LoginLog row : page.getRecords()) {
            list.add(toVO(row));
        }
        return PageResult.of(list, page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 取当前用户最近若干条登录日志（活动记录归并用，按时间倒序）。
     *
     * @param userId 用户 id
     * @param start  起始时间，可空
     * @param end    结束时间，可空
     * @param limit  最多条数
     * @return 日志实体列表
     */
    public List<LoginLog> recentOfUser(Long userId, LocalDateTime start, LocalDateTime end, int limit) {
        if (userId == null || limit <= 0) {
            return new ArrayList<>();
        }
        Page<LoginLog> page = new Page<>(1, limit);
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId);
        if (start != null) {
            wrapper.ge(LoginLog::getLoginAt, start);
        }
        if (end != null) {
            wrapper.le(LoginLog::getLoginAt, end);
        }
        wrapper.orderByDesc(LoginLog::getLoginAt).orderByDesc(LoginLog::getId);
        loginLogMapper.selectPage(page, wrapper);
        return page.getRecords();
    }

    /**
     * 分页查询登录日志（全局，供后续审计页复用）。
     *
     * @param keyword 可选关键字（匹配 username 或 ip）
     * @param success 可选结果过滤：1 成功 / 0 失败
     * @param pageNum 页码
     * @param size    每页大小
     * @return 分页结果
     */
    public PageResult<LoginLog> pageQuery(String keyword, Integer success, int pageNum, int size) {
        Page<LoginLog> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(LoginLog::getUsername, kw).or().like(LoginLog::getIp, kw));
        }
        if (success != null) {
            wrapper.eq(LoginLog::getSuccess, success);
        }
        wrapper.orderByDesc(LoginLog::getLoginAt).orderByDesc(LoginLog::getCreatedAt);
        loginLogMapper.selectPage(page, wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), (long) pageNum, (long) size);
    }

    /**
     * 最近若干条登录日志（供仪表盘 / 审计概览）。
     *
     * @param limit 条数
     * @return 日志列表
     */
    public List<LoginLog> recent(int limit) {
        Page<LoginLog> page = new Page<>(1, limit);
        loginLogMapper.selectPage(page, new LambdaQueryWrapper<LoginLog>()
                .orderByDesc(LoginLog::getLoginAt).orderByDesc(LoginLog::getCreatedAt));
        return page.getRecords();
    }

    // ============================ 清理 ============================

    /**
     * 物理清理早于指定时间的登录日志（内置定时任务调用）。
     *
     * @param before 截止时间
     * @return 删除条数
     */
    public int cleanBefore(LocalDateTime before) {
        if (before == null) {
            return 0;
        }
        return loginLogMapper.deleteBefore(before);
    }

    /**
     * 实体转 VO。
     *
     * @param row 日志实体
     * @return 视图对象
     */
    public LoginLogVO toVO(LoginLog row) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(row.getId());
        vo.setTime(row.getLoginAt());
        vo.setIp(row.getIp());
        vo.setBrowser(row.getBrowser());
        vo.setOs(row.getOs());
        vo.setSuccess(row.getSuccess() != null && row.getSuccess() == 1);
        vo.setFailReason(row.getFailReason());
        return vo;
    }
}
