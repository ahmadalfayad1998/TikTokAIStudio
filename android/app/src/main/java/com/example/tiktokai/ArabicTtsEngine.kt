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
    fun synthesize(context:Context,text:String):File {
        val output=File(context.cacheDir,"tiktok_ai_voice.wav")
        if(output.exists()) output.delete()

        val ready=CountDownLatch(1)
        val done=CountDownLatch(1)
        var initStatus=TextToSpeech.ERROR
        var tts:TextToSpeech?=null

        tts=TextToSpeech(context.applicationContext) { status ->
            initStatus=status
            ready.countDown()
        }

        if(!ready.await(15,TimeUnit.SECONDS) || initStatus!=TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw IllegalStateException("تعذر تشغيل محرك الصوت في الهاتف")
        }

        val sa=tts.setLanguage(Locale("ar","SA"))
        val languageResult=if(
            sa==TextToSpeech.LANG_MISSING_DATA ||
            sa==TextToSpeech.LANG_NOT_SUPPORTED
        ) tts.setLanguage(Locale("ar")) else sa

        if(
            languageResult==TextToSpeech.LANG_MISSING_DATA ||
            languageResult==TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.shutdown()
            throw IllegalStateException("الصوت العربي غير متوفر في محرك TTS على الهاتف")
        }

        runCatching {
            val best=tts.voices
                ?.filter { it.locale.language.equals("ar",ignoreCase=true) }
                ?.sortedWith(
                    compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.latency }
                )
                ?.firstOrNull()
            if(best!=null) tts.voice=best
        }

        tts.setSpeechRate(0.93f)
        tts.setPitch(0.99f)

        val utteranceId="tiktok_ai_ar"
        var error:String?=null
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {}
            override fun onDone(id:String?) { done.countDown() }

            @Deprecated("Deprecated in Java")
            override fun onError(id:String?) {
                error="فشل إنشاء الصوت العربي"
                done.countDown()
            }

            override fun onError(id:String?,errorCode:Int) {
                error="فشل إنشاء الصوت العربي: "+errorCode
                done.countDown()
            }
        })

        val cleaned=text
            .replace("…","، ")
            .replace(Regex("\\s+")," ")
            .trim()
            .take(3500)

        val result=tts.synthesizeToFile(cleaned,Bundle(),output,utteranceId)
        if(result!=TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw IllegalStateException("لم يبدأ إنشاء الصوت")
        }

        if(!done.await(90,TimeUnit.SECONDS)) {
            tts.stop()
            tts.shutdown()
            throw IllegalStateException("انتهت مهلة إنشاء الصوت")
        }

        tts.shutdown()
        error?.let { throw IllegalStateException(it) }
        if(!output.exists() || output.length()==0L) {
            throw IllegalStateException("ملف الصوت الناتج فارغ")
        }
        return output
    }
}
