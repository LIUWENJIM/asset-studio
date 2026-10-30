"use client";

import { formatRelativeTime } from "@/lib/format";
import { useJobs } from "@/lib/jobs-provider";
import { JobCard } from "@/components/job-card";

/**
 * 右侧历史栏。数据全部来自 JobsProvider，组件本身不发起请求，
 * 因此切换页面不会重建轮询，也不会出现多个轮询互相覆盖。
 */
export function HistoryPanel() {
  const { jobs, loading, error, lastSyncedAt, activeCount, refresh } = useJobs();

  return (
    <aside
      aria-label="制作历史"
      className="flex h-full flex-col rounded-xl border border-line bg-panel/60"
    >
      <header className="flex items-center justify-between gap-2 border-b border-line px-4 py-3">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-medium text-ink">History</h2>
          {activeCount > 0 ? (
            <span className="rounded-full bg-accent/15 px-2 py-0.5 text-[10px] text-accent">
              {activeCount} 进行中
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-ink-faint" aria-live="polite">
            {lastSyncedAt ? formatRelativeTime(lastSyncedAt) : "尚未同步"}
          </span>
          <button
            type="button"
            onClick={refresh}
            aria-label="立即刷新历史记录"
            title="立即刷新"
            className="rounded-md border border-line p-1.5 text-ink-muted transition-colors hover:border-line-strong hover:text-ink"
          >
            <svg
              aria-hidden
              viewBox="0 0 24 24"
              className="h-3.5 w-3.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M21 12a9 9 0 1 1-2.64-6.36" />
              <path d="M21 3v6h-6" />
            </svg>
          </button>
        </div>
      </header>

      {/* 查询异常与任务失败是两回事：这里只提示「拿不到最新状态」，卡片保持原样 */}
      {error ? (
        <div
          role="alert"
          className="flex items-center justify-between gap-2 border-b border-danger/30 bg-danger/5 px-4 py-2 text-[11px] text-danger"
        >
          <span>{error}</span>
          <button
            type="button"
            onClick={refresh}
            className="shrink-0 rounded border border-danger/40 px-2 py-0.5 text-danger transition-colors hover:bg-danger/10"
          >
            重试
          </button>
        </div>
      ) : null}

      <div className="scroll-thin min-h-0 flex-1 overflow-y-auto p-3">
        {loading && jobs.length === 0 ? (
          <div className="flex items-center justify-center gap-2 py-10 text-xs text-ink-muted">
            <span
              aria-hidden
              className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-line-strong border-t-accent"
            />
            正在读取历史记录…
          </div>
        ) : jobs.length === 0 ? (
          <div className="rounded-lg border border-dashed border-line px-4 py-10 text-center">
            <p className="text-xs text-ink-muted">还没有制作记录</p>
            <p className="mt-1 text-[10px] leading-relaxed text-ink-faint">
              上传图片并提交后，任务会出现在这里
            </p>
          </div>
        ) : (
          <ul className="grid grid-cols-2 gap-3">
            {jobs.map((job) => (
              <li key={job.id}>
                <JobCard job={job} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  );
}