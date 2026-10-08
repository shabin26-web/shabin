package sms2mm.core

import java.math.BigDecimal
import java.time.LocalDateTime

/** The three entry tabs Money Manager offers. */
enum class TxnType { EXPENSE, INCOME, TRANSFER }

data class Category(val name: String, val subcategory: String? = null) {
    companion object {
        val UNCATEGORIZED = Category("Uncategorized")
    }
}

/**
 * One transaction pulled out of a bank SMS.
 *
 * Only these fields are ever stored — the raw SMS text is not kept. Amounts are
 * BigDecimal, never Double, so 0.10 + 0.20 really is 0.30.
 */
data class ParsedTxn(
    val bank: String,
    val sender: String,
    val type: TxnType,
    val amount: BigDecimal,
    val currency: String,
    val merchant: String?,
    val cardLast4: String?,
    val receivedAt: LocalDateTime,
)

/** What happened to one incoming SMS. None of these carry the message body. */
sealed interface SmsOutcome {
    /** Sender is not one of the configured banks; dropped without being read further. */
    data object NotABank : SmsOutcome

    /** OTP / verification / "do not share" message from a bank; dropped, nothing stored. */
    data class Sensitive(val bank: String, val receivedAt: LocalDateTime) : SmsOutcome

    /** Promotions, declined transactions and other bank SMS the user chose to ignore. */
    data class Ignored(val bank: String, val receivedAt: LocalDateTime) : SmsOutcome

    /** A bank SMS that matched no rule — shown so the user can add it by hand. */
    data class Unparsed(val bank: String, val receivedAt: LocalDateTime) : SmsOutcome

    data class Parsed(
        val txn: ParsedTxn,
        val category: Category,
        val account: String,
        val dedupKey: String,
    ) : SmsOutcome
}
