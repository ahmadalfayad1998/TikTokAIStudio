package com.example.tiktokai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ArabicTtsEngine {
    data class VoiceChoice(
        val name:String,
        val locale:String,
        val quality:Int,
        val network:Boolean
    ) {
        val label:String
            get() = locale+" — "+name+(if(network) " (شبكي)" else "")
    }

    fun listArabicVoices(context:Context):List<VoiceChoice> {
        val ready=CountDownLatch(1)
        var status=TextToSpeech.ERROR
        var tts:TextToSpeech?=null
        tts=TextToSpeech(context.applicationContext) {
            status=it
            ready.countDown()
        }
        if(!ready.await(12,TimeUnit.SECONDS) || status!=TextToSpeech.SUCCESS) {
            tts.shutdown()
            return emptyList()
        }
        return try {
            tts.voices
                ?.filter { it.locale.language.equals("ar",ignoreCase=true) }
                ?.sortedByDescending { voiceScore(it) }
                ?.map {
                    VoiceChoice(
                        name=it.name,
                        locale=it.locale.toLanguageTag(),
                        quality=it.quality,
                        network=it.isNetworkConnectionRequired
                    )
                }
                ?: emptyList()
        } finally {
            tts.shutdown()
        }
    }

    fun previewVoice(context:Context,voiceName:String?,text:String="مرحبًا، هذا اختبار للصوت العربي في تيك توك أي آي ستوديو.") {
        Thread {
            val ready=CountDownLatch(1)
            var status=TextToSpeech.ERROR
            var tts:TextToSpeech?=null
            tts=TextToSpeech(context.applicationContext) {
                status=it
                ready.countDown()
            }
            if(!ready.await(12,TimeUnit.SECONDS) || status!=TextToSpeech.SUCCESS) {
                tts.shutdown()
                return@Thread
            }
            try {
                tts.setLanguage(Locale("ar","SA"))
                selectVoice(tts,voiceName)
                tts.setSpeechRate(0.93f)
                tts.setPitch(0.99f)
                tts.speak(text.take(400),TextToSpeech.QUEUE_FLUSH,null,"voice_preview")
                Thread.sleep(4500)
            } catch(_:Exception) {
            } finally {
                tts.stop()
                tts.shutdown()
            }
        }.start()
    }

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

        val preferred=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE)
            .getString("tts_voice_name","")
            .orEmpty()
            .ifBlank { null }
        selectVoice(tts,preferred)

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

    private fun selectVoice(tts:TextToSpeech,preferredName:String?) {
        runCatching {
            val arabic=tts.voices
                ?.filter { it.locale.language.equals("ar",ignoreCase=true) }
                .orEmpty()
            val preferred=preferredName?.let { wanted ->
                arabic.firstOrNull { it.name==wanted }
            }
            val chosen=preferred ?: arabic.maxByOrNull { voiceScore(it) }
            if(chosen!=null) tts.voice=chosen
        }
    }

    private fun voiceScore(voice:Voice):Int {
        val name=voice.name.lowercase(Locale.ROOT)
        val country=voice.locale.country.uppercase(Locale.ROOT)
        val maleHints=listOf(
            "male","hamed","laith","rami","taim","hamdan","fahed","bassel",
            "saleh","shakir","jamal","abdullah","moaz","omar","ali","hedi"
        )
        var score=voice.quality*4-voice.latency
        if(maleHints.any { name.contains(it) }) score+=2000
        if(country in setOf("SA","SY","LB","JO","AE","IQ","KW","QA","OM","YE")) score+=250
        if(!voice.isNetworkConnectionRequired) score+=40
        return score
    }
}
