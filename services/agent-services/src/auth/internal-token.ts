import type { FastifyReply, FastifyRequest } from 'fastify'

export const INTERNAL_SERVICE_TOKEN_HEADER = 'x-internal-service-token'

export function resolveInternalServiceToken(env: NodeJS.ProcessEnv = process.env): string {
  return env.AGENT_INTERNAL_SERVICE_TOKEN?.trim() ?? ''
}

export function createInternalTokenPreHandler(expectedToken: string) {
  return async function requireInternalServiceToken(
    request: FastifyRequest,
    reply: FastifyReply,
  ) {
    if (!expectedToken) {
      // 服务端未配置 token：拒绝一切内部调用，避免裸奔。
      return reply.code(503).send({
        ok: false,
        errorCode: 'EXTERNAL_DEPENDENCY_BLOCKED',
        message: 'Internal service token is not configured on agent-services.',
      })
    }

    const provided = request.headers[INTERNAL_SERVICE_TOKEN_HEADER]
    const token = Array.isArray(provided) ? provided[0] : provided
    if (!token || token !== expectedToken) {
      return reply.code(401).send({
        ok: false,
        errorCode: 'UNAUTHORIZED',
        message: 'Invalid or missing internal service token.',
      })
    }
  }
}
