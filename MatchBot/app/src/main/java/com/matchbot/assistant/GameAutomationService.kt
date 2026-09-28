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

    override fun onInterrupt() {
        stopAutomation()
    }

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
        overlay?.updateStatus("يحلل لوحة اللعب…")
        scheduleNext(120L, generation)
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
                    val threshold = prefs.getFloat(MainActivity.KEY_CONFIDENCE_V2, 0.92f)
                    val interval = prefs.getLong(MainActivity.KEY_INTERVAL_MS, 850L)

                    analyzerExecutor.execute {
                        val result = runCatching {
                            analyzer.findTriple(bitmap, threshold)
                        }.getOrNull()

                        bitmap.recycle()

                        mainHandler.post {
                            captureBusy.set(false)
                            if (!running || token != generation) return@post

                            if (result == null) {
                                overlay?.updateStatus("أبحث عن 3 قطع متطابقة…")
                                scheduleNext(interval, token)
                            } else {
                                val pct = (result.confidence * 100f).toInt()
                                overlay?.updateStatus(
                                    "تطابق $pct% • ${result.candidateCount} مرشح"
                                )

                                tapSequence(
                                    points = result.points.map { it.x to it.y },
                                    token = token
                                ) {
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

    private fun tapSequence(
        points: List<Pair<Float, Float>>,
        token: Long,
        done: () -> Unit
    ) {
        if (points.size != 3 || !running || token != generation) {
            done()
            return
        }

        fun tapAt(index: Int) {
            if (!running || token != generation) {
                done()
                return
            }

            if (index >= points.size) {
                done()
                return
            }

            val (x, y) = points[index]
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
                .build()

            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        mainHandler.postDelayed({ tapAt(index + 1) }, 130L)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        overlay?.updateStatus("أعيد محاولة الضغط…")
                        mainHandler.postDelayed({ tapAt(index + 1) }, 180L)
                    }
                },
                mainHandler
            )

            if (!accepted) {
                overlay?.updateStatus("تعذر إرسال الضغط")
                mainHandler.postDelayed({ tapAt(index + 1) }, 180L)
            }
        }

        tapAt(0)
    }
}
