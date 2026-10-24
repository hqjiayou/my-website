# syntax=docker/dockerfile:1
# admin-web 单应用镜像：node:22 + pnpm 建置，运行阶段是 caddy:2。
# 默认堆叠不引用本文件。compose 的 web 服务使用 apps/portal/Dockerfile，
# 那个镜像已经包含本应用的 dist，并和 Portal 共用一个 Caddy。
# 不要再单独起本镜像，否则会和 web 服务抢同一份后台入口。
# 构建 context 必须是仓库根。

FROM node:22 AS admin-build

WORKDIR /app
RUN npm install -g pnpm@10.15.0
COPY apps/admin-web/package.json apps/admin-web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/admin-web/ ./
RUN pnpm build

FROM caddy:2

EXPOSE 8080 8081

COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=admin-build /app/dist /srv/admin
