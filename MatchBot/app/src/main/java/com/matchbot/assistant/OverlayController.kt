package com.matchbot.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class OverlayController(
    private val service: AccessibilityService,
    private val onToggle: (Boolean) -> Unit
) {
    private val windowManager =
        service.getSystemService(
            AccessibilityService.WINDOW_SERVICE
        ) as WindowManager

    private var root: LinearLayout? = null
    private var button: Button? = null
    private var status: TextView? = null
    private var running = false

    private val params =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity =
                Gravity.TOP or
                    Gravity.END
            x = dp(8)
            y = dp(70)
        }

    fun show() {
        if (root != null) return

        val container =
            LinearLayout(service).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(7),
                    dp(5),
                    dp(7),
                    dp(5)
                )

                background =
                    GradientDrawable().apply {
                        cornerRadius =
                            dp(12)
                                .toFloat()

                        setColor(
                            0xCC1E1E1E.toInt()
                        )
                    }
            }

        val state =
            TextView(service).apply {
                text = "MatchBot جاهز"

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                textSize = 10f
                maxLines = 2
                maxWidth = dp(170)
            }

        val toggle =
            Button(service).apply {
                text = "ابدأ"
                minWidth = dp(76)
                minHeight = dp(42)
                textSize = 13f

                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    0
                )

                setOnClickListener {
                    setRunning(!running)
                    onToggle(running)
                }
            }

        container.addView(state)
        container.addView(toggle)

        installDrag(container)

        root = container
        button = toggle
        status = state

        windowManager.addView(
            container,
            params
        )
    }

    fun setRunning(value: Boolean) {
        running = value
        setCaptureVisible(true)

        button?.text =
            if (value) {
                "إيقاف"
            } else {
                "ابدأ"
            }

        if (!value) {
            status?.text =
                "MatchBot متوقف"
        }
    }

    fun updateStatus(text: String) {
        status?.text = text
    }

    fun setCaptureVisible(visible: Boolean) {
        root?.visibility =
            if (visible) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
    }

    fun destroy() {
        root?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        root = null
        button = null
        status = null
    }

    private fun installDrag(view: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx =
                        event.rawX -
                            downX
                    val dy =
                        event.rawY -
                            downY

                    if (
                        kotlin.math.abs(dx) >
                        dp(8) ||
                        kotlin.math.abs(dy) >
                        dp(8)
                    ) {
                        dragging = true

                        params.x =
                            (startX - dx)
                                .roundToInt()
                                .coerceAtLeast(0)

                        params.y =
                            (startY + dy)
                                .roundToInt()
                                .coerceAtLeast(0)

                        root?.let {
                            runCatching {
                                windowManager
                                    .updateViewLayout(
                                        it,
                                        params
                                    )
                            }
                        }

                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL ->
                    dragging

                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        (
            value *
                service.resources
                    .displayMetrics
                    .density
            )
            .roundToInt()
}
