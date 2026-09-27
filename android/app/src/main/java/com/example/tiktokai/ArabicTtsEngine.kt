package com.example.tiktokai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ArabicTtsEngine {
    fun synthesize(context: Context, text: String): File {
        val output = File(context.cacheDir, "tiktok_ai_voice.wav")
        if (output.exists()) output.delete()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        var tts: TextToSpeech? = null

        tts = TextToSpeech(context.applicationContext) { status ->
            initStatus = status
            ready.countDown()
        }
        if (!ready.await(15, TimeUnit.SECONDS) || initStatus != TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw IllegalStateException("تعذر تشغيل محرك الصوت في الهاتف")
        }

        val languageResult = tts.setLanguage(Locale("ar"))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.shutdown()
            throw IllegalStateException("الصوت العربي غير متوفر في محرك TTS على الهاتف")
        }

        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)
        val utteranceId = "tiktok_ai_ar"
        var error: String? = null
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.countDown() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                error = "فشل إنشاء الصوت العربي"
                done.countDown()
            }
            override fun onError(id: String?, errorCode: Int) {
                error = "فشل إنشاء الصوت العربي: $errorCode"
                done.countDown()
            }
        })

        val params = Bundle()
        val result = tts.synthesizeToFile(text.take(3500), params, output, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw IllegalStateException("لم يبدأ إنشاء الصوت")
        }
        if (!done.await(90, TimeUnit.SECONDS)) {
            tts.stop(); tts.shutdown()
            throw IllegalStateException("انتهت مهلة إنشاء الصوت")
        }
        tts.shutdown()
        error?.let { throw IllegalStateException(it) }
        if (!output.exists() || output.length() == 0L) {
            throw IllegalStateException("ملف الصوت الناتج فارغ")
        }
        return output
    }
}
