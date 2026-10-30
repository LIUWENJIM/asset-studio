import type { JobStage, JobStatus } from "@/lib/types";
import { STAGE_LABELS } from "@/lib/types";

const STATUS_STYLES: Record<JobStatus, string> = {
  PENDING: "border-line-strong bg-panel-raised text-ink-muted",
  PROCESSING: "border-accent-dim/60 bg-accent/10 text-accent",
  SUCCEEDED: "border-accent-dim/60 bg-accent/15 text-accent-strong",
  FAILED: "border-danger/50 bg-danger/10 text-danger",
};

const STATUS_LABELS: Record<JobStatus, string> = {
  PENDING: "等待处理",
  PROCESSING: "处理中",
  SUCCEEDED: "已完成",
  FAILED: "制作失败",
};

export function statusLabel(status: JobStatus): string {
  return STATUS_LABELS[status];
}

export function stageLabel(stage: JobStage | null): string | null {
  return stage ? STAGE_LABELS[stage] : null;
}

export function StatusBadge({
  status,
  stage,
}: {
  status: JobStatus;
  stage: JobStage | null;
}) {
  const processing = status === "PROCESSING" && stage;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium ${STATUS_STYLES[status]}`}
    >
      {status === "PROCESSING" ? (
        <span
          aria-hidden
          className="h-1.5 w-1.5 animate-pulse rounded-full bg-current"
        />
      ) : null}
      {STATUS_LABELS[status]}
      {processing ? (
        <span className="text-ink-muted">· {STAGE_LABELS[stage]}</span>
      ) : null}
    </span>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-ink-muted">
      <span
        aria-hidden
        className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-line-strong border-t-accent"
      />
      {label ? <span className="text-xs">{label}</span> : null}
    </span>
  );
}