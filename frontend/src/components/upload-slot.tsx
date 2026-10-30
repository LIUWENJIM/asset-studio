"use client";

import type { UploadSlotState } from "@/lib/upload-slot";

interface UploadSlotProps {
  index: number;
  total: number;
  slot: UploadSlotState;
  accept: string;
  disabled: boolean;
  onSelect: (index: number, file: File) => void;
  onRemove: (index: number) => void;
}

/**
 * 单个上传位：空态是虚线框，选图后立刻显示预览并进入上传中，
 * 上传失败时保留预览与错误原因，允许重选。
 */
export function UploadSlot({
  index,
  total,
  slot,
  accept,
  disabled,
  onSelect,
  onRemove,
}: UploadSlotProps) {
  const inputId = `upload-slot-${index}`;
  const uploading = slot.status === "uploading";
  const failed = slot.status === "error";
  const ready = slot.status === "ready";

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // 允许重复选择同一文件
    event.target.value = "";
    if (file) {
      onSelect(index, file);
    }
  };

  if (slot.previewUrl) {
    return (
      <div
        className={`group relative overflow-hidden rounded-xl border bg-panel-raised ${
          failed ? "border-danger/60" : ready ? "border-line" : "border-accent-dim/60"
        }`}
      >
        {/* 本地预览用 object URL，真正的素材已上传到服务端 */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={slot.previewUrl}
          alt={`第 ${index + 1} 张已选图片预览`}
          className="aspect-square h-full w-full object-cover"
        />

        <span className="absolute left-2 top-2 rounded bg-canvas/80 px-1.5 py-0.5 text-[10px] text-ink-muted">
          {index + 1} / {total}
        </span>

        {uploading ? (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 bg-canvas/70 backdrop-blur-[1px]">
            <span
              aria-hidden
              className="h-4 w-4 animate-spin rounded-full border-2 border-line-strong border-t-accent"
            />
            <span className="text-[11px] text-ink-muted" role="status">
              上传中…
            </span>
          </div>
        ) : null}

        {failed ? (
          <div className="absolute inset-x-0 bottom-0 bg-danger/90 px-2 py-1.5">
            <p className="text-[10px] leading-snug text-canvas">
              {slot.error ?? "上传失败"}
            </p>
          </div>
        ) : null}

        <div className="absolute right-2 top-2 flex gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
          <label
            htmlFor={inputId}
            className="cursor-pointer rounded bg-canvas/85 px-2 py-1 text-[10px] text-ink transition-colors hover:bg-canvas"
            aria-label={`替换第 ${index + 1} 张图片`}
          >
            {failed ? "重选" : "替换"}
          </label>
          <button
            type="button"
            onClick={() => onRemove(index)}
            aria-label={`移除第 ${index + 1} 张图片`}
            className="rounded bg-canvas/85 px-2 py-1 text-[10px] text-danger transition-colors hover:bg-canvas"
          >
            移除
          </button>
        </div>

        <input
          id={inputId}
          type="file"
          accept={accept}
          disabled={disabled}
          onChange={handleChange}
          className="sr-only"
        />
      </div>
    );
  }

  return (
    <label
      htmlFor={inputId}
      className="flex aspect-square cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-line bg-panel/40 px-3 text-center transition-colors hover:border-accent-dim focus-within:border-accent focus-within:ring-1 focus-within:ring-accent/40"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        className="h-6 w-6 text-ink-faint"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M12 16V4m0 0L7 9m5-5 5 5" />
        <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
      </svg>
      <span className="text-[11px] text-ink-muted">点击上传图片</span>
      <span className="text-[10px] text-ink-faint">
        第 {index + 1} / {total} 张
      </span>
      <input
        id={inputId}
        type="file"
        accept={accept}
        disabled={disabled}
        onChange={handleChange}
        className="sr-only"
      />
    </label>
  );
}