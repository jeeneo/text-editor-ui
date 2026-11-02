package texteditor.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import java.util.regex.PatternSyntaxException

@Composable
fun CustomSearchBar(
    editor: CodeEditor?,
    initialQuery: String? = null,
) {
    var searchQuery by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var showReplaceField by rememberSaveable { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var matchCount by remember { mutableIntStateOf(0) }

    fun performSearch(
        query: String = searchQuery,
        isIgnoreCase: Boolean = ignoreCase,
        isUseRegex: Boolean = useRegex,
    ) {
        if (query.isEmpty()) {
            editor?.searcher?.stopSearch()
            matchCount = 0
            return
        }
        try {
            editor?.searcher?.search(query, EditorSearcher.SearchOptions(isIgnoreCase, isUseRegex))
            regexError = null
            matchCount = if (isUseRegex) {
                try {
                    Regex(if (isIgnoreCase) "(?i)$query" else query).findAll(
                        editor?.text?.toString() ?: "",
                    ).count()
                } catch (_: Exception) {
                    0
                }
            } else {
                val haystack = editor?.text?.toString() ?: ""
                val needle = if (isIgnoreCase) query.lowercase() else query
                val source = if (isIgnoreCase) haystack.lowercase() else haystack
                var count = 0
                var idx = 0
                while (true) {
                    idx = source.indexOf(needle, idx)
                    if (idx == -1) break
                    count++
                    idx += needle.length
                }
                count
            }
        } catch (e: PatternSyntaxException) {
            regexError = e.message
            matchCount = 0
        }
    }

    LaunchedEffect(editor, initialQuery) {
        val query = initialQuery ?: return@LaunchedEffect
        if (query.isNotBlank()) {
            editor?.searcher?.isEnsureOccurrenceVisible = true
            performSearch(query)
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (showReplaceField) 0f else 180f,
        label = "chevron",
    )

    Surface(tonalElevation = 3.dp, color = Color(0xFF2A2A2A)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        .exclude(WindowInsets.ime),
                )
        ) {
            HorizontalDivider(thickness = 0.4.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { showReplaceField = !showReplaceField }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (showReplaceField) "Hide replace" else "Show replace",
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        performSearch(it)
                    },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    isError = regexError != null,
                    supportingText = regexError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext()
                    }),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = false,
                                ),
                            ) {
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = ignoreCase, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ignore case")
                                    }
                                }, onClick = {
                                    val newValue = !ignoreCase
                                    ignoreCase = newValue
                                    performSearch(isIgnoreCase = newValue)
                                })
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = useRegex, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Regex")
                                    }
                                }, onClick = {
                                    val newValue = !useRegex
                                    useRegex = newValue
                                    performSearch(isUseRegex = newValue)
                                })
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            AnimatedVisibility(visible = showReplaceField) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                ) {
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        placeholder = { Text("Replace") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, bottom = 4.dp),
            ) {
                TextButton(
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoPrevious() },
                    enabled = searchQuery.isNotEmpty(),
                ) { Text("Prev") }
                TextButton(
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext() },
                    enabled = searchQuery.isNotEmpty(),
                ) { Text("Next") }
                if (showReplaceField) {
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                editor?.searcher?.replaceCurrentMatch(replaceText)
                                editor?.searcher?.gotoNext()
                            }
                        },
                        enabled = searchQuery.isNotEmpty(),
                    ) { Text("Replace") }
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) editor?.searcher?.replaceAll(replaceText)
                        },
                        enabled = searchQuery.isNotEmpty(),
                    ) { Text("Replace all") }
                }
                Spacer(Modifier.weight(1f))
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = if (regexError != null) {
                            "Error"
                        } else if (matchCount == 0) {
                            "No results"
                        } else {
                            "$matchCount result${if (matchCount == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (regexError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}
