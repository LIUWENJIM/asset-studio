# Asset Studio · 素材工坊

> **一句话需求**：做一个可以独立运行的素材制作站 —— 用户浏览模板、上传图片并提交制作任务，Java 后端在后台按「素材检查 → 模拟生成 → 结果整理」三步异步跑完，期间用户可以继续使用网站，最后在历史记录里看到自己的结果或失败原因。

题库编号对应关系：模板列表 `R-02`、详情与上传 `R-03`、受理与异步 `R-04`、多步骤状态 `R-05`、失败模拟 `R-06`、前端状态 `R-07`、多任务与轮询生命周期 `R-08`、刷新恢复 `R-09`、身份 `R-10`、视觉与可用性 `R-11`、CI 与测试 `R-12`、交付说明 `R-13`、容器 `R-14`。

---

## 1. 技术栈与版本

| 层 | 选型 | 版本 |
| --- | --- | --- |
| 前端框架 | Next.js（App Router） | **16.4.3** |
| UI 运行时 | React / React DOM | **19.3.1** |
| 语言 | TypeScript | **5.9.3** |
| 样式 | Tailwind CSS（`@theme` 定义设计令牌） | **4.3.3** |
| 前端 Lint | ESLint + `eslint-config-next`（flat config） | **9.39.5 / 16.4.3** |
| 前端测试 | Vitest + Testing Library + jsdom | **5.0.5 / 16.3.3 / 30.1.3** |
| 包管理器 | pnpm | **11.1.2** |
| 后端框架 | Spring Boot | **3.5.16** |
| 语言 / 运行时 | Java（Temurin JDK / JRE） | **21** |
| 持久层 | MyBatis（`mybatis-spring-boot-starter`） | **3.0.5** |
| 数据库 | MySQL | **8.4**（镜像 `mysql:8.4`） |
| 迁移 | Flyway（`flyway-core` + `flyway-mysql`） | 随 Boot 3.5.16 BOM |
| 后端测试 | JUnit 5 + Spring Boot Test + H2（MySQL 兼容模式） | 随 Boot BOM |
| 构建 | Maven（含 `mvnw` wrapper） | 3.9.x |

> 版本取舍：Spring Boot 选择 3.5.16 而不是 4.x，是因为 `mybatis-spring-boot-starter 3.0.5` 与 Boot 3.5 的组合经过大量验证，笔试题阶段优先保证「开箱能跑」。前端选 Next 16 + React 19，App Router 与 Server Component 是默认形态。

**职责边界**：Next.js 只做页面渲染与交互，以及一层薄薄的同源转发（`rewrites`）；模板、素材、任务、个人记录的**业务规则与持久化全部在 Java 侧**，MySQL 由 Java 独占写入。

---

## 2. 快速启动

### 2.1 推荐：Docker Compose 一条命令

前置：Docker 24+（含 `docker compose` 插件），端口 3000 / 8080 可用。

```bash
docker compose up --build
```

- 前端：<http://localhost:3000>
- 后端：<http://localhost:8080>（探活 `GET /api/health` → `{"status":"ok"}`）
- MySQL 只在内网暴露，不映射到宿主机，避免和你本地的 3306 冲突

首次启动会依次：拉取镜像 → 构建 backend（Maven 打包）→ 构建 frontend（pnpm build）→ 启动 MySQL 并等待健康 → 启动后端（Flyway 自动建表 + 灌种子）→ 启动前端。

停止与清理：

```bash
docker compose down          # 停止并删除容器，保留数据卷
docker compose down -v       # 连数据卷一起删除（数据库与已上传素材一起清空）
```

端口被占用时改 `.env`（从 `.env.example` 复制）：

```bash
cp .env.example .env
# 修改 APP_PORT / BACKEND_PORT 后重新 docker compose up
```

### 2.2 本地开发（不使用容器）

需要 JDK 21、Maven 3.9+、Node 22+、pnpm 11、一个可连的 MySQL 8。

```bash
# 1) 准备数据库（库名可自定，Flyway 会自动建表灌种子）
mysql -uroot -p -e "CREATE DATABASE asset_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p -e "CREATE USER 'studio'@'%' IDENTIFIED BY 'studio'; GRANT ALL ON asset_studio.* TO 'studio'@'%';"

# 2) 启动后端（默认连 localhost:3306/asset_studio）
cd backend
DB_URL='jdbc:mysql://localhost:3306/asset_studio?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8' \
DB_USER=studio DB_PASSWORD=studio \
./mvnw spring-boot:run

# 3) 启动前端（另开一个终端）
cd frontend
pnpm install
pnpm dev            # http://localhost:3000
```

