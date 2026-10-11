package sms2mm.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Handles the Recorded / Ignore buttons on a transaction notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.action) {
            RECORDED -> setStatus(context, id, Status.ENTERED)
            IGNORE -> setStatus(context, id, Status.IGNORED)
        }
    }

    companion object {
        const val RECORDED = "sms2mm.app.RECORDED"
        const val IGNORE = "sms2mm.app.IGNORE"
        private const val EXTRA_ID = "id"

        private val MM_PACKAGES = listOf("com.realbyteapps.moneymanagerfree", "com.realbyteapps.moneymanager")

        fun intent(ctx: Context, action: String, t: PendingTxn): PendingIntent = PendingIntent.getBroadcast(
            ctx, (action + t.id).hashCode(),
            Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, t.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Pending → Recorded / Ignored (or back to Pending); clears the notification once it's handled. */
        fun setStatus(ctx: Context, id: String, status: Status) {
            val txn = Store.txn(ctx, id) ?: return
            Store.update(ctx, id) { it.copy(status = status, failReason = null) }
            if (status == Status.PENDING) Notifier.show(ctx, txn.copy(status = status)) else Notifier.cancel(ctx, txn)
        }

        /** Opens Money Manager (a plain app launch) so the user can type the entry. */
        fun openMoneyManager(ctx: Context) {
            val launch = MM_PACKAGES.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
            if (launch == null) Toast.makeText(ctx, "Money Manager is not installed", Toast.LENGTH_SHORT).show()
            else ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
