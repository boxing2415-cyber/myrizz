import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { buildReplyUser, languageDirective } from '../src/prompts.js';
import { createAnthropicProvider } from '../src/providers/anthropic.js';

function fakeProvider(handler) {
  return { name: 'fake', model: 'fake-1', generateJson: handler };
}

async function start(cfgOverrides, provider) {
  const cfg = { ...loadConfig({}), ...cfgOverrides };
  const app = createApp(cfg, provider);
  const server = await new Promise((resolve) => {
    const s = app.listen(0, () => resolve(s));
  });
  return { server, base: `http://127.0.0.1:${server.address().port}` };
}

const post = (base, path, body, headers = {}) =>
  fetch(base + path, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });

describe('API with a configured provider', () => {
  let ctx;
  let lastCall;
  before(async () => {
    ctx = await start(
      { accessTokens: ['secret-token'], rateLimitPerMin: 1000 },
      fakeProvider(async (args) => {
        lastCall = args;
        if (args.schemaName === 'submit_replies') {
          return { replies: ['one', 'two', 'two', 'three'], detectedLanguage: 'el', tone: 'playful' };
        }
        if (args.schemaName === 'submit_result') return { items: ['  fixed text  '] };
        return { turns: [{ speaker: 'me', text: 'hi' }, { speaker: 'x', text: 'yo' }, { speaker: 'me', text: '  ' }] };
      }),
    );
  });
  after(() => ctx.server.close());
  const auth = { authorization: 'Bearer secret-token' };

  it('health is public and reports AI configured', async () => {
    const r = await fetch(`${ctx.base}/health`);
    const j = await r.json();
    assert.equal(r.status, 200);
    assert.equal(j.aiConfigured, true);
    assert.equal(j.authRequired, true);
  });

  it('rejects missing or wrong token', async () => {
    assert.equal((await post(ctx.base, '/v1/replies', { conversation: 'hi' })).status, 401);
    const bad = await post(ctx.base, '/v1/replies', { conversation: 'hi' }, { authorization: 'Bearer nope' });
    assert.equal(bad.status, 401);
  });

  it('returns de-duplicated replies and passes style/language into the prompt', async () => {
    const r = await post(
      ctx.base,
      '/v1/replies',
      { conversation: 'Them: ti kaneis?', style: 'flirty', replyLanguage: 'greeklish', customInstruction: 'more romantic' },
      auth,
    );
    const j = await r.json();
    assert.equal(r.status, 200);
    assert.deepEqual(j.replies, ['one', 'two', 'three']);
    assert.equal(j.detectedLanguage, 'el');
    assert.match(lastCall.user, /Flirty/);
    assert.match(lastCall.user, /Greeklish/);
    assert.match(lastCall.user, /more romantic/);
    assert.match(lastCall.user, /ti kaneis/);
  });

  it('validates input', async () => {
    assert.equal((await post(ctx.base, '/v1/replies', { conversation: '   ' }, auth)).status, 400);
    const big = await post(ctx.base, '/v1/replies', { conversation: 'x'.repeat(7000) }, auth);
    assert.equal(big.status, 413);
    assert.equal((await post(ctx.base, '/v1/tools', { tool: 'nope', text: 'a' }, auth)).status, 400);
  });

  it('runs tools and trims output', async () => {
    const r = await post(ctx.base, '/v1/tools', { tool: 'fix', text: 'helo wrld' }, auth);
    assert.deepEqual(await r.json(), { items: ['fixed text'] });
  });

  it('allows the starter tool without text', async () => {
    const r = await post(ctx.base, '/v1/tools', { tool: 'starter', text: '' }, auth);
    assert.equal(r.status, 200);
  });

  it('transcribes and normalises speakers', async () => {
    const r = await post(ctx.base, '/v1/transcribe', { imageBase64: 'QUJD', mimeType: 'image/jpeg' }, auth);
    const j = await r.json();
    assert.equal(r.status, 200);
    assert.deepEqual(j.turns, [
      { speaker: 'me', text: 'hi' },
      { speaker: 'them', text: 'yo' },
    ]);
    assert.equal((await post(ctx.base, '/v1/transcribe', { imageBase64: '***' }, auth)).status, 400);
  });

  it('returns JSON 404 for unknown routes', async () => {
    const r = await fetch(`${ctx.base}/nope`);
    assert.equal(r.status, 404);
  });
});

describe('API without an AI key', () => {
  let ctx;
  before(async () => {
    ctx = await start({ accessTokens: [] }, null);
  });
  after(() => ctx.server.close());

  it('reports aiConfigured=false and answers 503 ai_not_configured (no fake AI)', async () => {
    const h = await (await fetch(`${ctx.base}/health`)).json();
    assert.equal(h.aiConfigured, false);
    const r = await post(ctx.base, '/v1/replies', { conversation: 'hello' });
    assert.equal(r.status, 503);
    assert.equal((await r.json()).error, 'ai_not_configured');
  });
});

describe('rate limiting', () => {
  let ctx;
  before(async () => {
    ctx = await start({ accessTokens: [], rateLimitPerMin: 2 }, fakeProvider(async () => ({ items: ['ok'] })));
  });
  after(() => ctx.server.close());

  it('returns 429 after the limit', async () => {
    const body = { tool: 'fix', text: 'a' };
    assert.equal((await post(ctx.base, '/v1/tools', body)).status, 200);
    assert.equal((await post(ctx.base, '/v1/tools', body)).status, 200);
    const third = await post(ctx.base, '/v1/tools', body);
    assert.equal(third.status, 429);
    assert.equal((await third.json()).error, 'rate_limited');
  });
});

describe('prompts', () => {
  it('describes each reply language', () => {
    assert.match(languageDirective('el'), /Greek script/);
    assert.match(languageDirective('greeklish'), /Latin letters/);
    assert.match(languageDirective('auto'), /SAME language/);
  });
  it('includes previous replies so regeneration differs', () => {
    const u = buildReplyUser({
      conversation: 'Them: hey',
      style: 'smooth',
      replyLanguage: 'en',
      customInstruction: '',
      previousReplies: ['old reply'],
    });
    assert.match(u, /DIFFERENT/);
    assert.match(u, /old reply/);
  });
});

describe('anthropic provider', () => {
  it('forces a tool call and returns its input', async () => {
    let sent;
    const fakeFetch = async (_url, init) => {
      sent = JSON.parse(init.body);
      assert.equal(init.headers['x-api-key'], 'k');
      return new Response(
        JSON.stringify({ content: [{ type: 'tool_use', name: 'submit_replies', input: { replies: ['a'] } }] }),
        { status: 200 },
      );
    };
    const p = createAnthropicProvider({ apiKey: 'k', model: 'm', timeoutMs: 1000, fetchImpl: fakeFetch });
    const out = await p.generateJson({
      system: 's',
      user: 'u',
      schema: { type: 'object' },
      schemaName: 'submit_replies',
    });
    assert.deepEqual(out, { replies: ['a'] });
    assert.deepEqual(sent.tool_choice, { type: 'tool', name: 'submit_replies' });
  });

  it('maps a rejected key to ai_not_configured', async () => {
    const p = createAnthropicProvider({
      apiKey: 'bad',
      model: 'm',
      timeoutMs: 1000,
      fetchImpl: async () => new Response('{}', { status: 401 }),
    });
    await assert.rejects(
      () => p.generateJson({ system: 's', user: 'u', schema: {}, schemaName: 'x' }),
      (e) => e.status === 503 && e.code === 'ai_not_configured',
    );
  });
});
