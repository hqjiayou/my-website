import base from './playwright.mock.config'

/** 仅跑 P0 截图用例；产物写入 tmp/，不入常规 CI。 */
export default {
  ...base,
  testMatch: ['**/99-p0-screenshots.mock.spec.ts'],
}
