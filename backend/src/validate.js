import { REPLY_LANGUAGES, STYLES, TOOLS } from './prompts.js';

export class HttpError extends Error {
  constructor(status, code, message) {
    super(message || code);
    this.status = status;
    this.code = code;
  }
}

function str(v) {
  return typeof v === 'string' ? v : '';
}

// Strip control characters (keep \n and \t) and normalise line endings.
export function clean(s) {
  return str(s)
    .replace(/\r\n?/g, '\n')
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '')
    .trim();
}

function common(body, cfg) {
  const replyLanguage = REPLY_LANGUAGES.includes(body.replyLanguage) ? body.replyLanguage : 'auto';
  const customInstruction = clean(body.customInstruction).slice(0, 300);
  return { replyLanguage, customInstruction, maxChars: cfg.maxInputChars };
}

export function validateReplies(body, cfg) {
  if (!body || typeof body !== 'object') throw new HttpError(400, 'bad_request', 'JSON body required');
  const { replyLanguage, customInstruction, maxChars } = common(body, cfg);
  const conversation = clean(body.conversation);
  if (!conversation) throw new HttpError(400, 'empty_input', 'conversation is required');
  if (conversation.length > maxChars) {
    throw new HttpError(413, 'input_too_long', `conversation exceeds ${maxChars} characters`);
  }
  const style = Object.hasOwn(STYLES, body.style) ? body.style : 'smooth';
  const previousReplies = Array.isArray(body.previousReplies)
    ? body.previousReplies.map((r) => clean(r).slice(0, 500)).filter(Boolean).slice(0, 6)
    : [];
  return { conversation, style, replyLanguage, customInstruction, previousReplies };
}

export function validateTool(body, cfg) {
  if (!body || typeof body !== 'object') throw new HttpError(400, 'bad_request', 'JSON body required');
  const { replyLanguage, customInstruction, maxChars } = common(body, cfg);
  if (!Object.hasOwn(TOOLS, body.tool)) throw new HttpError(400, 'unknown_tool', 'unknown tool');
  const text = clean(body.text);
  if (!text && body.tool !== 'starter') throw new HttpError(400, 'empty_input', 'text is required');
  if (text.length > maxChars) {
    throw new HttpError(413, 'input_too_long', `text exceeds ${maxChars} characters`);
  }
  const style = Object.hasOwn(STYLES, body.style) ? body.style : undefined;
  return { tool: body.tool, text, style, replyLanguage, customInstruction };
}

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export function validateTranscribe(body, cfg) {
  if (!body || typeof body !== 'object') throw new HttpError(400, 'bad_request', 'JSON body required');
  const mimeType = IMAGE_TYPES.includes(body.mimeType) ? body.mimeType : 'image/jpeg';
  const imageBase64 = str(body.imageBase64).replace(/^data:[^,]+,/, '').replace(/\s+/g, '');
  if (!imageBase64) throw new HttpError(400, 'empty_input', 'imageBase64 is required');
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(imageBase64)) throw new HttpError(400, 'bad_image', 'invalid base64');
  const approxBytes = Math.floor((imageBase64.length * 3) / 4);
  if (approxBytes > cfg.maxImageBytes) throw new HttpError(413, 'image_too_large', 'image too large');
  return { imageBase64, mimeType };
}

// ---- Provider output checking -------------------------------------------------

function cleanItems(arr) {
  if (!Array.isArray(arr)) return [];
  const seen = new Set();
  const out = [];
  for (const it of arr) {
    const t = clean(typeof it === 'string' ? it : '');
    const key = t.toLowerCase();
    if (t && !seen.has(key)) {
      seen.add(key);
      out.push(t.slice(0, 1000));
    }
  }
  return out;
}

export function checkReplies(raw) {
  const replies = cleanItems(raw && raw.replies).slice(0, 3);
  if (replies.length === 0) throw new HttpError(502, 'bad_ai_response', 'AI returned no replies');
  return {
    replies,
    detectedLanguage: clean(raw.detectedLanguage).slice(0, 20) || null,
    tone: clean(raw.tone).slice(0, 120) || null,
  };
}

export function checkTool(raw) {
  const items = cleanItems(raw && raw.items).slice(0, 3);
  if (items.length === 0) throw new HttpError(502, 'bad_ai_response', 'AI returned no result');
  return { items };
}

export function checkTranscribe(raw) {
  const turns = (Array.isArray(raw && raw.turns) ? raw.turns : [])
    .map((t) => ({
      speaker: t && t.speaker === 'me' ? 'me' : 'them',
      text: clean(t && t.text).slice(0, 1000),
    }))
    .filter((t) => t.text)
    .slice(0, 80);
  if (turns.length === 0) throw new HttpError(422, 'no_text_found', 'No conversation text found in the image');
  return { turns };
}