前端通过同源 `/api/**`、`/files/**`、`/media/**` 请求，由 `frontend/next.config.ts` 的 `rewrites` 转发到 `BACKEND_ORIGIN`（默认 `http://localhost:8080`）。这样浏览器侧始终是第一方请求，Cookie 不需要处理跨站，也免去 CORS。

> 注意：`BACKEND_ORIGIN` 在 `next build` 时会被固化进产物（rewrites 在构建期解析）。容器里由 `frontend/Dockerfile` 的构建参数传入 `http://backend:8080`；本地 `pnpm dev` 用默认值即可。

### 2.3 构建与测试命令

```bash
# 后端
cd backend
./mvnw test                       # 全部测试（H2 内存库，无需 MySQL）
./mvnw -DskipTests package        # 产出 target/asset-studio-backend-0.1.0.jar
java -jar target/asset-studio-backend-0.1.0.jar

# 前端
cd frontend
pnpm lint                         # ESLint
pnpm typecheck                    # tsc --noEmit
pnpm test                         # Vitest
pnpm build                        # next build（standalone 产物）
pnpm start                        # 起生产构建
```

Windows 下用 `mvnw.cmd`。

---

## 3. 目录结构

```
.
├── docker-compose.yml            # 前端 + 后端 + MySQL 一键启动
├── .env.example                  # 端口与模拟器参数示例（不含真实凭据）
├── .github/workflows/ci.yml      # CI：后端 test+package、前端 lint/typecheck/test/build
├── docs/
│   ├── design.md                 # 设计草案（数据模型、状态机、接口契约）
│   └── plan.md                   # 开发计划与模块拆解
├── backend/
│   ├── Dockerfile / .dockerignore
│   ├── pom.xml, mvnw, mvnw.cmd
│   └── src/
│       ├── main/java/dev/liuwenjie/assetstudio/
│       │   ├── AssetStudioApplication.java
│       │   ├── common/           # 统一错误码、异常、全局异常处理、健康检查
│       │   ├── config/           # WebMvc 配置（参数解析器、静态资源映射）
│       │   ├── identity/         # 匿名 uid Cookie、用户落库、会话接口
│       │   ├── template/         # 模板查询
│       │   ├── asset/            # 素材上传与归属校验
│       │   ├── job/              # 任务受理、状态机、异步流水线、启动补偿
│       │   ├── simulation/       # 可调耗时与指定阶段失败的运行时配置
│       │   └── result/           # Java2D 合成结果图
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/migration/     # Flyway：V1 建表、V2 种子模板、V3 任务快照列
│       │   └── static/media/covers/  # 6 张自绘 SVG 模板封面
│       └── test/                 # BackendSmokeTest + JobFlowTest
└── frontend/
    ├── Dockerfile / .dockerignore
    ├── package.json / pnpm-lock.yaml / pnpm-workspace.yaml
    ├── next.config.ts            # output: standalone + rewrites
    ├── eslint.config.mjs         # flat config（直接展开 eslint-config-next）
    ├── vitest.config.mts / vitest.setup.ts
    └── src/
        ├── app/                  # layout、模板列表页、制作页、404、globals.css
        ├── components/           # 卡片、上传位、阶段进度条、历史面板…
        ├── hooks/                # 轮询 Provider 的测试
        └── lib/                  # 类型、API 客户端、服务端取数、格式化、上传状态
```

---

## 4. 配置项

### 4.1 后端环境变量（`backend/src/main/resources/application.yml`）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/asset_studio?...` | JDBC 连接串 |
| `DB_USER` / `DB_PASSWORD` | `studio` / `studio` | 数据库账号（示例值，非真实凭据） |
| `STORAGE_ROOT` | `./data` | 素材与结果图的本地存储根目录（容器内 `/app/data`，挂在命名卷上） |
| `SIM_ASSET_CHECK_MS` | `1200` | 「素材检查」阶段基础耗时 |
| `SIM_GENERATING_MS` | `2500` | 「模拟生成」阶段基础耗时 |
| `SIM_FINALIZING_MS` | `900` | 「结果整理」阶段基础耗时 |
| `SIM_FAIL_STAGE` | `none` | 启动时默认失败阶段：`none` / `ASSET_CHECK` / `GENERATING` / `FINALIZING` |
| `SIM_POOL_SIZE` | `2` | 后台执行线程数（排队/并行演示用） |
| `SIM_DEV_API_ENABLED` | `true` | 是否暴露 `/api/dev/simulation` 调试接口 |

