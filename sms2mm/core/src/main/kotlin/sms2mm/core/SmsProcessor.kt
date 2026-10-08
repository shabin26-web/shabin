package sms2mm.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * The whole pipeline for one incoming SMS:
 *   sender allow-list → OTP filter → ignore lists → bank patterns →
 *   transfer rules / keyword rules → Money Manager account.
 *
 * The SMS body only lives inside [process]; no outcome carries it out.
 */
class SmsProcessor(private val rules: RuleSet) {

    fun process(sender: String, body: String, receivedAt: LocalDateTime): SmsOutcome {
        val bank = rules.banks.firstOrNull { it.sentBy(sender) } ?: return SmsOutcome.NotABank
        if (OtpFilter.isSensitive(body)) return SmsOutcome.Sensitive(bank.bank, receivedAt)

        val text = Digits.normalize(body)
        if (bank.ignoreIf.any { it.containsMatchIn(text) } || KeywordMatcher.best(text, rules.ignoreKeywords) { listOf(it) } != null) {
            return SmsOutcome.Ignored(bank.bank, receivedAt)
        }

        for (pattern in bank.patterns) {
            val match = pattern.regex.find(text) ?: continue
            val amount = parseAmount(match.group("amount")) ?: continue
            val txn = ParsedTxn(
                bank = bank.bank,
                sender = sender.trim(),
                type = pattern.type,
                amount = amount,
                currency = match.group("currency")?.let(::normalizeCurrency) ?: pattern.defaultCurrency,
                merchant = match.group("merchant")?.let(::cleanMerchant)?.ifEmpty { null },
                cardLast4 = match.group("card")?.takeLast(4),
                accountLast4 = match.group("account")?.takeLast(4),
                occurredAt = (parseDate(match.group("date"), pattern) ?: receivedAt).truncatedTo(ChronoUnit.MINUTES),
            )
            return resolve(txn, bank, pattern)
        }
        return SmsOutcome.Unparsed(bank.bank, receivedAt)
    }

    private fun resolve(txn: ParsedTxn, bank: BankRule, pattern: MessagePattern): SmsOutcome.Parsed {
        val bankAccount = txn.cardLast4?.let { rules.accountsByCard[it] }
            ?: txn.accountLast4?.let { rules.accountsByCard[it] }
            ?: bank.defaultAccount

        val transfer = KeywordMatcher.best(txn.merchant, rules.transfers.filter { it.account == null || it.account == bankAccount }) { it.keywords }
        if (transfer != null) {
            val moneyIn = txn.type == TxnType.INCOME
            return SmsOutcome.Parsed(
                txn = txn,
                type = TxnType.TRANSFER,
                category = Category.UNCATEGORIZED,
                account = if (moneyIn) transfer.otherAccount else bankAccount,
                toAccount = if (moneyIn) bankAccount else transfer.otherAccount,
                note = transfer.note ?: txn.merchant,
                description = transfer.description,
                needsReview = false,
                dedupKey = dedupKey(txn),
            )
        }

        val keyword = KeywordMatcher.best(
            txn.merchant,
            rules.keywords.filter { (it.type == null || it.type == txn.type) && (it.account == null || it.account == bankAccount) },
        ) { it.keywords }
        val category = keyword?.category ?: pattern.defaultCategory ?: Category.UNCATEGORIZED
        return SmsOutcome.Parsed(
            txn = txn,
            type = txn.type,
            category = category,
            account = bankAccount,
            toAccount = null,
            note = keyword?.note ?: txn.merchant,
            description = keyword?.description,
            needsReview = category.isUncategorized,
            dedupKey = dedupKey(txn),
        )
    }

    companion object {
        /** Same bank, minute, amount and card → same transaction (guards against re-delivered SMS). */
        fun dedupKey(txn: ParsedTxn): String = listOf(
            txn.sender.lowercase(),
            txn.occurredAt.truncatedTo(ChronoUnit.MINUTES).toString(),
            txn.amount.stripTrailingZeros().toPlainString(),
            txn.cardLast4 ?: txn.accountLast4.orEmpty(),
        ).joinToString("|")

        internal fun parseAmount(raw: String?): BigDecimal? {
            val cleaned = raw?.replace(",", "")?.trim().orEmpty()
            val amount = cleaned.toBigDecimalOrNull() ?: return null
            return if (amount.signum() > 0) amount.setScale(2, RoundingMode.HALF_UP) else null
        }

        private fun parseDate(raw: String?, pattern: MessagePattern): LocalDateTime? {
            val format = pattern.dateFormat ?: return null
            val cleaned = raw?.trim()?.replace(Regex("""\s+"""), " ") ?: return null
            return try { LocalDateTime.parse(cleaned, format) } catch (_: DateTimeParseException) { null }
        }

        private fun normalizeCurrency(raw: String): String = when (raw.trim().uppercase()) {
            "SR", "SAR", "ر.س", "ريال", "ريال سعودي" -> "SAR"
            "RS", "RS.", "INR", "₹" -> "INR"
            else -> raw.trim().uppercase()
        }

        private fun cleanMerchant(raw: String): String = raw.trim().trimEnd('.', ',', ';').replace(Regex("""\s+"""), " ")

        private fun MatchResult.group(name: String): String? =
            try { groups[name]?.value } catch (_: IllegalArgumentException) { null }
    }
}
