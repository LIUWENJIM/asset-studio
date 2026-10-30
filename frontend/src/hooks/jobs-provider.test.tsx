import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { JobsProvider, useJobs } from "@/lib/jobs-provider";
import type { Job } from "@/lib/types";

function job(overrides: Partial<Job>): Job {
  return {
    id: "job-1",
    templateId: "film-portrait",
    templateTitle: "胶片人像",
    templateCoverUrl: "/media/covers/film-portrait.svg",
    status: "PROCESSING",
    stage: "GENERATING",
    resultUrl: null,
    failureStage: null,
    failureReason: null,
    createdAt: "2026-01-01T10:00:00",
    updatedAt: "2026-01-01T10:00:00",
    finishedAt: null,
    ...overrides,
  };
}

function Probe() {
  const { jobs, error, loading, refresh } = useJobs();
  return (
    <div>
      <p data-testid="state">{loading ? "loading" : "ready"}</p>
      <p data-testid="error">{error ?? "no-error"}</p>
      <ul>
        {jobs.map((item) => (
          <li key={item.id} data-testid={`job-${item.id}`}>
            {item.templateTitle}|{item.status}|{item.failureReason ?? "-"}
          </li>
        ))}
      </ul>
      <button type="button" onClick={refresh}>
        手动刷新
      </button>
    </div>
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

describe("JobsProvider", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("多个任务各自保留自己的状态与失败原因", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse([
        job({ id: "a", templateTitle: "旅行明信片", status: "PROCESSING", stage: "ASSET_CHECK" }),
        job({
          id: "b",
          templateTitle: "活动海报",
          status: "FAILED",
          stage: "GENERATING",
          failureStage: "GENERATING",
          failureReason: "模拟失败：生成服务返回错误",
        }),
        job({ id: "c", templateTitle: "社交封面", status: "SUCCEEDED", stage: null, resultUrl: "/files/r.png" }),
      ]),
    );

    render(
      <JobsProvider>
        <Probe />
      </JobsProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("ready");
    });

    expect(screen.getByTestId("job-a")).toHaveTextContent("旅行明信片|PROCESSING|-");
    expect(screen.getByTestId("job-b")).toHaveTextContent(
      "活动海报|FAILED|模拟失败：生成服务返回错误",
    );
    expect(screen.getByTestId("job-c")).toHaveTextContent("社交封面|SUCCEEDED|-");
    // 有任务失败不等于请求出错
    expect(screen.getByTestId("error")).toHaveTextContent("no-error");
  });

  it("查询异常只提示出错，不把已有任务改成失败", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse([job({ id: "a", status: "PROCESSING", stage: "GENERATING" })]),
    );

    render(
      <JobsProvider>
        <Probe />
      </JobsProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("job-a")).toHaveTextContent("PROCESSING");
    });

    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "手动刷新" }));

    await waitFor(() => {
      expect(screen.getByTestId("error")).toHaveTextContent("网络异常，未能获取最新状态");
    });

    // 关键断言：任务状态维持原样，没有被误判为失败
    expect(screen.getByTestId("job-a")).toHaveTextContent("胶片人像|PROCESSING|-");
  });

  it("服务端返回的业务错误按后端文案展示", async () => {
    fetchMock.mockRejectedValueOnce(new Error("boom"));

    render(
      <JobsProvider>
        <Probe />
      </JobsProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("error")).toHaveTextContent("网络异常");
    });
    expect(screen.queryByTestId("job-a")).toBeNull();
  });

  it("全部任务结束后不再继续轮询", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse([job({ id: "a", status: "SUCCEEDED", stage: null, resultUrl: "/files/r.png" })]),
    );

    render(
      <JobsProvider>
        <Probe />
      </JobsProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("job-a")).toHaveTextContent("SUCCEEDED");
    });

    const callsAfterFirstLoad = fetchMock.mock.calls.length;
    expect(callsAfterFirstLoad).toBe(1);

    // 等过基础轮询间隔，确认没有新的请求发出
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1300));
    });

    expect(fetchMock.mock.calls.length).toBe(callsAfterFirstLoad);
  });
});