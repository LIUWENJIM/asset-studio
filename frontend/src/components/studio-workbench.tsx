"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { describeError, submitJob, uploadAsset } from "@/lib/api";
import { formatMegabytes, formatMimeTypes, toAcceptAttribute } from "@/lib/format";
import { useJobs } from "@/lib/jobs-provider";
import type { Template } from "@/lib/types";
import {
  collectAssetIds,
  createEmptySlots,
  emptySlot,
  validateFile,
  type UploadSlotState,
} from "@/lib/upload-slot";
import { HistoryPanel } from "@/components/history-panel";
import { UploadSlot } from "@/components/upload-slot";

export function StudioWorkbench({ template }: { template: Template }) {
  const { refresh } = useJobs();
  const [slots, setSlots] = useState<UploadSlotState[]>(() =>
    createEmptySlots(template.requiredImageCount),
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 需要在回调里读取「上一个预览地址」以便释放，只在事件处理里读写，不在渲染期访问
  const previewUrls = useRef(new Map<number, string>());
  const controllers = useRef(new Map<number, AbortController>());

  // 离开页面时中止仍在进行的上传，并释放本地预览地址
  useEffect(() => {
    const pending = controllers.current;
    const previews = previewUrls.current;
    return () => {
      pending.forEach((controller) => controller.abort());
      pending.clear();
      previews.forEach((url) => URL.revokeObjectURL(url));
      previews.clear();
    };
  }, []);

  const patchSlot = useCallback(
    (index: number, patch: Partial<UploadSlotState>) => {
      setSlots((previous) =>
        previous.map((slot, position) =>
          position === index ? { ...slot, ...patch } : slot,
        ),
      );
    },
    [],
  );

  const handleSelect = useCallback(
    (index: number, file: File) => {
      const problem = validateFile(
        file,
        template.acceptedMimeTypes,
        template.maxFileSizeBytes,
      );
      if (problem) {
        setFormError(`第 ${index + 1} 张：${problem}`);
        return;
      }
      setFormError(null);
      setSubmitError(null);
      setNotice(null);

      const previousPreview = previewUrls.current.get(index);
      if (previousPreview) {
        URL.revokeObjectURL(previousPreview);
      }

      const previewUrl = URL.createObjectURL(file);
      previewUrls.current.set(index, previewUrl);
      patchSlot(index, {
        file,
        previewUrl,
        assetId: null,
        status: "uploading",
        error: null,
      });

      controllers.current.get(index)?.abort();
      const controller = new AbortController();
      controllers.current.set(index, controller);

      uploadAsset(file, controller.signal)
        .then((asset) => {
          if (controller.signal.aborted) {
            return;
          }
          patchSlot(index, {
            assetId: asset.assetId,
            status: "ready",
            error: null,
          });
        })
        .catch((caught: unknown) => {
          if (controller.signal.aborted) {
            return;
          }
          patchSlot(index, {
            assetId: null,
            status: "error",
            error: describeError(caught),
          });
        })
        .finally(() => {
          if (controllers.current.get(index) === controller) {
            controllers.current.delete(index);
          }
        });
    },
    [patchSlot, template.acceptedMimeTypes, template.maxFileSizeBytes],
  );

  const handleRemove = useCallback(
    (index: number) => {
      controllers.current.get(index)?.abort();
      controllers.current.delete(index);
      const previousPreview = previewUrls.current.get(index);
      if (previousPreview) {
        URL.revokeObjectURL(previousPreview);
        previewUrls.current.delete(index);
      }
      patchSlot(index, emptySlot());
      setFormError(null);
    },
    [patchSlot],
  );

  const assetIds = collectAssetIds(slots);
  const uploading = slots.some((slot) => slot.status === "uploading");
  const canSubmit = assetIds !== null && !uploading && !submitting;

  const handleSubmit = useCallback(async () => {
    if (!assetIds || submitting) {
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    setNotice(null);
    try {
      const job = await submitJob(template.id, assetIds);
      // 受理即返回：按钮立刻恢复，任务交给 History 跟踪
      setNotice(`任务已受理（${job.id.slice(0, 8)}），正在后台制作`);
      refresh();
    } catch (caught) {
      setSubmitError(describeError(caught));
    } finally {
      setSubmitting(false);
    }
  }, [assetIds, refresh, submitting, template.id]);

  const readyCount = slots.filter((slot) => slot.status === "ready").length;

  return (
    <div className="grid gap-6 pt-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <section aria-labelledby="studio-title" className="min-w-0">
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 rounded text-xs text-ink-muted transition-colors hover:text-ink"
        >
          <span aria-hidden>←</span> 返回模板库
        </Link>

        <div className="mt-4 flex flex-col gap-2">
          <p className="text-xs uppercase tracking-[0.18em] text-ink-faint">
            {template.category}
          </p>
          <h1
            id="studio-title"
            className="text-2xl font-semibold tracking-tight"
          >
            {template.title}
          </h1>
          <p className="max-w-xl text-sm leading-relaxed text-ink-muted">
            {template.description}
          </p>
        </div>

        <div className="mt-5 overflow-hidden rounded-xl border border-line bg-panel-raised">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={template.coverUrl}
            alt={`${template.title} 示例效果`}
            className="aspect-[16/9] w-full object-cover"
          />
        </div>

        <div className="mt-6 rounded-xl border border-line bg-panel/60 p-5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-sm font-medium text-ink">上传素材</h2>
            <p className="text-[11px] text-ink-faint">
              需要 {template.requiredImageCount} 张 ·{" "}
              {formatMimeTypes(template.acceptedMimeTypes)} · 单张不超过{" "}
              {formatMegabytes(template.maxFileSizeBytes)}
            </p>
          </div>

          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {slots.map((slot, index) => (
              <UploadSlot
                key={index}
                index={index}
                total={slots.length}
                slot={slot}
                accept={toAcceptAttribute(template.acceptedMimeTypes)}
                disabled={submitting}
                onSelect={handleSelect}
                onRemove={handleRemove}
              />
            ))}
          </div>

          <div className="mt-4 space-y-2" aria-live="polite">
            {formError ? (
              <p role="alert" className="text-xs text-danger">
                {formError}
              </p>
            ) : null}
            {submitError ? (
              <p role="alert" className="text-xs text-danger">
                {submitError}
              </p>
            ) : null}
            {notice ? (
              <p role="status" className="text-xs text-accent">
                {notice}
              </p>
            ) : null}
            {!formError && !submitError && !notice ? (
              <p className="text-[11px] text-ink-faint">
                {uploading
                  ? "图片正在上传，请稍候…"
                  : readyCount < slots.length
                    ? `还差 ${slots.length - readyCount} 张图片`
                    : "素材已就绪，可以提交制作"}
              </p>
            ) : null}
          </div>

          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-canvas transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:bg-line-strong disabled:text-ink-faint"
          >
            {submitting ? (
              <>
                <span
                  aria-hidden
                  className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-canvas/40 border-t-canvas"
                />
                提交中…
              </>
            ) : (
              "开始制作"
            )}
          </button>

          <p className="mt-2 text-center text-[10px] text-ink-faint">
            提交后可以离开此页，制作在后台继续，结果会出现在右侧 History
          </p>
        </div>
      </section>

      <div className="lg:sticky lg:top-20 lg:h-[calc(100dvh-6.5rem)]">
        <HistoryPanel />
      </div>
    </div>
  );
}