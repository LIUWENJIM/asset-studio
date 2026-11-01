import Link from "next/link";
import { formatMegabytes, formatMimeTypes } from "@/lib/format";
import type { Template } from "@/lib/types";

export function TemplateCard({ template }: { template: Template }) {
  return (
    <Link
      href={`/studio/${template.id}`}
      className="group flex h-full flex-col overflow-hidden rounded-xl border border-line bg-panel transition-colors hover:border-accent-dim/70"
    >
      <div className="relative aspect-[4/5] overflow-hidden bg-panel-raised">
        {/* 封面由 Java 静态资源提供，Next 只做转发 */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={template.coverUrl}
          alt={`${template.title} 模板封面`}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
        />
        <span className="absolute left-3 top-3 rounded-full bg-canvas/80 px-2 py-0.5 text-[11px] text-ink-muted backdrop-blur">
          {template.category}
        </span>
      </div>

      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-sm font-medium text-ink">{template.title}</h3>
        <p className="line-clamp-2 flex-1 text-xs leading-relaxed text-ink-muted">
          {template.description}
        </p>
        <dl className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink-faint">
          <div className="flex items-center gap-1">
            <dt>需要</dt>
            <dd className="text-ink-muted">{template.requiredImageCount} 张图</dd>
          </div>
          <div className="flex items-center gap-1">
            <dt>格式</dt>
            <dd className="text-ink-muted">
              {formatMimeTypes(template.acceptedMimeTypes)}
            </dd>
          </div>
          <div className="flex items-center gap-1">
            <dt>单张上限</dt>
            <dd className="text-ink-muted">
              {formatMegabytes(template.maxFileSizeBytes)}
            </dd>
          </div>
        </dl>
      </div>
    </Link>
  );
}