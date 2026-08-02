# issueFlow 后端代码质量审查报告

> 审查日期：2026-08-02
> 审查范围：`src/backend/src/main/java/com/issueflow/` 的 service/、controller/、mapper/ 目录
> 审查维度：空指针风险、@Transactional 边界、Redis/MySQL 双写一致性、批量操作原子性

## 缺陷统计

| 严重程度 | 数量 |
|:---:|:---:|
| 高 | 9 |
| 中 | 9 |
| 低 | 2 |
| **合计** | **20** |

## 高优先级缺陷（建议立即修复）

### 1. RoleService @Transactional 自调用失效
- **文件**：`service/RoleService.java:144-145`
- **问题**：`assignPermissions(Long, RolePermissionReq)` 通过 `this.assignPermissions(id, codes)` 直接调用本类中标有 `@Transactional` 的同名重载方法（第104行）。Spring AOP 基于代理实现，`this.` 内部调用绕过代理，事务注解完全失效。此方法执行了 `deleteByRoleId` + `insertBatch` 两步写操作，事务不生效将导致部分失败时数据不一致。
- **修复**：通过 `@Lazy` 自注入 `RoleService self` 后调用 `self.assignPermissions(id, codes)`；或使用 `AopContext.currentProxy()`。

### 2. RoleService.delete() 批量操作无事务
- **文件**：`service/RoleService.java:79-91`
- **问题**：`delete()` 执行 `roleMapper.deleteById(id)` + `rolePermissionMapper.deleteByRoleId(id)` 两次 DB 写操作，未添加 `@Transactional`。若第二条失败，角色记录已删除但权限关联仍残留（孤儿数据）。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`，缓存失效移至 `afterCommit`。

### 3. SysConfigService 非原子 Upsert
- **文件**：`service/SysConfigService.java:41-53`
- **问题**：`setConfig()` 先 `selectOne` 查询再 `insert`/`updateById`，未包裹 `@Transactional`。高并发下两个线程可能同时查到 `config == null`，均执行 INSERT，导致唯一索引冲突。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`；或改用 `INSERT ... ON DUPLICATE KEY UPDATE`。

### 4. FieldConfigService 缓存先删后更新（顺序错误）
- **文件**：`service/FieldConfigService.java:177-178`
- **问题**：`update()` 系统字段分支中，缓存失效 `schemaCache.evict()` 在 `configMapper.updateById()` 之前执行。缓存被清空后 DB 尚未更新，并发读取线程会从 DB 读到旧数据并回填缓存。
- **修复**：调换为先 `updateById` 后 `evict`，与非系统字段分支（第256-257行）保持一致。

### 5. DictService 事务内缓存失效（7处）
- **文件**：`service/DictService.java:153,168,189,286,308,323,348`
- **问题**：7个 `@Transactional` 方法均在事务内部执行 `dictCache.evict()`。事务提交前缓存已被清空，并发读取线程会从 DB 读到未提交的旧值并回填缓存。
- **修复**：将缓存失效移至 `afterCommit` 回调。

### 6. FieldSectionService 事务内缓存失效（5处）
- **文件**：`service/FieldSectionService.java:61,85,104,120,129`
- **问题**：同上，4个 `@Transactional` 方法在事务内部调用 `schemaCache.evict()`。
- **修复**：同上。

### 7. FieldConfigService 事务内缓存失效（5处）
- **文件**：`service/FieldConfigService.java:137,177,257,269,278`
- **问题**：同上，5个 `@Transactional` 方法在事务内部调用 `schemaCache.evict()`。
- **修复**：同上。

### 8. PermissionService.getPermissions() Redis 无降级
- **文件**：`service/PermissionService.java:199-212`
- **问题**：Redis 读取(第204行)和写入(第210行)均未包裹 try-catch。若 Redis 宕机，所有非 ADMIN 用户的 `requirePermission()` 调用将失败，阻断整个鉴权链路。
- **修复**：读取失败时降级直读 DB；写入失败时仅 log.warn。

### 9. PermissionService.invalidate() Redis 无降级
- **文件**：`service/PermissionService.java:217-222`
- **问题**：`redisTemplate.delete(...)` 未包裹 try-catch。对比 `invalidateAll()`（第86-96行）已有 try-catch 降级，`invalidate()` 缺失保护。
- **修复**：添加与 `invalidateAll()` 相同的 try-catch 模式。

## 中优先级缺陷（建议迭代修复）

### 10. IssueRelationService N+1 查询
- **文件**：`service/IssueRelationService.java:56-57, 63-64`
- **问题**：`getRelations()` 在 stream map 中对每个关联调用 `issueMapper.selectById()`，N+M 次查询。
- **修复**：汇总 ID 后 `selectBatchIds` 一次查出。

### 11. FlowDefinitionService N+1 查询
- **文件**：`service/FlowDefinitionService.java:151-163`
- **问题**：`updateNodePositions()` 循环内 `selectById` + `updateById`，2N 次操作。
- **修复**：先 `selectBatchIds` 再 `updateBatchById`。

### 12. IssueRelationService 循环单条插入
- **文件**：`service/IssueRelationService.java:134-139`
- **问题**：`saveRelations()` 循环内逐条 `insert`。
- **修复**：收集后 `saveBatch` 批量插入。

### 13. IssueFieldValueService 循环内 N+1
- **文件**：`service/IssueFieldValueService.java:54-74`
- **问题**：`saveValues()` 循环内对每个字段执行 `softDelete()` + `insert()`，3N 次操作。
- **修复**：批量查询后批量软删。

### 14. OrganizationService @Transactional 缺失
- **文件**：`service/OrganizationService.java:84-97, 102-129, 134-147`
- **问题**：`create()`、`update()`、`delete()` 三个写操作均未添加 `@Transactional`。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`。

### 15. MenuService @Transactional 缺失
- **文件**：`service/MenuService.java:85-106, 111-143, 148-161`
- **问题**：同上。
- **修复**：同上。

### 16. ProjectService.deleteProject() @Transactional 缺失
- **文件**：`service/ProjectService.java:153-160`
- **问题**：`deleteProject()` 未添加 `@Transactional`，与 `createProject()`/`updateProject()` 不一致。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`。

### 17. IssueHistoryService @Transactional 缺失
- **文件**：`service/IssueHistoryService.java:29-39, 81-84`
- **问题**：`record()` 和 `deleteByIssue()` 均为 public 方法未添加 `@Transactional`。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`。

### 18. 全局 @Transactional 缺失 rollbackFor
- **文件**：全部 Service 层 15+ 文件
- **问题**：所有 `@Transactional` 注解均为裸注解，未指定 `rollbackFor`。Spring 默认仅对 RuntimeException 回滚，受检异常不回滚。
- **修复**：统一改为 `@Transactional(rollbackFor = Exception.class)`。

## 低优先级缺陷

### 19. 只读方法缺失 readOnly 标注
- **文件**：全部 Service 层只读方法
- **问题**：纯查询方法未标注 `@Transactional(readOnly = true)`。
- **修复**：对纯查询方法添加 `@Transactional(readOnly = true)`。

### 20. RoleService.toVO() N+1 查询
- **文件**：`service/RoleService.java:129-139`
- **问题**：`list()` 对每个 Role 调用 `toVO()`，产生 N+1 查询。
- **修复**：先批量查询权限码，构建 Map 回填。
