package sms2mm.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import sms2mm.core.TxnType
import java.time.LocalDate

/**
 * Fills one transaction into Money Manager's add screen, step by step, finding fields
 * by their on-screen text (as in the user's screenshots: tabs Income / Expense / Transfer,
 * then Account, Category, Amount, Note, Description).
 *
 * It never taps Save: the user checks the entry and saves it. Android only lets this
 * service see Money Manager (packageNames in res/xml/accessibility_service.xml).
 */
class MmAutofillService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null

    /** One run of the fill steps for one transaction. */
    private class Job(val txn: PendingTxn, val accountGroup: String?, val toAccountGroup: String?) {
        var step = 0
        var phase = 0
        var tries = 0
        var waitingForSave = false
        var toldToOpenAdd = false
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val j = job ?: return
        if (j.waitingForSave && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clicked = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).lowercase()
            if ("save" in clicked || "حفظ" in clicked) finish(Status.ENTERED, "Saved in Money Manager")
        }
    }

    // ------------------------------------------------------------------ steps

    private enum class Step { OPEN_ADD_SCREEN, TAB, ACCOUNT, TO_ACCOUNT, CATEGORY, AMOUNT, NOTE, DESCRIPTION, DONE }

    private sealed interface Result {
        data object Done : Result
        data object Retry : Result
        data class Fail(val reason: String) : Result
    }

    private fun begin(j: Job) {
        job = j
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(::tick, 900)
    }

    private fun steps(t: PendingTxn): List<Step> = buildList {
        add(Step.OPEN_ADD_SCREEN)
        add(Step.TAB)
        add(Step.ACCOUNT)
        if (t.type == TxnType.TRANSFER) add(Step.TO_ACCOUNT) else add(Step.CATEGORY)
        add(Step.AMOUNT)
        if (!t.note.isNullOrBlank()) add(Step.NOTE)
        if (!t.description.isNullOrBlank()) add(Step.DESCRIPTION)
        add(Step.DONE)
    }

    private fun tick() {
        val j = job ?: return
        if (j.waitingForSave) return
        val step = steps(j.txn)[j.step]
        val root = rootInActiveWindow
        if (root != null && Store.recordLayout(this) && step == Step.TAB) recordLayout(root)

        val result = if (root == null) Result.Retry else try {
            run(step, j, root)
        } catch (e: Exception) {
            Result.Fail("${step.name}: ${e.message}")
        }
        when (result) {
            Result.Done -> {
                j.step++
                j.phase = 0
                j.tries = 0
                handler.postDelayed(::tick, 450)
            }
            Result.Retry -> {
                j.tries++
                val limit = if (step == Step.OPEN_ADD_SCREEN) 100 else 14 // ~35 s to open the add screen, ~5 s per field
                if (j.tries > limit) fail("Stopped at ${step.label()}") else handler.postDelayed(::tick, 350)
            }
            is Result.Fail -> fail(result.reason)
        }
    }

    private fun Step.label() = name.lowercase().replace('_', ' ')

    private fun run(step: Step, j: Job, root: AccessibilityNodeInfo): Result {
        val t = j.txn
        return when (step) {
            Step.OPEN_ADD_SCREEN -> {
                if (isAddScreen(root)) return Result.Done
                // Money Manager's main screen has a round "+" button; try it, else ask the user.
                val plus = findAll(root) { n ->
                    val d = n.contentDescription?.toString().orEmpty().lowercase()
                    val tx = n.text?.toString().orEmpty().trim()
                    tx == "+" || d == "+" || d.contains("add") || d.contains("plus") || (n.viewIdResourceName ?: "").contains("add", ignoreCase = true)
                }.firstOrNull()
                if (plus != null && j.tries % 5 == 0) click(plus)
                if (plus == null && !j.toldToOpenAdd) {
                    j.toldToOpenAdd = true
                    toast("Tap + in Money Manager to open the add screen")
                }
                Result.Retry
            }

            Step.TAB -> {
                val tab = when (t.type) { TxnType.INCOME -> "Income"; TxnType.EXPENSE -> "Expense"; TxnType.TRANSFER -> "Transfer" }
                val node = byText(root, tab) ?: return Result.Retry
                click(node)
                Result.Done
            }

            Step.ACCOUNT -> pick(j, root, if (t.type == TxnType.TRANSFER) listOf("From", "Account") else listOf("Account"), t.account, j.accountGroup)

            Step.TO_ACCOUNT -> pick(j, root, listOf("To"), t.toAccount ?: return Result.Fail("transfer without a To account"), j.toAccountGroup)

            Step.CATEGORY -> {
                val sub = t.category.subcategory?.takeIf { it.isNotBlank() }
                when (j.phase) {
                    0 -> { clickField(root, "Category") ?: return Result.Retry; j.phase = 1; Result.Retry }
                    1 -> {
                        val parent = byText(root, t.category.name, exceptLabels = true) ?: return scroll(root, Result.Retry)
                        click(parent)
                        if (sub == null) Result.Done else { j.phase = 2; Result.Retry }
                    }
                    else -> {
                        val node = byText(root, sub!!, exceptLabels = true) ?: return scroll(root, Result.Retry)
                        click(node)
                        Result.Done
                    }
                }
            }

            Step.AMOUNT -> when (j.phase) {
                0 -> { clickField(root, "Amount") ?: return Result.Retry; j.phase = 1; Result.Retry }
                else -> {
                    val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                    if (field != null && setText(field, t.amount)) Result.Done
                    else if (typeOnKeypad(root, t.amount)) Result.Done
                    else Result.Retry
                }
            }

            Step.NOTE -> fillText(j, root, "Note", t.note!!)

            Step.DESCRIPTION -> fillText(j, root, "Description", t.description!!)

            Step.DONE -> {
                j.waitingForSave = true
                Store.update(this, t.id) { it.copy(status = Status.FILLED, failReason = null) }
                val dateHint = if (t.time.toLocalDate() != LocalDate.now()) " — set the date to ${t.time.toLocalDate()}" else ""
                toast("Filled. Check it and tap Save$dateHint")
                // If Save is never tapped, stop watching after 10 minutes; it stays "Filled" in the app.
                handler.postDelayed({ if (job === j) job = null }, 10 * 60 * 1000L)
                Result.Done
            }
        }
    }

    /** Opens an account picker field and chooses [account], first tapping its group (e.g. "Accounts") if needed. */
    private fun pick(j: Job, root: AccessibilityNodeInfo, labels: List<String>, account: String, group: String?): Result = when (j.phase) {
        0 -> {
            // The account panel may already be open (it is in the user's screenshot).
            if (byText(root, account, exceptLabels = true) != null) { j.phase = 2; Result.Retry }
            else { labels.firstNotNullOfOrNull { clickField(root, it) } ?: return Result.Retry; j.phase = 1; Result.Retry }
        }
        1 -> {
            if (byText(root, account, exceptLabels = true) != null) { j.phase = 2; Result.Retry }
            else {
                val g = byText(root, group ?: "Accounts", exceptLabels = true) ?: return Result.Retry
                click(g); j.phase = 2; Result.Retry
            }
        }
        else -> {
            val node = byText(root, account, exceptLabels = true) ?: return scroll(root, Result.Retry)
            click(node)
            Result.Done
        }
    }

    private fun fillText(j: Job, root: AccessibilityNodeInfo, label: String, value: String): Result {
        // Description is a hint inside an empty EditText; Note is a label next to a field.
        val editable = findAll(root) { it.isEditable && norm(it.hintText ?: it.text) == norm(label) }.firstOrNull()
        if (editable != null && setText(editable, value)) return Result.Done
        if (j.phase == 0) { clickField(root, label) ?: return Result.Retry; j.phase = 1; return Result.Retry }
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: return Result.Retry
        return if (setText(focused, value)) Result.Done else Result.Retry
    }

    // ------------------------------------------------------------------ screen helpers

    private fun isAddScreen(root: AccessibilityNodeInfo) =
        listOf("Income", "Expense", "Transfer").all { byText(root, it) != null } && byText(root, "Amount") != null

    private val fieldLabels = setOf("date", "account", "category", "amount", "note", "from", "to", "description", "income", "expense", "transfer")

    /** Compare texts ignoring emoji, punctuation, case and extra spaces: "SNB 💸" == "snb". */
    private fun norm(s: CharSequence?): String =
        s?.toString().orEmpty().filter { it.isLetterOrDigit() || it.isWhitespace() || it == '&' || it == '/' }
            .trim().replace(Regex("""\s+"""), " ").lowercase()

    private fun findAll(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isVisibleToUser && predicate(n)) out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return out
    }

    private fun byText(root: AccessibilityNodeInfo, text: String, exceptLabels: Boolean = false): AccessibilityNodeInfo? {
        val want = norm(text)
        if (want.isEmpty()) return null
        val matches = findAll(root) { norm(it.text) == want || norm(it.contentDescription) == want }
        // A category called "Home" must not match the "Home" tab twice; prefer the lowest one on screen (the picker).
        return if (exceptLabels) matches.maxByOrNull { bounds(it).top } else matches.firstOrNull()
    }

    /** Tap the row/field next to a label such as "Account". */
    private fun clickField(root: AccessibilityNodeInfo, label: String): Unit? {
        val node = findAll(root) { norm(it.text) == norm(label) && norm(label) in fieldLabels }.firstOrNull() ?: return null
        val labelBox = bounds(node)
        // Prefer the value area on the same row, right of the label.
        val rowTarget = findAll(root) { n ->
            val b = bounds(n)
            n !== node && (n.isClickable || n.isEditable) && b.left >= labelBox.right - 4 &&
                b.centerY() in labelBox.top..labelBox.bottom
        }.minByOrNull { bounds(it).left }
        click(rowTarget ?: node)
        return Unit
    }

    private fun bounds(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        tap(bounds(node))
    }

    private fun tap(b: Rect) {
        if (b.isEmpty) return
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Money Manager may show its own number pad instead of the keyboard: press its keys. */
    private fun typeOnKeypad(root: AccessibilityNodeInfo, amount: String): Boolean {
        val keys = amount.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        val nodes = keys.map { c -> findAll(root) { norm(it.text) == c.toString() || it.text?.toString() == c.toString() }.maxByOrNull { bounds(it).top } ?: return false }
        nodes.forEach(::click)
        return true
    }

    private fun scroll(root: AccessibilityNodeInfo, then: Result): Result {
        findAll(root) { it.isScrollable }.maxByOrNull { bounds(it).top }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return then
    }

    private fun recordLayout(root: AccessibilityNodeInfo) {
        val sb = StringBuilder("Money Manager screen layout (${java.time.LocalDateTime.now()})\n")
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val b = bounds(n)
            sb.append("  ".repeat(depth)).append(n.className?.toString()?.substringAfterLast('.'))
                .append(" text=").append(n.text).append(" desc=").append(n.contentDescription)
                .append(" hint=").append(n.hintText).append(" id=").append(n.viewIdResourceName)
                .append(" click=").append(n.isClickable).append(" edit=").append(n.isEditable)
                .append(" box=").append(b.toShortString()).append('\n')
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        Store.layoutFile(this).writeText(sb.toString())
        Store.setRecordLayout(this, false)
        toast("Layout recorded — share it from the Setup tab")
    }

    // ------------------------------------------------------------------ outcome

    private fun fail(reason: String) {
        val j = job ?: return
        job = null
        Store.update(this, j.txn.id) { it.copy(status = Status.FAILED, failReason = reason) }
        toast("Auto-fill stopped ($reason). Please finish this entry by hand.")
    }

    private fun finish(status: Status, message: String) {
        val j = job ?: return
        job = null
        Store.update(this, j.txn.id) { it.copy(status = status, failReason = null) }
        toast(message)
    }

    private fun toast(text: String) = handler.post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    companion object {
        val MM_PACKAGES = listOf("com.realbyteapps.moneymanagerfree", "com.realbyteapps.moneymanager")

        @Volatile
        private var instance: MmAutofillService? = null

        val isEnabled: Boolean get() = instance != null

        /** Starts filling [txn]; returns a message for the user when that is not possible. */
        fun start(ctx: Context, txn: PendingTxn): String? {
            val service = instance ?: return "Turn on \"SMS → Money Manager auto-fill\" in Accessibility settings (Setup tab)."
            val launch = MM_PACKAGES.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
                ?: return "Money Manager is not installed."
            val groups = Store.rules(ctx).accountGroups
            service.begin(Job(txn, groups[txn.account], txn.toAccount?.let { groups[it] }))
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return null
        }
    }
}