Multipart 限制：单文件 10MB、单请求 20MB（模板自身的上限由 `template.max_file_size_bytes` 收得更紧）。

### 4.2 前端环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BACKEND_ORIGIN` | `http://localhost:8080` | 服务端组件直连后端的地址；同时作为 rewrites 目标（构建期固化） |

### 4.3 构建期软件源（可选）

`docker compose build` 默认使用 npm 官方源与 Maven Central。若访问这两个源过慢，可在 `.env` 里覆盖，**无需修改 Dockerfile**：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `NPM_REGISTRY` | `https://registry.npmjs.org` | 前端镜像构建时 npm 使用的 registry |
| `MAVEN_MIRROR` | 空（即 Maven Central） | 后端镜像构建时 Maven 使用的镜像站 |

国内网络的例子：

```dotenv
NPM_REGISTRY=https://registry.npmmirror.com
MAVEN_MIRROR=https://maven.aliyun.com/repository/public
```

### 4.4 数据库初始化与种子数据

- 建表与种子全部由 **Flyway** 在应用启动时执行，无需手工导入 SQL：
  - `V1__init_schema.sql`：`app_user`、`template`、`asset`、`job`、`job_asset` 五张表与索引
  - `V2__seed_templates.sql`：6 个模板
  - `V3__job_simulation_snapshot.sql`：任务表增加 `sim_fail_stage`（受理时的失败阶段快照）
- 数据库里已有记录时 Flyway 只补未执行的版本，重启不会重复灌种子。

种子模板（4 个分类，所需素材 1~3 张不等）：

| id | 标题 | 分类 | 需要图片数 | 接受格式 | 单张上限 |
| --- | --- | --- | --- | --- | --- |
| `travel-postcard` | 旅行明信片 | 创意图片 | 2 | JPG / PNG | 5 MB |
| `film-portrait` | 胶片人像 | 创意图片 | 1 | JPG / PNG | 8 MB |
| `poster-bold` | 活动海报 | 品牌海报 | 1 | JPG / PNG | 8 MB |
| `social-cover` | 社交封面 | 社交封面 | 1 | JPG / PNG | 5 MB |
| `product-card` | 商品卡片 | 电商素材 | 3 | JPG / PNG | 5 MB |
| `avatar-frame` | 头像边框 | 社交封面 | 1 | JPG / PNG / WebP | 3 MB |

### 4.5 素材来源

- **模板封面**：`backend/src/main/resources/static/media/covers/*.svg`，6 张全部为本项目手写的矢量图（纯 SVG 几何与渐变），不含任何第三方素材或品牌元素，无版权风险。
- **用户上传的图片**：不提供预置素材，演示时用任意本地图片即可（JPG/PNG/WebP，每张 ≤ 各模板上限）。
- **生成结果图**：由后端 `ResultImageRenderer` 用 Java2D 把用户上传的图片拼成 900×1200 的结果卡片，落盘到 `{STORAGE_ROOT}/results/{userId}/{jobId}.png`，不依赖任何外部 AI 服务或图库。

---

## 5. 主要接口

所有接口都在 Java 侧，统一前缀 `/api`。请求错误统一返回：

```json
{ "code": "ASSET_COUNT_MISMATCH", "message": "该模板需要 2 张图片，当前提交 1 张", "field": "assetIds" }
```

`code` 取值：`INVALID_REQUEST` `TEMPLATE_NOT_FOUND` `ASSET_NOT_FOUND` `ASSET_NOT_OWNED` `ASSET_COUNT_MISMATCH` `ASSET_TYPE_NOT_SUPPORTED` `ASSET_TOO_LARGE` `ASSET_EMPTY` `JOB_NOT_FOUND` `UPLOAD_FAILED` `INTERNAL_ERROR`。

