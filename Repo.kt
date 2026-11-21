package com.skillforge.data

import android.content.Context
import com.skillforge.notify.Scheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class Repo(val ctx: Context) {
    val dao = Db.get(ctx).dao()

    suspend fun saveGoal(g: Goal): Long {
        val id = if (g.id == 0L) dao.insert(g) else { dao.update(g); g.id }
        Scheduler.schedule(ctx, g.copy(id = id))
        return id
    }
    suspend fun deleteGoal(id: Long) { dao.deleteGoal(id); dao.deleteCompletions(id); Scheduler.cancel(ctx, id) }
    suspend fun setDone(g: Goal, key: String, done: Boolean) {
        if (done) dao.addCompletion(Completion(g.id, key, System.currentTimeMillis())) else dao.removeCompletion(g.id, key)
    }
    suspend fun rescheduleAll() { dao.goalsNow().forEach { Scheduler.schedule(ctx, it) } }

    /** One-time task: change its date. Recurring: skip this occurrence and create a one-time copy on [day]. */
    suspend fun move(g: Goal, day: LocalDate) {
        if (g.frequency == "ONCE") saveGoal(g.copy(startDay = day.toEpochDay(), deadlineDay = day.toEpochDay()))
        else {
            val t = LocalDate.now()
            dao.addCompletion(Completion(g.id, Recur.key(g, t) ?: Recur.currentKey(g, t), 0, true))
            saveGoal(g.copy(id = 0, frequency = "ONCE", startDay = day.toEpochDay(), deadlineDay = day.toEpochDay(), customDays = 0))
        }
    }

    private fun days(mask: Int) = JSONArray().also { a -> for (i in 0..6) if (((mask shr i) and 1) == 1) a.put(i) }

    /** Real data snapshot sent to the backend with every chat request, so the AI never has to guess. */
    suspend fun contextJson(): JSONObject {
        val goals = dao.goalsNow(); val m = Marks.of(dao.completionsNow()); val t = LocalDate.now()
        val (cur, best) = Stats.streak(goals, m, t)
        val ga = JSONArray()
        for (g in goals) ga.put(JSONObject()
            .put("id", g.id).put("title", g.title).put("description", g.description.take(200)).put("category", g.category)
            .put("priority", listOf("low", "medium", "high")[g.priority.coerceIn(0, 2)])
            .put("frequency", g.frequency.lowercase()).put("custom_days", days(g.customDays)).put("est_minutes", g.estMinutes)
            .put("reminder_time", g.reminderMin?.let { "%02d:%02d".format(it / 60, it % 60) } ?: JSONObject.NULL)
            .put("start_date", LocalDate.ofEpochDay(g.startDay).toString())
            .put("deadline", g.deadlineDay?.let { LocalDate.ofEpochDay(it).toString() } ?: JSONObject.NULL)
            .put("done_this_period", "${g.id}|${Recur.currentKey(g, t)}" in m.done)
            .put("due_today", Recur.key(g, t) != null))
        val mem = JSONObject(); dao.memoryNow().forEach { mem.put(it.k, it.v) }
        return JSONObject().put("today", t.toString()).put("weekday", t.dayOfWeek.name).put("timezone", ZoneId.systemDefault().id)
            .put("goals", ga)
            .put("stats", JSONObject()
                .put("today_pct", Stats.pct(Stats.occ(goals, m, t, t)))
                .put("week_pct", Stats.pct(Stats.occ(goals, m, Recur.monday(t), t)))
                .put("month_pct", Stats.pct(Stats.occ(goals, m, t.withDayOfMonth(1), t)))
                .put("streak", cur).put("longest_streak", best))
            .put("memory", mem)
    }

    /** Executes a chatbot proposal after the user has confirmed it. Returns a human-readable result. */
    suspend fun apply(tool: String, a: JSONObject): String {
        val today = LocalDate.now()
        fun d(k: String): Long? = a.optString(k, "").takeIf { it.isNotBlank() }?.let { try { LocalDate.parse(it).toEpochDay() } catch (e: Exception) { null } }
        fun mins(k: String): Int? {
            val r = Regex("(\\d{1,2}):(\\d{2})").find(a.optString(k, "")) ?: return null
            return r.groupValues[1].toInt() * 60 + r.groupValues[2].toInt()
        }
        fun pri(s: String) = when (s.lowercase()) { "low" -> 0; "high" -> 2; else -> 1 }
        fun mask(): Int { val arr = a.optJSONArray("custom_days") ?: return 0; var m = 0; for (i in 0 until arr.length()) m = m or (1 shl arr.getInt(i)); return m }
        fun freq(s: String) = s.uppercase().let { if (it in setOf("DAILY", "WEEKLY", "MONTHLY", "CUSTOM", "ONCE")) it else "ONCE" }
        fun at(g: Goal) = g.reminderMin?.let { " — reminder at %02d:%02d".format(it / 60, it % 60) } ?: ""
        return when (tool) {
            "create_goal" -> {
                val g = Goal(title = a.getString("title"), description = a.optString("description"),
                    category = a.optString("category", "Personal Development"), priority = pri(a.optString("priority")),
                    startDay = d("start_date") ?: today.toEpochDay(), deadlineDay = d("deadline"),
                    frequency = freq(a.optString("frequency", "once")), customDays = mask(),
                    estMinutes = a.optInt("est_minutes", 30), reminderMin = mins("reminder_time"))
                saveGoal(g); "✅ Created “${g.title}”" + at(g)
            }
            "update_goal" -> {
                var n = dao.goal(a.getLong("goal_id")) ?: return "⚠️ Goal not found"
                if (a.has("title")) n = n.copy(title = a.getString("title"))
                if (a.has("description")) n = n.copy(description = a.getString("description"))
                if (a.has("category")) n = n.copy(category = a.getString("category"))
                if (a.has("priority")) n = n.copy(priority = pri(a.getString("priority")))
                if (a.has("frequency")) n = n.copy(frequency = freq(a.getString("frequency")))
                if (a.has("custom_days")) n = n.copy(customDays = mask())
                if (a.has("est_minutes")) n = n.copy(estMinutes = a.getInt("est_minutes"))
                if (a.has("reminder_time")) n = n.copy(reminderMin = mins("reminder_time"))
                if (a.has("deadline")) n = n.copy(deadlineDay = d("deadline"))
                if (a.has("start_date")) d("start_date")?.let { n = n.copy(startDay = it) }
                saveGoal(n); "✏️ Updated “${n.title}”" + at(n)
            }
            "complete_goal" -> {
                val g = dao.goal(a.getLong("goal_id")) ?: return "⚠️ Goal not found"
                setDone(g, Recur.currentKey(g, today), true); "✅ Marked “${g.title}” as done"
            }
            "reschedule_goal" -> {
                val g = dao.goal(a.getLong("goal_id")) ?: return "⚠️ Goal not found"
                val nd = d("new_date")?.let { LocalDate.ofEpochDay(it) } ?: return "⚠️ Missing new date"
                move(g, nd); "📅 Moved “${g.title}” to $nd"
            }
            "delete_goal" -> {
                val g = dao.goal(a.getLong("goal_id")) ?: return "⚠️ Goal not found"
                deleteGoal(g.id); "🗑️ Deleted “${g.title}”"
            }
            "save_memory" -> { dao.putMem(Mem(a.getString("key").take(60), a.getString("value").take(400))); "🧠 Saved to memory" }
            else -> "⚠️ Unknown action"
        }
    }
}
