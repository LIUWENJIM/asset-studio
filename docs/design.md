# 素材制作站 — 设计与实施思路（v0.1 草案）

> 目标：通过小影科技 2027 校招全栈笔试题。核心考察点不是功能数量，而是
> **后端多步骤异步处理是否可靠** + **前端异步状态是否清晰不串扰** + **能否被验证**。
> 所有取舍以「可运行、可解释、可验证」为准。

## 1. 范围与验收对齐

| 需求 | 落点 |
| --- | --- |
| R-01 技术栈 | Next.js(App Router)+React+TS 前端；Java+Spring Boot+MyBatis+MySQL 后端；业务与持久化只在 Java |
| R-02 模板列表 | 模板存 MySQL 种子数据，`GET /api/templates` 读取；分类筛选走服务端参数 |
| R-03 上传校验 | 前端即时校验（数量/类型/大小）+ 服务端二次校验；素材落后端本地目录 |
| R-04 受理异步 | `POST /api/jobs` 只落库 + 投递线程池，立即返回 job；关闭页面不影响 |
| R-05 三阶段 | ASSET_CHECK → GENERATING → FINALIZING，每步落库、有独立执行边界；查询只读不推进 |
| R-06 失败/耗时模拟 | 运行时可调 `failStage` 与各阶段耗时（开发接口 + 配置项），失败即中断 |
| R-07 状态表达 | 上传中/提交中/等待中/处理中/成功/失败 六态区分；查询异常 ≠ 任务失败 |
| R-08 多任务/生命周期 | 全局 JobProvider 轮询，只在有未完成任务时轮询，终态即停，切换用户清除 |
| R-09 刷新恢复 | `/works` 从后端拉历史；刷新后重新接管未完成任务 |
| R-10 身份 | 后端下发 HttpOnly 匿名 uid Cookie，任务按 uid 归属；可一键切换用户演示隔离 |
| R-11 视觉 | 自拟视觉规范（模板页/详情页/作品页），桌面优先、窄屏可用、语义化 + 键盘可达 |
| R-12 CI + 测试 | GitHub Actions 双 job；后端覆盖阶段顺序/失败中断，前端覆盖多任务隔离/查询异常 |
| R-13 README | 按要求逐条写明版本、结构、命令、接口、演示、AI 工作流、限制、部署回滚 |
| R-14 Compose | `docker compose up --build` 起 mysql + backend + frontend，数据经 Flyway 初始化 |

## 2. 技术选型（待确认）

| 层 | 选型 | 版本策略 |
| --- | --- | --- |
| 前端 | Next.js App Router + React + TypeScript | Next 16.4.x / React 19.3.x / TS 5.x（由 create-next-app 决定，避免 TS7 早期风险） |
| 样式 | Tailwind CSS | 4.3.x（CSS-first 配置） |
| 状态 | React Context + 自定义 hook（不引第三方状态库，规模不需要） | — |
| 后端 | Spring Boot + MyBatis | **Boot 3.5.16**（最新 3.5 LTS 线） + mybatis-spring-boot-starter 3.0.5 |
| JDK | Java 21 | 与本机一致 |
| 数据库 | MySQL 8.4 LTS（Compose 镜像） | 驱动 mysql-connector-j 9.x |
| 迁移/种子 | Flyway（同一套可移植 SQL，MySQL/H2 共用） | — |
| 后端测试 | JUnit 5 + Mockito + MockMvc；集成测试用 H2(MySQL 模式) | 不强制 Docker，CI 稳定 |
| 前端测试 | Vitest + React Testing Library + jsdom | — |
| 容器 | Docker Compose（mysql/backend/frontend） | — |

> 关于 Spring Boot 4.1：可用但不选。3.5.16 + MyBatis 3.0.5 是经过大量验证的组合，
> 笔试阶段用「确定能跑」换「用最新」。README 里会写清这个取舍。

## 3. 系统结构

```
browser ──► Next.js :3000 ──rewrite /api/**──► Spring Boot :8080 ──► MySQL :3306
                                                      │
                                                      └── 本地磁盘 data/uploads、data/results
```

