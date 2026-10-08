package sms2mm.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sms2mm.app.Store
import sms2mm.core.BankSetup
import sms2mm.core.KnownBanks

/** Which senders to trust per bank, card/account → Money Manager account, and account → picker group. */
@Composable
fun BanksScreen(modifier: Modifier) {
    val context = ctx()
    val version = storeVersion()
    val rules = remember(version) { Store.rules(context) }
    var suggestions by remember { mutableStateOf<Map<KnownBanks.Bank, Map<String, Int>>?>(null) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) suggestions = scanInbox(context)
    }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Bank senders")
        Text(
            "Only SMS from these sender names are read. Scan the inbox to find them: it looks at your recent SMS on this phone only and saves nothing but the sender names you add.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) suggestions = scanInbox(context)
            else askSms.launch(Manifest.permission.READ_SMS)
        }) { Text("Scan inbox for bank senders") }

        KnownBanks.Bank.entries.forEach { bank ->
            val setup = rules.banks.firstOrNull { it.bank == bank }
            BankCard(bank, setup, suggestions?.get(bank).orEmpty()) { updated ->
                Store.updateRules(context) { cur ->
                    val others = cur.banks.filterNot { it.bank == bank }
                    cur.copy(banks = if (updated == null) others else others + updated)
                }
            }
        }

        SectionTitle("Card / account number → Money Manager account")
        MapEditor(
            entries = rules.accounts,
            keyLabel = "Last 4 digits",
            valueLabel = "Money Manager account",
            suggestions = knownAccounts(rules),
        ) { map -> Store.updateRules(context) { it.copy(accounts = map) } }

        SectionTitle("Account → group in Money Manager's account picker")
        Text("E.g. SNB 💸 → Accounts, SNB Credit Card → Card. Accounts without a group are looked for under \"Accounts\".", style = MaterialTheme.typography.bodySmall)
        MapEditor(
            entries = rules.accountGroups,
            keyLabel = "Account",
            valueLabel = "Group",
            suggestions = listOf("Cash", "Accounts", "Card", "Debit Card", "Savings", "Top-Up/Prepaid", "Debt", "Overdrafts", "Credit", "Insurance", "Others"),
            keySuggestions = knownAccounts(rules),
        ) { map -> Store.updateRules(context) { it.copy(accountGroups = map) } }
        Text("", Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun BankCard(bank: KnownBanks.Bank, setup: BankSetup?, found: Map<String, Int>, onChange: (BankSetup?) -> Unit) {
    var senders by remember(setup) { mutableStateOf(setup?.senders?.joinToString(", ").orEmpty()) }
    var account by remember(setup) { mutableStateOf(setup?.defaultAccount ?: bank.defaultAccount) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bank.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = setup != null, onCheckedChange = { on ->
                    onChange(if (on) BankSetup(bank, splitList(senders), account.ifBlank { null }) else null)
                })
            }
            OutlinedTextField(senders, { senders = it }, label = { Text("Sender names, comma-separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            found.filterKeys { f -> splitList(senders).none { it.equals(f, true) } }.forEach { (sender, count) ->
                TextButton(onClick = { senders = (splitList(senders) + sender).joinToString(", ") }) { Text("+ $sender ($count SMS look like ${bank.title})") }
            }
            OutlinedTextField(account, { account = it }, label = { Text("Default Money Manager account") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onChange(BankSetup(bank, splitList(senders), account.trim().ifBlank { null })) }) { Text("Save ${bank.title}") }
        }
    }
}

@Composable
private fun MapEditor(
    entries: Map<String, String>,
    keyLabel: String,
    valueLabel: String,
    suggestions: List<String>,
    keySuggestions: List<String> = emptyList(),
    onSave: (Map<String, String>) -> Unit,
) {
    entries.forEach { (k, v) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$k → $v", Modifier.weight(1f))
            TextButton(onClick = { onSave(entries - k) }) { Text("Remove") }
        }
    }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    if (keySuggestions.isEmpty()) {
        OutlinedTextField(key, { key = it }, label = { Text(keyLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    } else {
        SuggestField(keyLabel, key, keySuggestions, { key = it })
    }
    SuggestField(valueLabel, value, suggestions, { value = it })
    TextButton(enabled = key.isNotBlank() && value.isNotBlank(), onClick = {
        onSave(entries + (key.trim() to value.trim()))
        key = ""; value = ""
    }) { Text("Add") }
}

/** Sender → count of recent inbox SMS that match each known bank layout. Message text stays in memory. */
private fun scanInbox(context: Context): Map<KnownBanks.Bank, Map<String, Int>> {
    val found = mutableMapOf<KnownBanks.Bank, MutableMap<String, Int>>()
    context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
        null, null,
        "${Telephony.Sms.DATE} DESC",
    )?.use { c ->
        var n = 0
        while (c.moveToNext() && n++ < 500) {
            val sender = c.getString(0) ?: continue
            val bank = KnownBanks.detect(c.getString(1) ?: continue) ?: continue
            val m = found.getOrPut(bank) { mutableMapOf() }
            m[sender] = (m[sender] ?: 0) + 1
        }
    }
    return found
}