### 5.1 会话与身份

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/session` | 读取当前身份 |
| `POST` | `/api/session/switch` | 换一个新身份（演示「不同用户互不可见」） |

```json
// GET /api/session
{ "userId": "8f14e45f-cec4-4f3a-9f0d-6b8f0d2f3a11", "displayName": "创作者 8F14", "createdAt": "2026-02-11T10:20:31" }
```

### 5.2 模板

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/templates?category=` | 模板列表，`category` 省略即全部 |
| `GET` | `/api/templates/categories` | 分类列表（`string[]`） |
| `GET` | `/api/templates/{id}` | 模板详情，不存在返回 404 `TEMPLATE_NOT_FOUND` |

```json
{
  "id": "travel-postcard",
  "title": "旅行明信片",
  "category": "创意图片",
  "coverUrl": "/media/covers/travel-postcard.svg",
  "description": "上传两张旅途照片，拼成一张带边框与地名的明信片。适合风景与合影各一张。",
  "requiredImageCount": 2,
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "maxFileSizeBytes": 5242880
}
```

### 5.3 素材上传

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/assets` | `multipart/form-data`，字段名 `file` |
| `GET` | `/files/{storedPath}` | 读取已上传素材（静态资源，映射到 `STORAGE_ROOT`） |

```json
// 响应
{ "assetId": "d2b1…", "url": "/files/assets/8f14…/d2b1….png", "mimeType": "image/png", "sizeBytes": 184320 }
```

服务端会重新校验 MIME 与大小，并且记录 `user_id`；提交任务时再校验一次归属。

### 5.4 任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/jobs` | 提交制作任务，**校验通过后立即返回 201**，不等制作完成 |
| `GET` | `/api/jobs` | 当前用户全部任务（按创建时间倒序），前端轮询只调这一个 |
| `GET` | `/api/jobs/{id}` | 单个任务，仅用于深链接；不属于当前用户返回 404 |

```json
// POST /api/jobs 请求
{ "templateId": "travel-postcard", "assetIds": ["d2b1…", "a7c9…"] }

// 任务视图（POST 返回体与查询接口一致）
{
  "id": "4c1f…",
  "templateId": "travel-postcard",
  "templateTitle": "旅行明信片",
  "templateCoverUrl": "/media/covers/travel-postcard.svg",
  "status": "PROCESSING",
  "stage": "GENERATING",
  "resultUrl": null,
  "failureStage": null,
  "failureReason": null,
  "createdAt": "2026-02-11T10:21:02",
  "updatedAt": "2026-02-11T10:21:05",
  "finishedAt": null
}
```

### 5.5 模拟器控制（开发接口）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/dev/simulation` | 读取当前耗时与失败阶段 |
| `PUT` | `/api/dev/simulation` | 运行时修改，立即生效，无需重启 |

```json
// PUT 请求（字段可只传要改的）
{ "failStage": "GENERATING", "generatingMs": 4000 }

// 响应
{ "failStage": "GENERATING", "assetCheckMs": 1200, "generatingMs": 4000, "finalizingMs": 900 }
```

`failStage` 取 `NONE` / `ASSET_CHECK` / `GENERATING` / `FINALIZING`（大小写不敏感），非法值返回 400。

---

## 6. 任务状态、阶段与后台执行

### 6.1 两个维度

- `status`（整体状态）：`PENDING` 等待处理 → `PROCESSING` 处理中 → `SUCCEEDED` 成功 / `FAILED` 失败
- `stage`（当前阶段）：`ASSET_CHECK` 素材检查 → `GENERATING` 模拟生成 → `FINALIZING` 结果整理

整体状态与阶段分开存：处理中时 `stage` 表示走到哪一步；成功时 `stage` 置空；失败时 `stage` 保留在失败的那一步，同时 `failureStage` / `failureReason` 记录原因，前端据此画出「停在哪一格」。

### 6.2 执行方式

1. `POST /api/jobs` 在**一个事务**里完成：模板存在性 → 素材数量 → 素材归属 → MIME/大小 → 插入 `job` → 插入 `job_asset`。事务提交后才把任务投递给线程池（`TransactionSynchronizationManager` 的 `afterCommit`），避免工作线程读到未提交的行。
2. 线程池 `ThreadPoolTaskExecutor`（core = max = `SIM_POOL_SIZE`，队列 100）执行 `JobExecutionService.run(jobId)`，严格按枚举顺序推进三个阶段。
3. 每进入一个阶段先写库（`UPDATE ... SET status='PROCESSING', stage=? WHERE id=? AND status IN ('PENDING','PROCESSING')`），再按该阶段耗时 `sleep`，因此前端能看到阶段**真的在库里变化**，而不是前端定时切文案。
4. **查询接口只读**：`GET /api/jobs*` 只是 `SELECT`，不会驱动任务前进（有对应测试用例）。
5. 所有状态更新都带 `WHERE status IN ('PENDING','PROCESSING')` 条件，已完成的任务不会被后续更新改回处理中。

