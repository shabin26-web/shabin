package sms2mm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import sms2mm.core.SmsOutcome
import sms2mm.core.SmsProcessor
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Receives every new SMS; only messages from the user's trusted bank senders get past [SmsProcessor]. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Work off the main thread so the phone stays smooth; goAsync keeps the receiver alive.
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                // A long SMS arrives in parts; join them per sender.
                messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val at = Instant.ofEpochMilli(parts.first().timestampMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    Pipeline.handle(app, sender, body, at)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object Pipeline {
    /** [body] is used here only and never stored. */
    fun handle(ctx: Context, sender: String, body: String, receivedAt: LocalDateTime) {
        when (val out = SmsProcessor(Store.ruleSet(ctx)).process(sender, body, receivedAt)) {
            SmsOutcome.NotABank -> Unit
            is SmsOutcome.Sensitive -> Store.addSkip(ctx, SkipEvent(out.bank, out.receivedAt.toString(), SkipEvent.OTP))
            is SmsOutcome.Ignored -> Store.addSkip(ctx, SkipEvent(out.bank, out.receivedAt.toString(), SkipEvent.IGNORED))
            is SmsOutcome.Unparsed -> {
                Store.addSkip(ctx, SkipEvent(out.bank, out.receivedAt.toString(), SkipEvent.UNPARSED))
                Notifier.showUnparsed(ctx, out.bank)
            }
            is SmsOutcome.Parsed -> {
                val txn = PendingTxn.from(out)
                if (Store.insert(ctx, txn)) Notifier.show(ctx, txn)
            }
        }
    }
}
