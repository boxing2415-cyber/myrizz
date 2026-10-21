// Reads configuration from environment variables. No secrets are hard-coded anywhere.

function int(name, fallback) {
  const raw = process.env[name];
  const n = raw === undefined || raw === '' ? NaN : Number.parseInt(raw, 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function loadConfig(env = process.env) {
  const provider = (env.AI_PROVIDER || 'anthropic').toLowerCase();
  return {
    port: int('PORT', 8080),
    provider,
    anthropicKey: env.ANTHROPIC_API_KEY || '',
    anthropicModel: env.ANTHROPIC_MODEL || 'claude-sonnet-4-5',
    openaiKey: env.OPENAI_API_KEY || '',
    openaiModel: env.OPENAI_MODEL || 'gpt-4o',
    accessTokens: (env.APP_ACCESS_TOKENS || '')
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean),
    rateLimitPerMin: int('RATE_LIMIT_PER_MIN', 20),
    trustProxy: env.TRUST_PROXY === '1' || env.TRUST_PROXY === 'true',
    maxInputChars: int('MAX_INPUT_CHARS', 6000),
    maxImageBytes: int('MAX_IMAGE_BYTES', 3_000_000),
    requestTimeoutMs: int('REQUEST_TIMEOUT_MS', 45_000),
  };
}
