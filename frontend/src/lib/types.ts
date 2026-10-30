/** 与 Java 后端契约一致的类型定义。字段名与接口返回一一对应，不做多余转换。 */

export type JobStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED";

export type JobStage = "ASSET_CHECK" | "GENERATING" | "FINALIZING";

export interface Template {
  id: string;
  title: string;
  category: string;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface Job {
  id: string;
  templateId: string;
  templateTitle: string;
  templateCoverUrl: string;
  status: JobStatus;
  stage: JobStage | null;
  resultUrl: string | null;
  failureStage: JobStage | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export interface Session {
  userId: string;
  displayName: string;
  createdAt: string;
}

export interface UploadedAsset {
  assetId: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
}

export const STAGE_LABELS: Record<JobStage, string> = {
  ASSET_CHECK: "素材检查",
  GENERATING: "模拟生成",
  FINALIZING: "结果整理",
};

export const STAGE_ORDER: JobStage[] = ["ASSET_CHECK", "GENERATING", "FINALIZING"];

export const ACTIVE_STATUSES: JobStatus[] = ["PENDING", "PROCESSING"];

export function isActiveJob(job: Job): boolean {
  return job.status === "PENDING" || job.status === "PROCESSING";
}