package sms2mm.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import sms2mm.core.Category
import sms2mm.core.RuleSet
import sms2mm.core.SmsOutcome
import sms2mm.core.TxnType
import sms2mm.core.UserRules
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

/** Where a captured transaction stands. */
@Serializable
enum class Status { PENDING, FILLED, ENTERED, IGNORED, FAILED }

/**
 * One captured bank transaction. Only parsed fields — the SMS text itself is never stored.
 */
@Serializable
data class PendingTxn(
    val id: String,
    val bank: String,
    val type: TxnType,
    val amount: String,
    val currency: String,
    val account: String,
    val toAccount: String? = null,
    val category: Category,
    val note: String? = null,
    val description: String? = null,
    val merchant: String? = null,
    val occurredAt: String,
    val needsReview: Boolean = false,
    val status: Status = Status.PENDING,
    val failReason: String? = null,
) {
    val time: LocalDateTime get() = LocalDateTime.parse(occurredAt)
    val amountValue: BigDecimal get() = BigDecimal(amount)
    val notificationId: Int get() = id.hashCode()

    companion object {
        fun from(p: SmsOutcome.Parsed) = PendingTxn(
            id = p.dedupKey,
            bank = p.txn.bank,
            type = p.type,
            amount = p.txn.amount.toPlainString(),
            currency = p.txn.currency,
            account = p.account,
            toAccount = p.toAccount,
            category = p.category,
            note = p.note,
            description = p.description,
            merchant = p.txn.merchant,
            occurredAt = p.txn.occurredAt.toString(),
            needsReview = p.needsReview,
        )
    }
}

/** A bank SMS that did not become a transaction. Bank, time and reason only — no text. */
@Serializable
data class SkipEvent(val bank: String, val at: String, val kind: String) {
    companion object {
        const val OTP = "otp"
        const val IGNORED = "ignored"
        const val UNPARSED = "unparsed"
    }
}

fun Category.label(): String = listOfNotNull(name, subcategory?.takeIf { it.isNotBlank() }).joinToString(" > ")

/**
 * Small JSON files in app-private storage (not backed up, not readable by other apps).
 * Volumes are tiny (a few transactions a day), so whole-file rewrites are fine.
 */
object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val _changes = MutableStateFlow(0)

    /** Bumped on every write so screens can refresh. */
    val changes: StateFlow<Int> = _changes

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun write(ctx: Context, name: String, text: String) {
        val target = file(ctx, name)
        val tmp = File(target.parentFile, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
        _changes.value = _changes.value + 1
    }

    // In-memory copies: files are read once, then served from memory (writes go to both).
    private var rulesCache: UserRules? = null
    private var ruleSetCache: Pair<UserRules, RuleSet>? = null
    private var txnCache: List<PendingTxn>? = null
    private var skipCache: List<SkipEvent>? = null

    // ---------------------------------------------------------------- rules

    fun rules(ctx: Context): UserRules = synchronized(lock) {
        rulesCache ?: run {
            val f = file(ctx, "rules.json")
            val loaded = if (!f.exists()) UserRules() else try { UserRules.fromJson(f.readText()) } catch (_: Exception) { UserRules() }
            loaded.also { rulesCache = it }
        }
    }

    /** The compiled rules for the SMS processor, rebuilt only when the rules change. */
    fun ruleSet(ctx: Context): RuleSet = synchronized(lock) {
        val r = rules(ctx)
        ruleSetCache?.takeIf { it.first === r }?.second ?: r.toRuleSet().also { ruleSetCache = r to it }
    }

    fun saveRules(ctx: Context, rules: UserRules) = synchronized(lock) {
        rulesCache = rules
        write(ctx, "rules.json", rules.toJson())
    }

    fun updateRules(ctx: Context, change: (UserRules) -> UserRules) = synchronized(lock) { saveRules(ctx, change(rules(ctx))) }

    // ---------------------------------------------------------------- transactions

    private val txnList = ListSerializer(PendingTxn.serializer())

    fun txns(ctx: Context): List<PendingTxn> = synchronized(lock) {
        txnCache ?: run {
            val f = file(ctx, "txns.json")
            val loaded = if (!f.exists()) emptyList() else try { json.decodeFromString(txnList, f.readText()) } catch (_: Exception) { emptyList() }
            loaded.also { txnCache = it }
        }
    }

    fun txn(ctx: Context, id: String): PendingTxn? = txns(ctx).firstOrNull { it.id == id }

    private fun saveTxns(ctx: Context, list: List<PendingTxn>) {
        txnCache = list
        write(ctx, "txns.json", json.encodeToString(txnList, list))
    }

    /** Adds a new transaction; false when the same SMS was already captured. */
    fun insert(ctx: Context, txn: PendingTxn): Boolean = synchronized(lock) {
        val list = txns(ctx)
        if (list.any { it.id == txn.id }) false else { saveTxns(ctx, listOf(txn) + list); true }
    }

    fun update(ctx: Context, id: String, change: (PendingTxn) -> PendingTxn) = synchronized(lock) {
        saveTxns(ctx, txns(ctx).map { if (it.id == id) change(it) else it })
    }

    fun delete(ctx: Context, id: String) = synchronized(lock) { saveTxns(ctx, txns(ctx).filterNot { it.id == id }) }

    // ---------------------------------------------------------------- skipped SMS

    private val skipList = ListSerializer(SkipEvent.serializer())

    fun skips(ctx: Context): List<SkipEvent> = synchronized(lock) {
        skipCache ?: run {
            val f = file(ctx, "skips.json")
            val loaded = if (!f.exists()) emptyList() else try { json.decodeFromString(skipList, f.readText()) } catch (_: Exception) { emptyList() }
            loaded.also { skipCache = it }
        }
    }

    fun addSkip(ctx: Context, event: SkipEvent) = synchronized(lock) {
        // Keep the current and previous month only.
        val cutoff = YearMonth.now().minusMonths(1).atDay(1).atStartOfDay()
        val kept = skips(ctx).filter { LocalDateTime.parse(it.at) >= cutoff } + event
        skipCache = kept
        write(ctx, "skips.json", json.encodeToString(skipList, kept))
    }

    // ---------------------------------------------------------------- settings

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** When on, the auto-fill service writes Money Manager's screen layout to a file (for tuning). */
    fun recordLayout(ctx: Context): Boolean = prefs(ctx).getBoolean("record_layout", false)

    fun setRecordLayout(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("record_layout", on).apply()
        _changes.value = _changes.value + 1
    }

    fun layoutFile(ctx: Context) = file(ctx, "mm_layout.txt")
}
