package com.example.tiktokai

import android.content.Context
import android.os.Build

object DeviceEnvironment {
    fun isEmulator():Boolean {
        val fingerprint=Build.FINGERPRINT.lowercase()
        val model=Build.MODEL.lowercase()
        val product=Build.PRODUCT.lowercase()
        val hardware=Build.HARDWARE.lowercase()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }

    fun backendUrl(context:Context):String {
        val prefs=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE)
        val stored=prefs.getString("backend",null)?.trim()?.trimEnd('/').orEmpty()
        if(!isEmulator() && stored.contains("10.0.2.2")) {
            prefs.edit().putString("backend","").apply()
            return ""
        }
        if(stored.isNotBlank()) return stored
        return if(isEmulator()) "http://10.0.2.2:8765" else ""
    }
}
