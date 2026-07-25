package com.vectr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class GoalPeriod(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly"), HALF_YEARLY("Half yearly"), YEARLY("Yearly") }
data class Goal(val id: String, val title: String, val period: GoalPeriod, val parentId: String?, val checked: Boolean)

object GoalsRepository {
    private const val KEY = "goals"
    fun all(context: Context): List<Goal> = LocalSyncStore.readArray(context, KEY).let { array ->
        (0 until array.length()).map { index -> array.getJSONObject(index).let { item -> Goal(item.getString("id"), item.getString("title"), GoalPeriod.valueOf(item.getString("period")), item.optString("parentId").takeIf(String::isNotBlank), item.optBoolean("checked")) } }
    }
    fun add(context: Context, title: String, period: GoalPeriod, parentId: String?) = save(context, all(context) + Goal(UUID.randomUUID().toString(), title, period, parentId, false))
    fun toggle(context: Context, id: String) = save(context, all(context).map { if (it.id == id) it.copy(checked = !it.checked) else it })
    private fun save(context: Context, goals: List<Goal>) = LocalSyncStore.writeArray(context, KEY, JSONArray().apply {
        goals.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("period", it.period.name).put("parentId", it.parentId).put("checked", it.checked)) }
    })
}
