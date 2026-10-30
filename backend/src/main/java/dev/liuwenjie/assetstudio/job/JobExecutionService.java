package dev.liuwenjie.assetstudio.job;

import dev.liuwenjie.assetstudio.asset.AssetService;
import dev.liuwenjie.assetstudio.common.ApiException;
import dev.liuwenjie.assetstudio.common.ErrorCode;
import dev.liuwenjie.assetstudio.result.ResultImageRenderer;
import dev.liuwenjie.assetstudio.simulation.SimulationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 后台流水线：素材检查 → 模拟生成 → 结果整理，严格按顺序执行，任何一步失败就停下。
 * <p>
 * 每个阶段都先写一次库（{@code beginStage}）再干活，所以查询接口随时能看到「现在停在哪一步」；
 * 每一步的耗时由 {@link SimulationSettings} 现读，运行中调整对后续阶段立即生效。
 */
@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobMapper jobMapper;
    private final SimulationSettings simulationSettings;
    private final ResultImageRenderer resultImageRenderer;
    private final AssetService assetService;

    public JobExecutionService(JobMapper jobMapper,
                               SimulationSettings simulationSettings,
                               ResultImageRenderer resultImageRenderer,
                               AssetService assetService) {
        this.jobMapper = jobMapper;
        this.simulationSettings = simulationSettings;
        this.resultImageRenderer = resultImageRenderer;
        this.assetService = assetService;
    }

    /** 在后台线程里把一条任务跑完。方法自己处理异常，不会向外抛。 */
    public void run(String jobId) {
        Job job = jobMapper.findById(jobId);
        if (job == null) {
            log.warn("任务 {} 不存在，跳过执行", jobId);
            return;
        }
        if (!job.status().isActive()) {
            log.info("任务 {} 已是终态 {}，跳过执行", jobId, job.status());
            return;
        }

        JobStage currentStage = null;
        String resultUrl = null;
        try {
            for (JobStage stage : JobStage.values()) {
                currentStage = stage;
                if (jobMapper.beginStage(jobId, JobStatus.PROCESSING, stage, LocalDateTime.now()) == 0) {
                    log.info("任务 {} 无法推进到 {}，可能已被收尾", jobId, stage);
                    return;
                }
                sleep(stage);

                if (stage == job.simFailStage()) {
                    fail(jobId, stage, simulatedFailureMessage(stage));
                    return;
                }

                switch (stage) {
                    case ASSET_CHECK -> verifyAssets(jobId);
                    case GENERATING -> log.debug("任务 {} 模拟生成中", jobId);
                    case FINALIZING -> resultUrl = buildResult(job);
                }
            }

            if (jobMapper.markSucceeded(jobId, resultUrl, LocalDateTime.now()) == 0) {
                log.info("任务 {} 结束时已不是进行中状态，忽略成功写入", jobId);
            }
        } catch (ApiException e) {
            fail(jobId, currentStage, e.getMessage());
        } catch (RuntimeException e) {
            log.error("任务 {} 在阶段 {} 执行失败", jobId, currentStage, e);
            fail(jobId, currentStage, "制作过程出现异常，请稍后重试");
        }
    }

    /** 素材检查阶段不是纯等待：真的去磁盘确认文件还在。 */
    private void verifyAssets(String jobId) {
        List<String> storedPaths = jobMapper.findStoredPaths(jobId);
        if (storedPaths.isEmpty()) {
            throw new ApiException(ErrorCode.ASSET_NOT_FOUND, "任务的素材记录已丢失，请重新提交", "assetIds");
        }
        Path root = assetService.root();
        for (String storedPath : storedPaths) {
            Path file = root.resolve(storedPath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new ApiException(ErrorCode.ASSET_NOT_FOUND, "素材文件已丢失，请重新上传后再提交", "assetIds");
            }
        }
    }

    private String buildResult(Job job) {
        List<String> storedPaths = jobMapper.findStoredPaths(job.id());
        return resultImageRenderer.render(job.userId(), job.id(), storedPaths);
    }

    private void sleep(JobStage stage) {
        long delay = simulationSettings.durationOf(stage);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("任务被中断", e);
        }
    }

    private void fail(String jobId, JobStage stage, String reason) {
        if (jobMapper.markFailed(jobId, stage, reason, LocalDateTime.now()) == 0) {
            log.info("任务 {} 已是终态，忽略失败写入", jobId);
            return;
        }
        log.info("任务 {} 在阶段 {} 失败：{}", jobId, stage, reason);
    }

    private static String simulatedFailureMessage(JobStage stage) {
        return switch (stage) {
            case ASSET_CHECK -> "模拟失败：素材检查未通过";
            case GENERATING -> "模拟失败：生成服务返回错误";
            case FINALIZING -> "模拟失败：结果整理超时";
        };
    }
}