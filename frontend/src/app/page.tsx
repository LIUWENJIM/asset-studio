import Link from "next/link";
import { fetchCategories, fetchTemplates } from "@/lib/server-api";
import type { Template } from "@/lib/types";
import { CategoryTabs } from "@/components/category-tabs";
import { TemplateCard } from "@/components/template-card";

// 模板数据来自 Java 后端，每次请求实时读取
export const dynamic = "force-dynamic";

interface PageProps {
  searchParams: Promise<{ category?: string }>;
}

export default async function TemplatesPage({ searchParams }: PageProps) {
  const { category } = await searchParams;
  const activeCategory = category?.trim() || undefined;

  let templates: Template[] = [];
  let categories: string[] = [];
  let loadError: string | null = null;

  try {
    [templates, categories] = await Promise.all([
      fetchTemplates(activeCategory),
      fetchCategories(),
    ]);
  } catch {
    loadError = "模板加载失败，请确认后端服务可用后重试。";
  }

  return (
    <div className="pt-8">
      <section className="flex flex-col gap-3 border-b border-line pb-6">
        <p className="text-xs uppercase tracking-[0.18em] text-ink-faint">
          Templates
        </p>
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          挑一个模板，把照片变成作品
        </h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          选择模板后上传所需数量的图片，提交即返回任务编号，制作在后台分三步完成，
          你可以继续浏览，随时回到历史记录查看结果。
        </p>
      </section>

      <div className="mt-6">
        <CategoryTabs categories={categories} active={activeCategory} />
      </div>

      {loadError ? (
        <div
          role="alert"
          className="mt-8 rounded-xl border border-danger/40 bg-danger/5 p-6 text-sm text-danger"
        >
          {loadError}
        </div>
      ) : templates.length === 0 ? (
        <div className="mt-8 rounded-xl border border-line bg-panel p-10 text-center">
          <p className="text-sm text-ink">这个分类下还没有模板</p>
          <Link
            href="/"
            className="mt-3 inline-block text-xs text-accent underline underline-offset-4"
          >
            查看全部模板
          </Link>
        </div>
      ) : (
        <ul className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {templates.map((template) => (
            <li key={template.id}>
              <TemplateCard template={template} />
            </li>
          ))}
        </ul>
      )}

      <section className="mt-14 rounded-xl border border-line bg-panel/60 p-6">
        <h2 className="text-sm font-medium text-ink">怎么用</h2>
        <ol className="mt-3 grid gap-3 text-xs leading-relaxed text-ink-muted sm:grid-cols-3">
          <li className="rounded-lg border border-line/70 bg-canvas/40 p-3">
            <span className="font-mono text-accent">01</span> 选模板，看清楚需要几张、
            什么格式的图片
          </li>
          <li className="rounded-lg border border-line/70 bg-canvas/40 p-3">
            <span className="font-mono text-accent">02</span> 上传素材，确认无误后提交，
            立刻拿到任务编号
          </li>
          <li className="rounded-lg border border-line/70 bg-canvas/40 p-3">
            <span className="font-mono text-accent">03</span> 页面会自己跟进进度，
            完成后在历史记录里预览结果
          </li>
        </ol>
      </section>
    </div>
  );
}