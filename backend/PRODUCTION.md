# Production deployment

The Android app never contains AI provider keys, TikTok client secrets, access tokens, or refresh tokens.

Required server environment:

- AI_PROVIDER and the selected AI provider settings.
- TIKTOK_CLIENT_KEY and TIKTOK_CLIENT_SECRET.
- TIKTOK_REDIRECT_URI, which must be the public HTTPS URL ending in /oauth/tiktok/callback and must match TikTok Developer configuration.
- TIKTOK_APP_RETURN_URI=tiktokai://oauth
- TOKEN_ENCRYPTION_KEY generated with Fernet.
- TOKEN_DB=/app/state/tokens.sqlite3 when using Docker.

Run:

docker compose up -d --build

Verify:

GET /health

For local Ollama, point OLLAMA_URL at an Ollama instance reachable by the backend container. Never commit .env.
