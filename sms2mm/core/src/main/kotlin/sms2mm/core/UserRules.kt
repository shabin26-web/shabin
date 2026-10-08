package sms2mm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which senders to trust for one known bank, and its fallback Money Manager account. */
@Serializable
data class BankSetup(
    val bank: KnownBanks.Bank,
    val senders: List<String>,
    val defaultAccount: String? = null,
)

/**
 * Everything the user can customise, stored as one JSON file in app-private storage
 * and importable/exportable for backup. Bank message layouts stay in code
 * ([KnownBanks]); this file only says which senders, accounts and keywords to use.
 */
@Serializable
data class UserRules(
    val version: Int = 1,
    val banks: List<BankSetup> = emptyList(),
    /** Card or account last-4 → Money Manager account name. */
    val accounts: Map<String, String> = emptyMap(),
    /** Money Manager account name → the group it sits under in the account picker ("Accounts", "Card", ...). */
    val accountGroups: Map<String, String> = emptyMap(),
    val keywords: List<KeywordRule> = emptyList(),
    val transfers: List<TransferRule> = emptyList(),
    val ignore: List<String> = emptyList(),
) {
    fun toRuleSet() = RuleSet(
        banks = banks.map { KnownBanks.rule(it.bank, it.senders.toSet(), it.defaultAccount ?: it.bank.defaultAccount) },
        keywords = keywords,
        transfers = transfers,
        accountsByCard = accounts,
        ignoreKeywords = ignore,
    )

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    /** Which rule (if any) a merchant name would hit — the "test box" on the Keywords screen. */
    fun explain(merchant: String, type: TxnType? = null, account: String? = null): KeywordRule? =
        KeywordMatcher.best(
            merchant,
            keywords.filter { (type == null || it.type == null || it.type == type) && (account == null || it.account == null || it.account == account) },
        ) { it.keywords }

    /** Add [keyword] to an existing rule (by index) — the "learn from this" action. */
    fun addKeyword(ruleIndex: Int, keyword: String): UserRules {
        val k = keyword.trim()
        require(k.isNotEmpty()) { "keyword must not be empty" }
        val rule = keywords[ruleIndex]
        if (rule.keywords.any { it.equals(k, ignoreCase = true) }) return this
        return copy(keywords = keywords.toMutableList().also { it[ruleIndex] = rule.copy(keywords = rule.keywords + k) })
    }

    companion object {
        private val JSON = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(json: String): UserRules = JSON.decodeFromString(serializer(), json)
    }
}
