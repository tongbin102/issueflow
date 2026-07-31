# service 包 — 业务逻辑层

> 全部为 `@Service` + `@RequiredArgsConstructor`；写操作标注 `@Transactional`。
> 数据范围过滤（SUBMITTER 仅查自己）统一在 `IssueService` / `DashboardService` / `IssueHistoryService` 中按 `roleCode` 判定。

## IssueService
问题 CRUD、分页筛选、权限/数据范围控制、编号生成调用、历史写入。
- `IssueVO createIssue(IssueCreateReq req, Long currentUser)` — 新建（生成编号、reporter=当前用户、status=OPEN、写 CREATE 历史；唯一索引冲突重试一次）
- `IssueVO update(Long id, IssueUpdateReq req, Long uid, String roleCode)` — 编辑（创建者/ADMIN；仅非空字段；写 EDIT 历史）
- `void delete(Long id, Long uid, String roleCode)` — 逻辑删除（级联删附件与历史）
- `PageResult<IssueVO> pageQuery(IssuePageReq req, Long uid, String roleCode)` — 分页+多条件（status/severity/tag/version/assignee/reporter/keyword/startDate/endDate）；SUBMITTER 仅己
- `IssueDetailVO detail(Long id, Long uid, String roleCode)` — 详情（含附件列表 + 最近历史，最多 20 条）

## IssueFlowService
状态流转、重开、流程开关配置。
- `IssueVO changeStatus(Long id, Integer toStatus, String remark, Long uid, String roleCode)` — 经 `StateMachine.isAllowed` 校验；回退必填原因；写历史；CLOSED 时写 `closedAt`
- `IssueVO reopen(Long id, String remark, Long uid, String roleCode)` — 仅 ADMIN 且 `flow_reopen_enabled` 开启；仅已关闭可重开
- `Map<String, Boolean> getFlowConfig()` / `void updateFlowConfig(FlowConfigReq req)` — 流程开关读写（委托 `SysConfigService`）

## IssueHistoryService
操作历史写入与查询。
- `void record(Long issueId, String action, Integer fromStatus, Integer toStatus, Long operatorId, String remark)`
- `List<IssueHistoryVO> queryByIssue(Long issueId)` — 某问题全部历史（含操作人姓名，时间倒序）
- `PageResult<IssueHistoryVO> queryPageByIssue(Long issueId, HistoryQueryReq req)` — 分页（操作人 + 时间范围）
- `void deleteByIssue(Long issueId)` — 级联逻辑删除

## DashboardService
看板聚合统计 + Excel 导出。
- `DashboardVO overview(DashboardQueryReq req, Long uid, String roleCode)` — 状态分布 / 每日趋势 / 严重占比 / 平均解决周期 / 解决率 / 总数；SUBMITTER 仅己
- `byte[] export(DashboardQueryReq req, Long uid, String roleCode)` — 委托 `ExcelExportUtil`

## AuthService
登录 / 登出 / 当前用户。
- `LoginVO login(LoginReq req)` — BCrypt 校验 → `JwtUtil.generate(userId, List<String> roles)`（Phase8 W3 #11 多角色，主角色置首）
- `void logout()` — 取 jti 写入 Redis 黑名单（TTL=剩余有效期）
- `LoginVO info()` — 当前用户信息（角色优先取 token 解析结果，缺失时回落数据库）

## UserService
用户查询、增删改查、密码加密、姓名映射、多角色解析。
- `User selectByUsername(String)` / `User getById(Long)`
- `UserVO getUserVO(User)` — 补充主角色码/名 + 全部角色码 `roles`，隐去密码
- `List<String> resolveRoleCodes(User)` — 多角色统一入口，取值优先级：`user.roles`（冗余列）→ `user_role` 关系表 → `user.role_id` 单角色兜底
- `List<String> listUserRoleCodes(Long id)` — 供 `GET /api/users/{id}/roles` 编辑回显（`user:list`）
- `PageResult<UserVO> pageUsers(int pageNum, int size)`
- `UserVO createUser(UserReq)` / `UserVO updateUser(Long id, UserReq)` — 写 `user` 后调 `UserRoleService.replaceRoles` 落关系，并对齐主角色 `roleId` 与 `roles` 首位
- `void deleteUser(Long id)`
- `Map<Long, String> userNameMap()` — userId→显示名（realName 优先，缺省 username）

## UserRoleService
用户-角色关系表（`user_role`）读写与角色码校验（Phase8 W3 #11 新增）。
> 职责边界：只管关系表；`user.role_id`（主角色）与 `user.roles`（冗余列）由 `UserService` 统一维护，避免两处写同一份数据。
- `List<String> listRoles(Long userId)` — 按 `id` 升序返回角色码（首个为主角色），永不为 null
- `List<String> normalize(List<String> roleCodes)` — 去空白 / 去重（保序）/ 剔除 `role` 表中不存在的非法码
- `void replaceRoles(Long userId, List<String> roleCodes)` — 整体替换（先删后插，`@Transactional`）
- `void removeByUserId(Long userId)` — 删除该用户全部角色关系

## TagService
标签字典管理。
- `List<Tag> list()` / `Tag create(Tag)` / `Tag update(Tag)` / `void delete(Long id)`

## SysConfigService
主题/布局/菜单/流程开关读写。
- `String getConfig(String key)` / `void setConfig(String key, String value)`（upsert）
- `boolean isEnabled(String key)` — 解析布尔（`flow_*` 开关）
- `Map<String, Boolean> getFlowConfig()` / `void setFlowConfig(Boolean reject, Boolean reopen)`
- `Map<String, Object> getPublicConfig()` — `{themeColor, layout, menuConfig, flow}`
- `void putConfig(SysConfigReq req)`

## IssueAttachmentService
附件上传/下载/预览/删除。
- `List<AttachmentVO> upload(Long issueId, MultipartFile[] files, Long uid, String roleCode)` — 创建者/ADMIN
- `void download(Long id, HttpServletResponse, boolean inline)` — 图片 inline 预览，其他 attachment 下载
- `void delete(Long id, Long uid, String roleCode)` — 逻辑删 + 删文件

## 依赖关系
```
IssueService      → IssueMapper, IssueAttachmentMapper, IssueHistoryService, IssueNoGenerator, UserService
IssueFlowService  → IssueMapper, IssueHistoryService, StateMachine, SysConfigService, UserService
DashboardService  → IssueMapper, ExcelExportUtil
AuthService       → UserService, RoleMapper, JwtUtil, PasswordEncoder, RedisTemplate, LoginLogService
UserService       → UserMapper, RoleMapper, OrganizationMapper, PasswordEncoder, PermissionService, SiteConfigService, UserRoleService
UserRoleService   → UserRoleMapper, RoleMapper
IssueAttachmentService → IssueMapper, FileUtil, UserService
所有 Service      → common(Result/BizException/Constants/PageResult)
```
