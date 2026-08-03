package com.issueflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 数据管理任务专用线程池（Phase10）。
 *
 * <p><b>为什么单独一个池而不复用 loginLogExecutor</b>：
 * 备份 / 恢复是长耗时（分钟级）、强 IO 的重任务，混进日志池会把登录日志堵死。</p>
 *
 * <p><b>为什么 core=1 / max=1 / queue=0</b>：
 * 备份与恢复在业务上本就全局互斥（{@code dm:lock:global}），
 * 线程池只留一条通道是第二道防线 —— 即使锁逻辑将来被改坏，
 * 也不会出现两个 mysqldump / mysql import 同时打库。</p>
 *
 * <p><b>拒绝策略用 AbortPolicy</b>：与登录日志相反，这里宁可快速失败并告诉用户
 * 「已有任务在执行」，也绝不能让调用线程（HTTP 线程）同步跑一个分钟级任务。</p>
 */
@Slf4j
@Configuration
public class DataTaskAsyncConfig {

    /** 数据管理任务线程池 Bean 名，{@code @Async} 按此名引用 */
    public static final String DATA_TASK_EXECUTOR = "dataTaskExecutor";

    /**
     * 备份 / 恢复任务线程池。
     *
     * @return 线程池执行器
     */
    @Bean(DATA_TASK_EXECUTOR)
    public Executor dataTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // 队列容量 1：允许一个任务在跑、一个在等，再多直接拒绝
        executor.setQueueCapacity(1);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("if-datatask-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅停机：等在途备份/恢复收尾，最多 60s
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("[DataTaskAsyncConfig] dataTaskExecutor initialized, core=1 max=1 queue=1");
        return executor;
    }
}
