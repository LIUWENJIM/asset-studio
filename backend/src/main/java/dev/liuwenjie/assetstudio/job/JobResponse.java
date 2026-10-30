package dev.liuwenjie.assetstudio.job;

import java.time.LocalDateTime;

/** 任务对外视图。status/stage 直接输出枚举名，前端按它做状态映射。 */
public record JobResponse(
        String id,
        String templateId,
        String templateTitle,
        String templateCoverUrl,
        String status,
        String stage,
        String resultUrl,
        String failureStage,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.templateId(),
                job.templateTitle(),
                job.templateCoverUrl(),
                job.status().name(),
                job.stage() == null ? null : job.stage().name(),
                job.resultUrl(),
                job.failureStage() == null ? null : job.failureStage().name(),
                job.failureReason(),
                job.createdAt(),
                job.updatedAt(),
                job.finishedAt());
    }
}