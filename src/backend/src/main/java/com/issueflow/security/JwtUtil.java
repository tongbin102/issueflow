package com.issueflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类（jjwt 0.12.x，HS256）
 * payload: {userId, roleCode, jti, exp}
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
     * 生成 token
     *
     * @param userId   用户 id
     * @param roleCode 角色码
     * @return JWT 字符串
     */
    public String generate(Long userId, String roleCode) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration * 1000);
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("roleCode", roleCode)
                .id(jti)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
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

    public String getRoleCode(String token) {
        return parseToken(token).get("roleCode", String.class);
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
