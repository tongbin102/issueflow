# mapper 包 — 持久层

> 均继承 `BaseMapper<X>`，被 `@MapperScan("com.issueflow.mapper")` 扫描。
> 逻辑删除由 MP 全局 `logic-delete-field: deleted` 自动生效；分页由 `MybatisPlusConfig` 拦截器支持。

## 基础 Mapper（仅继承 BaseMapper，无自定义方法）
| Mapper | 实体 | 说明 |
|---|---|---|
| `UserMapper` | `User` | 用户 CRUD + `selectOne(username)` 等 MP 标准方法 |
| `RoleMapper` | `Role` | 角色字典 CRUD |
| `TagMapper` | `Tag` | 标签字典 CRUD |
| `SysConfigMapper` | `SysConfig` | 配置 CRUD |
| `IssueAttachmentMapper` | `IssueAttachment` | 附件 CRUD |

## IssueMapper（含自定义 `@Select` 聚合查询）
- `Long maxSeqByIssueNoPrefix(@Param("prefix") String)` — 取当日最大编号序号（含逻辑删除行，避免软删导致序号回退）；`WHERE issue_no LIKE CONCAT(#{prefix},'%')`，取 SUBSTRING 序号段的 MAX（COALESCE 空为 0）
- `List<Map<String,Object>> statusDistribution(reporterId, version, start, end)` — 状态分布 `GROUP BY status`
- `List<Map<String,Object>> trendByDay(reporterId, version, start, end)` — 每日创建趋势 `GROUP BY DATE(created_at)`
- `BigDecimal avgResolveCycle(reporterId, version, start, end)` — 平均解决周期（小时，仅 `status=4` 且 `closed_at` 非空）
- `Long countTotal(...)` / `Long countClosed(...)` — 总数 / 已关闭数
- `List<Map<String,Object>> severityRatio(...)` — 严重等级占比 `GROUP BY severity`
- 上述聚合方法均支持 `reporterId`/`version`/`start`/`end` 可选条件（`<if>` 动态 SQL，SUBMITTER 仅己）

## IssueHistoryMapper（含联表查询）
- `List<IssueHistoryVO> selectByIssue(@Param("issueId") Long)` — 某问题全部历史，LEFT JOIN `user` 取 `operatorName`，`ORDER BY created_at DESC`
- `List<IssueHistoryVO> selectPage(Page<IssueHistoryVO> page, issueId, operatorId, start, end)` — 分页（按问题 + 操作人 + 时间范围过滤，含 `operatorName`）

## 对外接口 / 依赖关系
- 对外：被 `service` 层调用（`IssueMapper`/`IssueHistoryMapper` 额外被 `DashboardService`/`IssueNoGenerator`/`IssueHistoryService` 调用）
- 依赖：`BaseEntity`/`entity` 表映射；`dto.resp.IssueHistoryVO` 作为自定义查询返回类型
- 复杂 SQL 亦可放 `resources/mapper/*.xml`（当前聚合 SQL 已用注解实现）