- 浏览器只访问同源 `/api/**`、`/files/**`，由 Next.js `rewrites` 反代到 Java。
  好处：Cookie 天然第一方、无 CORS、`SameSite` 简单、容器内用服务名互通。
  Next 只做透传，不含业务逻辑（满足 R-01「业务执行和持久化不能仅由 Next.js 完成」）。
- 本地开发：`next.config.ts` 中 backend 地址取 `BACKEND_ORIGIN`，默认 `http://localhost:8080`；
  容器内为 `http://backend:8080`。

## 4. 数据模型

```sql
app_user(id VARCHAR(36) PK, display_name VARCHAR(64), created_at)

template(id VARCHAR(64) PK, title, category, cover_url, description,
         required_image_count INT, accepted_mime_types VARCHAR(255) /*逗号分隔*/,
         max_file_size_bytes BIGINT, sort_order INT)

asset(id VARCHAR(36) PK, user_id, original_name, stored_path, mime_type,
      size_bytes BIGINT, created_at)

job(id VARCHAR(36) PK, user_id, template_id, template_title /*快照*/, template_cover_url,
    status VARCHAR(16),  -- PENDING | PROCESSING | SUCCEEDED | FAILED
    stage VARCHAR(16),   -- ASSET_CHECK | GENERATING | FINALIZING | NULL
    result_url, failure_stage, failure_reason,
    created_at, updated_at, finished_at)

job_asset(job_id, asset_id, position, PK(job_id, position))
```

- 索引：`job(user_id, created_at)`、`job(status)`、`asset(user_id)`。
- 模板标题/封面在 job 里做快照，避免历史记录因模板变更而漂移（也符合「个人记录」语义）。

## 5. 状态机与执行

```
提交 ──► PENDING ──处理器取到──► PROCESSING(stage=ASSET_CHECK)
                                   │
                                   ▼  延迟 simulate.asset-check-ms
                                PROCESSING(stage=GENERATING)
                                   │
                                   ▼  延迟 simulate.generating-ms
                                PROCESSING(stage=FINALIZING)
                                   │
                                   ▼  延迟 simulate.finalizing-ms
                                SUCCEEDED(result_url)

任一阶段失败 ──► FAILED(failure_stage=当前阶段, failure_reason=说明)，后续阶段不再执行
```

- 执行器：`ThreadPoolTaskExecutor`（核心/最大线程数可配，默认 2 + 队列 100），
  体现「多个任务可并行，也可排队」。提交事务提交后再投递，避免读到未提交数据。
- **状态不回退**：所有状态更新都是条件更新
  `UPDATE job SET ... WHERE id=? AND status IN ('PENDING','PROCESSING')`。
  任务一旦 SUCCEEDED/FAILED，后续任何更新都不会把它改回处理中（R-05）。
- 查询接口纯读库，不做任何推进（R-05 明确要求）。
- 重启：不实现续跑（题面不要求）；启动时把残留 PENDING/PROCESSING 标记为
  `FAILED(reason=SERVICE_RESTART)`，避免永远转圈，README 如实说明。

## 6. 失败与耗时模拟

- 配置：`application.yml` 中 `simulation.stage-delay-ms.{asset-check,generating,finalizing}`、
  `simulation.fail-stage`（none/ASSET_CHECK/GENERATING/FINALIZING）、`simulation.pool-size`。
- 运行时调整（开发接口，演示用）：
  - `GET  /api/dev/simulation` 查看当前设置
  - `POST /api/dev/simulation` 覆盖 `failStage` / 各阶段耗时，内存生效、可重复触发
- 失败判定放在阶段边界：进入阶段前/后各写一次库，失败时写入 `failureStage` 与原因文案。
- 提交请求支持可选 `X-Simulate-Fail-Stage` 头（单任务级覆盖），方便一次性演示，不影响正常流程。

## 7. 接口契约（草案）

