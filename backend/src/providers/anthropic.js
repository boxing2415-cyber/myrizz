import { HttpError } from '../validate.js';

const API = 'https://api.anthropic.com/v1/messages';

// Forces a structured answer by requiring the model to call a single tool whose input is our schema.
export function createAnthropicProvider({ apiKey, model, timeoutMs, fetchImpl = fetch }) {
  async function generateJson({ system, user, image, schema, schemaName, maxTokens = 1024 }) {
    const content = [];
    if (image) {
      content.push({
        type: 'image',
        source: { type: 'base64', media_type: image.mimeType, data: image.base64 },
      });
    }
    content.push({ type: 'text', text: user });

    const body = {
      model,
      max_tokens: maxTokens,
      system,
      messages: [{ role: 'user', content }],
      tools: [
        {
          name: schemaName,
          description: 'Return the final structured result.',
          input_schema: schema,
        },
      ],
      tool_choice: { type: 'tool', name: schemaName },
    };

    let lastErr;
    for (let attempt = 0; attempt < 2; attempt++) {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), timeoutMs);
      try {
        const res = await fetchImpl(API, {
          method: 'POST',
          headers: {
            'content-type': 'application/json',
            'x-api-key': apiKey,
            'anthropic-version': '2023-06-01',
          },
          body: JSON.stringify(body),
          signal: ctrl.signal,
        });
        if (res.status === 429 || res.status === 529 || res.status >= 500) {
          lastErr = new HttpError(res.status === 429 ? 429 : 502, res.status === 429 ? 'rate_limited' : 'upstream_error');
          await new Promise((r) => setTimeout(r, 600 * (attempt + 1)));
          continue;
        }
        if (res.status === 401 || res.status === 403) {
          throw new HttpError(503, 'ai_not_configured', 'The AI provider rejected the server API key');
        }
         if (!res.ok) {
             const detail = await res.text().catch(() => '');
             console.error(`Anthropic rejected the request: ${res.status} ${detail.slice(0, 400)}`);
             throw new HttpError(502, 'upstream_error', `AI provider error ${res.status}`);
        }
        const data = await res.json();
        const block = (data.content || []).find((b) => b.type === 'tool_use');
        if (!block || typeof block.input !== 'object') {
          throw new HttpError(502, 'bad_ai_response', 'AI returned an unexpected format');
        }
        return block.input;
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

  return { name: 'anthropic', model, generateJson };
}
