# Changelog

## 1.0.0-beta1

### Verified on a physical Android device
- App launches successfully.
- Offline test content generation works.
- Arabic Android TTS generates narration.
- H.264 vertical video rendering works using YUV420 ByteBuffer encoding.
- TTS PCM audio is transcoded to AAC.
- H.264 + AAC are muxed into a playable MP4.
- Final video plays with Arabic narration and on-screen Arabic text.

### Build-verified
- Professional dark workflow UI.
- Structured content editor.
- Manual multi-image scene selection.
- Ken Burns image movement, fades, and weighted scene timing.
- Optional automatic Stable Diffusion scene images through backend.
- Automatic project persistence and multi-project library.
- In-app video preview.
- Share/export final MP4.
- Custom adaptive launcher icon.
- Backend health testing.
- Encrypted server-side TikTok token storage and refresh.
- TikTok OAuth browser flow with opaque Android session id.
- Creator Info, explicit privacy/interaction controls, AIGC marking.
- Direct Post initialization, sequential upload, status polling, duration validation.
- Docker backend deployment.
- Debug/release network security separation.
- Optional signed Release APK CI pipeline.

### External configuration/runtime validation still required
- Production AI backend deployment.
- Automatic image provider deployment.
- TikTok Developer app configuration and approved scopes/products.
- Public HTTPS TikTok callback.
- TikTok audit for unrestricted Direct Post behavior.
- Production Android release keystore/signing.
