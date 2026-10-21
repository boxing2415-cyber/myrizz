// Dependency-free HTTP API (Node built-ins only): fewer moving parts, no supply-chain surface.
import crypto from 'node:crypto';
import http from 'node:http';
import {
  REPLIES_SCHEMA,
  TOOL_SCHEMA,
  TRANSCRIBE_SCHEMA,
  buildReplySystem,
  buildReplyUser,
  buildToolSystem,
  buildToolUser,
  buildTranscribeSystem,
} from './prompts.js';
import {
  HttpError,
  checkReplies,
  checkTool,
  checkTranscribe,
  validateReplies,
  validateTool,
  validateTranscribe,
} from './validate.js';
import { createAnthropicProvider } from './providers/anthropic.js';
import { createOpenAIProvider } from './providers/openai.js';

export function createProvider(cfg) {
  if (cfg.provider === 'openai') {
    if (!cfg.openaiKey) return null;
    return createOpenAIProvider({ apiKey: cfg.openaiKey, model: cfg.openaiModel, timeoutMs: cfg.requestTimeoutMs });
  }
  if (!cfg.anthropicKey) return null;
  return createAnthropicProvider({ apiKey: cfg.anthropicKey, model: cfg.anthropicModel, timeoutMs: cfg.requestTimeoutMs });
}

function tokenFrom(header) {
  const m = /^Bearer\s+(.+)$/i.exec(header || '');
  return m ? m[1].trim() : '';
}

function tokenOk(given, tokens) {
  const g = Buffer.from(given);
  return tokens.some((t) => {
    const w = Buffer.from(t);
    return w.length === g.length && crypto.timingSafeEqual(w, g);
  });
}

function clientIp(req, trustProxy) {
  if (trustProxy) {
    const xff = req.headers['x-forwarded-for'];
    if (typeof xff === 'string' && xff) return xff.split(',')[0].trim();
  }
  return req.socket.remoteAddress || 'unknown';
}

function readJson(req, limitBytes) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limitBytes) {
        reject(new HttpError(413, 'input_too_long', 'Request too large'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      try {
        const text = Buffer.concat(chunks).toString('utf8');
        resolve(text ? JSON.parse(text) : {});
      } catch {
        reject(new HttpError(400, 'bad_request', 'Invalid JSON'));
      }
    });
    req.on('error', () => reject(new HttpError(400, 'bad_request', 'Could not read request')));
  });
}

function send(res, status, body, extraHeaders = {}) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    ...extraHeaders,
  });
  res.end(payload);
}

/** Fixed-window in-memory limiter. Good enough for a single small instance. */
function createLimiter(limit, windowMs = 60_000) {
  const hits = new Map();
  const timer = setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.resetAt <= now) hits.delete(k);
  }, windowMs);
  timer.unref();
  return {
    check(key) {
      const now = Date.now();
      let e = hits.get(key);
      if (!e || e.resetAt <= now) {
        e = { count: 0, resetAt: now + windowMs };
        hits.set(key, e);
      }
      e.count += 1;
      return { ok: e.count <= limit, retryAfterSec: Math.max(1, Math.ceil((e.resetAt - now) / 1000)) };
    },
  };
}

/**
 * @param {object} cfg    see config.js
 * @param {object|null} provider  { name, model, generateJson } or null when no API key is configured
 * @returns {http.Server} (not yet listening)
 */
export function createApp(cfg, provider) {
  const limiter = createLimiter(cfg.rateLimitPerMin);

  const routes = {
    'POST /v1/replies': async (body) => {
      const input = validateReplies(body, cfg);
      const raw = await provider.generateJson({
        system: buildReplySystem(),
        user: buildReplyUser(input),
        schema: REPLIES_SCHEMA,
        schemaName: 'submit_replies',
      });
      return checkReplies(raw);
    },
    'POST /v1/tools': async (body) => {
      const input = validateTool(body, cfg);
      const raw = await provider.generateJson({
        system: buildToolSystem(),
        user: buildToolUser(input),
        schema: TOOL_SCHEMA,
        schemaName: 'submit_result',
      });
      return checkTool(raw);
    },
    'POST /v1/transcribe': async (body) => {
      const input = validateTranscribe(body, cfg);
      const raw = await provider.generateJson({
        system: buildTranscribeSystem(),
        user: 'Transcribe the chat in this screenshot.',
        image: { mimeType: input.mimeType, base64: input.imageBase64 },
        schema: TRANSCRIBE_SCHEMA,
        schemaName: 'submit_transcript',
        maxTokens: 2048,
      });
      return checkTranscribe(raw);
    },
  };

  async function handle(req, res) {
    const path = new URL(req.url || '/', 'http://localhost').pathname;

    if (req.method === 'GET' && path === '/health') {
      return send(res, 200, {
        ok: true,
        aiConfigured: Boolean(provider),
        provider: provider ? provider.name : null,
        authRequired: cfg.accessTokens.length > 0,
      });
    }

    const route = routes[`${req.method} ${path}`];
    if (!route) {
      if (path.startsWith('/v1/')) throw new HttpError(path in routes ? 405 : 404, 'not_found', 'Not found');
      throw new HttpError(404, 'not_found', 'Not found');
    }

    // 1) auth
    const given = tokenFrom(req.headers.authorization);
    if (cfg.accessTokens.length > 0 && !(given && tokenOk(given, cfg.accessTokens))) {
      throw new HttpError(401, 'unauthorized', 'Missing or invalid access token');
    }
    // 2) rate limit (keyed by validated token, otherwise by client IP)
    const key = cfg.accessTokens.length > 0 ? `t:${given}` : `ip:${clientIp(req, cfg.trustProxy)}`;
    const rl = limiter.check(key);
    if (!rl.ok) {
      const e = new HttpError(429, 'rate_limited', 'Too many requests, slow down');
      e.headers = { 'retry-after': String(rl.retryAfterSec) };
      throw e;
    }
    // 3) provider available?
    if (!provider) {
      throw new HttpError(503, 'ai_not_configured', 'No AI API key is configured on the server');
    }
    const body = await readJson(req, 6 * 1024 * 1024);
    send(res, 200, await route(body));
  }

  return http.createServer((req, res) => {
    const t0 = Date.now();
    // Minimal log: method, path, status, duration. Message content is NEVER logged.
    res.on('finish', () => {
      const p = (req.url || '').split('?')[0];
      console.log(`${req.method} ${p} ${res.statusCode} ${Date.now() - t0}ms`);
    });
    handle(req, res).catch((err) => {
      if (res.headersSent) return res.end();
      if (err instanceof HttpError) {
        return send(res, err.status, { error: err.code, message: err.message }, err.headers || {});
      }
      console.error('Unhandled error:', err && err.message);
      send(res, 500, { error: 'internal_error', message: 'Internal server error' });
    });
  });
}
