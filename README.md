# TikTokAIStudio

Android short-video creation studio with Arabic-first AI content, narration, vertical video rendering, scene images, project persistence, export, and TikTok Content Posting integration.

## Architecture

- **Android app**: idea input, structured script, manual or automatic scene images, Arabic Android TTS, H.264 vertical rendering, AAC audio, MP4 muxing, in-app preview, project persistence, export/share, TikTok OAuth return handling and Direct Post UX.
- **Backend**: FastAPI, text AI provider abstraction, optional Stable Diffusion WebUI visual provider, encrypted TikTok token storage, OAuth flow, Creator Info, Direct Post upload/status, Docker deployment.
- **CI**: GitHub Actions builds Debug APK on Android changes and validates backend changes. Optional signed Release APK is supported through GitHub Secrets.

## Security

No AI API key, TikTok client secret, access token, refresh token, keystore password, or encryption key belongs in the Android APK or repository.

## Current external requirements

Real AI generation requires a reachable backend and configured AI provider. Automatic image generation requires the optional image provider. Real TikTok publishing requires a TikTok Developer app, approved scopes/products, public HTTPS callback, and configured backend secrets.

## Local Android testing

The Debug build can use local HTTP endpoints. Release builds disable cleartext traffic and should use HTTPS.

## TikTok

The integration queries Creator Info immediately before posting, presents available privacy choices and interaction settings, asks for explicit user consent, marks AI-generated content, initializes FILE_UPLOAD, uploads sequential chunks, and polls publish status.
