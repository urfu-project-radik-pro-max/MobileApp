package com.example.mobilereader.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.mobilereader.R
import com.example.mobilereader.data.translation.TranslatorApp
import com.example.mobilereader.data.translation.queryTranslatorApps
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.ui.theme.MobileReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberTranslatorApps(): List<TranslatorApp>? {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var apps by remember(context) { mutableStateOf<List<TranslatorApp>?>(null) }
    LaunchedEffect(context, lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            apps = withContext(Dispatchers.IO) { queryTranslatorApps(context) }
        }
    }
    return apps
}

@Composable
fun TranslatorPickerDialog(
    selected: String?,
    onChoose: (TranslatorApp) -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    val apps = rememberTranslatorApps()
    TranslatorPickerDialogContent(apps, selected, onChoose, onDismiss, busy, onReset)
}

@Composable
private fun TranslatorPickerDialogContent(
    apps: List<TranslatorApp>?,
    selected: String?,
    onChoose: (TranslatorApp) -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean,
    onReset: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settings_default_translator)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.translator_choose_description))
                when {
                    apps == null || busy -> CircularProgressIndicator()
                    apps.isEmpty() -> Text(stringResource(R.string.translator_none_available))
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup()) {
                        items(apps, key = { it.id }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .selectable(selected = app.id == selected, role = Role.RadioButton) { onChoose(app) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = app.id == selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    if (app.actionLabel != app.label) {
                                        Text(app.actionLabel, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.settings_close)) }
        },
        dismissButton = {
            if (onReset != null && selected != null) {
                TextButton(onClick = onReset, enabled = !busy) { Text(stringResource(R.string.translator_reset)) }
            }
        },
    )
}

@Preview(name = "Translator picker", showBackground = true)
@Composable
private fun TranslatorPickerPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        TranslatorPickerDialogContent(
            apps = listOf(
                TranslatorApp("dictionary", "Dictionary", "Explain selected text"),
                TranslatorApp("translator", "Translator", "Translate"),
            ),
            selected = "dictionary",
            onChoose = {},
            onDismiss = {},
            busy = false,
            onReset = {},
        )
    }
}
