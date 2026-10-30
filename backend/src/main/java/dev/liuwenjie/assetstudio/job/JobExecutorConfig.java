package dev.liuwenjie.assetstudio.job;

import dev.liuwenjie.assetstudio.simulation.SimulationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 后台执行线程池。pool-size=1 时任务串行排队，>1 时并行——两种模式都能演示「多任务」，
 * 排队模式更容易观察到 PENDING 状态。
 */
@Configuration
public class JobExecutorConfig {

    public static final String JOB_EXECUTOR = "jobExecutor";

    @Bean(JOB_EXECUTOR)
    public TaskExecutor jobExecutor(SimulationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int size = Math.max(1, properties.poolSize());
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(Math.max(1, properties.queueCapacity()));
        executor.setThreadNamePrefix("job-");
        // 停机不等待：残留的 PENDING/PROCESSING 任务由启动恢复统一标记为失败
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}