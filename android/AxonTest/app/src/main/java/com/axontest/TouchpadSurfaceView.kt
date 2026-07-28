package com.vectr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/** A familiar physical-trackpad layout: one pad, a ruler strip, and a split click bar. */
class TouchpadSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var commandSender: TouchpadCommandSender? = null
    var onReconnect: (() -> Unit)? = null
    var connected: Boolean = false
        set(value) { field = value; cancelGesture(); invalidate() }

    var sensitivity = 1.5f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = RectF()
    private val reconnectBounds = RectF()
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var activeTick = -1
    private var lastTickAt = 0L

    private enum class Mode { NONE, POINTER, SCROLL, LEFT_CLICK, RIGHT_CLICK }

    private val clickHeight get() = 62f * resources.displayMetrics.density
    private val stripWidth get() = 36f * resources.displayMetrics.density
    private val tickGap get() = 22f * resources.displayMetrics.density
    private val radius get() = 10f * resources.displayMetrics.density
    private val surfaceColor get() = Color.rgb(33, 11, 16)
    private val raisedColor get() = Color.rgb(24, 10, 14)
    private val borderColor get() = Color.argb(72, 242, 232, 220)
    private val lineColor get() = Color.rgb(163, 146, 126)
    private val accentColor get() = Color.rgb(229, 72, 77)
    private val textColor get() = Color.rgb(242, 232, 220)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pad.set(.5f, .5f, width - .5f, height - .5f)
        paint.style = Paint.Style.FILL; paint.color = surfaceColor; canvas.drawRoundRect(pad, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = borderColor; canvas.drawRoundRect(pad, radius, radius, paint)

        val clickTop = height - clickHeight
        val stripLeft = width - stripWidth
        // Single dark ruler strip, with visible pale ticks. It deliberately ends above the click bar.
        paint.style = Paint.Style.FILL; paint.color = raisedColor; canvas.drawRoundRect(RectF(stripLeft, 0f, width.toFloat(), clickTop), radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.color = borderColor; canvas.drawLine(stripLeft, 0f, stripLeft, clickTop, paint)
        drawTicks(canvas, clickTop)

        // A real touchpad click bar: only the top rule and centre split are visible—no labels or boxes.
        paint.color = borderColor; canvas.drawLine(0f, clickTop, width.toFloat(), clickTop, paint)
        canvas.drawLine(width / 2f, clickTop, width / 2f, height.toFloat(), paint)
        if (!connected) drawReconnectOverlay(canvas)
    }

    private fun drawTicks(canvas: Canvas, scrollBottom: Float) {
        val count = (scrollBottom / tickGap).toInt().coerceAtLeast(1)
        paint.strokeWidth = 1.5f * resources.displayMetrics.density
        for (index in 0..count) {
            val y = index * tickGap
            val selected = index == activeTick
            paint.color = if (selected) accentColor else lineColor
            val length = if (selected) 24f else 16f
            canvas.drawLine(width - length * resources.displayMetrics.density, y, width - 7f * resources.displayMetrics.density, y, paint)
        }
    }

    private fun drawReconnectOverlay(canvas: Canvas) {
        paint.style = Paint.Style.FILL; paint.color = Color.argb(230, 24, 10, 14); canvas.drawRoundRect(pad, radius, radius, paint)
        paint.color = textColor; paint.textSize = 18f * resources.displayMetrics.scaledDensity; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Reconnecting…", width / 2f, height / 2f - 18f * resources.displayMetrics.density, paint)
        val buttonWidth = 142f * resources.displayMetrics.density; val buttonHeight = 48f * resources.displayMetrics.density
        reconnectBounds.set(width / 2f - buttonWidth / 2f, height / 2f, width / 2f + buttonWidth / 2f, height / 2f + buttonHeight)
        paint.color = accentColor; canvas.drawRoundRect(reconnectBounds, radius, radius, paint)
        paint.color = textColor; paint.textSize = 13f * resources.displayMetrics.scaledDensity
        canvas.drawText("RECONNECT", reconnectBounds.centerX(), reconnectBounds.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!connected) {
            if (event.actionMasked == MotionEvent.ACTION_UP && reconnectBounds.contains(event.x, event.y)) onReconnect?.invoke()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP -> end()
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun begin(event: MotionEvent) {
        lastX = event.x; lastY = event.y; moved = false
        val clickTop = height - clickHeight
        mode = when {
            event.y >= clickTop && event.x < width / 2f -> Mode.LEFT_CLICK
            event.y >= clickTop -> Mode.RIGHT_CLICK
            event.x >= width - stripWidth -> { updateTick(event.y, false); Mode.SCROLL }
            else -> Mode.POINTER
        }
    }

    private fun move(event: MotionEvent) {
        val dx = event.x - lastX; val dy = event.y - lastY
        if (abs(dx) + abs(dy) > 3f) moved = true
        when (mode) {
            Mode.POINTER -> commandSender?.sendMove(dx * sensitivity, dy * sensitivity)
            Mode.SCROLL -> updateTick(event.y, true)
            else -> Unit
        }
        lastX = event.x; lastY = event.y
    }

    private fun end() {
        when (mode) {
            Mode.LEFT_CLICK -> if (!moved) click(ClickButton.LEFT)
            Mode.RIGHT_CLICK -> if (!moved) click(ClickButton.RIGHT)
            Mode.POINTER -> if (!moved) click(ClickButton.LEFT)
            else -> Unit
        }
        cancelGesture()
    }

    private fun updateTick(y: Float, dispatch: Boolean) {
        val index = (y.coerceIn(0f, (height - clickHeight).coerceAtLeast(0f)) / tickGap).roundToInt()
        if (index == activeTick) return
        val previous = activeTick; activeTick = index; invalidate()
        val now = System.currentTimeMillis()
        if (dispatch && previous >= 0 && now - lastTickAt >= TICK_THROTTLE_MS) {
            haptic(TICK_DURATION_MS)
            commandSender?.sendScroll(if (index > previous) SCROLL_STEP else -SCROLL_STEP)
            lastTickAt = now
        }
    }

    private fun click(button: ClickButton) { haptic(CLICK_DURATION_MS); commandSender?.sendClick(button) }
    private fun haptic(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator?.vibrate(durationMs)
    }
    private fun cancelGesture() { mode = Mode.NONE; activeTick = -1; invalidate() }

    private companion object {
        const val SCROLL_STEP = 64f
        const val TICK_THROTTLE_MS = 45L
        const val TICK_DURATION_MS = 10L
        const val CLICK_DURATION_MS = 15L
    }
}