### 6.3 失败处理

- 某一步失败：写入 `FAILED` + `failureStage` + `failureReason`，**立即停止**后续阶段。
- 失败两种来源：
  - **模拟失败**：受理任务时把当时的 `failStage` **快照**进 `job.sim_fail_stage`，执行到该阶段末尾时判定失败。快照的意义是：先设失败 → 提交任务 A → 复位 → 提交任务 B，两个任务的结果互不影响。
  - **真实失败**：素材文件在磁盘上丢失、结果图生成异常等，都会被 `try/catch` 兜底转成可读原因。
- 服务重启时，残留的 `PENDING/PROCESSING` 任务会被标记为 `FAILED`（`failure_stage = SERVICE_RESTART`，原因「服务重启，任务已中断，请重新提交」）。**不做断点续跑**，见 §10。

---

## 7. 演示步骤

以下命令用 `curl`；在 Windows PowerShell 里把 `curl` 换成 `curl.exe`，或用浏览器 / Postman。

### 7.1 成功路径

1. 打开 <http://localhost:3000>，选一个模板（如「旅行明信片」，需要 2 张图）。
2. 点两个上传位各选一张 JPG/PNG，等预览出现（上传中会有遮罩）。
3. 点「开始制作」——按钮立刻恢复，右侧 History 出现一张卡片，状态从「等待处理」→「处理中 · 素材检查」→「模拟生成」→「结果整理」→「已完成」，随后显示结果图。
4. 直接刷新页面，History 里的记录仍在（刷新后从 `GET /api/jobs` 恢复），未完成的任务会继续被跟踪。

### 7.2 触发指定阶段失败

```bash
# 让「模拟生成」这一步失败，并把该阶段拉长到 4 秒方便观察
curl -X PUT http://localhost:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStage":"GENERATING","generatingMs":4000}'
```

此时在页面上提交任意任务，会看到进度条停在第二格并变为失败态，卡片显示失败原因（「模拟失败：生成服务返回错误」）。复位：

```bash
curl -X PUT http://localhost:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"failStage":"NONE"}'
```

> 因为失败阶段是**受理时快照**的，先设置失败再提交的任务一定失败，复位后新提交的任务一定成功——可以在同一个页面上同时看到两张结果不同的卡片。

### 7.3 调整处理耗时

```bash
curl -X PUT http://localhost:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"assetCheckMs":300,"generatingMs":15000,"finalizingMs":300}'
```

耗时在**每进入一个阶段时读取**，所以对正在运行的任务的**后续阶段**也立即生效。想让某个任务长时间停在「处理中」，把对应阶段调到 15 秒即可。也可以在 `.env` 里用 `SIM_*` 变量设定启动默认值。

### 7.4 多任务

把「模拟生成」调到 10 秒以上，然后连续提交 3~4 个任务。由于线程池 `SIM_POOL_SIZE` 默认为 2：
- 前两个进入「处理中」，其余显示「等待处理」（`PENDING`）；
- 每张卡片有独立的阶段与结果，互不串扰；
- 全部结束后页面**停止轮询**（可在浏览器 Network 面板确认不再有 `/api/jobs` 请求）。

### 7.5 刷新恢复与身份切换

- 制作中途刷新页面：任务记录从后端恢复，进度条继续走。
- 点右上角「切换用户」：Cookie 换成新的 uid，History 立刻清空并重新拉取——看到的是新用户自己的记录，旧用户的任务不会串过来。
- 想切回原用户，可以在浏览器里把 `uid` Cookie 改回原来的值（HttpOnly，需要在 DevTools → Application → Cookies 中操作）。

### 7.6 查询异常与任务失败的区分

在页面上提交一个任务后，把后端停掉（`docker compose stop backend`）：
- 历史面板顶部出现红色提示「网络异常，未能获取最新状态」并提供「重试」按钮；
- **卡片状态保持不变**，不会因为查不到就被标成「制作失败」；
- 重新 `docker compose start backend` 后点「重试」，状态继续更新。

---

## 8. 用户身份与个人记录

**方案：匿名 HttpOnly Cookie，不做注册登录。**

