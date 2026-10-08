package sms2mm.core

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Real stc pay SMS layouts from the user, with the card number masked to 1234. */
class KnownBanksTest {

    private val processor = SmsProcessor(
        RuleSet(
            banks = listOf(KnownBanks.stcPay(senders = setOf("STC-SENDER"), defaultAccount = "STC Pay")),
            keywords = listOf(
                KeywordRule("LULU", Category("🏘️ Household", "Home Stationery"), note = "Lulu"),
                KeywordRule("shawrm", Category("🍜 Food & Dining", "Restaurant Bills")),
            ),
            accountsByCard = mapOf("1234" to "STC Pay 💳"),
        ),
    )

    // SMS arrives a few minutes after the purchase; the time printed in the SMS wins.
    private val arrived = LocalDateTime.of(2026, 10, 7, 22, 47, 5)

    @Test
    fun `pos purchase with decimals`() {
        val sms = "PoS Purchase\nCard:1234;Mada-mada Pay (Atheer)\nAmount:25.42SR\nAt:LULU H\n07/10/26 22:42"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("STC-SENDER", sms, arrived))

        assertEquals(TxnType.EXPENSE, out.txn.type)
        assertEquals(BigDecimal("25.42"), out.txn.amount)
        assertEquals("SAR", out.txn.currency)
        assertEquals("LULU H", out.txn.merchant)
        assertEquals("1234", out.txn.cardLast4)
        assertEquals(LocalDateTime.of(2026, 10, 7, 22, 42), out.txn.occurredAt)
        assertEquals("STC Pay 💳", out.account)
        assertEquals(Category("🏘️ Household", "Home Stationery"), out.category)
        assertEquals("Lulu", out.note)
    }

    @Test
    fun `pos purchase with whole riyal amount, note falls back to merchant`() {
        val sms = "PoS Purchase\nCard:1234;Mada-mada Pay (Atheer)\nAmount:10SR\nAt:shawrm\n05/10/26 23:31"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("STC-SENDER", sms, arrived))

        assertEquals(BigDecimal("10.00"), out.txn.amount)
        assertEquals("shawrm", out.txn.merchant)
        assertEquals("shawrm", out.note)
        assertEquals(LocalDateTime.of(2026, 10, 5, 23, 31), out.txn.occurredAt)
        assertEquals(Category("🍜 Food & Dining", "Restaurant Bills"), out.category)
    }

    @Test
    fun `missing date line falls back to arrival time`() {
        val sms = "PoS Purchase\nCard:1234;Mada-mada Pay (Atheer)\nAmount:1,250.00SR\nAt:IKEA"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("STC-SENDER", sms, arrived))
        assertEquals(BigDecimal("1250.00"), out.txn.amount)
        assertEquals(LocalDateTime.of(2026, 10, 7, 22, 47), out.txn.occurredAt)
        assertEquals(Category.UNCATEGORIZED, out.category)
    }

    @Test
    fun `unmapped card uses the bank's default account`() {
        val sms = "PoS Purchase\nCard:9999;Mada-mada Pay (Atheer)\nAmount:5SR\nAt:LULU H\n07/10/26 22:42"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("STC-SENDER", sms, arrived))
        assertEquals("STC Pay", out.account)
    }

    @Test
    fun `stc otp is dropped`() {
        val sms = "Your OTP for the purchase of 25.42 SR at LULU H is 123456. Do not share it."
        assertIs<SmsOutcome.Sensitive>(processor.process("STC-SENDER", sms, arrived))
    }
}
