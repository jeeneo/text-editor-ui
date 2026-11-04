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
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults.contentColor
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.widget.CodeEditor
import texteditor.R
import texteditor.SaveButtonState
import texteditor.SoraEditorViewModel
import ui.theme.AppTheme

class ActionHost(val inOverflow: Boolean, val dismissMenu: () -> Unit)
typealias BarAction = @Composable ActionHost.() -> Unit

@Composable
fun ActionHost.ActionItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    closesMenu: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    if (inOverflow) {
        DropdownMenuItem(
            text = { Text(label, color = contentColor.copy(alpha = 0.8f)) },
            leadingIcon = {
                Icon(
                    icon, contentDescription = null, tint = contentColor.copy(alpha = 0.5f)
                )
            },
            trailingIcon = trailing,
            enabled = enabled,
            onClick = {
                onClick()
                if (closesMenu) dismissMenu()
            },
        )
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = contentColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ActionHost.ToggleItem(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: () -> Unit,
) = ActionItem(
    label, icon,
    trailing = { Checkbox(checked = checked, onCheckedChange = null) },
    onClick = onToggle,
)

@Composable
fun TopBarActions(
    title: String = "",
    cursorLine: Int = 1,
    cursorColumn: Int = 1,
    lineCount: Int = 0,
    editorRef: CodeEditor? = null,
    viewModel: SoraEditorViewModel? = null,
    saveButtonState: MutableState<SaveButtonState> = remember { mutableStateOf(SaveButtonState.IDLE) },
    onSave: () -> Unit = {},
    onSaveAs: () -> Unit = {},
    onPreview: () -> Unit = {},
    canUndo: MutableState<Boolean> = remember { mutableStateOf(false) },
    canRedo: MutableState<Boolean> = remember { mutableStateOf(false) },
    refreshUndoRedo: () -> Unit = {},
    showOverflowMenu: Boolean = false,
    onShowOverflowMenuChange: (Boolean) -> Unit = {},
    showSearchPanel: Boolean = false,
    onShowSearchPanelChange: (Boolean) -> Unit = {},
    onShowSettingsChange: (Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onCloseFile: () -> Unit = {},
    onShowLanguagePicker: () -> Unit = {},
) {
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }

    val encoding = viewModel?.encoding ?: "???"
    val statusText = if (lineCount > 0) {
        // TODO: add selection count
        "$cursorLine:$cursorColumn  $encoding"
    } else {
        encoding
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
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open files",
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
                IconButton(onClick = {
                    editMenuExpanded = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "",
                    )
                    DropdownMenu(
                        expanded = editMenuExpanded,
                        containerColor = MaterialTheme.colorScheme.onSecondary,
                        onDismissRequest = { editMenuExpanded = false }) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.CopyAll,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Copy line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentCut,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Cut line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Delete line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Clear line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = {
                                Text(
                                    "Replace line (clipboard)",
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.TextIncrease,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = {
                                Text(
                                    "Duplicate line",
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.text_icon_uppercase),
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = {
                                Text(
                                    "Convert to UPPERCASE",
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.text_icon_lowercase),
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = {
                                Text(
                                    "convert to lowercase",
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.text_icon_altercase),
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = {
                                Text(
                                    "Convert to aLtErNaTiNg case",
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { })
                    }
                }
                val isReadOnly = viewModel?.readOnly == true
                val actions = listOf<BarAction>(
                    {
                        ActionItem(
                            "File", Icons.AutoMirrored.Outlined.InsertDriveFile, trailing = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropUp,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(90f),
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }) {
                            fileMenuExpanded = true
                        }
                    },
                    {
                        ActionItem("Search", Icons.Outlined.Search, enabled = !isReadOnly) {
                            onShowSearchPanelChange(!showSearchPanel)
                        }
                    },
                    {
                        ActionItem(
                            "Preview", Icons.Outlined.Language, closesMenu = true
                        ) { onPreview() }
                    },
                    {
                        ActionItem(
                            "Syntax", Icons.Outlined.Code, closesMenu = true
                        ) { onShowLanguagePicker() }
                    },
                    { ActionItem("Previous position", Icons.AutoMirrored.Outlined.ArrowBack) },
                    { ActionItem("Next position", Icons.AutoMirrored.Outlined.ArrowForward) },
                    { ActionItem("Jump to line", Icons.AutoMirrored.Outlined.ShortText) },
                    {
                        ToggleItem(
                            "Word wrap",
                            Icons.AutoMirrored.Outlined.ShortText,
                            viewModel?.wordWrap == true
                        ) {
                            viewModel?.toggleWordWrap()
                        }
                    },
                    {
                        ToggleItem("Read-only mode", Icons.Filled.RemoveRedEye, isReadOnly) {
                            viewModel?.toggleReadOnly()
                        }
                    },
                    {
                        ActionItem(
                            "Settings", Icons.Filled.Settings, closesMenu = true
                        ) { onShowSettingsChange(true) }
                    },
                    { ActionItem("Close file", Icons.Outlined.Close) { onCloseFile() } },
                )
                val barHost = remember { ActionHost(inOverflow = false, dismissMenu = {}) }
                val menuHost = ActionHost(inOverflow = true) { onShowOverflowMenuChange(false) }
                val maxVisibleActions = with(LocalDensity.current) {
                    when (LocalWindowInfo.current.containerSize.width.toDp()) {
                        in 0.dp..460.dp -> 0
                        in 461.dp..840.dp -> 2
                        else -> 4
                    }
                }
                val visible = actions.take(maxVisibleActions)
                val overflow = actions.drop(maxVisibleActions)
                visible.forEach { action -> barHost.action() }
                if (overflow.isNotEmpty()) {
                    IconButton(onClick = { onShowOverflowMenuChange(true) }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        DropdownMenu(
                            containerColor = MaterialTheme.colorScheme.onSecondary,
                            expanded = showOverflowMenu,
                            onDismissRequest = { onShowOverflowMenuChange(false) },
                            properties = PopupProperties(
                                focusable = false, dismissOnBackPress = false
                            ),
                        ) {
                            overflow.forEach { action -> menuHost.action() }
                        }
                    }
                }
                DropdownMenu(
                    expanded = fileMenuExpanded,
                    containerColor = MaterialTheme.colorScheme.onSecondary,
                    onDismissRequest = { fileMenuExpanded = false }) {
                    Text(
                        modifier = Modifier.padding(
                            start = 12.dp, top = 8.dp, bottom = 8.dp
                        ),
                        color = contentColor.copy(alpha = 0.8f),
                        text = "File",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                            )
                        },
                        text = { Text("Save as", color = contentColor.copy(alpha = 0.8f)) },
                        onClick = {
                            fileMenuExpanded = false
                            onSaveAs()
                        })
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                            )
                        },
                        text = { Text("Reload", color = contentColor.copy(alpha = 0.8f)) },
                        onClick = { })
                    DropdownMenuItem(leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            modifier = Modifier.rotate(45f),
                            contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                        )
                    }, text = {
                        Text(
                            "Reload with charset",
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }, onClick = { })
                    DropdownMenuItem(leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FormatSize,
                            contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                        )
                    }, text = {
                        Text(
                            "Set the saved encoding",
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }, onClick = { })
                    DropdownMenuItem(leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                        )
                    }, text = {
                        Text(
                            "Linebreak",
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }, onClick = { })
                    DropdownMenuItem(leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null, tint = contentColor.copy(alpha = 0.5f),
                        )
                    }, text = {
                        Text(
                            "File info",
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }, onClick = { })
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
                            text = statusText,
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
