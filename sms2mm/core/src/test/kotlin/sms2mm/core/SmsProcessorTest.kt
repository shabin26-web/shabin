package sms2mm.core

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The SMS texts below are ILLUSTRATIVE layouts, not copies of any real bank's
 * messages. They get replaced with the user's own (masked) samples once provided.
 */
class SmsProcessorTest {

    private val at = LocalDateTime.of(2026, 10, 8, 14, 22, 37)

    private val rules = RuleSet(
        banks = listOf(
            BankRule(
                bank = "Example Bank",
                senders = setOf("ExampleBank", "EXBANK"),
                defaultAccount = "Example Current",
                ignoreIf = listOf(Regex("declined", RegexOption.IGNORE_CASE), Regex("offer", RegexOption.IGNORE_CASE)),
                patterns = listOf(
                    MessagePattern(
                        TxnType.EXPENSE,
                        Regex("""Purchase.*?Card:\s*\*+(?<card>\d{4}).*?Amount:\s*(?<currency>[A-Z]{3})\s*(?<amount>[\d,]+(?:\.\d+)?).*?At:\s*(?<merchant>[^\n]+)""",
                            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                    ),
                    MessagePattern(
                        TxnType.EXPENSE,
                        Regex("""شراء.*?بطاقة\s*\*+(?<card>\d{4}).*?مبلغ\s*(?<amount>[\d,]+(?:\.\d+)?)\s*(ريال|SAR).*?لدى\s*(?<merchant>[^\n]+)""",
                            RegexOption.DOT_MATCHES_ALL),
                    ),
                    MessagePattern(
                        TxnType.INCOME,
                        Regex("""Salary.*?(?<amount>[\d,]+(?:\.\d+)?)\s*SAR""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                        defaultCategory = Category("Salary"),
                    ),
                ),
            ),
        ),
        keywords = listOf(
            KeywordRule("PANDA", Category("Food", "Groceries")),
            KeywordRule("STC", Category("Bills", "Phone")),
        ),
        accountsByCard = mapOf("4821" to "Example Credit Card"),
    )

    private val processor = SmsProcessor(rules)

    @Test
    fun `english card purchase is parsed, categorised and mapped to the card's account`() {
        val sms = "Purchase\nCard: **4821\nAmount: SAR 1,245.50\nAt: PANDA RETAIL CO.\n"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("ExampleBank", sms, at))

        assertEquals(TxnType.EXPENSE, out.txn.type)
        assertEquals(BigDecimal("1245.50"), out.txn.amount)
        assertEquals("SAR", out.txn.currency)
        assertEquals("PANDA RETAIL CO", out.txn.merchant)
        assertEquals("4821", out.txn.cardLast4)
        assertEquals(Category("Food", "Groceries"), out.category)
        assertEquals("Example Credit Card", out.account)
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 22), out.txn.occurredAt)
    }

    @Test
    fun `arabic purchase with arabic-indic digits is parsed`() {
        val sms = "شراء عبر بطاقة **٩٩١٠\nمبلغ ٤٥٫٧٥ ريال\nلدى STC PAY"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("EXBANK", sms, at))

        assertEquals(BigDecimal("45.75"), out.txn.amount)
        assertEquals("9910", out.txn.cardLast4)
        assertEquals(Category("Bills", "Phone"), out.category)
        assertEquals("Example Current", out.account) // 9910 has no mapping → bank default
    }

    @Test
    fun `salary credit uses the pattern's default category`() {
        val out = assertIs<SmsOutcome.Parsed>(processor.process("ExampleBank", "Salary credited 12,000.00 SAR", at))
        assertEquals(TxnType.INCOME, out.txn.type)
        assertEquals(BigDecimal("12000.00"), out.txn.amount)
        assertEquals(Category("Salary"), out.category)
        assertNull(out.txn.merchant)
    }

    @Test
    fun `unknown merchant falls back to Uncategorized`() {
        val sms = "Purchase Card: *4821 Amount: SAR 10 At: SOME SHOP"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("ExampleBank", sms, at))
        assertEquals(Category.UNCATEGORIZED, out.category)
        assertEquals(BigDecimal("10.00"), out.txn.amount)
    }

    @Test
    fun `messages from non-bank senders are not processed`() {
        assertEquals(SmsOutcome.NotABank, processor.process("Mom", "Purchase Card: *4821 Amount: SAR 10 At: X", at))
    }

    @Test
    fun `otp messages are dropped as sensitive, english and arabic`() {
        val samples = listOf(
            "Your OTP is 482913. Valid for 5 minutes.",
            "Use one-time password 1234 to confirm the purchase of SAR 50.00",
            "Verification code: 5521",
            "Do not share this code with anyone: 9981",
            "رمز التحقق الخاص بك هو ٤٥٦٧",
            "لا تشارك هذا الرمز مع أحد",
        )
        for (sms in samples) {
            assertIs<SmsOutcome.Sensitive>(processor.process("ExampleBank", sms, at), sms)
        }
    }

    @Test
    fun `promotions and declined transactions are ignored`() {
        assertIs<SmsOutcome.Ignored>(processor.process("ExampleBank", "Special offer: 0% instalments!", at))
        assertIs<SmsOutcome.Ignored>(
            processor.process("ExampleBank", "Purchase declined Card: *4821 Amount: SAR 99.00 At: PANDA", at),
        )
    }

    @Test
    fun `bank sms matching no pattern is reported as unparsed`() {
        assertIs<SmsOutcome.Unparsed>(processor.process("ExampleBank", "Your statement is ready.", at))
    }

    @Test
    fun `same sms delivered twice gives the same dedup key`() {
        val sms = "Purchase Card: *4821 Amount: SAR 45.00 At: PANDA"
        val first = assertIs<SmsOutcome.Parsed>(processor.process("ExampleBank", sms, at))
        val again = assertIs<SmsOutcome.Parsed>(processor.process("examplebank", sms, at.plusSeconds(15)))
        assertEquals(first.dedupKey, again.dedupKey)

        val other = assertIs<SmsOutcome.Parsed>(
            processor.process("ExampleBank", sms.replace("45.00", "46.00"), at),
        )
        assert(first.dedupKey != other.dedupKey)
    }

    @Test
    fun `zero or garbage amounts do not produce a transaction`() {
        assertIs<SmsOutcome.Unparsed>(processor.process("ExampleBank", "Purchase Card: *4821 Amount: SAR 0.00 At: PANDA", at))
        assertNull(SmsProcessor.parseAmount("1,2,3.4.5"))
    }
}
