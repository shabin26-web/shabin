package sms2mm.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Handles the Add / Ignore buttons on a transaction notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val txn = Store.txn(context, id) ?: return
        when (intent.action) {
            ADD -> addToMoneyManager(context, txn)
            IGNORE -> {
                Store.update(context, id) { it.copy(status = Status.IGNORED) }
                Notifier.cancel(context, txn)
            }
        }
    }

    companion object {
        const val ADD = "sms2mm.app.ADD"
        const val IGNORE = "sms2mm.app.IGNORE"
        private const val EXTRA_ID = "id"

        fun intent(ctx: Context, action: String, t: PendingTxn): PendingIntent = PendingIntent.getBroadcast(
            ctx, (action + t.id).hashCode(),
            Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, t.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Shared by the notification and the app screens. */
        fun addToMoneyManager(ctx: Context, txn: PendingTxn) {
            val problem = MmAutofillService.start(ctx, txn)
            if (problem != null) {
                Toast.makeText(ctx, problem, Toast.LENGTH_LONG).show()
            } else {
                Notifier.cancel(ctx, txn)
            }
        }
    }
}
