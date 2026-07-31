package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.resp.RedisInfoVO;
import com.issueflow.service.RedisMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 监控控制器（Phase 7 T7）。
 *
 * <p><b>只读</b>：本控制器只暴露一个 {@code INFO} 查询端点，不提供也不允许新增
 * {@code DEL} / {@code FLUSH} / {@code CONFIG SET} 等写操作（ARCH §7.9 安全红线）。</p>
 *
 * <p><b>失败降级</b>：Redis 不可用时返回 HTTP 200 + {@code available=false} +
 * 归一化 {@code errorMessage}，由前端渲染错误卡片 —— 监控页在被监控对象挂掉时
 * 抛 500 白屏是设计错误。</p>
 */
@RestController
@RequestMapping("/api/admin/redis")
@RequiredArgsConstructor
public class RedisMonitorController {

    private final RedisMonitorService redisMonitorService;

    /**
     * 获取 Redis 运行信息（{@code redis:monitor}）。
     *
     * @return 监控视图：server / memory / stats / keyspace / dbSize；
     *         异常时 {@code available=false} 且 HTTP 仍为 200
     */
    @GetMapping("/info")
    public Result<RedisInfoVO> info() {
        return Result.success(redisMonitorService.info());
    }
}
