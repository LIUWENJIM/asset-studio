package dev.liuwenjie.assetstudio.job;

/** 任务整体状态。PENDING/PROCESSING 之外的两个终态一旦写入就不再回退。 */
public enum JobStatus {
    /** 已受理，等待后台线程取走 */
    PENDING,
    /** 后台正在按阶段推进 */
    PROCESSING,
    SUCCEEDED,
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == PROCESSING;
    }
}