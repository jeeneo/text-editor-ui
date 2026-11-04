package texteditor.topbar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import texteditor.AppToasts
import texteditor.R
import texteditor.SaveButtonState
import texteditor.SoraEditorViewModel
import texteditor.encoding.EncodingPickerMode
import ui.theme.AppTheme
import kotlin.math.abs

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
    onReload: () -> Unit = {},
    onFileInfo: () -> Unit = {},
    onPreview: () -> Unit = {},
    onDocumentOptions: () -> Unit = {},
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
    val positionHistory = remember(editorRef) { mutableStateListOf<Int>() }
    var positionIndex by remember(editorRef) { mutableIntStateOf(-1) }
    fun showPosition(offset: Int) {
        val editor = editorRef ?: return
        val position = editor.text.indexer.getCharPosition(offset.coerceIn(0, editor.text.length))
        editor.setSelection(position.line, position.column, true)
    }
    LaunchedEffect(editorRef, cursorLine) {
        val editor = editorRef ?: return@LaunchedEffect
        val offset = editor.cursor.left
        if (offset == positionHistory.getOrNull(positionIndex)) return@LaunchedEffect
        while (positionHistory.size > positionIndex + 1) {
            positionHistory.removeAt(positionHistory.lastIndex)
        }
        positionHistory.add(offset)
        positionIndex = positionHistory.lastIndex
    }
    var selected by remember(editorRef) { mutableIntStateOf(0) }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        fun refreshSelection() {
            val cursor = editor.cursor
            selected = abs(cursor.right - cursor.left)
        }
        refreshSelection()
        val receipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            refreshSelection()
        }
        onDispose { receipt.unsubscribe() }
    }
    val encoding = viewModel?.encodingLabel ?: "???"
    val lineEnding = viewModel?.lineEndingLabel.orEmpty()
    val selectedText = if (selected >= 1) "($selected)  " else ""
    val statusSuffix = if (lineEnding.isEmpty()) encoding else "$encoding  $lineEnding"
    val statusText = if (lineCount > 0) {
        "$cursorLine:$cursorColumn  $selectedText$statusSuffix"
    } else {
        statusSuffix
    }
    val context = LocalContext.current

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
                        properties = PopupProperties(
                            focusable = false, dismissOnBackPress = false
                        ),
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
                            onClick = copyLine@{
                                editMenuExpanded = false
                                val editor = editorRef ?: return@copyLine
                                val line = editor.cursor.leftLine
                                context.copyToClipboard(
                                    "Line ${line + 1}", editor.text.getLineString(line)
                                )
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentCut,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Cut line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = cutLine@{
                                editMenuExpanded = false
                                val editor = editorRef ?: return@cutLine
                                val line = editor.cursor.leftLine
                                context.copyToClipboard(
                                    "Line ${line + 1}", editor.text.getLineString(line)
                                )
                                editor.deleteLine(line)
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Delete line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = deleteLine@{
                                editMenuExpanded = false
                                val editor = editorRef ?: return@deleteLine
                                editor.deleteLine(editor.cursor.leftLine)
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            },
                            text = { Text("Clear line", color = contentColor.copy(alpha = 0.8f)) },
                            onClick = clearLine@{
                                editMenuExpanded = false
                                val editor = editorRef ?: return@clearLine
                                editor.clearLine(editor.cursor.leftLine)
                            })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Replace line (paste)", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = replaceLine@{
                            editMenuExpanded = false
                            val editor = editorRef ?: return@replaceLine
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ?: return@replaceLine
                            val clip = clipboard.primaryClip
                            if (clip == null || clip.itemCount == 0) return@replaceLine
                            val replacement = clip.getItemAt(0).coerceToText(context).toString()
                                .removeSuffix("\r\n").removeSuffix("\n")
                            editor.replaceLine(editor.cursor.leftLine, replacement)
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.TextIncrease,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Duplicate line", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = duplicateLine@{
                            editMenuExpanded = false
                            val editor = editorRef ?: return@duplicateLine
                            editor.duplicateLine(editor.cursor.leftLine)
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.text_icon_uppercase),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Convert to UPPERCASE", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.uppercase() }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.text_icon_lowercase),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "convert to lowercase", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.lowercase() }
                        })
                        LongClickMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.text_icon_altercase),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Convert to aLtErNaTiNg", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.toAlternatingCase() }
                        }, onLongClick = {
                            editMenuExpanded = false
                            AppToasts.show(context, "it has been done")
                            editorRef?.convertCase { it.uppercase().replace("I", "i") }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.text_icon_caseinvert),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Invert case", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.toInvertedCase() }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Title,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Convert To Title Case", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.toTitleCase() }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.TextFormat,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Convert to sentence case", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = sentenceCase@{
                            editMenuExpanded = false
                            editorRef?.convertCase { it.toSentenceCase() }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Numbers,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                            )
                        }, text = {
                            Text(
                                "Numbers to words", color = contentColor.copy(alpha = 0.8f)
                            )
                        }, onClick = {
                            editMenuExpanded = false
                            editorRef?.convertCase { it.numbersToWords() }
                        })
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
                    {
                        ActionItem(
                            "Previous position",
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            enabled = positionIndex > 0,
                            closesMenu = true,
                        ) {
                            if (positionIndex > 0) {
                                positionIndex--
                                showPosition(positionHistory[positionIndex])
                            }
                        }
                    },
                    {
                        ActionItem(
                            "Next position",
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            enabled = positionIndex < positionHistory.lastIndex,
                            closesMenu = true,
                        ) {
                            if (positionIndex < positionHistory.lastIndex) {
                                positionIndex++
                                showPosition(positionHistory[positionIndex])
                            }
                        }
                    },
                    {
                        ActionItem(
                            "Jump to line",
                            Icons.AutoMirrored.Outlined.ShortText,
                            closesMenu = true,
                        ) { viewModel?.openJumpToLineDialog(cursorLine, lineCount) }
                    },
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
                        DropdownMenu(
                            expanded = fileMenuExpanded,
                            containerColor = MaterialTheme.colorScheme.onSecondary,
                            properties = PopupProperties(
                                focusable = false, dismissOnBackPress = false
                            ),
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
                                        contentDescription = null,
                                        tint = contentColor.copy(alpha = 0.5f),
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
                                        contentDescription = null,
                                        tint = contentColor.copy(alpha = 0.5f),
                                    )
                                },
                                text = { Text("Reload", color = contentColor.copy(alpha = 0.8f)) },
                                onClick = {
                                    fileMenuExpanded = false
                                    onReload()
                                })
                            DropdownMenuItem(leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    modifier = Modifier.rotate(45f),
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }, text = {
                                Text(
                                    "Reopen with encoding",
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }, onClick = {
                                onShowOverflowMenuChange(false)
                                fileMenuExpanded = false
                                viewModel?.openEncodingPicker(EncodingPickerMode.REOPEN)
                            })
                            DropdownMenuItem(leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.FormatSize,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }, text = {
                                Text(
                                    "Save with encoding",
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }, onClick = {
                                onShowOverflowMenuChange(false)
                                fileMenuExpanded = false
                                viewModel?.openEncodingPicker(EncodingPickerMode.SAVE)
                            })
                            DropdownMenuItem(leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }, text = {
                                Text(
                                    "Line break",
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }, onClick = {
                                onShowOverflowMenuChange(false)
                                fileMenuExpanded = false
                                viewModel?.openLineEndingPicker()
                            })
                            DropdownMenuItem(leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }, text = {
                                Text(
                                    "Statistics",
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }, onClick = {
                                onShowOverflowMenuChange(false)
                                fileMenuExpanded = false
                                onFileInfo()
                            })
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
                            .combinedClickable(
                                onClick = onDocumentOptions,
                                onLongClick = {
                                    context.copyToClipboard(
                                        "filename", title
                                    )
                                },
                            )
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
                            .combinedClickable(
                                onClick = onDocumentOptions,
                                onLongClick = {
                                    context.copyToClipboard(
                                        "status text", statusText
                                    )
                                },
                            )
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

