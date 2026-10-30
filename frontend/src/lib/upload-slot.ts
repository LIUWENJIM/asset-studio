import { formatMegabytes, formatMimeTypes } from "./format";

export type SlotStatus = "empty" | "uploading" | "ready" | "error";

export interface UploadSlotState {
  file: File | null;
  /** 浏览器本地预览地址（object URL），与服务端素材地址无关 */
  previewUrl: string | null;
  /** 上传成功后拿到的服务端素材 id，提交任务时用它 */
  assetId: string | null;
  status: SlotStatus;
  error: string | null;
}

export function emptySlot(): UploadSlotState {
  return {
    file: null,
    previewUrl: null,
    assetId: null,
    status: "empty",
    error: null,
  };
}

export function createEmptySlots(count: number): UploadSlotState[] {
  return Array.from({ length: Math.max(0, count) }, emptySlot);
}

/** 客户端预校验：与后端规则一致，提前反馈，最终仍以服务端校验为准。 */
export function validateFile(
  file: File,
  acceptedMimeTypes: string[],
  maxFileSizeBytes: number,
): string | null {
  if (file.size === 0) {
    return "这个文件是空的，换一张试试";
  }
  if (acceptedMimeTypes.length > 0 && !acceptedMimeTypes.includes(file.type)) {
    return `只支持 ${formatMimeTypes(acceptedMimeTypes)} 格式`;
  }
  if (file.size > maxFileSizeBytes) {
    return `图片不能超过 ${formatMegabytes(maxFileSizeBytes)}`;
  }
  return null;
}

export function isSlotReady(slot: UploadSlotState): boolean {
  return slot.status === "ready" && slot.assetId !== null;
}

export function collectAssetIds(slots: UploadSlotState[]): string[] | null {
  if (!slots.every(isSlotReady)) {
    return null;
  }
  return slots.map((slot) => slot.assetId as string);
}