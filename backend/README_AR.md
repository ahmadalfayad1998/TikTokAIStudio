# TikTokAIStudio Backend

الخادم مسؤول عن توليد المحتوى وإبقاء مفاتيح API خارج تطبيق Android.

## تشغيل محلي مجاني
1. ثبّت Ollama وشغّل نموذج `llama3.2` أو غيّر `OLLAMA_MODEL`.
2. `pip install -r requirements.txt`
3. `uvicorn app:app --host 0.0.0.0 --port 8765`
4. افتح `/health` للتأكد من التشغيل.

لا تضع `OPENAI_API_KEY` أو TikTok Client Secret داخل Android أو GitHub.
