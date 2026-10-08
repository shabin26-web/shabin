package sms2mm.core

/**
 * One message layout a bank uses, e.g. "card purchase" or "salary credit".
 *
 * [regex] is matched against the SMS after [Digits.normalize], and may use these
 * named groups (only `amount` is required):
 *   amount   — 1,234.50
 *   currency — SAR / USD; defaults to [defaultCurrency]
 *   merchant — where the money went / came from
 *   card     — last 4 digits of the card or account
 */
data class MessagePattern(
    val type: TxnType,
    val regex: Regex,
    val defaultCategory: Category? = null,
    val defaultCurrency: String = "SAR",
)

data class BankRule(
    val bank: String,
    /** SMS sender IDs this bank uses; compared ignoring case. */
    val senders: Set<String>,
    val patterns: List<MessagePattern>,
    /** Bank SMS containing any of these are skipped (promotions, declined, ...). */
    val ignoreIf: List<Regex> = emptyList(),
    /** Money Manager account used when the card number has no specific mapping. */
    val defaultAccount: String = bank,
) {
    fun sentBy(sender: String): Boolean = senders.any { it.equals(sender.trim(), ignoreCase = true) }
}

/** Keyword (matched case-insensitively inside the merchant name) → category. */
data class KeywordRule(val keyword: String, val category: Category)

data class RuleSet(
    val banks: List<BankRule>,
    val keywords: List<KeywordRule> = emptyList(),
    /** Card/account last-4 → Money Manager account name. */
    val accountsByCard: Map<String, String> = emptyMap(),
)

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
