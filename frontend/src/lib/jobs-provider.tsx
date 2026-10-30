"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { describeError, listJobs } from "./api";
import { isActiveJob, type Job } from "./types";

/** 轮询节奏：有变化就用基础间隔，连续多次没变化再逐级退避。 */
const BASE_INTERVAL_MS = 1000;
const MAX_INTERVAL_MS = 3000;
/** 连续这么多次没有变化后才开始退避，避免任务刚受理时被拖慢。 */
const UNCHANGED_BEFORE_BACKOFF = 3;

interface JobsContextValue {
  jobs: Job[];
  /** 首次加载尚未完成 */
  loading: boolean;
  /** 查询异常（网络/服务不可用）。它不是任务失败，jobs 会原样保留。 */
  error: string | null;
  /** 最近一次成功同步的时间戳 */
  lastSyncedAt: number | null;
  /** 仍在 PENDING/PROCESSING 的任务数 */
  activeCount: number;
  /** 手动重试/刷新 */
  refresh: () => void;
  /** 切换用户时清空并重新开始跟踪 */
  reset: () => void;
}

const JobsContext = createContext<JobsContextValue | null>(null);

function signatureOf(jobs: Job[]): string {
  return jobs
    .map((job) => `${job.id}:${job.status}:${job.stage ?? ""}:${job.resultUrl ?? ""}:${job.failureStage ?? ""}`)
    .join("|");
}

export function JobsProvider({ children }: { children: React.ReactNode }) {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = useState<number | null>(null);
  // 自增即「重新开始一轮跟踪」：手动刷新和切换用户都靠它
  const [runId, setRunId] = useState(0);

  const refresh = useCallback(() => setRunId((value) => value + 1), []);
  const reset = useCallback(() => {
    setJobs([]);
    setError(null);
    setLastSyncedAt(null);
    setLoading(true);
    setRunId((value) => value + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    let inFlight = false;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let controller: AbortController | null = null;
    let delay = BASE_INTERVAL_MS;
    let unchanged = 0;
    // 用签名而不是引用比较，避免每次请求都判定为「有变化」
    let signature = "";
    // 本轮跟踪已知的最新任务列表，决定「还要不要继续轮询」
    let latest: Job[] = [];

    const clearTimer = () => {
      if (timer !== null) {
        clearTimeout(timer);
        timer = null;
      }
    };

    const scheduleNext = () => {
      clearTimer();
      if (cancelled) {
        return;
      }
      // 没有未完成任务就彻底停下，不再空转
      if (!latest.some(isActiveJob)) {
        return;
      }
      // 页面不可见时暂停，回来再继续
      if (typeof document !== "undefined" && document.hidden) {
        return;
      }
      timer = setTimeout(() => {
        void tick();
      }, delay);
    };

    const tick = async () => {
      if (cancelled || inFlight) {
        return;
      }
      inFlight = true;
      controller = new AbortController();
      const currentController = controller;
      try {
        const next = await listJobs(currentController.signal);
        if (cancelled) {
          return;
        }
        const nextSignature = signatureOf(next);
        if (nextSignature === signature) {
          unchanged += 1;
          if (unchanged >= UNCHANGED_BEFORE_BACKOFF) {
            delay = Math.min(MAX_INTERVAL_MS, delay + 1000);
          }
        } else {
          signature = nextSignature;
          unchanged = 0;
          delay = BASE_INTERVAL_MS;
        }
        latest = next;
        setJobs(next);
        setError(null);
        setLoading(false);
        setLastSyncedAt(Date.now());
      } catch (caught) {
        if (cancelled || currentController.signal.aborted) {
          return;
        }
        // 关键：查询失败只更新错误提示，绝不改写已有任务的状态
        setError(describeError(caught));
        setLoading(false);
        unchanged += 1;
        if (unchanged >= UNCHANGED_BEFORE_BACKOFF) {
          delay = Math.min(MAX_INTERVAL_MS, delay + 1000);
        }
      } finally {
        inFlight = false;
        if (controller === currentController) {
          controller = null;
        }
        scheduleNext();
      }
    };

    const onVisibilityChange = () => {
      if (cancelled) {
        return;
      }
      if (document.hidden) {
        clearTimer();
        controller?.abort();
      } else {
        delay = BASE_INTERVAL_MS;
        unchanged = 0;
        clearTimer();
        void tick();
      }
    };

    void tick();
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      cancelled = true;
      clearTimer();
      controller?.abort();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [runId]);

  const activeCount = useMemo(() => jobs.filter(isActiveJob).length, [jobs]);

  const value = useMemo<JobsContextValue>(
    () => ({
      jobs,
      loading,
      error,
      lastSyncedAt,
      activeCount,
      refresh,
      reset,
    }),
    [jobs, loading, error, lastSyncedAt, activeCount, refresh, reset],
  );

  return <JobsContext.Provider value={value}>{children}</JobsContext.Provider>;
}

export function useJobs(): JobsContextValue {
  const context = useContext(JobsContext);
  if (!context) {
    throw new Error("useJobs 必须在 JobsProvider 内部使用");
  }
  return context;
}