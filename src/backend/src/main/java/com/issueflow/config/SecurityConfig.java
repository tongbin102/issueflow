package com.issueflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.security.JwtAuthenticationFilter;
import com.issueflow.security.RestAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Spring Security 配置：无状态、JWT 校验、白名单放行
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    /** 白名单：登录、文档、静态资源无需认证 */
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // 关闭 CSRF（前后端分离 + 无状态）
                .csrf(csrf -> csrf.disable())
                // 跨域
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated())
                // 无状态会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 异常处理：401 未认证 / 403 无权限
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler()))
                // 在用户名密码认证过滤器前插入 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 403 处理器：返回统一 Result
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJson(response, ResultCode.FORBIDDEN);
    }

    private void writeJson(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType("application/json;charset=UTF-8");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(response.getWriter(), Result.error(resultCode));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
