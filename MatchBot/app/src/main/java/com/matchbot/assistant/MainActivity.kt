package com.matchbot.assistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openAccessibility = findViewById<Button>(R.id.openAccessibilityButton)
        val serviceStatus = findViewById<TextView>(R.id.serviceStatus)
        val confidenceSeek = findViewById<SeekBar>(R.id.confidenceSeek)
        val confidenceValue = findViewById<TextView>(R.id.confidenceValue)
        val intervalSeek = findViewById<SeekBar>(R.id.intervalSeek)
        val intervalValue = findViewById<TextView>(R.id.intervalValue)

        val savedConfidence = prefs.getFloat(KEY_CONFIDENCE, 0.965f)
        confidenceSeek.progress = (((savedConfidence - 0.90f) / 0.001f).roundToInt()).coerceIn(0, 90)
        updateConfidenceLabel(confidenceValue, savedConfidence)

        val savedInterval = prefs.getLong(KEY_INTERVAL_MS, 850L).toInt()
        intervalSeek.progress = (savedInterval - 500).coerceIn(0, 1000)
        intervalValue.text = "\${savedInterval} ms"

        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        confidenceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.90f + progress * 0.001f
                updateConfidenceLabel(confidenceValue, value)
                if (fromUser) prefs.edit().putFloat(KEY_CONFIDENCE, value).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        intervalSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 500L + progress
                intervalValue.text = "$value ms"
                if (fromUser) prefs.edit().putLong(KEY_INTERVAL_MS, value).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        serviceStatus.text = if (isServiceEnabled(this)) {
            "الحالة: خدمة MatchBot مفعّلة"
        } else {
            "الحالة: فعّل خدمة MatchBot من إعدادات إمكانية الوصول"
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.serviceStatus)?.text = if (isServiceEnabled(this)) {
            "الحالة: خدمة MatchBot مفعّلة"
        } else {
            "الحالة: فعّل خدمة MatchBot من إعدادات إمكانية الوصول"
        }
    }

    private fun updateConfidenceLabel(view: TextView, value: Float) {
        view.text = String.format("%.1f%%", value * 100f)
    }

    private fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    companion object {
        const val PREFS = "matchbot_prefs"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_INTERVAL_MS = "interval_ms"
    }
}
