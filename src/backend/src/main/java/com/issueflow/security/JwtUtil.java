package com.issueflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * JWT 工具类（jjwt 0.12.x，HS256）
 * payload: {userId, roleCode, jti, exp}
 *
 * <p>Phase8 W3 #11：{@code roleCode} claim 由单个字符串升级为角色码<b>数组</b>。
 * 解析侧 {@link #getRoles(String)} 同时兼容旧版单字符串 token，
 * 保证升级瞬间仍在有效期内的存量 token 不会失效。</p>
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    /** 有效期（秒），默认 2 小时 */
    @Value("${jwt.expiration:7200}")
    private long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token（多角色）
     *
     * @param userId 用户 id
     * @param roles  角色码列表（去空白/去重后写入 roleCode claim；null 视为空数组）
     * @return JWT 字符串
     */
    public String generate(Long userId, List<String> roles) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration * 1000);
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("roleCode", sanitize(roles))
                .id(jti)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 清洗角色码集合：去 null / 去空白 / 去重（保序）
     */
    private List<String> sanitize(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                ordered.add(role.trim());
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 解析 token，返回 Claims（无效/过期会抛异常）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        Object value = parseToken(token).get("userId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 解析 token 中的全部角色码。
     *
     * <p>兼容两种 payload 形态：新版数组 {@code "roleCode":["ADMIN","TESTER"]}
     * 与旧版单值 {@code "roleCode":"ADMIN"}。</p>
     *
     * @param token JWT 字符串
     * @return 角色码列表，永不为 null
     */
    public List<String> getRoles(String token) {
        Object value = parseToken(token).get("roleCode");
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            List<String> roles = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    roles.add(item.toString().trim());
                }
            }
            return roles;
        }
        String single = value.toString().trim();
        return single.isEmpty() ? Collections.emptyList() : Collections.singletonList(single);
    }

    /**
     * 解析 token 中的主角色码（兼容保留：取角色列表首位，无角色返回 null）
     */
    public String getRoleCode(String token) {
        List<String> roles = getRoles(token);
        return roles.isEmpty() ? null : roles.get(0);
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }

    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    public boolean isTokenExpired(String token) {
        return getExpiration(token).before(new Date());
    }

    /**
     * 校验 token 签名与有效期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
