# syntax=docker/dockerfile:1
# 共用写法：一个 Caddy 镜像同时放入 Agent Portal 与 admin-web 的 dist。
# compose 的 web 服务使用本文件。构建 context 必须是仓库根。
# admin-web 的 vite.config.ts 没有读取 base 的环境变量，因此不能挂到 /admin/。
# admin 静态文件由 Caddy 的 :8081 提供，见 deploy/Caddyfile。

FROM node:22 AS portal-build

WORKDIR /app
RUN npm install -g pnpm@10.15.0
COPY apps/agent-portal/package.json apps/agent-portal/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/agent-portal/ ./
ARG VITE_PORTAL_AGENT_ID=
ENV VITE_PORTAL_AGENT_ID=${VITE_PORTAL_AGENT_ID}
RUN pnpm build

FROM node:22 AS admin-build

WORKDIR /app
RUN npm install -g pnpm@10.15.0
COPY apps/admin-web/package.json apps/admin-web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/admin-web/ ./
# 不设置 VITE_API_BASE_URL。admin-web 用 `?? '/api'`，空字符串不会落到这个回退。
RUN pnpm build

FROM caddy:2

COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=portal-build /app/dist /srv/portal
COPY --from=admin-build /app/dist /srv/admin

EXPOSE 8080 8081
