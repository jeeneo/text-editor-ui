package texteditor.dialogs.info

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import texteditor.dialogs.data.DialogRequest
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction

@Composable
fun FileInfoDialog(
    info: DialogRequest.FileInfo,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        onDismissRequest = onDismiss, title = "Statistics", content = {
            Column {
                Text("Byte count: ${info.byteCount}")
                Text("Character count: ${info.characterCount}")
                Text("Word count: ${info.wordCount}")
                Text("Line count: ${info.lineCount}")
            }
        }, trailingActions = listOf(
            DialogAction(text = "CLOSE", onClick = { onDismiss() })
        )
    )
}
