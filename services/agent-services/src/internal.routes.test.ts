import assert from 'node:assert/strict'
import { after, before, describe, it } from 'node:test'
import type { FastifyInstance } from 'fastify'
import { buildServer } from './server.js'

describe('agent-services internal routes', () => {
  let app: FastifyInstance

  before(async () => {
    app = await buildServer({
      AGENT_INTERNAL_SERVICE_TOKEN: 'test-internal-token',
      ENABLE_DEV_MOCK_ROUTES: 'false',
    })
  })

  after(async () => {
    await app.close()
  })

  it('rejects chat without internal token', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/internal/v1/chat',
      payload: { siteId: '1', message: 'hello' },
    })
    assert.equal(res.statusCode, 401)
    assert.equal(res.json().errorCode, 'UNAUTHORIZED')
  })

  it('returns EXTERNAL_DEPENDENCY_BLOCKED when provider has no key', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/internal/v1/chat',
      headers: { 'x-internal-service-token': 'test-internal-token' },
      payload: { siteId: '10', message: 'hello' },
    })
    assert.equal(res.statusCode, 503)
    const body = res.json()
    assert.equal(body.ok, false)
    assert.equal(body.errorCode, 'EXTERNAL_DEPENDENCY_BLOCKED')
    assert.equal(body.providerStatus, 'BLOCKED')
    assert.equal(body.message, 'AI provider is not configured (missing API key).')
    assert.equal(body.data, undefined)
  })

  it('distinguishes blocked message when API key is set but SDK not implemented', async () => {
    const withKey = await buildServer({
      AGENT_INTERNAL_SERVICE_TOKEN: 'test-internal-token',
      AI_PROVIDER_API_KEY: 'sk-test-not-wired',
      ENABLE_DEV_MOCK_ROUTES: 'false',
    })
    try {
      const res = await withKey.inject({
        method: 'POST',
        url: '/internal/v1/chat',
        headers: { 'x-internal-service-token': 'test-internal-token' },
        payload: { siteId: '10', message: 'hello' },
      })
      assert.equal(res.statusCode, 503)
      const body = res.json()
      assert.equal(body.errorCode, 'EXTERNAL_DEPENDENCY_BLOCKED')
      assert.equal(body.providerStatus, 'BLOCKED')
      assert.equal(
        body.message,
        'AI provider API key is set but the provider SDK is not implemented yet.',
      )
      assert.match(body.message, /not implemented/i)
      assert.doesNotMatch(body.message, /missing API key/i)
    } finally {
      await withKey.close()
    }
  })

  it('health requires token and reports BLOCKED provider', async () => {
    const denied = await app.inject({ method: 'GET', url: '/internal/v1/health' })
    assert.equal(denied.statusCode, 401)

    const ok = await app.inject({
      method: 'GET',
      url: '/internal/v1/health',
      headers: { 'x-internal-service-token': 'test-internal-token' },
    })
    assert.equal(ok.statusCode, 200)
    assert.equal(ok.json().providerStatus, 'BLOCKED')
  })

  it('does not expose reservation/site-config in production mode', async () => {
    const reservation = await app.inject({
      method: 'POST',
      url: '/api/site/1/reservations',
      payload: { customerName: 'a', phone: '1' },
    })
    assert.equal(reservation.statusCode, 404)

    const config = await app.inject({ method: 'GET', url: '/api/site/example.com/config' })
    assert.equal(config.statusCode, 404)
  })
})
