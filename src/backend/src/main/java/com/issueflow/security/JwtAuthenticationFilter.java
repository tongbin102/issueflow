package com.issueflow.security;

import com.issueflow.common.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器：校验签名/过期/Redis 黑名单，写入 SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/doc.html",
            "/doc.html/**",
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api/attachments/static/**",
            "/favicon.ico"
    };

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    String jti = jwtUtil.getJti(token);
                    String blacklistKey = Constants.REDIS_JWT_BLACKLIST_PREFIX + jti;
                    if (redisTemplate.opsForValue().get(blacklistKey) == null) {
                        Long userId = jwtUtil.getUserId(token);
                        // Phase8 W3 #11：多角色 —— token 的 roleCode claim 已是数组，
                        // 逐个转 SimpleGrantedAuthority 写入上下文（旧版单值 token 由 getRoles 兼容为单元素列表）
                        List<String> roles = jwtUtil.getRoles(token);
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.size());
                        for (String role : roles) {
                            authorities.add(new SimpleGrantedAuthority(role));
                        }
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userId, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ignored) {
                // 解析失败：不设置上下文，交由 Spring Security 返回 401
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : WHITE_LIST) {
            if (antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