统一错误体：`{"code":"TEMPLATE_NOT_FOUND","message":"...","field":"assetIds"}`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/templates?category=` | 模板列表 + 分类聚合 |
| GET | `/api/templates/{id}` | 模板详情（404 有明确 code） |
| POST | `/api/assets` | multipart 上传，返回 `{id,url,mimeType,sizeBytes}`；类型/大小不合法返回 4xx |
| POST | `/api/jobs` | `{templateId, assetIds[]}` → 202 `{job}`；数量/类型/归属校验 |
| GET | `/api/jobs/{id}` | 单任务详情（只能读自己的） |
| GET | `/api/jobs` | 当前用户任务列表，按创建时间倒序 |
| GET | `/api/session` | `{userId, displayName}` |
| POST | `/api/session/reset` | 生成新 uid，演示「切换用户」 |
| POST | `/api/dev/simulation` | 见上 |
| GET | `/files/**` | 素材与结果静态访问（后端本地目录） |

Job DTO 关键字段：`id, templateId, templateTitle, templateCoverUrl, status, stage,
failureStage, failureReason, resultUrl, assetUrls[], createdAt, updatedAt, finishedAt`。

## 8. 前端状态设计

- `SessionProvider`：启动拉 `/api/session`，暴露 `userId + resetSession()`。
- `JobsProvider`：持有 `jobs: Map<id, Job>`，单一轮询器；
  - 仅当存在 `PENDING|PROCESSING` 任务时启动 1.2s 轮询，全部终态立即停止；
  - 每轮只查未完成任务（`GET /api/jobs` 一次拿全量再按 id 合并，实现更简单、请求更少）；
  - 每次请求带 `generation + AbortController`，切换用户/卸载时 abort 并丢弃迟到响应；
  - **合并规则**：终态不可被非终态覆盖；同一 id 用 `updatedAt` 较新者胜。
  - 网络失败：保留上次状态，标记 `syncError`，提供「重试」按钮；**不**把任务改成 FAILED（R-07）。
- 页面：
  - `/` 模板列表：分类胶囊 + 卡片网格 + 加载骨架/空态/错误重试。
  - `/templates/[id]`：信息区（封面色块、描述、素材要求）+ 上传位网格；
    每个槽位独立 `idle/uploading/uploaded/error`，支持预览/替换/移除；提交按钮在校验通过前禁用。
  - `/works`：历史列表（状态徽标、阶段进度、失败原因、结果缩略图），点击可看大图。
  - 顶部：当前用户标识 + 「切换用户」。

## 9. 测试计划（对应 R-12 重点）

后端（不依赖 Docker）：
1. `JobProcessorTest`：断言阶段调用顺序；在 GENERATING 注入失败 → 后续阶段零调用，落库为 FAILED + failureStage。
2. `JobServiceTest`：模板不存在、素材数量/类型/大小不符、引用他人素材 → 全部拒绝。
3. `JobStateGuardTest`：SUCCEEDED 后再次更新被条件更新挡掉（完成后不回退）。
4. `JobApiIntegrationTest`(@SpringBootTest + H2)：提交 → 轮询至 SUCCEEDED；失败注入 → FAILED。

前端（Vitest + RTL）：
1. 两个任务独立更新，互不串扰（A 成功不影响 B 的处理中）。
2. 轮询请求抛错 → 任务仍显示处理中 + 出现重试入口，不显示失败。
3. 全部终态后停止轮询；卸载后清理定时器与在途请求。
4. 上传槽位校验（超数量/类型/大小）与失败反馈。

## 10. 里程碑（预计 14–16h）

| 阶段 | 内容 | 验证 |
| --- | --- | --- |
| M1 (2h) | Maven Wrapper + Boot 骨架 + Flyway 建表/种子 + 模板接口 | `./mvnw test` + curl 列表 |
| M2 (3h) | 上传、任务受理、异步三阶段、失败/耗时模拟、重启清扫 | 集成测试 + 手工多任务 |
| M3 (3h) | 前端列表/详情/上传/提交 | 浏览器走通单任务 |
| M4 (3h) | 轮询、历史、异常、切换用户、视觉打磨 | 手工多任务 + 刷新恢复 |
| M5 (2h) | 前后端测试补齐 | 全部绿灯 |
| M6 (2h) | Dockerfile/Compose、CI、README、全量自测 | `docker compose up --build` |

## 11. 已知风险

1. 本机 Docker 守护进程当前未启动，R-14 的真实验证需要先启动 Docker Desktop。
2. npm registry 较慢（~10s 响应），首次安装依赖耗时较长；要不要换国内镜像待定。
3. Next 16 / React 19 / Tailwind 4 都较新，先用脚手架验证组合可用，再写业务。
4. 不做：真实 AI、SSE/WS、对象存储、任务幂等/断点续跑、移动端单独设计。