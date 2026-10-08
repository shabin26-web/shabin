package sms2mm.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import sms2mm.core.Category
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

    // ---------------------------------------------------------------- rules

    fun rules(ctx: Context): UserRules = synchronized(lock) {
        val f = file(ctx, "rules.json")
        if (!f.exists()) return UserRules()
        try { UserRules.fromJson(f.readText()) } catch (_: Exception) { UserRules() }
    }

    fun saveRules(ctx: Context, rules: UserRules) = synchronized(lock) { write(ctx, "rules.json", rules.toJson()) }

    fun updateRules(ctx: Context, change: (UserRules) -> UserRules) = synchronized(lock) { saveRules(ctx, change(rules(ctx))) }

    // ---------------------------------------------------------------- transactions

    private val txnList = ListSerializer(PendingTxn.serializer())

    fun txns(ctx: Context): List<PendingTxn> = synchronized(lock) {
        val f = file(ctx, "txns.json")
        if (!f.exists()) return emptyList()
        try { json.decodeFromString(txnList, f.readText()) } catch (_: Exception) { emptyList() }
    }

    fun txn(ctx: Context, id: String): PendingTxn? = txns(ctx).firstOrNull { it.id == id }

    private fun saveTxns(ctx: Context, list: List<PendingTxn>) = write(ctx, "txns.json", json.encodeToString(txnList, list))

    /** Adds a new transaction; false when the same SMS was already captured. */
    fun insert(ctx: Context, txn: PendingTxn): Boolean = synchronized(lock) {
        val list = txns(ctx)
        if (list.any { it.id == txn.id }) return false
        saveTxns(ctx, listOf(txn) + list)
        true
    }

    fun update(ctx: Context, id: String, change: (PendingTxn) -> PendingTxn) = synchronized(lock) {
        saveTxns(ctx, txns(ctx).map { if (it.id == id) change(it) else it })
    }

    fun delete(ctx: Context, id: String) = synchronized(lock) { saveTxns(ctx, txns(ctx).filterNot { it.id == id }) }

    // ---------------------------------------------------------------- skipped SMS

    private val skipList = ListSerializer(SkipEvent.serializer())

    fun skips(ctx: Context): List<SkipEvent> = synchronized(lock) {
        val f = file(ctx, "skips.json")
        if (!f.exists()) return emptyList()
        try { json.decodeFromString(skipList, f.readText()) } catch (_: Exception) { emptyList() }
    }

    fun addSkip(ctx: Context, event: SkipEvent) = synchronized(lock) {
        // Keep the current and previous month only.
        val cutoff = YearMonth.now().minusMonths(1).atDay(1).atStartOfDay()
        val kept = skips(ctx).filter { LocalDateTime.parse(it.at) >= cutoff }
        write(ctx, "skips.json", json.encodeToString(skipList, kept + event))
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
