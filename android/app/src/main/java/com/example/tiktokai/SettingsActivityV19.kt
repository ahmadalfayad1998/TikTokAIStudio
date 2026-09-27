package com.example.tiktokai

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class SettingsActivityV19 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs=getSharedPreferences("app_settings", MODE_PRIVATE)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(40,40,40,40)
            setBackgroundColor(Color.rgb(11,13,18))
        }
        fun label(textValue:String)=TextView(this).apply {
            text=textValue
            textSize=16f
            setTextColor(Color.WHITE)
            setPadding(0,18,0,8)
        }

        val title=TextView(this).apply {
            text="الإعدادات"
            textSize=28f
            setTextColor(Color.WHITE)
        }
        val subtitle=TextView(this).apply {
            text="المفاتيح الحساسة تبقى على الخادم فقط"
            setTextColor(Color.LTGRAY)
        }
        val backend=EditText(this).apply {
            hint="https://backend.example.com"
            setText(prefs.getString("backend","http://10.0.2.2:8765"))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val tiktokState=TextView(this).apply {
            val connected=!prefs.getString("tiktok_session","").isNullOrBlank()
            text=if(connected) "TikTok: مرتبط ✓" else "TikTok: غير مرتبط"
            setTextColor(if(connected) Color.rgb(94,234,212) else Color.LTGRAY)
            setPadding(0,18,0,18)
        }
        val save=Button(this).apply { text="حفظ الإعدادات" }
        val test=Button(this).apply { text="اختبار اتصال Backend" }
        val testStatus=TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setPadding(0,10,0,10)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(label("Backend URL"))
        root.addView(backend)
        root.addView(test)
        root.addView(testStatus)
        root.addView(tiktokState)
        root.addView(save)
        setContentView(root)

        save.setOnClickListener {
            val value=backend.text.toString().trim().trimEnd('/')
            prefs.edit().putString("backend",value).apply()
            Toast.makeText(this,"تم الحفظ",Toast.LENGTH_SHORT).show()
        }

        test.setOnClickListener {
            val value=backend.text.toString().trim().trimEnd('/')
            if(value.isBlank()) {
                testStatus.text="اكتب Backend URL أولاً"
                return@setOnClickListener
            }
            test.isEnabled=false
            testStatus.text="جاري اختبار الاتصال…"
            thread {
                val ok=runCatching { BackendApiV16.health(value) }.getOrDefault(false)
                runOnUiThread {
                    test.isEnabled=true
                    testStatus.text=if(ok) "الخادم متصل ✓" else "تعذر الاتصال بالخادم"
                }
            }
        }
    }
}
