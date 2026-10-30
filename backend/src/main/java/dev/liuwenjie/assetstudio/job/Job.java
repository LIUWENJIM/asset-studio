package dev.liuwenjie.assetstudio.job;

import java.time.LocalDateTime;

/**
 * 一条制作任务。模板标题与封面在受理时一并快照，历史列表因此不依赖模板是否还在。
 * simFailStage 同样是受理时的快照。
 */
public record Job(
        String id,
        String userId,
        String templateId,
        String templateTitle,
        String templateCoverUrl,
        JobStatus status,
        JobStage stage,
        String resultUrl,
        JobStage failureStage,
        String failureReason,
        JobStage simFailStage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt) {
}