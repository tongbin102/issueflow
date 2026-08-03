package com.issueflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issueflow.common.Constants;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.service.data.DataTaskLock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 恢复期只读守卫（Phase10）。
 *
 * <p>数据恢复过程中整库会被 drop / import，此时任何业务写请求都会
 * ① 写进即将被覆盖的旧库（数据凭空消失），② 与 import 抢锁导致恢复失败。
 * 因此恢复期间必须把全站写操作拦下。</p>
 *
 * <p><b>放行清单</b>：</p>
 * <ul>
 *   <li>非写方法（GET / HEAD / OPTIONS）—— 只读期读操作照常；</li>
 *   <li>{@code /api/admin/data/**} —— 数据管理自身接口不能被自己拦死，
 *       否则恢复进度都查不了；</li>
 *   <li>{@code /api/auth/logout} —— 允许用户安全退出。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadOnlyGuardInterceptor implements HandlerInterceptor {

    private final DataTaskLock dataTaskLock;
    private final ObjectMapper objectMapper;

    /** 不受只读限制的 HTTP 方法 */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** 不受只读限制的路径前缀 */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            Constants.DATA_MANAGEMENT_API_PREFIX,
            "/api/auth/logout");

    /**
     * 请求进入 Controller 前做只读校验。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return true 放行；false 已写出 40121 响应并终止
     * @throws Exception 写响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        if (SAFE_METHODS.contains(method)) {
            return true;
        }

        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        for (String prefix : WHITELIST_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        if (!dataTaskLock.isReadOnly()) {
            return true;
        }

        log.warn("[ReadOnlyGuard] 只读期拦截写请求 {} {}", method, uri);
        writeReadOnlyResponse(response);
        return false;
    }

    /**
     * 以统一响应体写出「系统只读」错误。
     *
     * @param response 响应对象
     * @throws Exception 写出失败时抛出
     */
    private void writeReadOnlyResponse(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.error(ResultCode.DATA_SYSTEM_READONLY);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}
