package dev.liuwenjie.assetstudio.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/** 把任务丢进线程池。单独抽出来是为了让 JobService 只依赖它，避免与执行器循环依赖。 */
@Component
public class JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private final TaskExecutor executor;
    private final JobExecutionService executionService;

    public JobDispatcher(@Qualifier(JobExecutorConfig.JOB_EXECUTOR) TaskExecutor executor,
                         JobExecutionService executionService) {
        this.executor = executor;
        this.executionService = executionService;
    }

    public void dispatch(String jobId) {
        executor.execute(() -> {
            try {
                executionService.run(jobId);
            } catch (RuntimeException e) {
                // run() 内部已经尽力把异常落成 FAILED，这里只兜底不让线程池吞掉堆栈
                log.error("任务 {} 执行时抛出未处理异常", jobId, e);
            }
        });
    }
}