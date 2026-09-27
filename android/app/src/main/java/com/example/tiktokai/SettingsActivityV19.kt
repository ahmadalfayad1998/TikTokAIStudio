package com.example.tiktokai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
            hint=if(DeviceEnvironment.isEmulator()) "http://10.0.2.2:8765" else "اختياري: https://backend.example.com"
            setText(DeviceEnvironment.backendUrl(this@SettingsActivityV19))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val voiceState=TextView(this).apply {
            val selected=prefs.getString("tts_voice_name","").orEmpty()
            text=if(selected.isBlank()) "صوت الجهاز: تلقائي" else "صوت الجهاز: "+selected
            setTextColor(Color.LTGRAY)
            setPadding(0,18,0,8)
        }
        val chooseVoice=Button(this).apply { text="اختيار صوت عربي من الهاتف" }
        val previewVoice=Button(this).apply { text="تجربة الصوت المحلي" }

        val tiktokState=TextView(this).apply {
            val connected=!prefs.getString("tiktok_session","").isNullOrBlank()
            text=if(connected) "TikTok: مرتبط ✓" else "TikTok: غير مرتبط"
            setTextColor(if(connected) Color.rgb(94,234,212) else Color.LTGRAY)
            setPadding(0,18,0,18)
        }
        val save=Button(this).apply { text="حفظ الإعدادات" }
        val test=Button(this).apply { text="اختبار اتصال Backend" }
        val diagnostics=Button(this).apply { text="نسخ تقرير التشخيص" }
        val testStatus=TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setPadding(0,10,0,10)
            text=if(DeviceEnvironment.isEmulator())
                "10.0.2.2 يعمل فقط داخل محاكي Android."
            else
                "على الهاتف الحقيقي يمكنك ترك Backend فارغًا: الصور المحلية والفيديو يعملان بدون خادم. صور AI وTikTok الحقيقي يحتاجان Backend HTTPS."
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(label("Backend URL"))
        root.addView(backend)
        root.addView(test)
        root.addView(testStatus)
        root.addView(label("الصوت المحلي"))
        root.addView(voiceState)
        root.addView(chooseVoice)
        root.addView(previewVoice)
        root.addView(tiktokState)
        root.addView(save)
        root.addView(diagnostics)
        setContentView(root)

        save.setOnClickListener {
            val value=backend.text.toString().trim().trimEnd('/')
            prefs.edit().putString("backend",value).apply()
            Toast.makeText(this,"تم الحفظ",Toast.LENGTH_SHORT).show()
        }

        chooseVoice.setOnClickListener {
            chooseVoice.isEnabled=false
            voiceState.text="جاري قراءة الأصوات العربية المثبتة…"
            thread {
                val voices=ArabicTtsEngine.listArabicVoices(this)
                runOnUiThread {
                    chooseVoice.isEnabled=true
                    if(voices.isEmpty()) {
                        voiceState.text="لم يتم العثور على أصوات عربية إضافية في محرك الهاتف"
                        return@runOnUiThread
                    }
                    val labels=mutableListOf("تلقائي — اختيار أفضل صوت متاح")
                    labels.addAll(voices.map { it.label })
                    AlertDialog.Builder(this)
                        .setTitle("اختر صوتًا عربيًا")
                        .setItems(labels.toTypedArray()) { _,which ->
                            if(which==0) {
                                prefs.edit().remove("tts_voice_name").apply()
                                voiceState.text="صوت الجهاز: تلقائي"
                                ArabicTtsEngine.previewVoice(this,null)
                            } else {
                                val picked=voices[which-1]
                                prefs.edit().putString("tts_voice_name",picked.name).apply()
                                voiceState.text="صوت الجهاز: "+picked.name
                                ArabicTtsEngine.previewVoice(this,picked.name)
                            }
                        }
                        .setNegativeButton("إلغاء",null)
                        .show()
                }
            }
        }

        previewVoice.setOnClickListener {
            val selected=prefs.getString("tts_voice_name","").orEmpty().ifBlank { null }
            ArabicTtsEngine.previewVoice(this,selected)
            Toast.makeText(this,"يتم تشغيل معاينة الصوت",Toast.LENGTH_SHORT).show()
        }

        diagnostics.setOnClickListener {
            val report=AppDiagnostics.report(this)
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("TikTok AI Studio Diagnostics",report))
            Toast.makeText(this,"تم نسخ تقرير التشخيص",Toast.LENGTH_SHORT).show()
        }

        test.setOnClickListener {
            val value=backend.text.toString().trim().trimEnd('/')
            if(value.isBlank()) {
                testStatus.text="لا يوجد Backend. سيستخدم التطبيق الصور المحلية والوضع المحلي."
                return@setOnClickListener
            }
            if(!DeviceEnvironment.isEmulator() && value.contains("10.0.2.2")) {
                testStatus.text="10.0.2.2 خاص بمحاكي Android ولا يعمل على هاتف حقيقي."
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
