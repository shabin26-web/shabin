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

    fun summary(t: PendingTxn) = (if (t.duplicateOf != null) "⚠ Possible duplicate · " else "") + when (t.type) {
        TxnType.TRANSFER -> "Transfer ${t.account} → ${t.toAccount}"
        else -> "${t.category.label()} · ${t.account}" + if (t.needsReview) " · choose a category" else ""
    }

    /** Everything to type into Money Manager, in its field order. */
    fun details(t: PendingTxn): String = buildString {
        append(t.type.name.lowercase().replaceFirstChar { it.uppercase() })
        if (t.type == TxnType.TRANSFER) append(" · ${t.account} → ${t.toAccount}")
        else append(" · ${t.account} · ${if (t.needsReview) "pick a category" else t.category.label()}")
        append("\n${t.currency} ${t.amount}")
        t.note?.let { append(" · note: $it") }
        t.description?.let { append(" · $it") }
        append("\n${t.time.toLocalDate()} ${t.time.toLocalTime()}")
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
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(
            (if (t.duplicateOf != null) "⚠ Possible duplicate: same amount and merchant as an SMS a few minutes earlier\n" else "") + details(t),
        ))
        builder.addAction(0, "Recorded ✔", ActionReceiver.intent(ctx, ActionReceiver.RECORDED, t))
        builder.addAction(0, if (t.needsReview) "Pick category" else "Edit", edit)
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
            .setContentText("Enter it in Money Manager yourself. Send the masked SMS so this layout can be added.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(UNPARSED_ID, n)
    }

    fun cancel(ctx: Context, t: PendingTxn) = NotificationManagerCompat.from(ctx).cancel(t.notificationId)
}
