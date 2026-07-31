# security 包 — JWT 认证与安全

> 采用 Spring Security 6 `SecurityFilterChain`（无状态 STATELESS），以 `JwtAuthenticationFilter` 注入 `SecurityContext`，不依赖 `WebSecurityConfigurerAdapter`。

## 职责

无状态鉴权（Phase8 W3 #11 起为多角色）：校验 JWT 签名与有效期 → 校验 Redis 黑名单 → 将 `userId`（principal）与全部 `roleCode`（多个 `SimpleGrantedAuthority`）写入 `SecurityContext`；未认证统一返回 401，越权统一返回 403。

## 组件

### JwtUtil（`@Component`）
- `String generate(Long userId, List<String> roles)` — 生成 token；payload：`{userId, roleCode(角色码数组), jti(uuid), exp}`，HS256，有效期 `jwt.expiration`（默认 7200s）
- `Claims parseToken(String)` / `Long getUserId(String)` / `List<String> getRoles(String)`（兼容旧版单值 token）/ `String getRoleCode(String)`（取角色列表首位，兼容保留）/ `String getJti(String)` / `Date getExpiration(String)`
- `boolean validateToken(String)` — 校验签名且未过期

### JwtAuthenticationFilter（`@Component`，`OncePerRequestFilter`）
- `doFilterInternal`：白名单（`WHITE_LIST`）直接放行；否则取 `Bearer` token → `validateToken` → 比对 Redis 黑名单（`Constants.REDIS_JWT_BLACKLIST_PREFIX + jti`）→ 遍历 `jwtUtil.getRoles(token)` 为每个角色构建一个 `SimpleGrantedAuthority`，组装 `UsernamePasswordAuthenticationToken(userId, null, authorities)` 写入 `SecurityContext`
- 白名单含：`/api/auth/login`、`/doc.html**`、`/v3/api-docs**`、`/swagger-resources**`、`/swagger-ui**`、`/api/attachments/static/**`、`/favicon.ico`

### RestAuthenticationEntryPoint（`@Component`，`AuthenticationEntryPoint`）
- `commence`：未认证时返回 `401` + `Result.error(ResultCode.UNAUTHORIZED)`（JSON）

## Redis 黑名单机制

- 登出（`AuthService.logout`）将 `jwt:blacklist:{jti}` 写入 Redis，TTL = token 剩余有效期。
- 过滤器每次请求校验该 key 是否存在；存在则视为已失效，不写入上下文，最终由 Spring Security 返回 401。
- Key 前缀常量：`Constants.REDIS_JWT_BLACKLIST_PREFIX = "jwt:blacklist:"`

## 对外接口 / 依赖关系

- 对外：被 `SecurityConfig` 装配（在 `UsernamePasswordAuthenticationFilter` 之前插入）。
- 依赖：`JwtUtil` ↔ `SecurityUtils`（取当前用户）；`RedisTemplate`（黑名单）；`Constants`（白名单前缀）。
- 配合：`GlobalExceptionHandler` 处理 `AccessDeniedException` → 403；`AuthService.logout` 写黑名单。
