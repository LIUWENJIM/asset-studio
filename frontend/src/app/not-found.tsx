import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <p className="font-mono text-xs uppercase tracking-[0.2em] text-ink-faint">
        404
      </p>
      <h1 className="text-xl font-medium">页面走丢了</h1>
      <p className="max-w-sm text-sm text-ink-muted">
        链接可能已经失效，回到模板库重新挑一个吧。
      </p>
      <Link
        href="/"
        className="mt-2 rounded-lg border border-line px-4 py-2 text-xs text-ink transition-colors hover:border-accent-dim hover:text-accent"
      >
        返回模板库
      </Link>
    </div>
  );
}