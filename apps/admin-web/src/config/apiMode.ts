/**
 * mock / real 开关（仅影响**明确允许 mock** 的域）。
 *
 * 核心业务域（Site Core / Template / Appearance / Media / Content / Reservation /
 * auth / rbac）以及站点 AI（Prompt / KB / Conversation / Preview）为 FORCE REAL，
 * 不受本开关门控。
 *
 * 当前仍受门控：site runtime-status 等未实现能力。
 *
 * - 开发（dev）：默认 `true`（可用 `VITE_USE_MOCK=false` 关闭）
 * - 生产（prod）：除非显式 `VITE_USE_MOCK=true`，否则 `false`
 */
const rawUseMock = import.meta.env.VITE_USE_MOCK

export const USE_MOCK: boolean = import.meta.env.PROD
  ? rawUseMock === 'true'
  : rawUseMock !== 'false'

if (import.meta.env.PROD && USE_MOCK) {
  console.error(
    '[apiMode] 生产构建中 VITE_USE_MOCK=true：仅 runtime-status 等非 FORCE REAL 域可能走 mock。请确认有意为之。',
  )
}
