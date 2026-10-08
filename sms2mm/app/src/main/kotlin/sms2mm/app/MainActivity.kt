package sms2mm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import sms2mm.app.ui.BanksScreen
import sms2mm.app.ui.EditTxnDialog
import sms2mm.app.ui.KeywordsScreen
import sms2mm.app.ui.PendingScreen
import sms2mm.app.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private var editId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        editId = intent.getStringExtra(EXTRA_EDIT)
        setContent { App(editId) { editId = it } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_EDIT)?.let { editId = it }
    }

    companion object {
        const val EXTRA_EDIT = "edit_id"
    }
}

private val tabs = listOf("🧾" to "Pending", "🔑" to "Keywords", "🏦" to "Banks", "⚙️" to "Setup")

@Composable
private fun App(editId: String?, setEditId: (String?) -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                0 -> PendingScreen(modifier, onEdit = setEditId)
                1 -> KeywordsScreen(modifier)
                2 -> BanksScreen(modifier)
                else -> SetupScreen(modifier)
            }
        }
        if (editId != null) EditTxnDialog(editId, onClose = { setEditId(null) })
    }
}
