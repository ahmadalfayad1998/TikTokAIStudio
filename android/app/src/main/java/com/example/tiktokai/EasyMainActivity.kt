package com.example.tiktokai

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import java.io.File
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class EasyMainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var topic: EditText
    private lateinit var result: TextView
    private var lastVideo: File? = null
    private var lastContent: BackendApiV16.GeneratedContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        topic=findViewById(R.id.topic)
        result=findViewById(R.id.result)

        findViewById<Button>(R.id.generateBtn).setOnClickListener { generate() }
        findViewById<Button>(R.id.renderBtn).setOnClickListener { renderVideo() }
        findViewById<Button>(R.id.previewBtn).setOnClickListener { previewVideo() }
        findViewById<Button>(R.id.connectBtn).setOnClickListener {
            status.text="ربط TikTok يحتاج Client Key معتمد من TikTok Developer"
        }
        findViewById<Button>(R.id.publishBtn).setOnClickListener {
            status.text="النشر سيتفعّل بعد تسجيل TikTok والتحقق من Creator Info"
        }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivityV19::class.java))
        }
    }

    private fun renderVideo() {
        val content=lastContent
        if(content==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val narration=(listOf(content.hook)+content.scenes).filter { it.isNotBlank() }.joinToString(". ")
        if(narration.isBlank()) {
            status.text="المحتوى لا يحتوي نصًا صالحًا للصوت"
            return
        }
        status.text="جاري إنشاء الصوت العربي والفيديو العمودي…"
        thread {
            try {
                val voice=ArabicTtsEngine.synthesize(this, narration)
                val mmr=MediaMetadataRetriever()
                mmr.setDataSource(voice.absolutePath)
                val duration=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 8000L
                mmr.release()
                val silent=SimpleVideoRenderer.render(this, content.scenes.ifEmpty { listOf(content.hook) }, duration+500L)
                val finalFile=File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
                val file=AudioVideoMuxer.mux(silent,voice,finalFile)
                lastVideo=file
                runOnUiThread { status.text="تم إنشاء الفيديو ✓ — اضغط معاينة" }
            } catch(ex:Exception) {
                runOnUiThread { status.text="فشل إنشاء الفيديو: "+(ex.message ?: "خطأ غير معروف") }
            }
        }
    }

    private fun previewVideo() {
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(!file.exists()) {
            status.text="لا يوجد فيديو بعد — أنشئ الفيديو أولاً"
            return
        }
        val uri: Uri=FileProvider.getUriForFile(this, packageName+".provider", file)
        val intent=Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,"video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch(ex:Exception) {
            status.text="لا يوجد مشغل فيديو متاح على الجهاز"
        }
    }

    private fun displayContent(c:BackendApiV16.GeneratedContent):String = buildString {
        if(c.title.isNotBlank()) append("العنوان: ").append(c.title).append("\n\n")
        if(c.hook.isNotBlank()) append("الافتتاحية: ").append(c.hook).append("\n\n")
        if(c.scenes.isNotEmpty()) {
            append("المشاهد:\n")
            c.scenes.forEachIndexed { i,s -> append(i+1).append(". ").append(s).append("\n") }
            append("\n")
        }
        if(c.description.isNotBlank()) append("الوصف: ").append(c.description).append("\n\n")
        if(c.hashtags.isNotEmpty()) append(c.hashtags.joinToString(" "))
    }

    private fun offlineDemoContent(idea:String)=BackendApiV16.GeneratedContent(
        title=idea,
        hook="تخيل أن هذا حدث فجأة… ماذا ستكون أول ردة فعل لك؟",
        description="فيديو تجريبي تم إنشاؤه محليًا لاختبار الصوت والمشاهد قبل اتصال خادم الذكاء الاصطناعي.",
        hashtags=listOf("#ذكاء_اصطناعي","#معلومات","#TikTok"),
        scenes=listOf(
            "نبدأ بالسؤال: $idea",
            "في اللحظات الأولى سيحاول الجميع فهم ما الذي يحدث.",
            "بعدها تبدأ التأثيرات بالظهور في حياتنا اليومية بشكل واضح.",
            "بعض النتائج ستكون متوقعة، لكن نتائج أخرى قد تفاجئنا.",
            "والآن دورك: ماذا تتوقع أن يحدث؟"
        )
    )

    private fun generate() {
        val idea=topic.text.toString().trim()
        if(idea.isEmpty()) {
            status.text="اكتب فكرة الفيديو أولاً"
            return
        }
        val prefs=getSharedPreferences("app_settings", MODE_PRIVATE)
        val backend=prefs.getString("backend","http://10.0.2.2:8765") ?: return
        status.text="جاري إنشاء المحتوى بالذكاء الاصطناعي…"
        result.text=""
        if(backend.contains("10.0.2.2")) {
            val c=offlineDemoContent(idea)
            lastContent=c
            result.text=displayContent(c)
            status.text="وضع الاختبار المحلي ✓"
            return
        }
        thread {
            try {
                val c=BackendApiV16.generateContent(backend,idea)
                lastContent=c
                val text=displayContent(c)
                runOnUiThread {
                    result.text=if(text.isBlank()) "وصل رد من الخادم لكنه لا يحتوي محتوى صالحًا." else text
                    status.text="تم إنشاء المحتوى ✓"
                }
            } catch(ex:Exception) {
                val c=offlineDemoContent(idea)
                lastContent=c
                val text=displayContent(c)
                runOnUiThread {
                    result.text=text
                    status.text="وضع الاختبار المحلي ✓ — الخادم غير متصل"
                }
            }
        }
    }
}
