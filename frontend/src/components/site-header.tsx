"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { describeError, getSession, switchUser } from "@/lib/api";
import { useJobs } from "@/lib/jobs-provider";
import type { Session } from "@/lib/types";

export function SiteHeader() {
  const pathname = usePathname();
  const { activeCount, reset } = useJobs();
  const [session, setSession] = useState<Session | null>(null);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getSession(controller.signal)
      .then(setSession)
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(describeError(caught));
        }
      });
    return () => controller.abort();
  }, []);

  const handleSwitch = useCallback(async () => {
    if (switching) {
      return;
    }
    setSwitching(true);
    setError(null);
    try {
      const next = await switchUser();
      setSession(next);
      // 换人后必须清空并重新开始跟踪，防止旧请求覆盖新用户的数据
      reset();
    } catch (caught) {
      setError(describeError(caught));
    } finally {
      setSwitching(false);
    }
  }, [reset, switching]);

  const onLibrary = pathname === "/" || pathname.startsWith("/studio");

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-canvas/85 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-[1400px] items-center gap-6 px-5">
        <Link href="/" className="flex items-center gap-2.5 rounded-md">
          <span
            aria-hidden
            className="grid h-7 w-7 place-items-center rounded-md bg-accent text-sm font-bold text-canvas"
          >
            A
          </span>
          <span className="text-sm font-semibold tracking-wide">
            Asset Studio
          </span>
        </Link>

        <nav aria-label="主导航" className="flex items-center gap-1 text-sm">
          <Link
            href="/"
            aria-current={onLibrary ? "page" : undefined}
            className={`rounded-md px-3 py-1.5 transition-colors ${
              onLibrary
                ? "bg-panel text-ink"
                : "text-ink-muted hover:bg-panel hover:text-ink"
            }`}
          >
            模板库
          </Link>
        </nav>

        <div className="ml-auto flex items-center gap-3">
          {activeCount > 0 ? (
            <span
              className="hidden items-center gap-1.5 rounded-full border border-accent-dim/50 bg-accent/10 px-2.5 py-1 text-xs text-accent sm:inline-flex"
              role="status"
            >
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent" />
              {activeCount} 个任务处理中
            </span>
          ) : null}

          {error ? (
            <span role="alert" className="text-xs text-danger">
              {error}
            </span>
          ) : null}

          <div className="flex items-center gap-2">
            <span
              aria-hidden
              className="grid h-7 w-7 place-items-center rounded-full bg-accent-dim text-xs font-semibold text-canvas"
            >
              {session ? session.displayName.slice(0, 1) : "?"}
            </span>
            <span className="hidden text-xs text-ink-muted md:inline">
              {session ? session.displayName : "正在识别身份…"}
            </span>
            <button
              type="button"
              onClick={handleSwitch}
              disabled={switching}
              className="rounded-md border border-line px-2.5 py-1 text-xs text-ink-muted transition-colors hover:border-line-strong hover:text-ink disabled:opacity-50"
            >
              {switching ? "切换中…" : "切换用户"}
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}