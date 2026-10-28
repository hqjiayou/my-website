# `cliproxy/` · CLIProxyAPI 本地附加组件

## 这是什么

仓库内自包含、可独立起停的本地附加组件，封装 [router-for-me/CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI)——把 Gemini CLI / Codex / Claude Code 等订阅账号包装成 OpenAI 兼容 API。

Portal 的 AI 模型入口支持两种模式：Key 直连 / CLI 代理池（见 admin 后台「模型入口」）。**只有要用 CLI 代理池模式时才需要本组件**；只用 Key 直连模式的话不用起它。

本组件不与根目录 `docker-compose.yml`（自架部署栈）或仓库外 local-stack（PG/Redis/MinIO，见 [`LOCAL_DEVELOPMENT.md`](../../../../../docs/operations/LOCAL_DEVELOPMENT.md) §1）耦合，是独立的 Compose 项目。

## 启停

在本目录（`services/website-services/scripts/local/cliproxy/`）下执行：

```bash
docker compose up -d
docker compose down
```

API 地址：`http://127.0.0.1:8317`（只绑回环，宿主机其他网卡与局域网访问不到）。

## 首次登录

> ⚠️ **只用非主力的 Google / OpenAI / Anthropic 账号登录，不要用任何主力账号。** 把订阅额度转成 API 调用是否符合对应服务条款存在灰区，一旦被判违规，封号风险落在登录所用的账号本身。

登录产生的 OAuth 凭证落在本目录 `auths/`（挂载到容器内 `/root/.cli-proxy-api`），已被 `.gitignore` 排除，不会进仓库。

两种登录方式任选其一：

### 方式一：官方管理界面（可视化，需额外开启）

本组件自带的 `config.yaml` 是最小配置，默认未开启管理界面。若要用可视化 OAuth 流程，先在 `config.yaml` 里补充 `management` 段并设置一个 `secret-key`（上游文档见 [Basic Configuration](https://help.router-for.me/configuration/basic)），重启容器后访问 `http://127.0.0.1:8317/management.html`，用这把 `secret-key` 登录即可在界面里走各 Provider 的 OAuth 流程。

### 方式二：容器内跑登录命令（无需改配置）

```bash
# OpenAI Codex
docker compose exec cli-proxy-api /CLIProxyAPI/CLIProxyAPI -no-browser --codex-login

# Claude（Anthropic）
docker compose exec cli-proxy-api /CLIProxyAPI/CLIProxyAPI -no-browser --claude-login

# Antigravity（Google，含部分 Claude/Gemini 模型）
docker compose exec cli-proxy-api /CLIProxyAPI/CLIProxyAPI -no-browser --antigravity-login
```

Gemini CLI / Google 账号按上游当前文档的登录流程操作（旗标与回调端口随上游版本可能调整，以 [CLIProxyAPI 官方文档](https://help.router-for.me/) 当前内容为准）。`-no-browser` 会打印登录 URL 而不是尝试拉起浏览器，适合在容器/无桌面环境里用；在宿主机浏览器里打开该 URL 完成授权即可。

## 后台接法（admin「模型入口」）

登录完成、代理可用后，在 admin 后台「模型入口」新建一条：

| 字段 | 值 |
|------|----|
| 模式 | CLI 代理 |
| baseUrl | `http://127.0.0.1:8317/v1` |
| apiKey | `config.yaml` 里 `access.api-keys` 那把客户端 key（不是任何 Provider 账号密钥） |
| modelSpec | `openai:<模型名>`，模型名以代理的 `/v1/models` 实际返回为准 |
| embeddingSpec | 留空（该代理不提供 embedding，向量检索仍用进程里的 embedding 配置） |
| 默认入口 | 先不勾选 |

先按备援入口接入、验证链路通过之后再考虑是否设为默认（生效链见 admin-web「模型入口」页说明）。

## 安全边界

- 端口全部只绑 `127.0.0.1`（见 `docker-compose.yml`）；不要改成 `0.0.0.0` 或局域网地址对外暴露，CLIProxyAPI 本身没有强身份边界，暴露出去约等于把登录账号的订阅额度开放给任何能访问该端口的人。
- `access.api-keys` 里的客户端 key 与 `auths/` 下的 OAuth 凭证文件都是敏感信息，不要提交进仓库（`auths/.gitignore` 已处理凭证；`config.yaml` 里的占位 key 改成自己的值后也不要提交真实值到公共分支）。
