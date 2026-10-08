package sms2mm.core

import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

/**
 * One message layout a bank uses, e.g. "card purchase" or "salary credit".
 *
 * [regex] is matched against the SMS after [Digits.normalize], and may use these
 * named groups (only `amount` is required):
 *   amount   — 1,234.50
 *   currency — SAR / SR / ريال / USD; defaults to [defaultCurrency]
 *   merchant — where the money went / came from
 *   card     — last 4 digits of the card
 *   account  — last 4 digits of the bank account
 *   date     — transaction time printed in the SMS, read with [dateFormat];
 *              when absent or unreadable the SMS received time is used
 */
data class MessagePattern(
    val type: TxnType,
    val regex: Regex,
    val defaultCategory: Category? = null,
    val defaultCurrency: String = "SAR",
    val dateFormat: DateTimeFormatter? = null,
)

data class BankRule(
    val bank: String,
    /** SMS sender IDs this bank uses; compared ignoring case. */
    val senders: Set<String>,
    val patterns: List<MessagePattern>,
    /** Bank SMS containing any of these are skipped (promotions, declined, ...). */
    val ignoreIf: List<Regex> = emptyList(),
    /** Money Manager account used when the card/account number has no specific mapping. */
    val defaultAccount: String = bank,
) {
    fun sentBy(sender: String): Boolean = senders.any { it.equals(sender.trim(), ignoreCase = true) }
}

/**
 * Merchant keywords → category, plus the Note to write in Money Manager.
 *
 * Matches when ANY of [keywords] appears in the merchant (ignoring case), e.g.
 * `["LULU", "LULU HYPER", "لولو"]`. [type] and [account] narrow the rule to one
 * transaction type or Money Manager account when set.
 */
@Serializable
data class KeywordRule(
    val keywords: List<String>,
    val category: Category,
    val note: String? = null,
    val description: String? = null,
    val type: TxnType? = null,
    val account: String? = null,
) {
    init {
        require(keywords.any { it.isNotBlank() }) { "a keyword rule needs at least one keyword" }
    }
}

/**
 * Payee/payer keywords → treat the SMS as a Money Manager TRANSFER with [otherAccount].
 *
 * Money in (e.g. "From: HASHIR") becomes a transfer otherAccount → this bank's account;
 * money out becomes this bank's account → otherAccount.
 */
@Serializable
data class TransferRule(
    val keywords: List<String>,
    val otherAccount: String,
    val note: String? = null,
    val description: String? = null,
    val account: String? = null,
) {
    init {
        require(keywords.any { it.isNotBlank() }) { "a transfer rule needs at least one keyword" }
    }
}

data class RuleSet(
    val banks: List<BankRule>,
    val keywords: List<KeywordRule> = emptyList(),
    val transfers: List<TransferRule> = emptyList(),
    /** Card/account last-4 → Money Manager account name. */
    val accountsByCard: Map<String, String> = emptyMap(),
    /** Bank SMS containing any of these words are skipped. */
    val ignoreKeywords: List<String> = emptyList(),
)

/**
 * Picks the rule whose keyword matches best: the longest matching keyword wins
 * (so "LULU HYPER" beats "LULU"), and ties go to the rule listed first.
 */
object KeywordMatcher {
    fun <T> best(text: String?, rules: List<T>, keywordsOf: (T) -> List<String>): T? {
        if (text.isNullOrBlank()) return null
        val haystack = Digits.normalize(text)
        var best: T? = null
        var bestLength = 0
        for (rule in rules) {
            val length = keywordsOf(rule)
                .map { Digits.normalize(it.trim()) }
                .filter { it.isNotEmpty() && haystack.contains(it, ignoreCase = true) }
                .maxOfOrNull { it.length } ?: continue
            if (length > bestLength) {
                best = rule
                bestLength = length
            }
        }
        return best
    }
}

/** Converts Arabic-Indic digits and separators so one regex handles both scripts. */
object Digits {
    private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
    private const val EASTERN_ARABIC_INDIC = "۰۱۲۳۴۵۶۷۸۹"

    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            val arabic = ARABIC_INDIC.indexOf(c)
            val eastern = EASTERN_ARABIC_INDIC.indexOf(c)
            when {
                arabic >= 0 -> append('0' + arabic)
                eastern >= 0 -> append('0' + eastern)
                c == '٫' -> append('.') // Arabic decimal separator
                c == '٬' -> append(',') // Arabic thousands separator
                else -> append(c)
            }
        }
    }
}
