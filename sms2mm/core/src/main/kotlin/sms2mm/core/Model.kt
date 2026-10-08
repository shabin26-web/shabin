package sms2mm.core

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

/** The three entry tabs Money Manager offers. */
@Serializable
enum class TxnType { EXPENSE, INCOME, TRANSFER }

@Serializable
data class Category(val name: String, val subcategory: String? = null) {
    val isUncategorized: Boolean get() = this == UNCATEGORIZED

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
    /** As the SMS reads it: money out = EXPENSE, money in = INCOME. */
    val type: TxnType,
    val amount: BigDecimal,
    val currency: String,
    /** Merchant, payee or payer as printed in the SMS. */
    val merchant: String?,
    val cardLast4: String?,
    val accountLast4: String?,
    /** Transaction time: from the SMS text when the bank prints it, else when the SMS arrived. */
    val occurredAt: LocalDateTime,
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

    /**
     * Ready for Money Manager. For EXPENSE/INCOME, [account] is the account the money
     * left or reached. For TRANSFER, money moves from [account] to [toAccount].
     */
    data class Parsed(
        val txn: ParsedTxn,
        val type: TxnType,
        val category: Category,
        val account: String,
        val toAccount: String?,
        /** Money Manager "Note" field: the matching rule's note, else the merchant. */
        val note: String?,
        /** Money Manager "Description" field. */
        val description: String?,
        /** No rule said what this is (e.g. UPI to a person): the user should pick a category. */
        val needsReview: Boolean,
        val dedupKey: String,
    ) : SmsOutcome
}
