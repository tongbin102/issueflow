# util 包 — 工具类

> 通用工具，无业务状态，供 Service / Controller 调用。

## IssueNoGenerator（`@Component`）
- 用途：生成问题编号 `IS-YYYYMMDD-序号`（每日 0001 起，序号补零 4 位）
- `String nextIssueNo()` — 取当日日期 → 调 `IssueMapper.maxSeqByIssueNoPrefix("IS-日期-")` → 最大序号+1 格式化（含逻辑删除行，避免序号回退）
- 并发与软删兜底：唯一索引由 DB 保证；Service 层捕获 `DuplicateKeyException` 循环重试最多 3 次（每次重新生成编号）

## FileUtil（`@Component`）
- 用途：附件落盘（`/data/attachments/{yyyyMM}/{uuid}.ext`）、校验大小与类型
- `StoredFile store(MultipartFile)` — 校验非空、≤`Constants.MAX_ATTACHMENT_SIZE`、content_type 非空；返回 `record(fileName, filePath, fileSize, contentType)`
- `boolean isImage(String contentType)` — 是否图片（可内联预览）
- `void deleteFile(String filePath)` — 删除磁盘文件（忽略失败）
- 根目录：`${app.attachment-base-path:/data/attachments}`（与 `WebMvcConfig` 一致）

## ExcelExportUtil（`final`，静态方法）
- 用途：看板数据导出 Excel（EasyExcel，内存字节数组）
- `byte[] export(DashboardVO vo)` — 将状态分布/每日趋势/严重占比/平均周期/解决率/总数/已关闭数写入 `看板统计` sheet

## SecurityUtils（`final`，静态方法）
- 用途：从 `SecurityContext` 取当前登录用户/角色
- `Long getCurrentUserId()` — principal 为 userId（兼容 Long/Number/String）
- `String getCurrentRoleCode()` — 取首个 authority（roleCode）
- 依赖：`JwtAuthenticationFilter` 已将 userId/roleCode 写入上下文

## DateTimeUtils（`final`，静态方法）
- 用途：将 `yyyy-MM-dd` 字符串解析为 `LocalDateTime`
- `LocalDateTime parseDate(String dateStr, boolean isStart)` — `isStart=true` 取 `00:00:00`，`false` 取 `23:59:59`；空串/格式错误返回 `null`
- 用于看板/历史/问题筛选的时间范围入参

## 依赖关系
```
IssueNoGenerator → IssueMapper
FileUtil         → Constants（大小/路径）、BizException
ExcelExportUtil  → dto.resp.DashboardVO、EasyExcel
SecurityUtils    → Spring Security SecurityContext
DateTimeUtils    → 无依赖（被 service/controller 的筛选逻辑调用）
```
