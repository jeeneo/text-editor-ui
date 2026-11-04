package texteditor.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import texteditor.OpenDocument
import texteditor.PRELOAD_BG_DARK
import texteditor.fileNameOf
import texteditor.parentOf
import ui.SimpleShadow

@Composable
fun EditorDrawerSheet(
    documents: List<OpenDocument>,
    activeDocumentId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onMinimize: () -> Unit,
    width: Dp = DrawerDefaults.MaximumDrawerWidth,
) {
    ModalDrawerSheet(
        modifier = Modifier.requiredWidth(width),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Start
        ),
    ) {
        Text(
            text = "Text editor",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(PRELOAD_BG_DARK))
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (documents.isEmpty()) {
                        item {
                            Text(
                                text = "No files open",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(documents, key = { it.id }) { document ->
                            DocumentRow(
                                document = document,
                                selected = document.id == activeDocumentId,
                                onSelect = { onSelect(document.id) },
                                onClose = { onClose(document.id) },
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp),
                ) {
                    Button(
                        onClick = onMinimize,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.inverseSurface
                        )
                    ) {
                        Text("MINIMIZE")
                    }
                }
            }
            SimpleShadow()
        }
    }
}

@Composable
private fun DocumentRow(
    document: OpenDocument,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val name = document.name ?: document.path?.let(::fileNameOf) ?: "New file"
    val location = document.path?.let(::parentOf) ?: document.directory ?: "Not saved yet"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onSelect)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(end = 10.dp),
            imageVector = Icons.Filled.Edit,
            contentDescription = null
        )
        Column(modifier = Modifier.weight(1f)) {
            val thisText = if (document.isModified) "*$name" else name
            Text(
                text = thisText,
                fontWeight = FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close $name")
        }
    }
}
