package texteditor.dialogs.close

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TransitEnterexit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.dialogs.BaseDialog
import ui.theme.AppTheme

@Composable
fun UnsavedChangesDialog(
    documentName: String,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        title = "Unsaved changes",
        onDismissRequest = onDismiss,
        usePlatformDefaults = false,
        modifier = Modifier.widthIn(max = 250.dp)
    ) {
        Column {
            Text(
                "\"$documentName\" has unsaved changes.",
                style = MaterialTheme.typography.bodyMedium,
            )

            TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = "",
                    tint = LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "",
                    tint = LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Keep editing", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            TextButton(onClick = onMinimize, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.TransitEnterexit,
                    contentDescription = "",
                    tint = LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Minimize", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            TextButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "",
                    tint = LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Discard", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

        }
    }
}

@Preview(
    name = "Search bar with replace",
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun CustomSearchBarReplacePreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                UnsavedChangesDialog(
                    documentName = "",
                    onDiscard = {},
                    onSave = { },
                    onMinimize = {},
                    onDismiss = { })
            }
        }
    }
}
