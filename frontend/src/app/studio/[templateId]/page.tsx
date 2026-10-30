import Link from "next/link";
import { fetchTemplate } from "@/lib/server-api";
import { StudioWorkbench } from "@/components/studio-workbench";

export const dynamic = "force-dynamic";

interface PageProps {
  params: Promise<{ templateId: string }>;
}

export default async function StudioPage({ params }: PageProps) {
  const { templateId } = await params;

  let template;
  try {
    template = await fetchTemplate(templateId);
  } catch {
    return (
      <div className="pt-10">
        <div
          role="alert"
          className="rounded-xl border border-danger/40 bg-danger/5 p-6 text-sm text-danger"
        >
          暂时无法加载模板详情，请确认后端服务可用后重试。
        </div>
        <Link
          href="/"
          className="mt-4 inline-block text-xs text-accent underline underline-offset-4"
        >
          返回模板库
        </Link>
      </div>
    );
  }

  if (!template) {
    return (
      <div className="pt-10">
        <h1 className="text-lg font-medium">模板不存在</h1>
        <p className="mt-2 text-sm text-ink-muted">
          这个模板可能已经下架，或者链接拼错了。
        </p>
        <Link
          href="/"
          className="mt-4 inline-block text-xs text-accent underline underline-offset-4"
        >
          返回模板库
        </Link>
      </div>
    );
  }

  return <StudioWorkbench template={template} />;
}