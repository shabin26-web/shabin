package sms2mm.core

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real SMS layouts from the user's banks. Card/account numbers are masked
 * (1111, 2222, ...) and personal names replaced with placeholders.
 */
class OtherBanksTest {

    private val rules = UserRules(
        banks = listOf(
            BankSetup(KnownBanks.Bank.SNB, listOf("SNB-SENDER")),
            BankSetup(KnownBanks.Bank.D360, listOf("D360-SENDER")),
            BankSetup(KnownBanks.Bank.FEDERAL, listOf("FED-SENDER")),
            BankSetup(KnownBanks.Bank.BOB, listOf("BOB-SENDER")),
        ),
        accounts = mapOf("2222" to "SNB 💸", "3333" to "D360", "5555" to "Federal Bank", "6666" to "Vijaya Bank"),
    )
    private val processor = SmsProcessor(rules.toRuleSet())
    private val arrived = LocalDateTime.of(2026, 10, 9, 8, 0)

    private fun parse(sender: String, sms: String) = assertIs<SmsOutcome.Parsed>(processor.process(sender, sms, arrived), sms)

    private fun SmsOutcome.Parsed.check(amount: String, currency: String, merchant: String?, at: LocalDateTime, account: String, type: TxnType = TxnType.EXPENSE) {
        assertEquals(BigDecimal(amount), txn.amount, "amount")
        assertEquals(currency, txn.currency, "currency")
        assertEquals(merchant, txn.merchant, "merchant")
        assertEquals(at, txn.occurredAt, "time")
        assertEquals(account, this.account, "account")
        assertEquals(type, this.type, "type")
    }

    // ------------------------------------------------------------ SNB

    @Test
    fun `snb online purchase`() = parse(
        "SNB-SENDER",
        "Online Purchase\nAmount 13.50 SAR\nAccount *1111\nAt Express F\nMada *2222\non 08/10/26 at 21:24",
    ).check("13.50", "SAR", "Express F", LocalDateTime.of(2026, 10, 8, 21, 24), "SNB 💸")

    @Test
    fun `snb local internet purchase`() = parse(
        "SNB-SENDER",
        "Local Internet purchase\nAmount 103.50 SAR\nAccount *1111\nAt Jawwy fro\nMada *2222\non 06/10/26 at 18:49",
    ).check("103.50", "SAR", "Jawwy fro", LocalDateTime.of(2026, 10, 6, 18, 49), "SNB 💸")

    @Test
    fun `snb pos purchase without account line`() {
        val out = parse("SNB-SENDER", "POS Purchase\nAmount 81.05 SAR\nAt ALAAM ALT\nMada-Pay *2222\non 03/10/26 at 21:19")
        out.check("81.05", "SAR", "ALAAM ALT", LocalDateTime.of(2026, 10, 3, 21, 19), "SNB 💸")
        assertEquals("2222", out.txn.cardLast4)
        assertNull(out.txn.accountLast4)
    }

    @Test
    fun `snb account number alone is enough to find the account`() {
        val only = SmsProcessor(rules.copy(accounts = mapOf("1111" to "SNB 💸")).toRuleSet())
        val out = assertIs<SmsOutcome.Parsed>(
            only.process("SNB-SENDER", "Online Purchase\nAmount 5.00 SAR\nAccount *1111\nAt X\nMada *9999\non 08/10/26 at 21:24", arrived),
        )
        assertEquals("SNB 💸", out.account)
    }

    // ------------------------------------------------------------ D360

    @Test
    fun `d360 local pos purchases`() {
        val samples = listOf(
            Triple("12.00", "PROFICIENCY COFFEE COMPAN", LocalDateTime.of(2026, 9, 17, 23, 44)),
            Triple("66.43", "LULU HYPER MADINAH ROAD", LocalDateTime.of(2026, 9, 11, 0, 37)),
            Triple("21.80", "IKEA", LocalDateTime.of(2026, 9, 10, 23, 28)),
            Triple("56.42", "LULU HYPERMARKET MADEENA", LocalDateTime.of(2026, 9, 9, 21, 52)),
        )
        for ((amount, merchant, at) in samples) {
            val sms = "Local POS Purchase\nAmount: SAR $amount\nCard: *3333 - mada (Samsung Pay)\nAt: $merchant\nOn: ${at.toLocalDate()} ${"%02d:%02d".format(at.hour, at.minute)}"
            parse("D360-SENDER", sms).check(amount, "SAR", merchant, at, "D360")
        }
    }

