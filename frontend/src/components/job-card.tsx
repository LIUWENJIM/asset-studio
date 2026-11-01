import { formatDateTime } from "@/lib/format";
import { isActiveJob, type Job } from "@/lib/types";
import { StatusBadge } from "@/components/job-status";
import { StageTrack } from "@/components/stage-track";

/** 历史记录里的一张任务卡：状态、阶段、时间、失败原因或结果预览。 */
export function JobCard({ job }: { job: Job }) {
  const active = isActiveJob(job);
  const succeeded = job.status === "SUCCEEDED" && job.resultUrl;

  return (
    <article className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="relative aspect-[3/4] overflow-hidden bg-panel-raised">
        {succeeded ? (
          <a
            href={job.resultUrl ?? "#"}
            target="_blank"
            rel="noreferrer"
            className="block h-full w-full"
            aria-label={`查看「${job.templateTitle}」的结果大图`}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={job.resultUrl ?? ""}
              alt={`${job.templateTitle} 的生成结果`}
              loading="lazy"
              className="h-full w-full object-cover"
            />
          </a>
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-2 px-4 text-center">
            {job.status === "FAILED" ? (
              <>
                <span aria-hidden className="text-lg text-danger">
                  ✕
                </span>
                <p className="text-xs leading-relaxed text-danger">
                  {job.failureReason ?? "制作失败"}
                </p>
                {job.failureStage ? (
                  <p className="text-[10px] text-ink-faint">
                    停在「
                    {job.failureStage === "ASSET_CHECK"
                      ? "素材检查"
                      : job.failureStage === "GENERATING"
                        ? "模拟生成"
                        : "结果整理"}
                    」
                  </p>
                ) : null}
              </>
            ) : (
              <>
                <span
                  aria-hidden
                  className="h-5 w-5 animate-spin rounded-full border-2 border-line-strong border-t-accent"
                />
                <p className="text-xs text-ink-muted">
                  {job.status === "PENDING" ? "排队中…" : "制作中…"}
                </p>
              </>
            )}
          </div>
        )}

        </div>

      <div className="space-y-1.5 p-3">
        <StatusBadge status={job.status} stage={job.stage} />
        <h3 className="truncate text-xs font-medium text-ink">
          {job.templateTitle}
        </h3>
        <time
          dateTime={job.createdAt}
          className="block text-[10px] text-ink-faint"
        >
          {formatDateTime(job.createdAt)}
        </time>

        <StageTrack
          status={job.status}
          stage={job.stage}
          failureStage={job.failureStage}
        />

        {active ? (
          <p className="text-[10px] text-ink-faint">后台继续处理，可离开此页</p>
        ) : null}
      </div>
    </article>
  );
}