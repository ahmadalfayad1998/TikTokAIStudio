# Release checklist

- Latest Android GitHub Actions build succeeds.
- Latest backend CI succeeds.
- Test on at least one physical Android device.
- Test create content, image selection, automatic visuals when configured, Arabic TTS, video render, AAC mux, in-app preview, share/export, project restore.
- Configure production HTTPS Backend URL.
- Configure TOKEN_ENCRYPTION_KEY and TikTok server secrets.
- Register TikTok callback URL and required products/scopes.
- Confirm Creator Info and Direct Post flow on a test TikTok account.
- Complete TikTok audit before expecting non-private Direct Post behavior.
- Add production Android signing secrets to GitHub Actions.
- Verify release signing certificate fingerprints in TikTok Developer settings.
- Review privacy policy, terms, data retention, and deletion behavior before store submission.
