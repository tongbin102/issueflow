package com.issueflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 启动类冒烟测试：验证 Spring 应用上下文能够正常加载。
 * <p>
 * 注意：@SpringBootTest 会加载完整上下文（含 DataSource / Redis 自动配置），
 * 运行前需保证 MySQL 与 Redis 可用（推荐先 {@code docker compose up -d}）。
 * 若依赖不可达，本用例将失败——这是环境依赖问题，非代码问题。
 * </p>
 */
@SpringBootTest
class IssueFlowApplicationTests {

    /**
     * 上下文加载成功即通过。
     */
    @Test
    void contextLoads() {
        // 仅验证容器成功装配，无额外断言
    }
}
