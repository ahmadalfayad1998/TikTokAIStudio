package com.example.tiktokai

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivityV19 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs=getSharedPreferences("app_settings", MODE_PRIVATE)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(40,40,40,40)
        }
        val title=TextView(this).apply { text="إعدادات متقدمة"; textSize=24f }
        val backend=EditText(this).apply {
            hint="Backend URL"
            setText(prefs.getString("backend","http://10.0.2.2:8765"))
        }
        val save=Button(this).apply { text="حفظ" }
        root.addView(title); root.addView(backend); root.addView(save)
        setContentView(root)
        save.setOnClickListener {
            prefs.edit().putString("backend",backend.text.toString().trim()).apply()
            Toast.makeText(this,"تم الحفظ",Toast.LENGTH_SHORT).show()
        }
    }
}
