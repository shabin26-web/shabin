package sms2mm.app.ui

import android.content.Context
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sms2mm.app.Store
import sms2mm.core.Category
import sms2mm.core.KnownBanks
import sms2mm.core.UserRules

/** Re-read on every Store write. */
@Composable
fun storeVersion(): Int {
    val v by Store.changes.collectAsState()
    return v
}

@Composable
fun ctx(): Context = LocalContext.current

/** Every Money Manager account name the user has mentioned anywhere. */
fun knownAccounts(rules: UserRules): List<String> = buildSet {
    addAll(rules.accounts.values)
    addAll(rules.accountGroups.keys)
    rules.banks.forEach { add(it.defaultAccount ?: it.bank.defaultAccount) }
    KnownBanks.Bank.entries.forEach { add(it.defaultAccount) }
    rules.transfers.forEach { add(it.otherAccount) }
}.filter { it.isNotBlank() }.sorted()

fun knownCategories(rules: UserRules): List<Category> = rules.keywords.map { it.category }.distinct().sortedBy { it.name + it.subcategory }

fun splitList(text: String): List<String> = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** A text field with tap-to-fill suggestions underneath (filtered by what was typed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestField(label: String, value: String, suggestions: List<String>, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val shown = suggestions.filter { it != value && (value.isBlank() || it.contains(value, ignoreCase = true)) }.take(8)
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { s -> AssistChip(onClick = { onChange(s) }, label = { Text(s, style = MaterialTheme.typography.labelSmall) }) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = Palette.Cyan,
    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
)
