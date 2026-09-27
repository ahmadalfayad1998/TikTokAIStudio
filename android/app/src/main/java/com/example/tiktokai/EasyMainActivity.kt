package com.example.tiktokai

import android.content.Intent
import android.os.Bundle
import android.net.Uri
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
        val script=result.text.toString().trim()
        if(script.isEmpty()) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        status.text="جاري إنشاء فيديو عمودي 720×1280…"
        thread {
            try {
                val file=SimpleVideoRenderer.render(this, script)
                lastVideo=file
                runOnUiThread { status.text="تم إنشاء الفيديو ✓ — اضغط معاينة" }
            } catch(ex:Exception) {
                runOnUiThread { status.text="فشل إنشاء الفيديو: "+(ex.message ?: "خطأ غير معروف") }
            }
        }
    }

    private fun previewVideo() {
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_latest.mp4")
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
        thread {
            try {
                val c=BackendApiV16.generateContent(backend,idea)
                val text=buildString {
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
                runOnUiThread {
                    result.text=if(text.isBlank()) "وصل رد من الخادم لكنه لا يحتوي محتوى صالحًا." else text
                    status.text="تم إنشاء المحتوى ✓"
                }
            } catch(ex:Exception) {
                runOnUiThread {
                    status.text="تعذر الاتصال بخادم الذكاء الاصطناعي. راجع Backend URL من الإعدادات."
                    result.text=ex.message ?: "خطأ غير معروف"
                }
            }
        }
    }
}
