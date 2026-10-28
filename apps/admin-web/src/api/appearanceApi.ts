/**
 * 站点外观 API。
 * Appearance（get/put）→ 始终 REAL `/admin/agents/{id}/appearance`。
 */
import type { AppearanceDetail, AppearancePutRequest } from '@/types/appearance'
import {
  APPEARANCE_SCHEMA_VERSION_V1,
  BUTTON_STYLE_VALUES,
  FONT_STYLE_VALUES,
  HERO_LAYOUT_VALUES,
  PRIMARY_COLOR_PATTERN,
  createDefaultAppearanceConfig,
} from '@/types/appearance'
import type { HttpMessageBody } from '@/types/rbac'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

export const appearanceApi = {
  getAppearance(agentId: number): Promise<AppearanceDetail> {
    return httpClient
      .get<HttpMessageBody<AppearanceDetail>>(`/admin/agents/${agentId}/appearance`)
      .then(unwrapData)
  },

  putAppearance(agentId: number, input: AppearancePutRequest): Promise<AppearanceDetail> {
    return httpClient
      .put<HttpMessageBody<AppearanceDetail>, AppearancePutRequest>(
        `/admin/agents/${agentId}/appearance`,
        input,
      )
      .then(unwrapData)
  },
}

export type {
  AppearanceConfigV1,
  AppearanceDetail,
  AppearancePutRequest,
  ButtonStyle,
  HeroLayout,
  AgentFontStyle,
} from '@/types/appearance'

export {
  APPEARANCE_SCHEMA_VERSION_V1,
  BUTTON_STYLE_VALUES,
  FONT_STYLE_VALUES,
  HERO_LAYOUT_VALUES,
  PRIMARY_COLOR_PATTERN,
  createDefaultAppearanceConfig,
}
