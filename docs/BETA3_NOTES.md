# TikTok AI Studio — Beta 3 engineering notes

## What changed

- Scene planning now separates narration from short on-screen captions.
- On-screen Arabic captions are limited for mobile readability.
- Visual scene duration is weighted by narration length.
- Local visual mode is explicitly a motion-design fallback, not AI image generation.
- Technology scenes use six distinct visual compositions.
- Energy/blackout topics have dedicated scripts and visual templates.
- Other local themes vary composition by scene role.
- H.264/storage/image accessibility preflight runs before rendering.
- Render and publishing failures are recorded in a bounded diagnostics log.
- Settings can copy a diagnostics report for support.
- Backend visual generation supports cached Stable Diffusion WebUI or Pexels providers.
- Backend credentials remain server-side.
- CI runs unit tests, Android lint, Debug build, and unsigned Release build.

## Quality gates

A build is not considered test-ready unless:
1. Android unit tests pass.
2. Android lint passes.
3. Debug APK compiles.
4. Unsigned Release build compiles.
5. Backend compilation and tests pass.

## External requirements

AI-generated/stock photographic scenes require a configured HTTPS backend and an image provider.
TikTok publishing requires a TikTok Developer configuration and server-side credentials.
Physical-device runtime rendering must still be verified on target hardware before production release.
