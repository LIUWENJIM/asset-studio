import type {
  Job,
  Session,
  Template,
  UploadedAsset,
} from "./types";

/** 后端统一错误体：{ code, message, field }。 */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly field?: string;

  constructor(message: string, status: number, code?: string, field?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.field = field;
  }
}

/** 把任意异常转成能直接显示给用户的一句话。查询异常不能被当成任务失败。 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof DOMException && error.name === "AbortError") {
    return "请求已取消";
  }
  return "网络异常，未能获取最新状态";
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, { ...init, headers, cache: "no-store" });

  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    let code: string | undefined;
    let field: string | undefined;
    try {
      const body: unknown = await response.json();
      if (body && typeof body === "object") {
        const record = body as Record<string, unknown>;
        if (typeof record.message === "string" && record.message) {
          message = record.message;
        }
        if (typeof record.code === "string") {
          code = record.code;
        }
        if (typeof record.field === "string") {
          field = record.field;
        }
      }
    } catch {
      // 响应不是 JSON 时保留默认文案
    }
    throw new ApiError(message, response.status, code, field);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function getSession(signal?: AbortSignal): Promise<Session> {
  return request<Session>("/api/session", { signal });
}

export function switchUser(): Promise<Session> {
  return request<Session>("/api/session/switch", { method: "POST" });
}

export function listJobs(signal?: AbortSignal): Promise<Job[]> {
  return request<Job[]>("/api/jobs", { signal });
}

export function getJob(id: string, signal?: AbortSignal): Promise<Job> {
  return request<Job>(`/api/jobs/${encodeURIComponent(id)}`, { signal });
}

export function submitJob(
  templateId: string,
  assetIds: string[],
): Promise<Job> {
  return request<Job>("/api/jobs", {
    method: "POST",
    body: JSON.stringify({ templateId, assetIds }),
  });
}

export function uploadAsset(
  file: File,
  signal?: AbortSignal,
): Promise<UploadedAsset> {
  const body = new FormData();
  body.append("file", file);
  // 交给浏览器自己带 multipart boundary，不能手写 Content-Type
  return request<UploadedAsset>("/api/assets", {
    method: "POST",
    body,
    signal,
  });
}

export interface SimulationState {
  failStage: string;
  assetCheckMs: number;
  generatingMs: number;
  finalizingMs: number;
}

export function getSimulation(): Promise<SimulationState> {
  return request<SimulationState>("/api/dev/simulation");
}

export function updateSimulation(
  patch: Partial<SimulationState>,
): Promise<SimulationState> {
  return request<SimulationState>("/api/dev/simulation", {
    method: "PUT",
    body: JSON.stringify(patch),
  });
}

export type { Job, Session, Template, UploadedAsset };