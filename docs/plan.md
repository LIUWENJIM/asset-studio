# 开发计划

> 配套文档：[架构与接口契约](design.md)。本文件回答「有哪些模块、每个模块干什么、按什么顺序做、怎么验收」。

## 0. 一句话需求

做一个可独立运行的素材制作站：用户浏览后端下发的模板、上传图片并提交制作任务，**Java 后端把「素材检查 → 模拟生成 → 结果整理」三步异步执行并持久化到 MySQL**，前端在后台处理期间可以继续浏览和连续建任务，刷新后还能从服务端恢复个人历史记录与制作结果。

拆成三个能力面：

1. **后端异步编排**（真正的考点）：受理即返回、步骤独立可观察、失败即中断、完成态不回退。
2. **前端异步状态**（真正的考点）：六种状态不混、多任务互不串扰、查询异常不等于任务失败、离开/切号清理干净。
3. **可交付性**：CI 能跑、测试能证明上面两条、`docker compose up --build` 一次起齐。

## 1. 最终目录结构

```
asset-studio/
├─ backend/                              Spring Boot 3.5.16 + MyBatis + MySQL
│  ├─ mvnw, mvnw.cmd, .mvn/              Maven Wrapper（本机无 Maven 也能构建）
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/dev/liuwenjie/assetstudio/
│     │  ├─ AssetStudioApplication.java
│     │  ├─ common/        ApiError / ApiException / ErrorCode / GlobalExceptionHandler / HealthController
│     │  ├─ config/        StorageProperties / WebMvcConfig(静态资源) / AsyncConfig(线程池)
│     │  ├─ identity/      UserIdentityFilter / CurrentUserId / UserMapper / SessionController
│     │  ├─ template/      Template / TemplateMapper / TemplateService / TemplateController
│     │  ├─ asset/         Asset / AssetMapper / LocalFileStorage / AssetService / AssetController
│     │  ├─ job/           Job / JobStatus / JobStage / JobMapper / JobService / JobProcessor
│     │  │                 / ResultComposer / JobController
│     │  └─ simulation/    SimulationSettings / SimulationController
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  ├─ db/migration/  V1__init_schema.sql, V2__seed_templates.sql
│     │  └─ static/media/covers/  模板封面（自绘 SVG，无版权问题）
│     └─ test/java/...     单元测试 + H2 集成测试
├─ frontend/                             Next.js 16 App Router + React 19 + TS 5.9
│  ├─ package.json, pnpm-lock.yaml, tsconfig.json, next.config.ts
│  ├─ postcss.config.mjs, eslint.config.mjs, vitest.config.ts
│  └─ src/
│     ├─ app/            layout.tsx / page.tsx(模板列表) / templates/[id]/page.tsx / works/page.tsx
│     ├─ components/     TemplateCard / CategoryFilter / UploadSlot / JobCard / JobStatusBadge
│     │                  / StageTimeline / AccountDialog / EmptyState / ErrorState / Skeleton
│     ├─ providers/      SessionProvider.tsx / JobsProvider.tsx
│     ├─ lib/            api.ts / types.ts / validation.ts / format.ts
│     └─ __tests__/      jobs-isolation / query-error / polling-lifecycle / upload-validation
├─ docker/                backend.Dockerfile / frontend.Dockerfile
├─ docker-compose.yml     mysql + backend + frontend
├─ .github/workflows/ci.yml
├─ docs/                  design.md（架构与契约）/ plan.md（本文件）
├─ data/                  运行时上传与结果（git 忽略，运行时自动创建）
└─ README.md
```

## 2. 后端模块

| 编号 | 模块 | 作用 | 关键产出 | 对应需求 |
| --- | --- | --- | --- | --- |
| B0 | 工程基建 | 让项目能构建、能起、错误有统一形状 | pom + wrapper + application.yml + 统一错误体 + `/api/health` | R-01 |
| B1 | 身份 | 无登录地认人，任务归属 | 首次请求下发 HttpOnly uid Cookie；`@CurrentUserId` 参数解析器；`/api/session`、`/api/session/switch` | R-10 |
| B2 | 模板 | 模板只读数据与分类筛选 | V2 种子 + `GET /api/templates`、`/api/templates/{id}` + 封面静态资源 | R-02、R-03 |
| B3 | 素材 | 收图、校验、落盘、可访问 | `POST /api/assets`（类型/大小/落盘），`/files/**` 静态映射 | R-03 |
| B4 | 任务受理 | 校验 + 建任务 + 立刻返回 | `POST /api/jobs` 202；校验模板存在、素材数量/类型/大小、素材归属 | R-04、R-03 |
| B5 | 异步执行 | 三阶段推进，失败即停 | `JobProcessor`：ASSET_CHECK → GENERATING → FINALIZING；条件更新防回退；`ResultComposer` 用 Java2D 拼结果图 | R-04、R-05、R-06 |
| B6 | 查询与历史 | 只读查询、重启清扫 | `GET /api/jobs/{id}`、`GET /api/jobs`；启动时把残留 PENDING/PROCESSING 标记为 FAILED(SERVICE_RESTART) | R-05、R-09 |
| B7 | 模拟控制 | 可重复触发失败与调耗时 | `SimulationSettings` + `GET/POST /api/dev/simulation` | R-06 |
| B8 | 后端测试 | 证明步骤顺序与失败中断 | `JobProcessorTest`、`JobServiceTest`、`JobStateGuardTest`、`JobApiIntegrationTest` | R-12 |

## 3. 前端模块

