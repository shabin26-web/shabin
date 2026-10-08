package sms2mm.core

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Message layouts of banks the user actually has, written from real SMS.
 *
 * Sender IDs and account names are passed in rather than hard-coded: they come from
 * the user's on-device rules file ([UserRules]), together with card → account mappings.
 */
object KnownBanks {

    private const val AMT = """[\d,]+(?:\.\d+)?"""

    private fun format(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)

    private val DECLINED = Regex("""declined|rejected|failed|reversed|مرفوض""", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- stc pay

    /**
     *     PoS Purchase
     *     Card:1234;Mada-mada Pay (Atheer)
     *     Amount:25.42SR
     *     At:LULU H
     *     07/10/26 22:42
     */
    val STC_POS_PURCHASE = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """PoS\s+Purchase\s*Card:\s*(?<card>\d{4})[^\n]*\s*Amount:\s*(?<amount>$AMT)\s*(?<currency>SAR|SR)\s*At:\s*(?<merchant>[^\n]+)(?:\s*\n\s*(?<date>\d{2}/\d{2}/\d{2}\s+\d{1,2}:\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ),
        dateFormat = format("dd/MM/yy H:mm"),
    )

    // ---------------------------------------------------------------- SNB

    /**
     *     Online Purchase            | Local Internet purchase   | POS Purchase
     *     Amount 13.50 SAR           | Amount 103.50 SAR         | Amount 81.05 SAR
     *     Account *1111              | Account *1111             | At ALAAM ALT
     *     At Express F               | At Jawwy fro              | Mada-Pay *2222
     *     Mada *2222                 | Mada *2222                | on 03/10/26 at 21:19
     *     on 08/10/26 at 21:24       | on 06/10/26 at 18:49      |
     */
    val SNB_PURCHASE = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """(?:Online|Local\s+Internet|Internet|POS)\s+Purchase\s*Amount:?\s*(?<amount>$AMT)\s*(?<currency>[A-Z]{2,3})\s*(?:Account:?\s*\*+(?<account>\d{4})\s*)?At:?\s*(?<merchant>[^\n]+?)\s*\n(?:\s*Mada[^*\n]*\*+(?<card>\d{4})\s*)?(?:\s*on\s+(?<date>\d{2}/\d{2}/\d{2}\s+at\s+\d{1,2}:\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ),
        dateFormat = format("dd/MM/yy 'at' H:mm"),
    )

    // ---------------------------------------------------------------- D360

    private val D360_DATE = format("yyyy-MM-dd H:mm")

    /**
     *     Local POS Purchase
     *     Amount: SAR 12.00
     *     Card: *3333 - mada (Samsung Pay)
     *     At: PROFICIENCY COFFEE COMPAN
     *     On: 2026-09-17 23:44
     */
    val D360_PURCHASE = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """(?:Local\s+|International\s+)?(?:POS|Online|Internet)\s+Purchase\s*Amount:\s*(?<currency>[A-Z]{3})\s*(?<amount>$AMT)\s*Card:\s*\*+(?<card>\d{4})[^\n]*\s*At:\s*(?<merchant>[^\n]+?)\s*(?:\n\s*On:\s*(?<date>\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}))?\s*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        ),
        dateFormat = D360_DATE,
    )

    /**
     *     Incoming Internal transfer: D360 Bank
     *     Amount: SAR 166.00
     *     From: PAYER NAME
     *     IBAN: ****4444
     *     On: 2026-09-07 11:56
     */
    val D360_INCOMING_TRANSFER = MessagePattern(
        type = TxnType.INCOME,
        regex = Regex(
            """Incoming\s+(?:Internal\s+|Local\s+|International\s+)?transfer[^\n]*\s*Amount:\s*(?<currency>[A-Z]{3})\s*(?<amount>$AMT)\s*From:\s*(?<merchant>[^\n]+?)\s*\n(?:[\s\S]*?On:\s*(?<date>\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ),
        dateFormat = D360_DATE,
    )

    // ---------------------------------------------------------------- Federal Bank (INR)

    /** Debited Rs 3045.00 from a/c X5555 on 30Sep26 21:52 via UPI to PAYEE NAME. Ref 6639….Bal Rs 6738.6. … -Federal Bank */
    val FEDERAL_UPI_DEBIT = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """Debited\s+Rs\.?\s*(?<amount>$AMT)\s+from\s+a/c\s+X*(?<account>\d{4})\s+on\s+(?<date>\d{1,2}[A-Za-z]{3}\d{2}\s+\d{1,2}:\d{2})\s+via\s+\S+\s+to\s+(?<merchant>[^.\n]+)""",
            RegexOption.IGNORE_CASE,
        ),
        defaultCurrency = "INR",
        dateFormat = format("dMMMyy H:mm"),
    )

    /** Your mandate with ref no- …@okicici registered against Google Play for Rs 1999.00 successfully executed on 04-09-2026 16:06:44. … */
    val FEDERAL_MANDATE = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """mandate[\s\S]*?registered\s+against\s+(?<merchant>.+?)\s+for\s+Rs\.?\s*(?<amount>$AMT)\s+successfully\s+executed\s+on\s+(?<date>\d{2}-\d{2}-\d{4}\s+\d{2}:\d{2}:\d{2})""",
            RegexOption.IGNORE_CASE,
        ),
        defaultCurrency = "INR",
        dateFormat = format("dd-MM-yyyy HH:mm:ss"),
    )

    // ---------------------------------------------------------------- Bank of Baroda (INR)

    /** Rs.1000.00 Dr. from A/C XXXXXX6666 and Cr. to someone@ybl. Ref:6551…. AvlBal:Rs6632.59(2026:07:04 09:02:11). … -BOB */
    val BOB_UPI_DEBIT = MessagePattern(
        type = TxnType.EXPENSE,
        regex = Regex(
            """Rs\.?\s*(?<amount>$AMT)\s+Dr\.?\s+from\s+A/C\s+X*(?<account>\d{4})\s+and\s+Cr\.?\s+to\s+(?<merchant>\S+?)\.?\s+Ref(?:[\s\S]*?\((?<date>\d{4}:\d{2}:\d{2}\s+\d{2}:\d{2}:\d{2})\))?""",
            RegexOption.IGNORE_CASE,
        ),
        defaultCurrency = "INR",
        dateFormat = format("yyyy:MM:dd HH:mm:ss"),
    )

    // ---------------------------------------------------------------- bank catalogue

    /** Every bank layout the app knows, by the id used in the user's rules file. */
    enum class Bank(val title: String, val defaultAccount: String, val patterns: List<MessagePattern>) {
        STC_PAY("stc pay", "STC Pay 💳", listOf(STC_POS_PURCHASE)),
        SNB("SNB", "SNB 💸", listOf(SNB_PURCHASE)),
        D360("D360", "D360", listOf(D360_PURCHASE, D360_INCOMING_TRANSFER)),
        FEDERAL("Federal Bank", "Federal Bank", listOf(FEDERAL_UPI_DEBIT, FEDERAL_MANDATE)),
        BOB("Bank of Baroda", "Vijaya Bank", listOf(BOB_UPI_DEBIT)),
    }

    fun rule(bank: Bank, senders: Set<String>, defaultAccount: String = bank.defaultAccount) = BankRule(
        bank = bank.title,
        senders = senders,
        defaultAccount = defaultAccount,
        patterns = bank.patterns,
        ignoreIf = listOf(DECLINED),
    )

    fun stcPay(senders: Set<String>, defaultAccount: String) = rule(Bank.STC_PAY, senders, defaultAccount)

    /** Which known bank layout (if any) a message looks like — used to suggest sender IDs on setup. */
    fun detect(body: String): Bank? {
        val text = Digits.normalize(body)
        return Bank.entries.firstOrNull { bank -> bank.patterns.any { it.regex.containsMatchIn(text) } }
    }
}
