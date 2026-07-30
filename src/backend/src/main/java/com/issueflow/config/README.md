# config 包 — Spring 配置类

> 全部为 `@Configuration`（除 `SecurityConfig` 额外 `@EnableWebSecurity`）。

## MybatisPlusConfig
- `mybatisPlusInterceptor()`：注册 `PaginationInnerInterceptor(MySQL)` +（预留，`@Deprecated` 注释）`OptimisticLockerInnerInterceptor`（issue 表无 `version` 字段，MVP 默认不启用）。
- `metaObjectHandler()`：自动填充 `createdAt`/`updatedAt`（INSERT 与 INSERT_UPDATE）。

## RedisConfig
- `redisTemplate(RedisConnectionFactory)`：装配 `RedisTemplate<String, Object>`，key 用 `StringRedisSerializer`，value/hash 用 `Jackson2JsonRedisSerializer`，用于 JWT 黑名单等。

## Knife4jConfig
- `issueFlowOpenAPI()`：OpenAPI3 `OpenAPI` Bean（title=`issueFlow API`，version=`v1.0`），Knife4j 文档入口 `/doc.html`。

## WebMvcConfig（`implements WebMvcConfigurer`）
- `corsConfigurationSource()`：跨域 `CorsConfigurationSource` Bean（被 `SecurityConfig` 引用）；允许 `*` 来源、`GET/POST/PUT/DELETE/OPTIONS/PATCH`、携带凭证。
- `addResourceHandlers`：附件静态映射 `Constants.ATTACHMENT_STATIC_URL_PREFIX`(`/api/attachments/static/**`) → `file:{app.attachment-base-path}`（默认 `/data/attachments`）。

## SecurityConfig（`@EnableWebSecurity`）
- `securityFilterChain(HttpSecurity, CorsConfigurationSource)`：
  - 关闭 CSRF；启用 CORS；`anyRequest().authenticated()`；会话 `STATELESS`
  - 白名单 `WHITE_LIST`（登录/文档/静态资源）`permitAll`
  - 异常处理：`authenticationEntryPoint` → `RestAuthenticationEntryPoint`（401）；`accessDeniedHandler` → 返回 `Result.error(FORBIDDEN)`（403）
  - 在 `UsernamePasswordAuthenticationFilter` 之前插入 `JwtAuthenticationFilter`
- `passwordEncoder()`：返回 `BCryptPasswordEncoder` Bean（供 `AuthService`/`UserService` 注入）

## 依赖关系
```
SecurityConfig   → JwtAuthenticationFilter, RestAuthenticationEntryPoint, WebMvcConfig(CORS)
WebMvcConfig     → Constants(附件前缀/路径)
RedisConfig      → 被 Security/JwtUtil 黑名单、AuthService 使用
MybatisPlusConfig→ 全局分页/自动填充（被 Mapper/Service 透明使用）
Knife4jConfig    → 仅文档，无业务依赖
```
