import { describe, expect, it } from "vitest";
import {
  collectAssetIds,
  createEmptySlots,
  emptySlot,
  isSlotReady,
  validateFile,
  type UploadSlotState,
} from "@/lib/upload-slot";
import { formatMegabytes, formatMimeTypes, toAcceptAttribute } from "@/lib/format";

function fileOf(name: string, type: string, size: number): File {
  return new File([new Uint8Array(size)], name, { type });
}

describe("validateFile", () => {
  const accepted = ["image/jpeg", "image/png"];
  const max = 5 * 1024 * 1024;

  it("接受模板允许的类型与大小", () => {
    expect(validateFile(fileOf("a.png", "image/png", 1024), accepted, max)).toBeNull();
  });

  it("拒绝模板不接受的文件类型", () => {
    const message = validateFile(fileOf("a.gif", "image/gif", 1024), accepted, max);
    expect(message).toContain("JPG / PNG");
  });

  it("拒绝超过上限的文件", () => {
    const message = validateFile(fileOf("a.png", "image/png", max + 1), accepted, max);
    expect(message).toContain("5 MB");
  });

  it("拒绝空文件", () => {
    expect(validateFile(fileOf("a.png", "image/png", 0), accepted, max)).toContain("空的");
  });
});

describe("slots", () => {
  it("按模板要求生成对应数量的空位", () => {
    const slots = createEmptySlots(3);
    expect(slots).toHaveLength(3);
    expect(slots.every((slot) => slot.status === "empty")).toBe(true);
    // 每个槽位必须是独立对象，修改一个不能影响其他
    expect(slots[0]).not.toBe(slots[1]);
  });

  it("只有拿到 assetId 才算就绪", () => {
    const uploading: UploadSlotState = {
      ...emptySlot(),
      status: "uploading",
      assetId: null,
    };
    const ready: UploadSlotState = {
      ...emptySlot(),
      status: "ready",
      assetId: "asset-1",
    };
    expect(isSlotReady(uploading)).toBe(false);
    expect(isSlotReady(ready)).toBe(true);
  });

  it("任一位未就绪就不给出提交用的素材列表", () => {
    const ready: UploadSlotState = { ...emptySlot(), status: "ready", assetId: "a" };
    const failed: UploadSlotState = {
      ...emptySlot(),
      status: "error",
      error: "上传失败",
    };
    expect(collectAssetIds([ready, failed])).toBeNull();
    expect(collectAssetIds([ready])).toEqual(["a"]);
  });
});

describe("format helpers", () => {
  it("把字节上限格式化成 MB", () => {
    expect(formatMegabytes(5 * 1024 * 1024)).toBe("5 MB");
    expect(formatMegabytes(1.5 * 1024 * 1024)).toBe("1.5 MB");
  });

  it("把 mime 类型转成人类可读的名字", () => {
    expect(formatMimeTypes(["image/jpeg", "image/png"])).toBe("JPG / PNG");
  });

  it("生成 file input 的 accept 属性", () => {
    expect(toAcceptAttribute(["image/png"])).toBe("image/png");
  });
});