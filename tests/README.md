# tests/ — 测试目录

测试与 `src/` 结构一一对应，分为两类：

- **单元 / 集成测试（与源码同仓）**
  - 后端：`src/backend/src/test/java/com/issueflow/`（JUnit 5 + MockMvc），由 Maven 执行 `mvn test`。
    - `IssueFlowApplicationTests`：上下文加载冒烟。
    - `AuthControllerTest`：登录成功/失败、无 token 访问受保护接口返回 401。
    - `IssueFlowTest`：登录 → 创建问题 → 完整状态流转 → 校验历史写入；非法流转返回 1002。
  - 前端：组件/工具函数测试可放置于 `src/frontend/src/**/__tests__/` 或 `*.spec.js`，由 Vitest 执行（本项目当前以构建验证为主，可后续补充）。
- **跨应用集成 / API 测试**：`tests/api/`
  - `issueflow.postman_collection.json`：Postman v2.1 集合，变量 `{{baseUrl}}` / `{{token}}`，按 登录→创建→分页→流转→看板→导出 顺序编排。
  - `test-api.sh`：bash + curl + jq 等价脚本，先登录提取 token 再依次调用。

> 说明：受构建工具约束（Maven 约定测试源码位于 `src/test`，Vite 约定测试靠近组件），单元测试与源码同目录存放以保证可构建；`tests/` 主要承载跨应用、端到端的 API 集成测试，作为「镜像层」的补充。
>
> 运行方式示例：
> ```bash
> # 后端单测（需本地 MySQL/Redis 就绪）
> cd src/backend && mvn test
> # API 集成测试
> bash tests/api/test-api.sh
> ```
