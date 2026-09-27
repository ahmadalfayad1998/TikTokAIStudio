package com.example.tiktokai

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class EasyMainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var topic: EditText
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        topic=findViewById(R.id.topic)
        result=findViewById(R.id.result)

        findViewById<Button>(R.id.generateBtn).setOnClickListener { generate() }
        findViewById<Button>(R.id.renderBtn).setOnClickListener {
            status.text="محرك إنشاء الفيديو هو المرحلة التالية"
        }
        findViewById<Button>(R.id.previewBtn).setOnClickListener {
            status.text="لا يوجد فيديو بعد — أنشئ الفيديو أولاً"
        }
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