- 后端 `UserIdentityFilter` 拦截 `/api/**`：读取名为 `uid` 的 Cookie，用 UUID 正则校验；没有或不合法就生成一个新的 UUID，写入 `Set-Cookie`（`HttpOnly`、`SameSite=Lax`、`Path=/`、有效期 365 天）。
- 首次出现该 uid 时，在 `app_user` 表里落一行（昵称 `创作者 XXXX`，XXXX 为 uid 前 4 位大写），后续请求直接复用。
- 所有业务表（`asset`、`job`）都带 `user_id`；查询与提交时以 Cookie 中的 uid 为准，跨用户访问返回 404 而不是 403，不泄露他人数据是否存在。
- 「切换用户」= `POST /api/session/switch` 生成新 uid 并覆盖 Cookie，同时前端清空本地任务列表，防止旧数据短暂串台。

**为什么这样设计**：题目允许自行选择身份策略，且明确不要求第三方登录。匿名 Cookie 能完整演示「个人记录隔离」和「切换用户时清理状态」两个考点，又不引入密码、会话表、注册流程等与本题考察重点无关的复杂度。

**局限**（已在 §10 说明）：换浏览器/清 Cookie 就找不回旧记录；没有账号找回、没有多设备同步。

---

## 9. 前端异步状态设计

- **单一轮询源**：`JobsProvider` 挂在 `layout` 上，全站只有一个轮询循环，所有页面通过 `useJobs()` 读同一份任务列表。R-08 的护栏（只跟踪未完成任务、无任务即停、不可见即停、切用户清理）只写一遍。
- **自适应间隔**：默认 1s；连续 3 次请求返回的状态签名（`id:status:stage:resultUrl:failureStage` 拼接）没有变化就退避到 2s、3s（3s 封顶）；状态一变立刻回到 1s。
- **严格区分四类状态**：
  | 场景 | 表现 |
  | --- | --- |
  | 素材上传中 | 该上传位显示遮罩 + 「上传中」，提交按钮不可用 |
  | 提交请求中 | 按钮显示「提交中…」并禁用 |
  | 任务等待/处理中 | 卡片显示「等待处理」/「制作中」+ 三格阶段进度条 |
  | 任务成功/失败 | 成功显示结果图；失败显示失败阶段与原因 |
- **查询异常 ≠ 任务失败**：轮询失败只设置 `error` 文案，**绝不修改**已拿到的任务数据；面板提供「重试」。
- **请求生命周期**：`AbortController` 在组件卸载 / 切换用户 / 页面隐藏时中止在途请求；只有最新一次请求的结果会被写入状态，避免旧响应覆盖新数据。
- **受理即返回**：`POST /api/jobs` 成功后就清掉「提交中」，任务交给 History 跟踪，符合 R-04「不等制作结束」和 R-07「展示任务而不是让按钮一直转」。

---

## 10. 测试与 CI

### 10.1 测试覆盖

后端 `backend/src/test/java/dev/liuwenjie/assetstudio/`（共 19 个用例，H2 + Flyway 真实建表）：

- `BackendSmokeTest`：模板列表/分类/详情、素材上传与静态读取、身份 Cookie、404/422 类错误码。
- `JobFlowTest`：
  - 受理立即返回任务 ID，随后按 `ASSET_CHECK → GENERATING → FINALIZING` **顺序推进**（用 400ms 阶段 + 60ms 轮询记录出现顺序）
  - 指定阶段失败后**停在 FAILED**，记录 `failureStage`/`failureReason`，**不再执行后续阶段**，且没有结果地址
  - 失败阶段是**逐任务快照**（同一批任务可一失败一成功）
  - **查询不推进任务**：把线程池压成 1 并让前序任务占住线程，反复查询排队任务，状态不变
  - 已完成任务**不会因为继续查询而变回处理中**
  - 参数校验：素材数量不符、模板不存在、用别人的素材、别人看不到我的任务
  - 调试接口拒绝未知阶段

前端 `frontend/src/**/*.test.*`（14 个用例）：

- `upload-slot.test.ts`：文件类型/大小校验、槽位创建、提交前收集 assetId、格式化函数
- `jobs-provider.test.tsx`：
  - 多个任务各自保留自己的状态与失败原因（互不串扰）
  - 查询异常只提示出错，**不把已有任务改成失败**
  - 服务端业务错误按后端文案展示
  - 全部任务结束后**不再继续轮询**

### 10.2 CI

