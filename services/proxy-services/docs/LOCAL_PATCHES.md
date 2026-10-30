# 本地补丁

`services/proxy-services` 是上游 CLIProxyAPI 的 vendor 副本。下面这些改动不在上游里。升级上游后按本文重打，不要靠记忆。

## 账号 pacing（Issue #152）

本地选择路径上，按账号限制三件事：两次成功完成之间的最短间隔（可加抖动）、同时在途数、已观察到的额度使用百分比。状态挂在 `sdk/cliproxy/auth.Manager` 上，和 cooldown 同级，只在内存里，进程重启即清。间隔闸用的就是各账号已有的 `nextAllowedAt`，不另建一套状态。

落点是资格过滤，不造新错误码，也不写 cooldown。撞限的账号本轮不合格。全部不合格时走现成的 `auth_not_found`（`no auth available`，handler 映射 503）。该错误不进入 request-retry。

有一个例外：这次选择里，本来可以选的账号全部只被间隔闸挡住，并且最近的 `nextAllowedAt` 不超过 `now + max-wait` 时，先停一次，用现成的 `waitForCooldown`（认 ctx 取消），然后重新选择一次。等到之后仍然没有可用账号，才走 `auth_not_found`。不等第二次。额度阈值或已启用的在途上限挡住的账号不触发这次等待，仍是立刻跳过或拒绝。`max-wait` 为 0 时也不等，回到立刻拒绝。最近的允许时间超过上限时同样立刻拒绝。

成功时钟写在 `MarkResult` 记账成功的地方。失败不改这只钟。在途闸默认关闭；配置写成正数才占用。账号被选定即占一个在途位，成功、失败、取消、panic，以及占位后没有真正发出，都要释放。流式请求在转发结束时释放，不在 `ExecuteStream` 返回时释放。释放用一次有效的计数，参照仓内 Home 并发的「占住再还」方式，不用 Home 那套执行注册表。

session-affinity 先解析已有绑定，再只对绑定账号判 pacing。绑定账号只被间隔挡住且在 `max-wait` 内可等时，等完仍用这个账号，不改绑。超过 `max-wait`，或被额度、在途挡住，返回 `auth_not_found`，不改绑，也不换别的账号。affinity 关闭时，撞限账号跳过，改选其它账号；若剩下的账号全是间隔挡住且可等，先等最近的一个再选一次。

带 `-home-jwt` 时整段不生效。不读 `credentials.concurrency` 注释当开关。不改 retry / cooldown 既有逻辑。

配置在 `routing.account-pacing`。省略字段用默认值：间隔 `500ms`、抖动上限 `200ms`、在途 `0`（关）、额度 `85%`、`max-wait` `3s`。任一参数为 `0` 关闭该闸。间隔、在途、额度三个闸都是 `0` 时选择行为与未打补丁时一致。`max-wait: 0` 是等待行为的回滚：改配置后重启，不用改代码。热重载走和 `SetRetryConfig` 同一处，调用 `SetAccountPacingConfig`。

生效值由 `GET /v8/management/routing/account-pacing` 返回，多一个 `max-wait-ms`。管理后台只透传，不读配置文件。

### 补丁文件

- `internal/config/config_types.go`
- `internal/config/account_pacing.go`
- `internal/config/account_pacing_test.go`
- `config.example.yaml`
- `sdk/cliproxy/auth/account_pacing.go`
- `sdk/cliproxy/auth/account_pacing_test.go`
- `sdk/cliproxy/auth/conductor.go`
- `sdk/cliproxy/auth/conductor_selection.go`
- `sdk/cliproxy/auth/conductor_cooldown.go`
- `sdk/cliproxy/auth/conductor_execution.go`
- `sdk/cliproxy/auth/selector.go`
- `sdk/cliproxy/service_auth.go`
- `internal/api/server.go`
- `internal/api/server_reload.go`
- `internal/api/server_management_v8.go`
- `internal/api/handlers/management/account_pacing.go`
- `internal/api/handlers/management/account_pacing_test.go`

过滤函数是 `accountPacingAllows`。`conductor_selection.go` 的 eligibility 和 scheduler 的候选构建共用它。

仓库里配套的只读展示不在代理进程内：

- `services/website-services/admin` 的代理状态 DTO 与透传
- `apps/admin-web` 的 API 管理页与三语文案
- `docs/overview/ROADMAP.md`、`docs/website-services/CURRENT_STATE.md`

### 上游升级后重打

1. 把上游合入 `services/proxy-services`，先解决与本清单文件的冲突。
2. 对照本清单把 pacing 接回去。资格过滤仍只留一份；`MarkResult` 只在成功记账处盖时钟，不要盖到 `recordAvailabilityNeutralResult`。
3. 在途占位仍放在三条本地执行循环里，四条出口都释放。`-home-jwt` 路径不占位。
4. `SetAccountPacingConfig` 仍紧挨每一处 `SetRetryConfig`。管理接口仍读 Manager 上的生效值，不读配置文件。间隔短等只在选择前发生一次，复用 `waitForCooldown`，不要写进 retry / cooldown。
5. 跑 `go test ./sdk/cliproxy/auth/ ./internal/config/ ./internal/api/handlers/management/ -count=1`，以及 `go test -race -count=1 -timeout 180s -run TestAccountPacing ./sdk/cliproxy/auth/`。
6. 更新本文件的清单，使它和实际补丁文件一致。