// small funs
private fun CodeEditor.convertCase(transform: (String) -> String) {
    val content = text
    val cursor = cursor
    val hasSelection = cursor.left != cursor.right
    val start = if (hasSelection) {
        minOf(cursor.left, cursor.right)
    } else {
        content.getCharIndex(cursor.leftLine, 0)
    }
    val end = if (hasSelection) {
        maxOf(cursor.left, cursor.right)
    } else {
        start + content.getLineString(cursor.leftLine).length
    }
    if (start >= end) return
    content.replace(start, end, transform(content.subSequence(start, end).toString()))
}

private fun String.toTitleCase(): String {
    val builder = StringBuilder(length)
    var atWordStart = true
    for (char in this) {
        when {
            char.isLetter() -> {
                builder.append(if (atWordStart) char.uppercaseChar() else char.lowercaseChar())
                atWordStart = false
            }

            // An apostrophe stays inside the word, so "don't" becomes "Don't".
            char == '\'' -> builder.append(char)

            else -> {
                builder.append(char)
                atWordStart = !char.isDigit()
            }
        }
    }
    return builder.toString()
}

private fun String.toSentenceCase(): String {
    val builder = StringBuilder(length)
    var atSentenceStart = true
    for (char in this) {
        when {
            char.isLetter() -> {
                builder.append(
                    if (atSentenceStart) char.uppercaseChar() else char.lowercaseChar()
                )
                atSentenceStart = false
            }

            char.isDigit() -> {
                builder.append(char)
                atSentenceStart = false
            }

            else -> {
                builder.append(char)
                if (char == '.' || char == '!' || char == '?' || char == '\n') {
                    atSentenceStart = true
                }
            }
        }
    }
    return builder.toString()
}