| 编号 | 模块 | 作用 | 关键产出 | 对应需求 |
| --- | --- | --- | --- | --- |
| F0 | 工程基建 | 能起、能 lint、能测 | 手写脚手架（不用 create-next-app，避开沙箱与联网字体）+ 设计令牌 | R-01 |
| F1 | API 层 | 统一请求/错误/取消 | `lib/api.ts`：超时、AbortSignal、错误归一化为 `{code,message}` | R-07 |
| F2 | 模板列表 | 分类筛选与卡片 | 服务端取数、分类胶囊、加载骨架/空态/错误重试 | R-02、R-11 |
| F3 | 详情与上传 | 上传位状态机与提交前校验 | 每槽 idle/uploading/uploaded/error；预览/替换/移除；数量/类型/大小校验；提交中禁用 | R-03、R-07 |
| F4 | 任务跟踪 | 轮询生命周期与多任务隔离 | `JobsProvider`：单轮询器、终态即停、失败退避、切号清理、终态不回退合并 | R-07、R-08 |
| F5 | 我的作品 | 历史与结果 | 列表（时间/状态/阶段/失败原因/结果预览） | R-09、R-11 |
| F6 | 身份与账户 | 切号与个人记录 | 顶部用户区 + Account 弹窗（照 R-10 截图）+ 切换后清空并重拉 | R-10 |
| F7 | 视觉与可用性 | 深色+橄榄绿、键盘可达 | 焦点环、aria 标签、窄屏可用 | R-11 |
| F8 | 前端测试 | 证明串扰与异常区分 | 4 组 Vitest + RTL 用例 | R-12 |

## 4. 工程模块

| 编号 | 模块 | 作用 | 对应需求 |
| --- | --- | --- | --- |
| E1 | 容器化 | 一条命令起全栈 | R-14 |
| E2 | CI | push/PR 自动 lint+typecheck+build+test | R-12 |
| E3 | 文档与交付 | README 覆盖 R-13 全部条目 | R-13 |

## 5. 实施顺序与提交策略

每个模块一次提交，提交信息用 `feat(backend): ...` / `feat(frontend): ...` / `chore(ci): ...`，保证历史可读。

| 步骤 | 内容 | 验收方式 | 预计 |
| --- | --- | --- | --- |
| 1 | B0 基建 | `./mvnw -q -DskipTests package` 通过，`/api/health` 返回 ok | 1.5h |
| 2 | B1+B2 身份与模板 | curl 拿到模板列表与分类，Cookie 下发 | 2h |
| 3 | B3+B4 素材与受理 | 上传成功/超限/类型错三种响应正确 | 2h |
| 4 | B5+B6+B7 异步与模拟 | 多任务并行、指定阶段失败、耗时可调 | 3h |
| 5 | B8 后端测试 | `./mvnw test` 全绿 | 2h |
| 6 | F0+F1 前端基建 | `pnpm lint/typecheck/build` 全绿 | 1.5h |
| 7 | F2+F3 列表与制作页 | 浏览器走通「选模板→上传→提交」 | 3h |
| 8 | F4+F5+F6 跟踪与历史 | 多任务互不串扰、刷新恢复、切号隔离 | 3h |
| 9 | F7 视觉打磨 | 对照截图气质；键盘可操作 | 2h |
| 10 | F8 前端测试 | `pnpm test` 全绿 | 2h |
| 11 | E1 容器 | `docker compose up --build` 后浏览器全流程可用 | 2h |
| 12 | E2+E3 CI 与文档 | Actions 绿灯；README 逐条自检 | 2h |

合计约 **24h**（含调试余量），按每天 8-10h 计约 3 天，与剩余时间匹配；若紧张，优先砍 F7 打磨与 B7 的扩展项，不砍 B8/F8 测试。

## 6. 关键决策（已确认）

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 身份 | 匿名 uid Cookie，不引身份服务 | 题面允许自选；最少的代码讲清楚「个人记录」归属，且天然支持切号演示 |
| 状态更新 | 轮询 | 无代理缓冲问题；异步状态管理的考点在生命周期与串扰，不在传输协议 |
| 轮询间隔 | 1s 基准 | 见下节 |
| 前端取数 | Next rewrites 同源透传 | 无 CORS、Cookie 第一方、容器内服务名直连 |
| 结果图 | Java2D 拼贴 | 不引图像库，真出图，失败路径真实 |
| 数据库迁移 | Flyway | 建表与种子可复现，MySQL/H2 共用 SQL |

### 轮询间隔的取值逻辑

- **1s 基准**：默认阶段耗时定为 1200/2500/900ms，任一阶段都至少被采样一次，用户能看到阶段跳变而不是「直接成功」。
- **失败退避**：连续失败 1s → 2s → 4s → 8s 封顶，成功一次即重置。目的是「查询异常时不打爆后端，也不假装任务失败」。
- **不可见即停**：`document.hidden` 时暂停，页面重新可见立刻补拉一次。
- **无任务即停**：没有未完成任务时定时器完全停止（0 请求）。
- **单请求在途**：上一轮未返回则跳过本轮，避免慢响应堆积。

## 7. 风险与预案

| 风险 | 预案 |
| --- | --- |
| 本机 Docker 未启动 | 中期先做本地进程验证，最后统一跑 compose；起不来则退化为「源码 + 单测 + 构建」并如实写进 README |
| 依赖下载慢（npm 10s 延迟） | 依赖装在 `.tools/` 缓存目录；必要时切国内镜像但保持 lockfile 可用 |
| Next 16 / React 19 / Tailwind 4 较新 | 先跑通 lint+typecheck+build 再写业务，问题前置暴露 |
| 时间不足 | 砍视觉打磨，保测试与文档；README 如实写未完成项 |

## 8. 实际投入记录

（随开发过程补充，最终同步进 README）