package sms2mm.core

import java.time.format.DateTimeFormatter

/**
 * Message layouts of banks the user actually has, written from real (masked) SMS.
 *
 * Sender IDs are passed in rather than hard-coded: they come from the user's phone
 * and live in their on-device rules file, together with card → account mappings.
 */
object KnownBanks {

    private val DD_MM_YY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy H:mm")

    /**
     * stc pay / STC Bank card purchase, e.g.
     *
     *     PoS Purchase
     *     Card:1234;Mada-mada Pay (Atheer)
     *     Amount:25.42SR
     *     At:LULU H
     *     07/10/26 22:42
     */
    val STC_POS_PURCHASE = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """PoS\s+Purchase\s*Card:\s*(?<card>\d{4})[^\n]*\s*Amount:\s*(?<amount>[\d,]+(?:\.\d+)?)\s*(?<currency>SAR|SR)\s*At:\s*(?<merchant>[^\n]+)(?:\s*\n\s*(?<date>\d{2}/\d{2}/\d{2}\s+\d{1,2}:\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ),
        dateFormat = DD_MM_YY_TIME,
    )

    fun stcPay(senders: Set<String>, defaultAccount: String) = BankRule(
        bank = "stc pay",
        senders = senders,
        defaultAccount = defaultAccount,
        patterns = listOf(STC_POS_PURCHASE),
        ignoreIf = listOf(Regex("""declined|rejected|مرفوض""", RegexOption.IGNORE_CASE)),
    )
}
