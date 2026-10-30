import type { Template } from "./types";

/**
 * 服务端组件直连 Java 后端（不走 Next 的 rewrite，少一跳）。
 * 浏览器端一律走同源 /api，见 lib/api.ts。
 */
const ORIGIN = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${ORIGIN}${path}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`后端返回 ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function fetchTemplates(category?: string): Promise<Template[]> {
  const query = category ? `?category=${encodeURIComponent(category)}` : "";
  return getJson<Template[]>(`/api/templates${query}`);
}

export async function fetchCategories(): Promise<string[]> {
  return getJson<string[]>("/api/templates/categories");
}

/** 模板不存在时返回 null，由页面决定是 404 还是错误提示。 */
export async function fetchTemplate(id: string): Promise<Template | null> {
  const response = await fetch(
    `${ORIGIN}/api/templates/${encodeURIComponent(id)}`,
    { cache: "no-store" },
  );
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`后端返回 ${response.status}`);
  }
  return (await response.json()) as Template;
}