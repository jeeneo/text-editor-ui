package texteditor.dialogs.close

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.dialogs.BaseDialog

@Composable
fun UnsavedChangesDialog(
    documentName: String,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(title = "Unsaved changes", onDismissRequest = onDismiss) {
        Column {
            Text(
                "\"$documentName\" has unsaved changes.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            Button(onClick = onMinimize, modifier = Modifier.fillMaxWidth()) {
                Text("Minimize")
            }

            TextButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) { Text("Discard") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Keep editing")
            }
        }
    }
}
