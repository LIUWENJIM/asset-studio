import { STAGE_LABELS, STAGE_ORDER, type JobStage, type JobStatus } from "@/lib/types";

type StepState = "idle" | "active" | "done" | "failed";

function stepState(
  status: JobStatus,
  index: number,
  currentIndex: number,
  failedIndex: number,
): StepState {
  if (status === "SUCCEEDED") {
    return "done";
  }
  if (status === "FAILED") {
    if (failedIndex < 0) {
      return "idle";
    }
    if (index < failedIndex) return "done";
    if (index === failedIndex) return "failed";
    return "idle";
  }
  if (status === "PENDING" || currentIndex < 0) {
    return "idle";
  }
  if (index < currentIndex) return "done";
  if (index === currentIndex) return "active";
  return "idle";
}

const BAR_STYLES: Record<StepState, string> = {
  idle: "bg-line",
  active: "bg-accent/60 animate-pulse",
  done: "bg-accent",
  failed: "bg-danger",
};

const TEXT_STYLES: Record<StepState, string> = {
  idle: "text-ink-faint",
  active: "text-accent",
  done: "text-ink-muted",
  failed: "text-danger",
};

/** 三阶段进度条：让「后台停在哪一步」在卡片上直接可见。 */
export function StageTrack({
  status,
  stage,
  failureStage,
}: {
  status: JobStatus;
  stage: JobStage | null;
  failureStage: JobStage | null;
}) {
  const currentIndex = stage ? STAGE_ORDER.indexOf(stage) : -1;
  const failedIndex = failureStage ? STAGE_ORDER.indexOf(failureStage) : -1;

  return (
    <ol className="flex gap-1.5" aria-label="处理阶段">
      {STAGE_ORDER.map((item, index) => {
        const state = stepState(status, index, currentIndex, failedIndex);
        return (
          <li
            key={item}
            aria-current={state === "active" ? "step" : undefined}
            className="flex-1"
          >
            <span className={`block h-1 rounded-full ${BAR_STYLES[state]}`} />
            <span className={`mt-1 block text-[10px] ${TEXT_STYLES[state]}`}>
              {STAGE_LABELS[item]}
            </span>
          </li>
        );
      })}
    </ol>
  );
}