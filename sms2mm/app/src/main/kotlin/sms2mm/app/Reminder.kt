package sms2mm.app

import android.Manifest
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Optional daily "you have N pending" reminder. Inexact repeating alarm (no exact-alarm permission),
 * rescheduled after a reboot. Posts nothing when there is nothing pending.
 */
object Reminder {
    private const val PREFS = "reminder"
    private const val CHANNEL = "reminder"
    private const val NOTIFICATION_ID = 2

    data class Settings(val enabled: Boolean, val hour: Int, val minute: Int)

    fun settings(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(p.getBoolean("enabled", false), p.getInt("hour", 21), p.getInt("minute", 0))
    }

    fun save(ctx: Context, s: Settings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled).putInt("hour", s.hour).putInt("minute", s.minute).apply()
        schedule(ctx)
    }

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context) {
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(ctx)
        alarms.cancel(intent)
        val s = settings(ctx)
        if (!s.enabled) return
        var next = LocalDate.now().atTime(LocalTime.of(s.hour, s.minute))
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, intent)
    }

    /** "3 pending · SAR 145.20 · INR 500.00", or null when nothing is pending. */
    fun pendingText(ctx: Context): String? {
        val open = Store.txns(ctx).filter { it.status.isOpen }
        if (open.isEmpty()) return null
        val sums = open.groupBy { it.currency }.entries.sortedBy { it.key }.joinToString(" · ") { (c, v) ->
            "$c " + v.fold(BigDecimal.ZERO) { a, t -> a + t.amountValue }.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        }
        return "${open.size} pending · $sums"
    }

    fun notify(ctx: Context) {
        val text = pendingText(ctx) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily pending reminder", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Enter today's transactions in Money Manager")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminder.schedule(context)
            else -> Reminder.notify(context)
        }
    }
}
