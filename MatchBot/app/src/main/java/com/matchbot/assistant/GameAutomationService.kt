package com.matchbot.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class GameAutomationService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val analyzer = VisionAnalyzer()
    private val captureBusy = AtomicBoolean(false)
    private var overlay: OverlayController? = null
    private var running = false
    private var generation = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this) { shouldRun ->
            if (shouldRun) startAutomation() else stopAutomation()
        }.also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = stopAutomation()

    override fun onDestroy() {
        stopAutomation()
        overlay?.destroy()
        overlay = null
        analyzerExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startAutomation() {
        if (running) return
        running = true
        generation++
        overlay?.setRunning(true)
        overlay?.updateStatus("يحلل الشاشة…")
        scheduleNext(100L, generation)
    }

    private fun stopAutomation() {
        running = false
        generation++
        captureBusy.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        overlay?.setRunning(false)
    }

    private fun scheduleNext(delayMs: Long, token: Long) {
        mainHandler.postDelayed({
            if (running && token == generation) captureAndAnalyze(token)
        }, delayMs)
    }

    private fun captureAndAnalyze(token: Long) {
        if (!running || token != generation) return
        if (!captureBusy.compareAndSet(false, true)) {
            scheduleNext(150L, token)
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            ContextCompat.getMainExecutor(this),
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()

                    if (bitmap == null) {
                        captureBusy.set(false)
                        overlay?.updateStatus("تعذر قراءة الشاشة")
                        scheduleNext(900L, token)
                        return
                    }

                    val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    val threshold = prefs.getFloat(MainActivity.KEY_CONFIDENCE, 0.965f)
                    val interval = prefs.getLong(MainActivity.KEY_INTERVAL_MS, 850L)

                    analyzerExecutor.execute {
                        val result = runCatching { analyzer.findTriple(bitmap, threshold) }.getOrNull()
                        bitmap.recycle()

                        mainHandler.post {
                            captureBusy.set(false)
                            if (!running || token != generation) return@post

                            if (result == null) {
                                overlay?.updateStatus("لا يوجد تطابق آمن")
                                scheduleNext(interval, token)
                            } else {
                                overlay?.updateStatus(
                                    "تطابق \${(result.confidence * 100).toInt()}% • \${result.candidateCount} مرشح"
                                )
                                tapTriple(result.points.map { it.x to it.y }) {
                                    if (running && token == generation) {
                                        scheduleNext(maxOf(interval, 700L), token)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    captureBusy.set(false)
                    overlay?.updateStatus("فشل التقاط الشاشة: $errorCode")
                    scheduleNext(1000L, token)
                }
            }
        )
    }

    private fun tapTriple(points: List<Pair<Float, Float>>, done: () -> Unit) {
        if (points.size != 3 || !running) {
            done()
            return
        }

        val builder = GestureDescription.Builder()
        points.forEachIndexed { index, (x, y) ->
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    index * 140L,
                    70L
                )
            )
        }

        dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    done()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    done()
                }
            },
            mainHandler
        )
    }
}
