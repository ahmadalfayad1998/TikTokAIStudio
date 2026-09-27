package com.example.tiktokai

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * V19 user-facing controller.
 * Keeps the normal workflow on one screen. Technical settings are separate.
 * TikTok tokens are never entered by the user and never stored here.
 */
class EasyMainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var topic: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        topic=findViewById(R.id.topic)

        findViewById<Button>(R.id.generateBtn).setOnClickListener {
            if(topic.text.toString().trim().isEmpty()) status.text="اكتب فكرة الفيديو أولاً"
            else status.text="جاري إنشاء المحتوى…"
            // Existing AI/backend integration is connected here in the next build step.
        }
        findViewById<Button>(R.id.renderBtn).setOnClickListener {
            status.text="جاري تجهيز الفيديو…"
        }
        findViewById<Button>(R.id.previewBtn).setOnClickListener {
            status.text="افتح آخر فيديو تم إنشاؤه"
        }
        findViewById<Button>(R.id.connectBtn).setOnClickListener {
            status.text="فتح تسجيل TikTok الرسمي…"
            // OpenSDK Login Kit entry point; requires real Developer client key.
        }
        findViewById<Button>(R.id.publishBtn).setOnClickListener {
            status.text="سيتم التحقق من Creator Info قبل النشر"
        }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivityV19::class.java))
        }
    }
}
