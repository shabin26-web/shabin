package sms2mm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sms2mm.app.ActionReceiver
import sms2mm.app.PendingTxn
import sms2mm.app.Status
import sms2mm.app.Store
import sms2mm.core.Category
import sms2mm.core.KeywordRule
import sms2mm.core.TransferRule
import sms2mm.core.TxnType
import sms2mm.core.UserRules

/** Fix a captured transaction, optionally teaching the app its merchant ("learn from this"). */
@Composable
fun EditTxnDialog(id: String, onClose: () -> Unit) {
    val context = ctx()
    val original = remember(id) { Store.txn(context, id) }
    if (original == null) {
        LaunchedEffect(id) { onClose() }
        return
    }
    val rules = remember { Store.rules(context) }
    val accounts = remember(rules) { knownAccounts(rules) }
    val categories = remember(rules) { knownCategories(rules) }

    var type by remember { mutableStateOf(original.type) }
    var account by remember { mutableStateOf(original.account) }
    var toAccount by remember { mutableStateOf(original.toAccount.orEmpty()) }
    var category by remember { mutableStateOf(if (original.category == Category.UNCATEGORIZED) "" else original.category.name) }
    var sub by remember { mutableStateOf(original.category.subcategory.orEmpty()) }
    var note by remember { mutableStateOf(original.note.orEmpty()) }
    var description by remember { mutableStateOf(original.description.orEmpty()) }
    var learn by remember { mutableStateOf(original.merchant != null) }

    fun edited(): PendingTxn = original.copy(
        type = type,
        account = account.trim(),
        toAccount = toAccount.trim().takeIf { type == TxnType.TRANSFER && it.isNotEmpty() },
        category = if (type == TxnType.TRANSFER || category.isBlank()) Category.UNCATEGORIZED else Category(category.trim(), sub.trim().ifEmpty { null }),
        note = note.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
        needsReview = type != TxnType.TRANSFER && category.isBlank(),
        status = if (original.status == Status.FAILED) Status.PENDING else original.status,
        failReason = null,
    )

    fun save(): PendingTxn {
        val t = edited()
        Store.update(context, id) { t }
        if (learn && original.merchant != null) Store.updateRules(context) { learnFrom(it, original.merchant, t) }
        return t
    }

    val canAdd = account.isNotBlank() && (if (type == TxnType.TRANSFER) toAccount.isNotBlank() else category.isNotBlank())

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${original.currency} ${original.amount}" + (original.merchant?.let { " · $it" } ?: "")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TxnType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                SuggestField(if (type == TxnType.TRANSFER) "From account" else "Account", account, accounts, { account = it })
                if (type == TxnType.TRANSFER) {
                    SuggestField("To account", toAccount, accounts, { toAccount = it })
                } else {
                    SuggestField("Category", category, categories.map { it.name }.distinct(), { category = it })
                    SuggestField("Subcategory", sub, categories.filter { it.name == category }.mapNotNull { it.subcategory }.distinct(), { sub = it })
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                if (original.merchant != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = learn, onCheckedChange = { learn = it })
                        Text("Remember \"${original.merchant}\" for next time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canAdd, onClick = {
                val t = save()
                onClose()
                ActionReceiver.addToMoneyManager(context, t)
            }) { Text("Save & add") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClose) { Text("Cancel") }
                TextButton(onClick = { save(); onClose() }) { Text("Save") }
            }
        },
    )
}

/**
 * "Learn from this": add the merchant as a keyword to the rule that already gives the same
 * result, or create a new rule. Transfers become transfer rules.
 */
fun learnFrom(rules: UserRules, merchant: String, t: PendingTxn): UserRules {
    val keyword = merchant.trim()
    if (keyword.isEmpty()) return rules
    if (t.type == TxnType.TRANSFER) {
        // The "other" side is the account that is not one of the user's bank accounts.
        val bankAccounts = rules.accounts.values + rules.banks.map { it.defaultAccount ?: it.bank.defaultAccount }
        val other = listOfNotNull(t.account, t.toAccount).firstOrNull { it !in bankAccounts } ?: return rules
        val i = rules.transfers.indexOfFirst { it.otherAccount == other && it.description == t.description }
        val transfers = if (i >= 0) rules.transfers.toMutableList().also { list ->
            val r = list[i]
            if (r.keywords.none { it.equals(keyword, ignoreCase = true) }) list[i] = r.copy(keywords = r.keywords + keyword)
        } else rules.transfers + TransferRule(listOf(keyword), otherAccount = other, note = t.note, description = t.description)
        return rules.copy(transfers = transfers)
    }
    if (t.category == Category.UNCATEGORIZED) return rules
    val existing = rules.keywords.indexOfFirst { it.category == t.category && it.note == t.note && it.account == null && it.type == null }
    return if (existing >= 0) rules.addKeyword(existing, keyword)
    else rules.copy(keywords = rules.keywords + KeywordRule(listOf(keyword), t.category, note = t.note, description = t.description))
}