`.github/workflows/ci.yml`，在 `push`（任意分支）与 `pull_request` 时触发，两个 job：

- **backend**：JDK 21 → `mvn -B verify`（编译 + 全部测试 + 打包），上传 surefire 报告
- **frontend**：pnpm 11 + Node 22 → `pnpm install --frozen-lockfile` → `pnpm lint` → `pnpm typecheck` → `pnpm test` → `pnpm build`

CI 里后端测试使用 H2 内存库，不需要额外起 MySQL 服务。

---

## 11. 完成度与已知限制

### 11.1 已完成

| 需求 | 状态 |
| --- | --- |
| R-02 模板列表 / 分类筛选 / 卡片进详情 | 完成，数据来自 Java 接口 |
| R-03 详情、上传位选择/预览/替换/移除、上传中与失败反馈、前后端双向校验 | 完成 |
| R-04 受理即返回、后台异步执行、关页面不中断 | 完成 |
| R-05 三阶段顺序执行、状态与阶段分离、查询只读、完成不回退 | 完成，有测试守护 |
| R-06 指定阶段失败（可重复、运行时切换）、耗时可调 | 完成，`PUT /api/dev/simulation` |
| R-07 六类前端状态区分、查询异常不误判为失败 | 完成 |
| R-08 多任务隔离、自动跟踪与停止、切用户清理 | 完成，自适应轮询 + AbortController |
| R-09 历史记录、刷新恢复、未完成任务继续跟踪 | 完成 |
| R-10 身份与个人记录 | 完成（匿名 Cookie），见 §8 |
| R-11 桌面布局、小屏可用、语义与焦点 | 完成（深色 + 橄榄绿，参考截图布局：左工作区 + 右 History） |
| R-12 CI + 核心测试 | 完成，见 §10 |
| R-13 README / 交付材料 | 本文档 |
| R-14 docker compose 一键启动 | 完成，见 §2.1 |

### 11.2 明确的取舍与未实现项

- **不做执行中任务的断点续跑**：服务重启后，残留任务统一标记为失败（`SERVICE_RESTART`）。真正的续跑需要在阶段边界落检查点并处理幂等重放，收益低于复杂度，题目也明确不要求。当前实现至少保证了「不会永远卡在处理中」。
- **不做任务受理幂等**：同一个提交请求重复发送会创建多个任务（各自的素材校验独立通过）。真实场景应引入客户端幂等键（`Idempotency-Key`），本题未做。
- **不做失败任务重试/重新制作、结果下载/删除**：题目明确不要求。
- **素材与结果都存本地磁盘**（`STORAGE_ROOT`，容器里是命名卷）：单实例可用，多副本部署需要换成对象存储。
- **结果图是「排版合成」而非 AI 生成**：题目说明不调用真实 AI，`ResultImageRenderer` 只把上传图按版式拼成卡片，且**只绘制 ASCII 文案**（容器 JRE 常缺中文字体，避免出现方块字）。
- **身份是匿名 Cookie**：换浏览器或清 Cookie 会丢失历史，属于设计内取舍。
- **没有真实部署产物**：只提供 `docker compose` 本地/单机运行方式。
- **前端未做端到端浏览器测试**（题目不要求），交互路径靠单元测试 + 手工验证覆盖。

### 11.3 实际投入

**总计约 2.5 小时**（含读题、选型、编码、测试、容器排错与文档整理）。

全程使用 AI 编程工具辅助，因此编码本身的占比很小，时间主要花在三个地方：

1. **需求确认与接口契约**：先与需求方对齐状态枚举、轮询策略、失败模拟方式这些会影响全局结构的决策，避免中途返工。
2. **容器构建环境排查**：本机网络环境导致基础镜像与 npm、Maven 依赖反复超时，这部分耗时明显超出预期（详见 §4.3 的软件源配置）。
3. **验证与测试补齐**：后端 19 个、前端 14 个用例，以及用真实 MySQL + 真实 HTTP 跑通端到端流程。

> 说明：以上为提交者自行统计的投入时间，不含等待依赖下载的空闲时段。

**主要取舍记录**：

