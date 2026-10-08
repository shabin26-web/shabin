package sms2mm.core

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The whole pipeline for one incoming SMS:
 *   sender allow-list → OTP filter → ignore list → bank patterns → category → account.
 *
 * The SMS body only lives inside [process]; no outcome carries it out.
 */
class SmsProcessor(private val rules: RuleSet) {

    fun process(sender: String, body: String, receivedAt: LocalDateTime): SmsOutcome {
        val bank = rules.banks.firstOrNull { it.sentBy(sender) } ?: return SmsOutcome.NotABank
        if (OtpFilter.isSensitive(body)) return SmsOutcome.Sensitive(bank.bank, receivedAt)

        val text = Digits.normalize(body)
        if (bank.ignoreIf.any { it.containsMatchIn(text) }) return SmsOutcome.Ignored(bank.bank, receivedAt)

        for (pattern in bank.patterns) {
            val match = pattern.regex.find(text) ?: continue
            val amount = parseAmount(match.group("amount")) ?: continue
            val txn = ParsedTxn(
                bank = bank.bank,
                sender = sender.trim(),
                type = pattern.type,
                amount = amount,
                currency = match.group("currency")?.uppercase() ?: pattern.defaultCurrency,
                merchant = match.group("merchant")?.let(::cleanMerchant)?.ifEmpty { null },
                cardLast4 = match.group("card")?.takeLast(4),
                receivedAt = receivedAt.truncatedTo(ChronoUnit.MINUTES),
            )
            return SmsOutcome.Parsed(
                txn = txn,
                category = categorize(txn.merchant, pattern),
                account = txn.cardLast4?.let { rules.accountsByCard[it] } ?: bank.defaultAccount,
                dedupKey = dedupKey(txn),
            )
        }
        return SmsOutcome.Unparsed(bank.bank, receivedAt)
    }

    private fun categorize(merchant: String?, pattern: MessagePattern): Category {
        if (merchant != null) {
            rules.keywords.firstOrNull { merchant.contains(it.keyword, ignoreCase = true) }
                ?.let { return it.category }
        }
        return pattern.defaultCategory ?: Category.UNCATEGORIZED
    }

    companion object {
        /** Same bank, minute, amount and card → same transaction (guards against re-delivered SMS). */
        fun dedupKey(txn: ParsedTxn): String = listOf(
            txn.sender.lowercase(),
            txn.receivedAt.truncatedTo(ChronoUnit.MINUTES).toString(),
            txn.amount.stripTrailingZeros().toPlainString(),
            txn.cardLast4.orEmpty(),
        ).joinToString("|")

        internal fun parseAmount(raw: String?): BigDecimal? {
            val cleaned = raw?.replace(",", "")?.trim().orEmpty()
            val amount = cleaned.toBigDecimalOrNull() ?: return null
            return if (amount.signum() > 0) amount.setScale(2, java.math.RoundingMode.HALF_UP) else null
        }

        private fun cleanMerchant(raw: String): String = raw.trim().trimEnd('.', ',', ';').replace(Regex("""\s+"""), " ")

        private fun MatchResult.group(name: String): String? =
            try { groups[name]?.value } catch (_: IllegalArgumentException) { null }
    }
}
