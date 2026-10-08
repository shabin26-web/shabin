package sms2mm.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sms2mm.app.MmAutofillService
import sms2mm.app.Store
import sms2mm.core.UserRules

@Composable
fun SetupScreen(modifier: Modifier) {
    val context = ctx()
    val version = storeVersion()
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Store.setRecordLayout(context, Store.recordLayout(context)) }
    val exportRules = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(Store.rules(context).toJson().toByteArray()) }
        Toast.makeText(context, "Rules exported", Toast.LENGTH_SHORT).show()
    }
    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        try {
            val rules = UserRules.fromJson(text)
            Store.saveRules(context, rules)
            Toast.makeText(context, "Imported ${rules.keywords.size} keyword rules", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Not a valid rules file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val recording = remember(version) { Store.recordLayout(context) }
    val layout = Store.layoutFile(context)

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("1. Permissions")
        val sms = granted(Manifest.permission.RECEIVE_SMS)
        val notify = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        Text("Receive SMS: ${if (sms) "✔" else "✖"}    Notifications: ${if (notify) "✔" else "✖"}")
        if (!sms || !notify) Button(onClick = {
            askPermissions.launch(buildList {
                add(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }) { Text("Allow") }

        SectionTitle("2. Money Manager auto-fill")
        Text("Accessibility service: ${if (MmAutofillService.isEnabled) "on ✔" else "off ✖"}")
        Text(
            "Turn on \"SMS → Money Manager auto-fill\" under Accessibility → Installed apps. Android only lets it see Money Manager. It fills the entry and waits — you tap Save.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Accessibility settings") }

        SectionTitle("3. Banks and keywords")
        Text("Set sender names on the Banks tab, then keywords on the Keywords tab. Or import a rules file:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importRules.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import rules") }
            OutlinedButton(onClick = { exportRules.launch("sms2mm-rules.json") }) { Text("Export rules") }
        }

        SectionTitle("Tuning the auto-fill")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Record Money Manager's add screen on the next Add", Modifier.weight(1f))
            Switch(checked = recording, onCheckedChange = { Store.setRecordLayout(context, it) })
        }
        Text(
            "Saves the names and positions of Money Manager's buttons and fields (it includes your account and category names). Share it only if auto-fill needs fixing.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (layout.exists()) OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, layout.readText())
            context.startActivity(Intent.createChooser(send, "Share Money Manager layout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Share recorded layout") }

        SectionTitle("Security")
        Text(
            "• No internet permission: this app cannot send anything off the phone (check: Settings → Apps → SMS → Money Manager → Permissions).\n" +
                "• Only SMS from your bank senders are read.\n" +
                "• OTP / verification / password messages are dropped before anything else — only a count is kept.\n" +
                "• The SMS text is never saved: only date, amount, merchant, card last-4, account and category.\n" +
                "• Not included in phone backups.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
