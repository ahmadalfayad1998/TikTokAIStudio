# Security Policy

## Secrets

Never commit or embed:

- TikTok client secret
- TikTok access or refresh tokens
- AI provider API keys
- TOKEN_ENCRYPTION_KEY
- Android release keystore or passwords

The Android client stores only an opaque backend session identifier after TikTok authorization. TikTok tokens are encrypted at rest by the backend.

## Production

Use HTTPS for the backend. Keep .env outside source control. Use a dedicated production database/volume with backups and access controls. Configure Android release signing through CI secrets, not repository files.

## Reporting

Rotate credentials immediately if a secret is ever exposed and invalidate affected sessions.
