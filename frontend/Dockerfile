# syntax=docker/dockerfile:1

# ---- 安装依赖 ----
FROM node:22-alpine AS deps
WORKDIR /app
# CI 模式让 pnpm 不进入交互确认分支。
ENV CI=true
# 默认使用 npm 官方源；国内网络可在 .env 里把 NPM_REGISTRY 指向镜像站。
# 这里不用 corepack：corepack 会额外联网拉取 pnpm 本体，在网络不稳时同样会卡住。
ARG NPM_REGISTRY=https://registry.npmjs.org
RUN npm config set registry "$NPM_REGISTRY" \
 && npm install -g pnpm@11.1.2 --registry="$NPM_REGISTRY" \
 && pnpm config set registry "$NPM_REGISTRY"
# pnpm-workspace.yaml 里声明了允许执行安装脚本的依赖（allowBuilds），必须一起拷贝，
# 否则 pnpm 按默认策略忽略构建脚本并以非零码退出。
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --fetch-timeout=1200000 --fetch-retries=10

# ---- 构建 ----
FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# rewrites 的目标地址在 next build 时被写进产物，所以必须在构建期就给定。
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
# 直接用 node 镜像自带的 npm 跑脚本，不再安装一份 pnpm。
RUN npm run build

# ---- 运行（利用 next.config.ts 的 output: "standalone"）----
FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

USER node
EXPOSE 3000
CMD ["node", "server.js"]