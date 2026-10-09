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
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Fills one transaction into Money Manager's add screen, step by step, finding fields
 * by their on-screen text (tabs Income / Expense / Transfer, then Account or From/To,
 * Category, Amount, Note, Description), and checks each value after setting it.
 *
 * Speed: the screen is read once per step into a [Screen] snapshot, and steps run as soon
 * as Money Manager redraws (accessibility events) instead of on fixed timers.
 *
 * It never taps Save: the user checks the entry and saves it. Android only lets this
 * service see Money Manager (packageNames in res/xml/accessibility_service.xml).
 */
class MmAutofillService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { tick() }
    private var job: Job? = null

    /** One run of the fill steps for one transaction. */
    private class Job(val txn: PendingTxn, val accountGroup: String?, val toAccountGroup: String?) {
        val steps: List<Step> = buildList {
            add(Step.OPEN_ADD_SCREEN)
            add(Step.TAB)
            add(Step.ACCOUNT)
            if (txn.type == TxnType.TRANSFER) add(Step.TO_ACCOUNT) else add(Step.CATEGORY)
            add(Step.AMOUNT)
            if (!txn.note.isNullOrBlank()) add(Step.NOTE)
            if (!txn.description.isNullOrBlank()) add(Step.DESCRIPTION)
            add(Step.DONE)
        }
        var step = 0
        var phase = 0
        var tries = 0
        var reopened = false
        var checks = 0
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
        if (j.waitingForSave) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val clicked = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).lowercase()
                if ("save" in clicked || "حفظ" in clicked) finish(Status.ENTERED, "Saved in Money Manager ✔")
            }
            return
        }
        // Money Manager redrew: run the next step shortly (debounced), not on a fixed timer.
        schedule(120)
    }

    // ------------------------------------------------------------------ steps

    private enum class Step(val label: String) {
        OPEN_ADD_SCREEN("opening the add screen"), TAB("choosing the tab"), ACCOUNT("choosing the account"),
        TO_ACCOUNT("choosing the To account"), CATEGORY("choosing the category"), AMOUNT("typing the amount"),
        NOTE("writing the note"), DESCRIPTION("writing the description"), DONE("finishing"),
    }

    private sealed interface Result {
        data object Done : Result
        data object Retry : Result
        data class Fail(val reason: String) : Result
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, delayMs)
    }

    private fun begin(j: Job) {
        job = j
        schedule(500)
    }

    private fun tick() {
        val j = job ?: return
        if (j.waitingForSave) return
        val step = j.steps[j.step]
        val root = rootInActiveWindow
        if (root == null) { retry(j, step); return }
        val screen = Screen.read(root)

        if (closeStrayWindow(screen)) { schedule(500); return }
        if (Store.recordLayout(this) && step == Step.TAB) recordLayout(screen)

        val result = try { run(step, j, screen) } catch (e: Exception) { Result.Fail("${step.label}: ${e.message}") }
        when (result) {
            Result.Done -> {
                j.step++
                j.phase = 0
                j.tries = 0
                j.reopened = false
                j.checks = 0
                schedule(150)
            }
            Result.Retry -> retry(j, step)
            is Result.Fail -> fail(result.reason)
        }
    }

    private fun retry(j: Job, step: Step) {
        j.tries++
        val limit = if (step == Step.OPEN_ADD_SCREEN) 120 else 20 // ~35 s to open the add screen, ~6 s per field
        if (j.tries > limit) fail("stopped while ${step.label}") else schedule(300)
    }

    private fun run(step: Step, j: Job, s: Screen): Result {
        val t = j.txn
        return when (step) {
            Step.OPEN_ADD_SCREEN -> {
                if (isAddScreen(s)) return Result.Done
                // Money Manager's main screen has a round "+" button; try it, else ask the user.
                val plus = s.nodes.firstOrNull { n ->
                    n.raw == "+" || n.descLower == "+" || "add" in n.descLower || "plus" in n.descLower || "add" in n.idLower
                }
                if (plus != null && j.tries % 6 == 0) click(plus)
                if (plus == null && !j.toldToOpenAdd) {
                    j.toldToOpenAdd = true
                    toast("Tap + in Money Manager to open the add screen")
                }
                Result.Retry
            }

            Step.TAB -> {
                val tab = when (t.type) { TxnType.INCOME -> "Income"; TxnType.EXPENSE -> "Expense"; TxnType.TRANSFER -> "Transfer" }
                val node = s.byText(tab) ?: return Result.Retry
                click(node)
                Result.Done
            }

            Step.ACCOUNT -> pick(j, s, if (t.type == TxnType.TRANSFER) listOf("From", "Account") else listOf("Account"), t.account, j.accountGroup)

            Step.TO_ACCOUNT -> pick(j, s, listOf("To"), t.toAccount ?: return Result.Fail("transfer without a To account"), j.toAccountGroup)

            Step.CATEGORY -> {
                val sub = t.category.subcategory?.takeIf { it.isNotBlank() }
                when (j.phase) {
                    0 -> { clickField(s, "Category") ?: return Result.Retry; j.phase = 1; Result.Retry }
                    1 -> {
                        val parent = s.byText(t.category.name, lowest = true) ?: return scroll(s)
                        click(parent)
                        j.phase = if (sub == null) 3 else 2
                        Result.Retry
                    }
                    2 -> {
                        val node = s.byText(sub!!, lowest = true) ?: return scroll(s)
                        click(node)
                        j.phase = 3
                        Result.Retry
                    }
                    // Check the Category row now shows what we picked.
                    else -> verify(j, s, "Category", listOfNotNull(sub, t.category.name))
                }
            }

            Step.AMOUNT -> when (j.phase) {
                0 -> { clickField(s, "Amount") ?: return Result.Retry; j.phase = 1; Result.Retry }
                1 -> {
                    val field = s.root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                    if (field != null && setText(field, t.amount)) { j.phase = 3; Result.Retry }
                    else if (typeOnKeypad(s, t.amount, t.currency)) { j.phase = 2; Result.Retry }
                    else Result.Retry
                }
                2 -> {
                    // Close Money Manager's number pad with its "Done" key.
                    s.keypadKey("Done")?.let(::click)
                    j.phase = 3
                    Result.Retry
                }
                else -> {
                    val shown = s.rowText("Amount").filter(Char::isDigit)
                    val want = BigDecimal(t.amount).stripTrailingZeros().toPlainString().filter(Char::isDigit)
                    if (want in shown) Result.Done
                    else if (j.checks++ < 6) Result.Retry
                    else Result.Fail("the amount shows \"${s.rowText("Amount")}\" instead of ${t.amount}")
                }
            }

            Step.NOTE -> fillText(j, s, "Note", t.note!!)

            Step.DESCRIPTION -> fillText(j, s, "Description", t.description!!)

            Step.DONE -> {
                j.waitingForSave = true
                Store.update(this, t.id) { it.copy(status = Status.FILLED, failReason = null) }
                val dateHint = if (t.time.toLocalDate() != LocalDate.now()) " — set the date to ${t.time.toLocalDate()}" else ""
                toast("Filled ✔ Check it and tap Save$dateHint")
                // If Save is never tapped, stop watching after 10 minutes; it stays "Filled" in the app.
                handler.postDelayed({ if (job === j) job = null }, 10 * 60 * 1000L)
                Result.Done
            }
        }
    }

    /** Opens an account picker field and chooses [account], first tapping its group (e.g. "Accounts") if needed. */
    private fun pick(j: Job, s: Screen, labels: List<String>, account: String, group: String?): Result = when (j.phase) {
        0 -> {
            // The account panel may already be open (it is in the user's screenshot).
            if (s.byText(account, lowest = true) != null) j.phase = 2
            else if (labels.firstNotNullOfOrNull { clickField(s, it) } != null) j.phase = 1
            Result.Retry
        }
        1 -> {
            if (s.byText(account, lowest = true) == null) s.byText(group ?: "Accounts", lowest = true)?.let(::click)
            j.phase = 2
            Result.Retry
        }
        2 -> {
            val node = s.byText(account, lowest = true)
            if (node == null) scroll(s) else { click(node); j.phase = 3; Result.Retry }
        }
        // Check the From/Account row now shows the account.
        else -> verify(j, s, labels.firstOrNull { s.label(it) != null } ?: labels.first(), listOf(account))
    }

    /**
     * After choosing something, the form row must show it. If not, open the picker once more;
     * if it still doesn't show, stop instead of leaving a wrong value.
     */
    private fun verify(j: Job, s: Screen, label: String, expected: List<String>): Result {
        val shown = norm(s.rowText(label))
        if (expected.any { norm(it).isNotEmpty() && norm(it) in shown }) return Result.Done
        if (j.checks++ < 4) return Result.Retry // the form may still be redrawing
        if (!j.reopened) { j.reopened = true; j.phase = 0; j.tries = 0; j.checks = 0; return Result.Retry }
        return Result.Fail("$label shows \"${s.rowText(label)}\" instead of ${expected.first()}")
    }

    private fun fillText(j: Job, s: Screen, label: String, value: String): Result {
        // Description is a hint inside an empty EditText; Note is a label next to a field.
        val editable = s.nodes.firstOrNull { it.editable && (norm(it.hint) == norm(label) || norm(it.text) == norm(label)) }
        if (editable != null && setText(editable.node, value)) return Result.Done
        if (j.phase == 0) { clickField(s, label) ?: return Result.Retry; j.phase = 1; return Result.Retry }
        val focused = s.root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: return Result.Retry
        return if (setText(focused, value)) Result.Done else Result.Retry
    }

    // ------------------------------------------------------------------ screen snapshot

    /** One visible node, with everything the steps compare read once. */
    private class N(val node: AccessibilityNodeInfo) {
        val raw: String = node.text?.toString()?.trim().orEmpty()
        val text: String = raw
        val desc: String = node.contentDescription?.toString().orEmpty()
        val descLower = desc.lowercase()
        val hint: String = node.hintText?.toString().orEmpty()
        val idLower: String = node.viewIdResourceName.orEmpty().lowercase()
        val box: Rect = Rect().also(node::getBoundsInScreen)
        val clickable = node.isClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        val normText = norm(raw)
        val normDesc = norm(desc)

        /** Buttons the auto-fill must never press: currency conversion, calculator, fees, camera, favourites. */
        val forbidden: Boolean = raw.equals("Fees", ignoreCase = true) ||
            listOf("globe", "currency", "exchange", "convert", "calc", "fee", "camera", "photo", "bookmark", "favorite", "favourite")
                .any { it in descLower || it in idLower } // id/description only: "Coffee" must not match "fee"
    }

    private class Screen(val root: AccessibilityNodeInfo, val nodes: List<N>) {
        companion object {
            fun read(root: AccessibilityNodeInfo): Screen {
                val out = ArrayList<N>(256)
                val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
                while (queue.isNotEmpty()) {
                    val n = queue.removeFirst()
                    if (n.isVisibleToUser) out.add(N(n))
                    for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
                }
                return Screen(root, out)
            }
        }

        /** By text or description, ignoring emoji/case. [lowest] prefers the picker below the form. */
        fun byText(text: String, lowest: Boolean = false): N? {
            val want = norm(text)
            if (want.isEmpty()) return null
            val matches = nodes.filter { (it.normText == want || it.normDesc == want) && !it.forbidden }
            return if (lowest) matches.maxByOrNull { it.box.top } else matches.firstOrNull()
        }

        /** The form row label (topmost: the number pad and pickers repeat labels like "Amount"). */
        fun label(label: String): N? = nodes.filter { it.normText == norm(label) }.minByOrNull { it.box.top }

        /** Everything written on the label's row, right of the label. */
        fun rowText(label: String): String {
            val l = label(label) ?: return ""
            return nodes.filter { it !== l && it.raw.isNotEmpty() && it.box.left >= l.box.right - 4 && it.box.centerY() in l.box.top..l.box.bottom }
                .sortedBy { it.box.left }.joinToString(" ") { it.raw }
        }

        /** A number-pad key by its exact label, the lowest one on screen (the pad sits at the bottom). */
        fun keypadKey(label: String): N? = nodes.filter { it.raw == label && !it.forbidden }.maxByOrNull { it.box.top }
    }

    private fun isAddScreen(s: Screen) =
        listOf("Income", "Expense", "Transfer").all { s.byText(it) != null } && s.label("Amount") != null

    private val fieldLabels = setOf("date", "account", "category", "amount", "note", "from", "to", "description")

    /** Tap the row/field next to a label such as "Account". */
    private fun clickField(s: Screen, label: String): Unit? {
        if (norm(label) !in fieldLabels) return null
        val l = s.label(label) ?: return null
        val rowTarget = s.nodes.filter { n ->
            n !== l && (n.clickable || n.editable) && !n.forbidden && n.box.left >= l.box.right - 4 && n.box.centerY() in l.box.top..l.box.bottom
        }.minByOrNull { it.box.left }
        click(rowTarget ?: l)
        return Unit
    }

    private fun click(n: N) {
        if (n.forbidden) return
        var cur: AccessibilityNodeInfo? = n.node
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            cur = cur.parent
        }
        tap(n.box)
    }

    private fun tap(b: Rect) {
        if (b.isEmpty) return
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Money Manager shows its own number pad (currency row ر.س / ₹, digits, 00, ".", Done). */
    private fun typeOnKeypad(s: Screen, amount: String, currency: String): Boolean {
        val one = s.keypadKey("1") ?: return false
        // Choose the currency first, so a SAR amount is not entered as INR (or the reverse).
        val symbols = when (currency) {
            "SAR" -> listOf("ر.س", "SAR", "SR")
            "INR" -> listOf("₹", "INR", "Rs")
            else -> listOf(currency)
        }
        s.nodes.filter { n -> n.raw in symbols && n.box.bottom <= one.box.top + 4 }.maxByOrNull { it.box.top }?.let(::click)
        val keys = BigDecimal(amount).stripTrailingZeros().toPlainString()
        val nodes = keys.map { c -> s.keypadKey(c.toString()) ?: return false }
        nodes.forEach(::click)
        return true
    }

    /** If a currency-conversion (or similar) window opened by mistake, press Back once. */
    private fun closeStrayWindow(s: Screen): Boolean {
        val stray = s.nodes.any { n ->
            val tx = n.raw.lowercase()
            "exchange rate" in tx || "conversion" in tx || "convert currency" in tx || "currency setting" in tx
        }
        if (stray) performGlobalAction(GLOBAL_ACTION_BACK)
        return stray
    }

    private fun scroll(s: Screen): Result {
        s.nodes.filter { it.scrollable }.maxByOrNull { it.box.top }?.node?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return Result.Retry
    }

    private fun recordLayout(s: Screen) {
        val sb = StringBuilder("Money Manager screen layout (${java.time.LocalDateTime.now()})\n")
        s.nodes.forEach { n ->
            sb.append(n.node.className?.toString()?.substringAfterLast('.'))
                .append(" text=").append(n.raw).append(" desc=").append(n.desc).append(" hint=").append(n.hint)
                .append(" id=").append(n.idLower).append(" click=").append(n.clickable).append(" edit=").append(n.editable)
                .append(" box=").append(n.box.toShortString()).append('\n')
        }
        Store.layoutFile(this).writeText(sb.toString())
        Store.setRecordLayout(this, false)
        toast("Layout recorded — share it from the Setup tab")
    }

    // ------------------------------------------------------------------ outcome

    private fun fail(reason: String) {
        val j = job ?: return
        job = null
        handler.removeCallbacks(tickRunnable)
        Store.update(this, j.txn.id) { it.copy(status = Status.FAILED, failReason = reason) }
        toast("Auto-fill stopped: $reason. Please finish this entry by hand.")
    }

    private fun finish(status: Status, message: String) {
        val j = job ?: return
        job = null
        Store.update(this, j.txn.id) { it.copy(status = status, failReason = null) }
        Notifier.cancel(this, j.txn)
        toast(message)
    }

    private fun toast(text: String) = handler.post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    companion object {
        val MM_PACKAGES = listOf("com.realbyteapps.moneymanagerfree", "com.realbyteapps.moneymanager")

        @Volatile
        private var instance: MmAutofillService? = null

        val isEnabled: Boolean get() = instance != null

        /** Compare texts ignoring emoji, punctuation, case and extra spaces: "SNB 💸" == "snb". */
        private fun norm(s: CharSequence?): String =
            s?.toString().orEmpty().filter { it.isLetterOrDigit() || it.isWhitespace() || it == '&' || it == '/' }
                .trim().replace(Regex("""\s+"""), " ").lowercase()

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