private fun String.toInvertedCase(): String {
    val builder = StringBuilder(length)
    for (char in this) {
        builder.append(
            when {
                char.isUpperCase() -> char.lowercaseChar()
                char.isLowerCase() -> char.uppercaseChar()
                else -> char
            }
        )
    }
    return builder.toString()
}

private fun String.toAlternatingCase(): String =
    if (this == alternate(startUpper = false)) alternate(startUpper = true)
    else alternate(startUpper = false)

private fun String.alternate(startUpper: Boolean): String {
    val builder = StringBuilder(length)
    var upper = startUpper
    for (char in this) {
        builder.append(
            when {
                !char.isLetter() -> char
                upper -> char.uppercaseChar()
                else -> char.lowercaseChar()
            }
        )
        if (char.isLetter()) upper = !upper
    }
    return builder.toString()
}

private fun CodeEditor.replaceLine(line: Int, replacement: String) {
    val content = text
    val start = content.getCharIndex(line, 0)
    content.replace(start, start + content.getLineString(line).length, replacement)
}

private fun CodeEditor.clearLine(line: Int) {
    val content = text
    val start = content.getCharIndex(line, 0)
    content.replace(start, start + content.getLineString(line).length, "")
}

private fun CodeEditor.duplicateLine(line: Int) {
    val content = text
    val lineText = content.getLineString(line)
    val end = content.getCharIndex(line, 0) + lineText.length
    content.replace(end, end, "\n$lineText")
}

private fun CodeEditor.deleteLine(line: Int) {
    val content = text
    val hasNext = line + 1 < content.lineCount
    val start = when {
        hasNext -> content.getCharIndex(line, 0)
        line > 0 -> content.getCharIndex(line - 1, 0) + content.getLineString(line - 1).length
        else -> 0
    }
    val end = if (hasNext) content.getCharIndex(line + 1, 0) else content.length
    content.replace(start, end, "")
}

@Composable
private fun LongClickMenuItem(
    text: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .defaultMinSize(minWidth = 112.dp, minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingIcon()
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            text()
        }
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    @Suppress("UsePropertyAccessSyntax") clipboard.setPrimaryClip(
        ClipData.newPlainText(
            label, text
        )
    )
}

private val ONES = arrayOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)
private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
)
private val SCALES = arrayOf(
    "", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion",
    "sextillion", "septillion", "octillion", "nonillion", "decillion",
)

private val NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")

private fun String.numbersToWords(): String = NUMBER_REGEX.replace(this) { match ->
    val intPart = match.value.substringBefore('.')
    val fracPart = match.value.substringAfter('.', "")
    val words = digitsToWords(intPart)
    if (fracPart.isEmpty()) words
    else "$words point " + fracPart.map { ONES[it - '0'] }.joinToString(" ")
}

private fun digitsToWords(digits: String): String {
    val trimmed = digits.trimStart('0')
    if (trimmed.isEmpty()) return "zero"
    if (trimmed.length > SCALES.size * 3) {
        return trimmed.map { ONES[it - '0'] }.joinToString(" ")
    }
    val groups = trimmed.reversed().chunked(3).map { it.reversed().toInt() }
    val parts = mutableListOf<String>()
    for (i in groups.indices.reversed()) {
        if (groups[i] == 0) continue
        val words = threeDigitsToWords(groups[i])
        parts += if (SCALES[i].isEmpty()) words else "$words ${SCALES[i]}"
    }
    return parts.joinToString(" ")
}

private fun threeDigitsToWords(n: Int): String {
    val parts = mutableListOf<String>()
    val hundreds = n / 100
    val rest = n % 100
    if (hundreds > 0) parts += "${ONES[hundreds]} hundred"
    if (rest > 0) {
        parts += if (rest < 20) {
            ONES[rest]
        } else {
            val tens = TENS[rest / 10]
            val ones = rest % 10
            if (ones == 0) tens else "$tens-${ONES[ones]}"
        }
    }
    return parts.joinToString(" ")
}
