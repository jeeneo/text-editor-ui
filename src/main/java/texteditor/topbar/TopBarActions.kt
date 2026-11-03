package texteditor.topbar

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import texteditor.SaveButtonState
import texteditor.SoraEditorViewModel
import texteditor.TopBarAction
import texteditor.dialogs.LanguagePickerDialog
import ui.theme.AppTheme
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun TopBarActions(
    title: String = "",
    editorRef: CodeEditor? = null,
    viewModel: SoraEditorViewModel? = null,
    saveButtonState: MutableState<SaveButtonState> = remember { mutableStateOf(SaveButtonState.IDLE) },
    onSave: () -> Unit = {},
    onPreview: () -> Unit = {},
    canUndo: MutableState<Boolean> = remember { mutableStateOf(false) },
    canRedo: MutableState<Boolean> = remember { mutableStateOf(false) },
    refreshUndoRedo: () -> Unit = {},
    showOverflowMenu: Boolean = false,
    onShowOverflowMenuChange: (Boolean) -> Unit = {},
    showSearchPanel: Boolean = false,
    onShowSearchPanelChange: (Boolean) -> Unit = {},
    onShowSettingsChange: (Boolean) -> Unit = {},
    detectedScope: String? = null,
    languageOverride: String? = null,
    onLanguageSelected: (String?) -> Unit = {},
) {
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            detectedScope = detectedScope,
            selected = languageOverride,
            onSelect = onLanguageSelected,
            onDismiss = { showLanguagePicker = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "",
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "",
                        modifier = Modifier.rotate(45f),
                    )
                }
                IconButton(
                    onClick = {
                        editorRef?.undo()
                        refreshUndoRedo()
                    },
                    enabled = canUndo.value,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = {
                        editorRef?.redo()
                        refreshUndoRedo()
                    },
                    enabled = canRedo.value,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = onSave,
                    enabled = viewModel?.isModified == true && saveButtonState.value == SaveButtonState.IDLE,
                ) {
                    Crossfade(
                        targetState = saveButtonState.value,
                        animationSpec = tween(durationMillis = 300),
                        label = "saveButtonMorph",
                    ) { state ->
                        @Suppress("CascadeIf") if (state == SaveButtonState.IDLE) Icon(
                            Icons.Filled.Save, contentDescription = "Save"
                        )
                        else if (state == SaveButtonState.SAVED) Icon(
                            Icons.Filled.Check,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        else if (state == SaveButtonState.ERROR) Icon(
                            Icons.Filled.Error,
                            contentDescription = "Save error",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "",
                    )
                }
                val isReadOnly = viewModel?.readOnly == true
                val flexibleActions = listOf(
                    TopBarAction("File", Icons.AutoMirrored.Outlined.InsertDriveFile) { },
                    TopBarAction(
                        "Search",
                        Icons.Outlined.Search,
                        enabled = !isReadOnly,
                    ) { onShowSearchPanelChange(!showSearchPanel) },
                    TopBarAction("Preview", Icons.Outlined.Language) {
                        onShowOverflowMenuChange(false)
                        onPreview()
                    },
                    TopBarAction("Syntax", Icons.Outlined.Code) {
                        onShowOverflowMenuChange(false)
                        showLanguagePicker = true
                    },
                    TopBarAction("Previous position", Icons.AutoMirrored.Outlined.ArrowBack) { },
                    TopBarAction("Next position", Icons.AutoMirrored.Outlined.ArrowForward) { },
                    TopBarAction("Jump to line", Icons.AutoMirrored.Outlined.ShortText) { },
                    TopBarAction(
                        "Word wrap",
                        Icons.AutoMirrored.Outlined.ShortText,
                        checked = viewModel?.wordWrap,
                    ) { viewModel?.toggleWordWrap() },
                    TopBarAction(
                        "Read-only mode",
                        Icons.Filled.RemoveRedEye,
                        checked = viewModel?.readOnly,
                    ) { viewModel?.toggleReadOnly() },
                    TopBarAction(
                        "Code completion",
                        Icons.Outlined.Copyright,
                        checked = viewModel?.autoCompletion,
                    ) { viewModel?.toggleAutoCompletion() },
                    TopBarAction("Settings", Icons.Filled.Settings) {
                        onShowSettingsChange(true)
                        onShowOverflowMenuChange(false)
                    },
                    TopBarAction("Close file", Icons.Outlined.Close) { },
                )
                val maxVisibleActions = with(LocalDensity.current) {
                    when (LocalWindowInfo.current.containerSize.width.toDp()) {
                        in 0.dp..460.dp -> 0
                        in 461.dp..840.dp -> 2
                        else -> 4
                    }
                }
                val visibleActions = flexibleActions.take(maxVisibleActions)
                val overflowActions = flexibleActions.drop(maxVisibleActions)
                visibleActions.forEach { action ->
                    IconButton(onClick = action.onClick, enabled = action.enabled) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
                if (overflowActions.isNotEmpty()) {
                    IconButton(onClick = { onShowOverflowMenuChange(true) }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { onShowOverflowMenuChange(false) },
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnBackPress = false,
                            ),
                        ) {
                            overflowActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                    trailingIcon = action.checked?.let {
                                        { Checkbox(checked = it, onCheckedChange = null) }
                                    },
                                    enabled = action.enabled,
                                    onClick = { action.onClick() },
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(thickness = 0.4.dp, color = DividerDefaults.color.copy(0.8f))
                val alpha = if (viewModel?.isModified ?: false) 0.9f else 0.7f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 14.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 14.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "1:1 UTF-8",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun TsPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                TopBarActions()
            }
        }
    }
}
