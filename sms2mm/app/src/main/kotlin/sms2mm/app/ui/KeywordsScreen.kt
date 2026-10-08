package sms2mm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sms2mm.app.Store
import sms2mm.app.label
import sms2mm.core.Category
import sms2mm.core.KeywordRule
import sms2mm.core.TransferRule
import sms2mm.core.TxnType

/** Edit keyword rules at any time: several keywords per rule, search, test box, transfers, ignore words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordsScreen(modifier: Modifier) {
    val context = ctx()
    val version = storeVersion()
    val rules = remember(version) { Store.rules(context) }
    var search by remember { mutableStateOf("") }
    var test by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) } // index, or -1 for new
    var editingTransfer by remember { mutableStateOf<Int?>(null) }
    var ignoreText by remember(rules) { mutableStateOf(rules.ignore.joinToString(", ")) }

    val filtered = rules.keywords.withIndex().filter { (_, r) ->
        search.isBlank() || r.keywords.any { it.contains(search, true) } || r.category.label().contains(search, true) || (r.note ?: "").contains(search, true)
    }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionTitle("Test a merchant name")
            OutlinedTextField(test, { test = it }, label = { Text("e.g. LULU HYPER MADINAH ROAD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (test.isNotBlank()) {
                val hit = rules.explain(test)
                val transfer = rules.transfers.firstOrNull { r -> r.keywords.any { test.contains(it, true) } }
                Text(
                    when {
                        transfer != null -> "→ Transfer with ${transfer.otherAccount}"
                        hit != null -> "→ ${hit.category.label()}" + (hit.note?.let { " · note \"$it\"" } ?: "")
                        else -> "→ No rule: you will be asked to choose a category"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text("Search rules") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { editing = -1 }, modifier = Modifier.padding(top = 8.dp)) { Text("+ Rule") }
            }
            Text("${rules.keywords.size} keyword rules · longest matching keyword wins", style = MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(filtered, key = { _, it -> "k${it.index}" }) { _, (i, r) ->
            Card(Modifier.fillMaxWidth().clickable { editing = i }) {
                Column(Modifier.padding(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { r.keywords.forEach { AssistChip(onClick = { editing = i }, label = { Text(it) }) } }
                    Text("→ ${r.category.label()}", style = MaterialTheme.typography.bodyMedium)
                    val extra = listOfNotNull(r.note?.let { "note: $it" }, r.account?.let { "only $it" }, r.type?.let { "only ${it.name.lowercase()}" })
                    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionTitle("Transfer rules (payer / payee → other account)")
            TextButton(onClick = { editingTransfer = -1 }) { Text("+ Transfer rule") }
        }
        itemsIndexed(rules.transfers, key = { i, _ -> "t$i" }) { i, r ->
            Card(Modifier.fillMaxWidth().clickable { editingTransfer = i }) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.keywords.joinToString(", "))
                    Text("→ transfer with ${r.otherAccount}" + (r.description?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionTitle("Ignore bank SMS containing")
            OutlinedTextField(ignoreText, { ignoreText = it }, label = { Text("Comma-separated, e.g. offer, cashback") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { Store.updateRules(context) { it.copy(ignore = splitList(ignoreText)) } }) { Text("Save ignore words") }
        }
    }

    editing?.let { i ->
        RuleDialog(
            rule = rules.keywords.getOrNull(i),
            accounts = knownAccounts(rules),
            categories = knownCategories(rules),
            onSave = { r -> Store.updateRules(context) { cur -> cur.copy(keywords = if (i >= 0) cur.keywords.toMutableList().also { it[i] = r } else cur.keywords + r) }; editing = null },
            onDelete = if (i >= 0) ({ Store.updateRules(context) { cur -> cur.copy(keywords = cur.keywords.filterIndexed { j, _ -> j != i }) }; editing = null }) else null,
            onClose = { editing = null },
        )
    }
    editingTransfer?.let { i ->
        TransferDialog(
            rule = rules.transfers.getOrNull(i),
            accounts = knownAccounts(rules),
            onSave = { r -> Store.updateRules(context) { cur -> cur.copy(transfers = if (i >= 0) cur.transfers.toMutableList().also { it[i] = r } else cur.transfers + r) }; editingTransfer = null },
            onDelete = if (i >= 0) ({ Store.updateRules(context) { cur -> cur.copy(transfers = cur.transfers.filterIndexed { j, _ -> j != i }) }; editingTransfer = null }) else null,
            onClose = { editingTransfer = null },
        )
    }
}

/** Keywords are chips: add with the field + "Add", remove by tapping a chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChips(keywords: List<String>, onChange: (List<String>) -> Unit) {
    var newKeyword by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keywords.forEach { k -> AssistChip(onClick = { onChange(keywords - k) }, label = { Text("$k  ✕") }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(newKeyword, { newKeyword = it }, label = { Text("Add keyword(s), comma-separated") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val add = splitList(newKeyword).filter { n -> keywords.none { it.equals(n, true) } }
            onChange(keywords + add)
            newKeyword = ""
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
    }
}

@Composable
private fun RuleDialog(
    rule: KeywordRule?,
    accounts: List<String>,
    categories: List<Category>,
    onSave: (KeywordRule) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var keywords by remember { mutableStateOf(rule?.keywords.orEmpty()) }
    var category by remember { mutableStateOf(rule?.category?.name.orEmpty()) }
    var sub by remember { mutableStateOf(rule?.category?.subcategory.orEmpty()) }
    var note by remember { mutableStateOf(rule?.note.orEmpty()) }
    var description by remember { mutableStateOf(rule?.description.orEmpty()) }
    var account by remember { mutableStateOf(rule?.account.orEmpty()) }
    var type by remember { mutableStateOf(rule?.type) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (rule == null) "New keyword rule" else "Edit keyword rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Matches when ANY keyword appears in the SMS merchant name (case doesn't matter).", style = MaterialTheme.typography.bodySmall)
                KeywordChips(keywords) { keywords = it }
                SuggestField("Category", category, categories.map { it.name }.distinct(), { category = it })
                SuggestField("Subcategory", sub, categories.filter { it.name == category }.mapNotNull { it.subcategory }.distinct(), { sub = it })
                OutlinedTextField(note, { note = it }, label = { Text("Note to write (optional)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, singleLine = true)
                SuggestField("Only for account (optional)", account, accounts, { account = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == null, onClick = { type = null }, label = { Text("Any") })
                    FilterChip(selected = type == TxnType.EXPENSE, onClick = { type = TxnType.EXPENSE }, label = { Text("Expense") })
                    FilterChip(selected = type == TxnType.INCOME, onClick = { type = TxnType.INCOME }, label = { Text("Income") })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = keywords.isNotEmpty() && category.isNotBlank(), onClick = {
                onSave(
                    KeywordRule(
                        keywords = keywords,
                        category = Category(category.trim(), sub.trim().ifEmpty { null }),
                        note = note.trim().ifEmpty { null },
                        description = description.trim().ifEmpty { null },
                        type = type,
                        account = account.trim().ifEmpty { null },
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TransferDialog(
    rule: TransferRule?,
    accounts: List<String>,
    onSave: (TransferRule) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var keywords by remember { mutableStateOf(rule?.keywords.orEmpty()) }
    var other by remember { mutableStateOf(rule?.otherAccount.orEmpty()) }
    var note by remember { mutableStateOf(rule?.note.orEmpty()) }
    var description by remember { mutableStateOf(rule?.description.orEmpty()) }
    var account by remember { mutableStateOf(rule?.account.orEmpty()) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (rule == null) "New transfer rule" else "Edit transfer rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When the payer/payee contains ANY keyword, the SMS becomes a Money Manager transfer with the other account.", style = MaterialTheme.typography.bodySmall)
                KeywordChips(keywords) { keywords = it }
                SuggestField("Other account", other, accounts, { other = it })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, singleLine = true)
                SuggestField("Only for bank account (optional)", account, accounts, { account = it })
            }
        },
        confirmButton = {
            TextButton(enabled = keywords.isNotEmpty() && other.isNotBlank(), onClick = {
                onSave(TransferRule(keywords, other.trim(), note.trim().ifEmpty { null }, description.trim().ifEmpty { null }, account.trim().ifEmpty { null }))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}
