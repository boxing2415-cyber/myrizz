import { createApp, createProvider } from './app.js';
import { loadConfig } from './config.js';

const cfg = loadConfig();
const provider = createProvider(cfg);

if (!provider) {
  console.warn(
    `[myrizz] WARNING: no API key for provider "${cfg.provider}". /health works, but AI endpoints return 503 ai_not_configured until you set the key.`,
  );
}
if (cfg.accessTokens.length === 0) {
  console.warn('[myrizz] WARNING: APP_ACCESS_TOKENS is empty - anyone with the URL can spend your AI credits.');
}

createApp(cfg, provider).listen(cfg.port, () => {
  console.log(`[myrizz] listening on :${cfg.port} (provider=${provider ? provider.name : 'none'})`);
});
