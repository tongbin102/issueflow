# entity 包 — 数据实体与枚举

> 实体位于 `com.issueflow.entity`；枚举位于 `com.issueflow.enums`（同层独立包，本文件一并说明）。
> 表映射使用 MyBatis-Plus 注解；逻辑删除字段 `deleted` 由 `BaseEntity` 提供（除 `Role`/`SysConfig`）。

## 一、实体（8 张表）

### User（`user`，多角色模型：`roleId` 主角色 + `roles` 全部角色）
- 字段：`id`、`username`(UNIQUE)、`password`(BCrypt)、`realName`、`email`、`phone`、`roleId`(FK→role，**主角色**)、`roles`(JSON 数组角色码，Phase8 W3 #11)、`status`(1启用/0禁用)
- `roles` 用 `JacksonTypeHandler` 读写（类上 `@TableName(autoResultMap = true)`）；为 `null` 时以 `user_role` 关系表为准
- 继承 `BaseEntity`（`createdAt`/`updatedAt`/`deleted`）

### Role（`role`，角色字典）
- 字段：`id`、`code`(UNIQUE: SUBMITTER/DEVELOPER/TESTER/ADMIN)、`name`、`description`、`createdAt`
- **不继承** `BaseEntity`（无 `deleted`）

### UserRole（`user_role`，用户-角色关系，Phase8 W3 #11 新增）
- 字段：`id`、`userId`(→`user.id`)、`roleCode`(→`role.code`)；`UNIQUE KEY (user_id, role_code)`
- 存角色**码**而非角色 id：JWT / SecurityContext 直接消费，鉴权链路免 id→code 反查
- **不继承** `BaseEntity`（无 `deleted`）——与 `RolePermission` 同口径，关联随主体整体替换，物理删除

### Issue（`issue`，问题主表）
- 字段：`id`、`issueNo`(UNIQUE, IS-YYYYMMDD-序号)、`title`、`description`、`severity`(0~3)、`tags`(逗号分隔)、`reproduceSteps`、`envOs`、`envBrowser`、`envAppVersion`、`envDevice`、`status`(0~4)、`reporterId`、`assigneeId`、`closedAt`
- 索引：`uk_issue_no`、`idx_status/reporter/assignee/created/severity/version`
- 继承 `BaseEntity`

### IssueAttachment（`issue_attachment`，附件）
- 字段：`id`、`issueId`(FK)、`fileName`(uuid.ext)、`originalName`、`filePath`、`fileSize`、`contentType`、`uploaderId`
- 继承 `BaseEntity`

### IssueHistory（`issue_history`，操作历史）
- 字段：`id`、`issueId`(FK)、`action`、`fromStatus`、`toStatus`、`operatorId`、`remark`
- 索引：`idx_issue/operator/created`、`idx_operator_created`
- 继承 `BaseEntity`

### Tag（`tag`，分类标签字典）
- 字段：`id`、`name`(UNIQUE)、`color`
- 继承 `BaseEntity`

### SysConfig（`sys_config`，主题/流程/菜单配置）
- 字段：`id`、`configKey`(UNIQUE)、`configValue`(JSON 文本)、`description`、`updatedAt`
- **不继承** `BaseEntity`（无 `deleted`、无 `createdAt`）

## 二、枚举（`com.issueflow.enums`）

### RoleEnum
`SUBMITTER`(提交者) / `DEVELOPER`(开发人员) / `TESTER`(测试人员) / `ADMIN`(管理员)；`getByCode(String)`

### IssueStatusEnum
`OPEN=0`(待处理) / `IN_PROGRESS=1`(处理中) / `PENDING_VERIFY=2`(待验证) / `VERIFIED=3`(验证通过) / `CLOSED=4`(已关闭)；`getByCode(Integer)`

### SeverityEnum
`FATAL=0`(致命) / `SERIOUS=1`(严重) / `NORMAL=2`(一般) / `MINOR=3`(轻微)；`getByCode(Integer)`

### HistoryActionEnum
`CREATE`(新建) / `CLAIM`(认领) / `SUBMIT_FIX`(提交修复) / `VERIFY_PASS`(验证通过) / `VERIFY_REJECT`(验证回退) / `CLOSE`(关闭) / `REOPEN`(重开) / `EDIT`(编辑)；`getByCode(String)`

## 三、依赖关系

- 实体被 `mapper` / `service` / `dto` 引用；枚举被 `service` / `handler.StateMachine` / `common.ResultCode` 引用。
- `BaseEntity` 由 `common` 提供，被除 `Role`/`SysConfig` 外的实体继承。
- 枚举中文 `desc` 经 `IssueService`/`IssueHistoryService` 反查填充到 `*VO`。
