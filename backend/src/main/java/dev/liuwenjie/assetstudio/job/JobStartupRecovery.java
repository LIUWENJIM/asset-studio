package dev.liuwenjie.assetstudio.job;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 进程重启时，上一轮没跑完的任务不会续跑（本题不要求断点续跑），
 * 统一标记为失败并写清原因，避免它们永远停在「处理中」。
 */
@Component
public class JobStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobStartupRecovery.class);

    private static final String REASON = "服务重启，任务已中断，请重新提交";

    private final JobMapper jobMapper;

    public JobStartupRecovery(JobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int count = jobMapper.failStale(REASON, LocalDateTime.now());
        if (count > 0) {
            log.warn("启动恢复：{} 条进行中的任务被标记为失败", count);
        }
    }
}