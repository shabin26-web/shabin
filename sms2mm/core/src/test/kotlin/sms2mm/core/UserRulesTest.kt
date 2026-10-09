package sms2mm.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRulesTest {

    private val household = Category("🏘️ Household", "Home Stationery")
    private val appliances = Category("🏘️ Household", "Home Appliances")
    private val restaurant = Category("🍜 Food & Dining", "Restaurant Bills")

    private val rules = UserRules(
        banks = listOf(
            BankSetup(KnownBanks.Bank.D360, listOf("D360-SENDER")),
            BankSetup(KnownBanks.Bank.SNB, listOf("SNB-SENDER")),
        ),
        accounts = mapOf("3333" to "D360", "2222" to "SNB 💸"),
        accountGroups = mapOf("D360" to "Accounts", "SNB 💸" to "Accounts"),
        keywords = listOf(
            KeywordRule(listOf("LULU", "لولو", "LULU HYPERMARKET"), household, note = "Lulu"),
            KeywordRule(listOf("LULU HYPER MADINAH"), appliances, note = "Lulu Madinah Rd"),
            KeywordRule(listOf("SHAWARMA", "SHAWRM", "SHAVARMA"), restaurant),
            KeywordRule(listOf("IKEA"), appliances, note = "IKEA", account = "SNB 💸"),
        ),
        transfers = listOf(
            TransferRule(listOf("HASHIR"), otherAccount = "Corprights Expenses", description = "Reimbursement"),
        ),
        ignore = listOf("cashback offer"),
    )
    private val processor = SmsProcessor(rules.toRuleSet())
    private val at = LocalDateTime.of(2026, 9, 10, 23, 30)

    private fun d360(merchant: String, amount: String = "10.00") = assertIs<SmsOutcome.Parsed>(
        processor.process("D360-SENDER", "Local POS Purchase\nAmount: SAR $amount\nCard: *3333 - mada (Samsung Pay)\nAt: $merchant\nOn: 2026-09-10 23:28", at),
    )

    @Test
    fun `any keyword of a rule matches`() {
        assertEquals(household, d360("LULU H").category)
        assertEquals(household, d360("هايبر لولو").category)
        assertEquals(restaurant, d360("JEDDAH SHAVARMA").category)
        assertEquals(restaurant, d360("shawrm").category)
    }

    @Test
    fun `longest matching keyword wins`() {
        val out = d360("LULU HYPER MADINAH ROAD")
        assertEquals(appliances, out.category)
        assertEquals("Lulu Madinah Rd", out.note)
        assertEquals("Lulu", d360("LULU HYPERMARKET MADEENA").note)
    }

    @Test
    fun `account filter limits a rule to one account`() {
        val out = d360("IKEA") // the IKEA rule only applies to SNB
        assertEquals(Category.UNCATEGORIZED, out.category)
        assertTrue(out.needsReview)
        assertEquals("IKEA", out.note) // note falls back to the merchant
    }

    @Test
    fun `categorised transactions do not need review`() {
        assertFalse(d360("LULU H").needsReview)
    }

    @Test
    fun `transfer rule turns incoming money into a transfer from the other account`() {
        val sms = "Incoming Internal transfer: D360 Bank\nAmount: SAR 166.00\nFrom: MUHAMMED HASHIR\nIBAN: ****4444\nOn: 2026-09-07 11:56"
        val out = assertIs<SmsOutcome.Parsed>(processor.process("D360-SENDER", sms, at))
        assertEquals(TxnType.TRANSFER, out.type)
        assertEquals("Corprights Expenses", out.account)
        assertEquals("D360", out.toAccount)
        assertEquals("Reimbursement", out.description)
        assertFalse(out.needsReview)
    }

    @Test
    fun `user ignore words skip the sms`() {
        assertIs<SmsOutcome.Ignored>(processor.process("D360-SENDER", "Enjoy our CASHBACK OFFER this weekend", at))
    }

    @Test
    fun `a rule must have at least one keyword`() {
        assertFailsWith<IllegalArgumentException> { KeywordRule(emptyList(), household) }
        assertFailsWith<IllegalArgumentException> { KeywordRule(listOf(" "), household) }
        assertFailsWith<IllegalArgumentException> { TransferRule(emptyList(), "Cash") }
    }

    @Test
    fun `explain shows which rule a merchant hits`() {
        assertEquals("Lulu", rules.explain("lulu hypermarket")?.note)
        assertEquals(appliances, rules.explain("IKEA", account = "SNB 💸")?.category)
        assertNull(rules.explain("IKEA", account = "D360"))
        assertNull(rules.explain("UNKNOWN SHOP"))
    }

    @Test
    fun `learn from this adds a keyword once`() {
        val updated = rules.addKeyword(2, "  Shawarma Mahroof ")
        assertEquals(listOf("SHAWARMA", "SHAWRM", "SHAVARMA", "Shawarma Mahroof"), updated.keywords[2].keywords)
        assertEquals(updated, updated.addKeyword(2, "shawarma mahroof"))
        assertFailsWith<IllegalArgumentException> { rules.addKeyword(2, " ") }
    }

    @Test
    fun `rules survive a json round trip`() {
        val json = rules.toJson()
        assertEquals(rules, UserRules.fromJson(json))
        assertTrue("\"LULU HYPERMARKET\"" in json)
    }

    @Test
    fun `json from an older or newer app version still loads`() {
        val json = """{"banks":[{"bank":"SNB","senders":["SNB-SENDER"]}],"keywords":[{"keywords":["LULU"],"category":{"name":"🏘️ Household"}}],"someFutureField":1}"""
        val loaded = UserRules.fromJson(json)
        assertEquals(listOf("LULU"), loaded.keywords.single().keywords)
        assertEquals("SNB 💸", loaded.toRuleSet().banks.single().defaultAccount)
    }

    @Test
    fun `keywords match regardless of spaces and dashes`() {
        val r = UserRules(keywords = listOf(KeywordRule(listOf("AL BAIK"), restaurant), KeywordRule(listOf("STC"), household)))
        assertEquals(restaurant, r.explain("ALBAIK JEDDAH")?.category)
        assertEquals(restaurant, r.explain("Al-Baik Express")?.category)
        // Short keywords (under 4 letters) still need to appear as written.
        assertNull(r.explain("S-T-C"))
    }
}
