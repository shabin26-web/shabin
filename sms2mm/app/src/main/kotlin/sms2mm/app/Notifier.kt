package sms2mm.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sms2mm.core.TxnType

object Notifier {
    private const val CHANNEL = "transactions"
    private const val UNPARSED_ID = 1

    fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Bank transactions", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "One notification per bank SMS transaction, with Add / Edit / Ignore"
                },
            )
        }
    }

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun title(t: PendingTxn) = "${t.currency} ${t.amount} · ${t.note ?: t.merchant ?: t.bank}"

    fun summary(t: PendingTxn) = when (t.type) {
        TxnType.TRANSFER -> "Transfer ${t.account} → ${t.toAccount}"
        else -> "${t.category.label()} · ${t.account}" + if (t.needsReview) " · choose a category" else ""
    }

    fun show(ctx: Context, t: PendingTxn) {
        if (!allowed(ctx)) return
        ensureChannel(ctx)
        val edit = PendingIntent.getActivity(
            ctx, t.notificationId,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_EDIT, t.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title(t))
            .setContentText(summary(t))
            .setContentIntent(edit)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // Uncategorised (e.g. UPI to a person): Edit first, then Add from the edit screen.
        if (!t.needsReview) builder.addAction(0, "Add", ActionReceiver.intent(ctx, ActionReceiver.ADD, t))
        builder.addAction(0, if (t.needsReview) "Choose category" else "Edit", edit)
        builder.addAction(0, "Ignore", ActionReceiver.intent(ctx, ActionReceiver.IGNORE, t))
        NotificationManagerCompat.from(ctx).notify(t.notificationId, builder.build())
    }

    fun showUnparsed(ctx: Context, bank: String) {
        if (!allowed(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, UNPARSED_ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$bank SMS not recognised")
            .setContentText("Add it to Money Manager by hand, and send the masked SMS so the layout can be added.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(UNPARSED_ID, n)
    }

    fun cancel(ctx: Context, t: PendingTxn) = NotificationManagerCompat.from(ctx).cancel(t.notificationId)
}
