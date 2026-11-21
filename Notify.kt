package com.skillforge.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skillforge.MainActivity
import com.skillforge.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Notifier {
    const val CH = "reminders"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun channels(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Goal reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "Reminders for your goals and tasks" })
    }

    private fun allowed(c: Context) =
        Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(c: Context, g: Goal, key: String) {
        channels(c)
        val open = PendingIntent.getActivity(c, g.id.toInt() * 3,
            Intent(c, MainActivity::class.java).putExtra("goalId", g.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), FLAGS)
        fun act(a: String, rc: Int) = PendingIntent.getBroadcast(c, rc,
            Intent(c, ActionReceiver::class.java).setAction(a).putExtra("goalId", g.id).putExtra("key", key), FLAGS)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(g.title)
            .setContentText(g.description.ifBlank { "Time for your ${g.category} goal" })
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Done", act("DONE", g.id.toInt() * 3 + 1))
            .addAction(0, "Snooze 10 min", act("SNOOZE", g.id.toInt() * 3 + 2))
            .build()
        if (allowed(c)) try { NotificationManagerCompat.from(c).notify(g.id.toInt(), n) } catch (e: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun test(c: Context): Boolean {
        channels(c)
        if (!allowed(c)) return false
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("SkillForge test").setContentText("Notifications are working.").setAutoCancel(true).build()
        NotificationManagerCompat.from(c).notify(999_999, n); return true
    }
}

object Scheduler {
    private const val SNOOZE = 1_000_000
    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    /** Next local date-time (strictly in the future) at which this goal's reminder should fire. */
    fun nextTrigger(g: Goal, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val m = g.reminderMin ?: return null
        val t = LocalTime.of(m / 60, m % 60)
        var d = now.toLocalDate()
        repeat(400) {
            if (Recur.key(g, d) != null) { val dt = LocalDateTime.of(d, t); if (dt > now) return dt }
            d = d.plusDays(1)
        }
        return null
    }

    private fun pi(c: Context, rc: Int, goalId: Long, snooze: Boolean) = PendingIntent.getBroadcast(c, rc,
        Intent(c, AlarmReceiver::class.java).putExtra("goalId", goalId).putExtra("snooze", snooze),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun canExact(c: Context) = Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    private fun setAt(c: Context, rc: Int, goalId: Long, snooze: Boolean, ms: Long) {
        val a = am(c); val p = pi(c, rc, goalId, snooze)
        try {
            if (canExact(c)) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
            else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)   // inexact fallback if exact-alarm permission is denied
        } catch (e: SecurityException) { a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p) }
    }

    fun schedule(c: Context, g: Goal) {
        cancel(c, g.id)
        if (g.archived) return
        val dt = nextTrigger(g) ?: return
        setAt(c, g.id.toInt(), g.id, false, dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    fun cancel(c: Context, id: Long) { am(c).cancel(pi(c, id.toInt(), id, false)); am(c).cancel(pi(c, id.toInt() + SNOOZE, id, true)) }
    fun snooze(c: Context, goalId: Long, minutes: Int) = setAt(c, goalId.toInt() + SNOOZE, goalId, true, System.currentTimeMillis() + minutes * 60_000L)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("goalId", 0); val snooze = i.getBooleanExtra("snooze", false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Db.get(c).dao(); val g = dao.goal(id)
                if (g != null && !g.archived) {
                    val k = if (snooze) Recur.currentKey(g, LocalDate.now()) else Recur.key(g, LocalDate.now())
                    val done = k != null && dao.completionsNow().any { it.goalId == id && it.periodKey == k }
                    if (k != null && !done) Notifier.show(c, g, k)
                    if (!snooze) Scheduler.schedule(c, g)   // chain the next occurrence
                }
            } finally { pending.finish() }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("goalId", 0); val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (i.action) {
                    "DONE" -> i.getStringExtra("key")?.let { Db.get(c).dao().addCompletion(Completion(id, it, System.currentTimeMillis())) }
                    "SNOOZE" -> Scheduler.snooze(c, id, 10)
                }
                NotificationManagerCompat.from(c).cancel(id.toInt())
            } finally { pending.finish() }
        }
    }
}

/** Restores alarms after reboot, app update, timezone change, clock change, or exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { Repo(c).rescheduleAll() } finally { pending.finish() } }
    }
}

/** Periodic safety net (WorkManager) in case an OEM battery manager dropped alarms. */
class RescheduleWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result { Repo(applicationContext).rescheduleAll(); return Result.success() }
}