    @Test
    fun `d360 incoming transfer is income until a transfer rule says otherwise`() {
        val sms = "Incoming Internal transfer: D360 Bank\nAmount: SAR 166.00\nFrom: PAYER NAME\nIBAN: ****4444\nOn: 2026-09-07 11:56"
        val out = parse("D360-SENDER", sms)
        out.check("166.00", "SAR", "PAYER NAME", LocalDateTime.of(2026, 9, 7, 11, 56), "D360", TxnType.INCOME)
        assertTrue(out.needsReview)
    }

    // ------------------------------------------------------------ Federal Bank (INR)

    @Test
    fun `federal upi debits`() {
        parse("FED-SENDER", "Debited Rs 3045.00 from a/c X5555 on 30Sep26 21:52 via UPI to PAYEE NAME. Ref 663981394127.Bal Rs 6738.6. Not you?Call 18004251199 -Federal Bank")
            .check("3045.00", "INR", "PAYEE NAME", LocalDateTime.of(2026, 9, 30, 21, 52), "Federal Bank")
        parse("FED-SENDER", "Debited Rs 283.00 from a/c X5555 on 22Aug26 13:48 via UPI to Google India. Ref 128319250987.Bal Rs 5253.96. Not you?Call 18004251199 -Federal Bank")
            .check("283.00", "INR", "Google India", LocalDateTime.of(2026, 8, 22, 13, 48), "Federal Bank")
    }

    @Test
    fun `federal mandate executed`() = parse(
        "FED-SENDER",
        "Dear Customer, Your mandate with ref no- 0000abcd@okicici registered against Google Play for Rs 1999.00 successfully executed on 04-09-2026 16:06:44. TXN Ref No -448446962476- Federal Bank",
    ).check("1999.00", "INR", "Google Play", LocalDateTime.of(2026, 9, 4, 16, 6), "Federal Bank")

    // ------------------------------------------------------------ Bank of Baroda (INR)

    @Test
    fun `bob upi debit uses the time inside the balance brackets`() = parse(
        "BOB-SENDER",
        "Rs.1000.00 Dr. from A/C XXXXXX6666 and Cr. to someone@ybl. Ref:655165251454. AvlBal:Rs6632.59(2026:07:04 09:02:11). Not you? Call 18005700/5000-BOB",
    ).check("1000.00", "INR", "someone@ybl", LocalDateTime.of(2026, 7, 4, 9, 2), "Vijaya Bank")

    // ------------------------------------------------------------ setup helper

    @Test
    fun `detect recognises each bank layout`() {
        assertEquals(KnownBanks.Bank.STC_PAY, KnownBanks.detect("PoS Purchase\nCard:1234;Mada-mada Pay (Atheer)\nAmount:25.42SR\nAt:LULU H\n07/10/26 22:42"))
        assertEquals(KnownBanks.Bank.SNB, KnownBanks.detect("POS Purchase\nAmount 81.05 SAR\nAt ALAAM ALT\nMada-Pay *2222\non 03/10/26 at 21:19"))
        assertEquals(KnownBanks.Bank.D360, KnownBanks.detect("Local POS Purchase\nAmount: SAR 12.00\nCard: *3333 - mada (Samsung Pay)\nAt: IKEA\nOn: 2026-09-10 23:28"))
        assertEquals(KnownBanks.Bank.FEDERAL, KnownBanks.detect("Debited Rs 500.00 from a/c X5555 on 24Aug26 17:37 via UPI to PAYEE. Ref 1.Bal Rs 1. -Federal Bank"))
        assertEquals(KnownBanks.Bank.BOB, KnownBanks.detect("Rs.1000.00 Dr. from A/C XXXXXX6666 and Cr. to someone@ybl. Ref:1. AvlBal:Rs1(2026:07:04 09:02:11)."))
        assertNull(KnownBanks.detect("Hi, are we meeting today?"))
    }
}
