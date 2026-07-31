package com.issueflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置（Phase 7）。
 *
 * <p>目前仅服务于登录日志埋点：登录是最高频且最不能被拖慢的接口，
 * 日志落库必须与主流程解耦。</p>
 *
 * <p><b>拒绝策略特意选 CallerRunsPolicy 而非 AbortPolicy</b>：队列打满时宁可让调用线程
 * 同步写一条日志（多几毫秒），也不能丢审计记录 —— 但配合 {@code try/catch}
 * 兜底，即便同步写也抛不到登录主流程上。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 登录日志线程池 Bean 名，{@code @Async} 按此名引用 */
    public static final String LOGIN_LOG_EXECUTOR = "loginLogExecutor";

    /**
     * 登录日志专用线程池。
     *
     * @return 线程池执行器
     */
    @Bean(LOGIN_LOG_EXECUTOR)
    public Executor loginLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("if-loginlog-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待在途日志写完，最多 10s
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("[AsyncConfig] loginLogExecutor initialized, core=2 max=4 queue=500");
        return executor;
    }
}
