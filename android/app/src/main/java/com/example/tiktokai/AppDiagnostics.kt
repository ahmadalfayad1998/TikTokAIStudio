package com.example.tiktokai

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.StatFs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDiagnostics {
    data class Preflight(
        val ok:Boolean,
        val message:String,
        val readableImages:List<Uri>,
        val freeBytes:Long
    )

    private fun logFile(context:Context):File =
        File(context.filesDir,"diagnostics.log")

    fun logError(context:Context,stage:String,error:Throwable) {
        runCatching {
            val file=logFile(context)
            if(file.exists() && file.length()>512_000L) {
                val tail=file.readLines().takeLast(120)
                file.writeText(tail.joinToString("\n") + "\n")
            }
            val stamp=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())
            val detail=(error.message ?: error.javaClass.simpleName)
                .replace("\n"," ")
                .take(600)
            file.appendText(stamp+" | "+stage+" | "+error.javaClass.simpleName+" | "+detail+"\n")
        }
    }

    fun renderPreflight(context:Context,images:List<Uri>):Preflight {
        if(!hasAvcEncoder()) {
            return Preflight(false,"الجهاز لا يوفّر مشفّر H.264 متوافقًا",emptyList(),0L)
        }

        val dir=context.getExternalFilesDir(null)
            ?: return Preflight(false,"تعذر الوصول إلى مجلد ملفات التطبيق",emptyList(),0L)

        val free=runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)
        if(free in 1 until MIN_FREE_BYTES) {
            return Preflight(false,"المساحة الحرة غير كافية لإنشاء الفيديو",emptyList(),free)
        }

        val readable=images.filter { isReadable(context,it) }
        return Preflight(true,"جاهز",readable,free)
    }

    fun hasAvcEncoder():Boolean = runCatching {
        val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,720,1280)
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)!=null
    }.getOrDefault(false)

    fun report(context:Context):String {
        val prefs=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE)
        val backend=DeviceEnvironment.backendUrl(context).ifBlank { "غير مضبوط" }
        val tiktok=if(prefs.getString("tiktok_session","").isNullOrBlank()) "غير مرتبط" else "مرتبط"
        val dir=context.getExternalFilesDir(null)
        val free=dir?.let { runCatching { StatFs(it.absolutePath).availableBytes }.getOrDefault(0L) } ?: 0L
        val logs=runCatching { logFile(context).readLines().takeLast(20).joinToString("\n") }.getOrDefault("")

        return buildString {
            appendLine("TikTok AI Studio Diagnostics")
            appendLine("Device: "+Build.MANUFACTURER+" "+Build.MODEL)
            appendLine("Android SDK: "+Build.VERSION.SDK_INT)
            appendLine("Emulator: "+DeviceEnvironment.isEmulator())
            appendLine("H.264 encoder: "+hasAvcEncoder())
            appendLine("Free app storage MB: "+(free/1024/1024))
            appendLine("Backend: "+backend)
            appendLine("TikTok: "+tiktok)
            if(logs.isNotBlank()) {
                appendLine()
                appendLine("Recent errors:")
                append(logs)
            }
        }
    }

    private fun isReadable(context:Context,uri:Uri):Boolean {
        return runCatching {
            when(uri.scheme) {
                "file" -> uri.path?.let { File(it).exists() && File(it).canRead() } == true
                "content" -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.read()
                    }
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private const val MIN_FREE_BYTES=80L*1024L*1024L
}
