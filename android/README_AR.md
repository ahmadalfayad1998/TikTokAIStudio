
# TikTok AI Studio PRO V12 — Android

V12 improves the Android-side renderer without removing the older architecture.

## New
- Animated Ken-Burns-style movement for selected images.
- Better Arabic caption readability with a translucent lower panel.
- Scene title + caption rendered into the video.
- Progress marker.
- Cover timestamp selector before TikTok Direct Post.
- Fixed the missing `script` view reference that existed in the V11 source.
- TikTok creator information is re-queried before publishing.

## Build
The archive is source code. This environment does not contain an Android SDK/Gradle toolchain, so no claim is made that an APK was compiled here.

Open the `android/` directory in Android Studio and let Gradle sync. If your local Android Studio has the required SDK/platform installed, build a debug APK.

## TikTok
Direct Post still requires a registered TikTok developer application and authorization for `video.publish`. TikTok's current docs require querying creator info before rendering the export UI and require privacy selection to match the returned options.

Official docs:
https://developers.tiktok.com/docs/en/content-posting-api-get-started
https://developers.tiktok.com/docs/en/content-posting-api-reference-direct-post
https://developers.tiktok.com/docs/en/content-posting-api-reference-query-creator-info
