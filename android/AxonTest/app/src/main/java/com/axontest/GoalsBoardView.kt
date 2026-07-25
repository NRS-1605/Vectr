package com.vectr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class GoalsBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    var goals: List<Goal> = emptyList(); var onGoalTapped: ((Goal) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private val cards = mutableMapOf<String, android.graphics.RectF>()
    private val titles = listOf(GoalPeriod.WEEKLY, GoalPeriod.MONTHLY, GoalPeriod.HALF_YEARLY, GoalPeriod.YEARLY)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); cards.clear(); val gap = 18f; val boxW = (width - gap * 3) / 2f; val boxH = (height - gap * 3) / 2f
        val boxes = titles.mapIndexed { index, period -> period to android.graphics.RectF(gap + (index % 2) * (boxW + gap), gap + (index / 2) * (boxH + gap), gap + (index % 2) * (boxW + gap) + boxW, gap + (index / 2) * (boxH + gap) + boxH) }.toMap()
        // Links first, so cards remain legible. Arrow direction always points to the parent goal.
        paint.color = Color.rgb(216, 161, 70); paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE
        goals.forEach { child -> child.parentId?.let { parentId -> val parent = goals.firstOrNull { it.id == parentId } ?: return@let; val from = boxes[child.period]!!; val to = boxes[parent.period]!!; arrow(canvas, from.centerX(), from.centerY(), to.centerX(), to.centerY()) } }
        boxes.forEach { (period, box) ->
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(32, 39, 48); canvas.drawRoundRect(box, 22f, 22f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.rgb(124, 95, 44); canvas.drawRoundRect(box, 22f, 22f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(243, 190, 82); paint.textSize = 15f; paint.isFakeBoldText = true; canvas.drawText("${period.label.uppercase()} GOALS", box.left + 14, box.top + 27, paint)
            var y = box.top + 48
            goals.filter { it.period == period }.take(4).forEach { goal ->
                val card = android.graphics.RectF(box.left + 10, y, box.right - 10, y + 35); cards[goal.id] = card
                paint.color = if (goal.checked) Color.rgb(47, 92, 73) else Color.rgb(53, 61, 72); canvas.drawRoundRect(card, 10f, 10f, paint)
                paint.color = if (goal.checked) Color.rgb(171, 232, 190) else Color.WHITE; paint.textSize = 12f; paint.isFakeBoldText = false
                canvas.drawText(if (goal.checked) "✓  ${goal.title}" else "○  ${goal.title}", card.left + 9, card.centerY() + 4, paint); y += 42
            }
        }
        // Draw a visible connection overlay after the cards; the arrowhead points at the parent timeframe.
        paint.color = Color.rgb(243, 190, 82); paint.strokeWidth = 2.5f; paint.style = Paint.Style.STROKE
        goals.forEach { child -> child.parentId?.let { parentId ->
            val parent = goals.firstOrNull { it.id == parentId } ?: return@let
            val from = cards[child.id]; val to = cards[parent.id]
            if (from != null && to != null) arrow(canvas, from.centerX(), from.centerY(), to.centerX(), to.centerY())
        } }
    }
    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) { canvas.drawLine(x1, y1, x2, y2, paint); val angle = kotlin.math.atan2(y2-y1, x2-x1); val path = Path().apply { moveTo(x2, y2); lineTo((x2 - 12*kotlin.math.cos(angle-0.45)).toFloat(), (y2 - 12*kotlin.math.sin(angle-0.45)).toFloat()); moveTo(x2, y2); lineTo((x2 - 12*kotlin.math.cos(angle+0.45)).toFloat(), (y2 - 12*kotlin.math.sin(angle+0.45)).toFloat()) }; canvas.drawPath(path, paint) }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean { if (event.action == android.view.MotionEvent.ACTION_UP) cards.entries.firstOrNull { it.value.contains(event.x, event.y) }?.let { hit -> goals.firstOrNull { it.id == hit.key }?.let { onGoalTapped?.invoke(it) } }; return true }
}