1. **轮询而不是 SSE/WebSocket**：题目不强制，轮询实现简单、断线自愈、易于演示「查询异常 ≠ 任务失败」；代价是有最高 3s 的延迟，用自适应退避把空转压到最低。
2. **列表接口一次返回全部任务，而不是每个任务单独查**：轮询只打一个请求，多任务天然一致，避免 N 个定时器互相覆盖。
3. **失败阶段快照进任务行**：让「设失败 → 提交 A → 复位 → 提交 B」可以稳定演示一败一成，而不需要改代码或重启。
4. **不用 Redis / 消息队列**：单实例 + `ThreadPoolTaskExecutor` 已满足「排队或并行、不要求真正高并发」的要求，少一个中间件就少一处部署风险。
5. **前端只访问同源路径**：Next `rewrites` 透传，免 CORS、免第三方 Cookie 问题。

---

## 12. 部署与回滚

当前交付目标是「可重复地在单机跑起来」，不涉及生产集群。

**部署**

```bash
git clone <repo>
cd asset-studio
cp .env.example .env         # 按需改端口/模拟参数
docker compose up --build -d
docker compose ps            # 三个服务均应为 running / healthy
curl -f http://localhost:8080/api/health
```

**升级**

```bash
git pull
docker compose up --build -d      # 重新构建并滚动替换容器
```

数据库结构变更通过新增 Flyway 迁移文件（`V4__xxx.sql`）完成，应用启动时自动执行；**迁移只前进不后退**。

**回滚**

```bash
git checkout <上一个可用 commit>
docker compose up --build -d
```

若本次升级包含数据库结构变更，回滚代码前需评估迁移是否兼容旧版本：Flyway 社区版不支持自动 undo，稳妥做法是回滚前先备份：

```bash
docker compose exec mysql mysqldump -ustudio -pstudio asset_studio > backup_$(date +%Y%m%d).sql
```

数据卷 `mysql-data`（数据库）与 `storage`（上传素材与结果图）在 `docker compose down` 时保留，只有 `down -v` 才会删除。

---

## 13. 关于 AI 工具的使用

**使用了 AI 编程工具（DeepSeek Harness 智能体）辅助完成本项目。** 具体工作流：

1. **拆解与对齐**：先让模型读题目并产出一份「需求 → 模块」的对照表，人工逐条确认（例如 R-05 要求「查询不驱动推进」，明确落到「查询只读 + 有测试守护」）。
2. **关键决策人工拍板**：模型给出 2~3 个方案与取舍（如「列表快照轮询」vs「每任务独立轮询」），由人选择后再动手，避免实现方向跑偏。
3. **分模块生成 + 即时验证**：每次只做一个模块（身份 → 模板 → 素材 → 任务 → 前端），每个模块写完立刻跑测试/类型检查/构建，通过后再进入下一个。**不做「一次生成整个项目」**。
4. **以测试和运行结果为准**：模型产出的代码不直接采信，靠 `mvn test`、`tsc --noEmit`、`eslint`、`vitest`、`next build` 和实际 `docker compose up` 的结果判断对错。
5. **问题定位与修正**：遇到报错时把**完整错误信息**回灌给模型（例如 ESLint 的 `TypeError: Converting circular structure to JSON`、React Compiler 的 `Cannot access refs during render`），由模型给出修复方案并说明原因，人工复核后落地。

**一个具体例子（ESLint 配置）**：初始用 `FlatCompat` 加载 `next/core-web-vitals`，`eslint` 直接抛 `Converting circular structure to JSON`。把完整堆栈交给模型后，定位到 `eslint-config-next@16` 已原生提供 flat config（`dist/*.js` 直接导出数组），`FlatCompat` 反而会二次转换导致插件对象循环引用。改为直接 `import` 后问题消失，`import/no-anonymous-default-export` 的告警也一并通过抽成 `config` 常量解决。

**边界**：涉及「要不要做某个功能」「用哪种方案」「参数取多少」这类判断，由人决策；模型负责实现细节、排错与文档初稿。

---

## 14. 第三方来源与复用范围

- **脚手架**：前端目录结构参考 `create-next-app` 的 App Router 默认布局（未直接使用生成器产物，配置逐项手写以便控制版本）。后端使用 Spring Boot 官方 Maven 骨架结构。
- **开源依赖**：全部通过 `pnpm-lock.yaml` / `pom.xml` 声明，见 §1 版本表。未引入任何需要付费或许可授权受限的组件。
- **素材**：6 张模板封面为本项目手写 SVG；结果图由 Java2D 在运行时合成；无第三方图片、字体或品牌素材。运行时字体使用容器内的 DejaVu（SIL OFL），仅用于结果卡片的 ASCII 文案。