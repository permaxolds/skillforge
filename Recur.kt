package com.skillforge.data

import java.time.LocalDate
import java.time.YearMonth

object Recur {
    fun monday(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    /** Returns the period key if [g] is due on [d], else null. Weekly goals are due on Monday, monthly on the 1st. */
    fun key(g: Goal, d: LocalDate): String? {
        val s = LocalDate.ofEpochDay(g.startDay)
        if (g.frequency != "ONCE" && g.deadlineDay != null && d.toEpochDay() > g.deadlineDay) return null
        return when (g.frequency) {
            "DAILY" -> if (d >= s) d.toString() else null
            "CUSTOM" -> if (d >= s && ((g.customDays shr (d.dayOfWeek.value - 1)) and 1) == 1) d.toString() else null
            "WEEKLY" -> if (d == monday(d) && d >= monday(s)) d.toString() else null
            "MONTHLY" -> if (d.dayOfMonth == 1 && YearMonth.from(d) >= YearMonth.from(s)) YearMonth.from(d).toString() else null
            else -> if (d == LocalDate.ofEpochDay(g.deadlineDay ?: g.startDay)) d.toString() else null
        }
    }

    /** Key of the period that contains [d] (used when completing a weekly/monthly goal on any day). */
    fun currentKey(g: Goal, d: LocalDate): String = when (g.frequency) {
        "WEEKLY" -> monday(d).toString()
        "MONTHLY" -> YearMonth.from(d).toString()
        "ONCE" -> LocalDate.ofEpochDay(g.deadlineDay ?: g.startDay).toString()
        else -> d.toString()
    }
}

data class Occ(val g: Goal, val d: LocalDate, val k: String, val done: Boolean)

class Marks(val done: Set<String>, val skip: Set<String>) {
    companion object {
        fun of(c: List<Completion>) = Marks(
            c.filter { !it.skipped }.map { "${it.goalId}|${it.periodKey}" }.toSet(),
            c.filter { it.skipped }.map { "${it.goalId}|${it.periodKey}" }.toSet()
        )
    }
}

object Stats {
    fun occ(goals: List<Goal>, m: Marks, from: LocalDate, to: LocalDate): List<Occ> {
        val r = ArrayList<Occ>(); var d = from
        while (!d.isAfter(to)) {
            for (g in goals) {
                val k = Recur.key(g, d) ?: continue
                val id = "${g.id}|$k"
                if (id in m.skip) continue
                r.add(Occ(g, d, k, id in m.done))
            }
            d = d.plusDays(1)
        }
        return r
    }
    fun pct(l: List<Occ>) = if (l.isEmpty()) 0 else 100 * l.count { it.done } / l.size
    private fun dayOk(goals: List<Goal>, m: Marks, d: LocalDate) = occ(goals, m, d, d).any { it.done }
    /** (current streak, longest streak in the last year). A day counts if at least one task was completed. */
    fun streak(goals: List<Goal>, m: Marks, today: LocalDate): Pair<Int, Int> {
        var cur = 0; var d = if (dayOk(goals, m, today)) today else today.minusDays(1)
        while (dayOk(goals, m, d) && cur < 400) { cur++; d = d.minusDays(1) }
        var best = 0; var run = 0
        for (i in 364 downTo 0) { if (dayOk(goals, m, today.minusDays(i.toLong()))) { run++; best = maxOf(best, run) } else run = 0 }
        return cur to maxOf(best, cur)
    }
}
