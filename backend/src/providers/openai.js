import { HttpError } from '../validate.js';

const API = 'https://api.openai.com/v1/chat/completions';

export function createOpenAIProvider({ apiKey, model, timeoutMs, fetchImpl = fetch }) {
  async function generateJson({ system, user, image, schema, schemaName, maxTokens = 1024 }) {
    const userContent = [{ type: 'text', text: user }];
    if (image) {
      userContent.push({
        type: 'image_url',
        image_url: { url: `data:${image.mimeType};base64,${image.base64}` },
      });
    }
    const body = {
      model,
      temperature: 0.9,
      max_completion_tokens: maxTokens,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: userContent },
      ],
      response_format: {
        type: 'json_schema',
        json_schema: { name: schemaName, strict: true, schema },
      },
    };

    let lastErr;
    for (let attempt = 0; attempt < 2; attempt++) {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), timeoutMs);
      try {
        const res = await fetchImpl(API, {
          method: 'POST',
          headers: { 'content-type': 'application/json', authorization: `Bearer ${apiKey}` },
          body: JSON.stringify(body),
          signal: ctrl.signal,
        });
        if (res.status === 429 || res.status >= 500) {
          lastErr = new HttpError(res.status === 429 ? 429 : 502, res.status === 429 ? 'rate_limited' : 'upstream_error');
          await new Promise((r) => setTimeout(r, 600 * (attempt + 1)));
          continue;
        }
        if (res.status === 401 || res.status === 403) {
          throw new HttpError(503, 'ai_not_configured', 'The AI provider rejected the server API key');
        }
        if (!res.ok) throw new HttpError(502, 'upstream_error', `AI provider error ${res.status}`);
        const data = await res.json();
        const text = data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content;
        if (typeof text !== 'string') throw new HttpError(502, 'bad_ai_response', 'AI returned an unexpected format');
        try {
          return JSON.parse(text);
        } catch {
          throw new HttpError(502, 'bad_ai_response', 'AI returned invalid JSON');
        }
      } catch (e) {
        if (e instanceof HttpError) throw e;
        if (e && e.name === 'AbortError') throw new HttpError(504, 'ai_timeout', 'AI timed out');
        lastErr = new HttpError(502, 'upstream_error', 'Could not reach the AI provider');
      } finally {
        clearTimeout(timer);
      }
    }
    throw lastErr || new HttpError(502, 'upstream_error');
  }

  return { name: 'openai', model, generateJson };
}
