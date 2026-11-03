package texteditor.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.FontRegistry
import texteditor.PRELOAD_BG_DARK
import texteditor.SoraEditorViewModel
import texteditor.ThemeRegistry
import texteditor.dialogs.FontChoiceDialog
import texteditor.dialogs.ThemeChoiceDialog
import ui.PaneShadow
import java.io.File

@Composable
fun EditorSettingsScreen(
    viewModel: SoraEditorViewModel,
    onNavigateUp: () -> Unit,
) {
    BackHandler(onBack = onNavigateUp)
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showFontPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    if (showThemePicker) {
        ThemeChoiceDialog(
            selected = viewModel.selectedTheme,
            onThemeSelected = { viewModel.setTheme(it) },
            onDismiss = { showThemePicker = false },
        )
    }
    if (showFontPicker) {
        FontChoiceDialog(
            fontOptions = viewModel.fontOptions,
            systemFontOptions = viewModel.systemFontOptions,
            onShowSystemFonts = { viewModel.loadSystemFonts() },
            selected = viewModel.selectedFont,
            onSelect = { viewModel.setFont(it) },
            onFontImported = { imported ->
                viewModel.refreshFontOptions()
                viewModel.setFont(imported.id)
            },
            onFontDeleted = { deleted ->
                val newFontId =
                    FontRegistry.deleteImportedFont(context, deleted.id, viewModel.selectedFont)
                if (newFontId != viewModel.selectedFont) {
                    viewModel.setFont(newFontId)
                }
                viewModel.refreshFontOptions()
            },
            onDismiss = { showFontPicker = false },
        )
    }
    val themeDisplayName =
        ThemeRegistry.availableThemes.find { it.id == viewModel.selectedTheme }?.displayName
            ?: viewModel.selectedTheme
    val fontDisplayName =
        viewModel.fontOptions.find { it.id == viewModel.selectedFont }?.displayName
            ?: viewModel.systemFontOptions.find { it.id == viewModel.selectedFont }?.displayName
            ?: File(viewModel.selectedFont).name
    val fontFamily = remember(viewModel.selectedFont) {
        FontFamily(FontRegistry.loadTypeface(context, viewModel.selectedFont))
    }
    Surface(
        modifier = Modifier.fillMaxSize(), color = Color(PRELOAD_BG_DARK),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    SectionHeader("Appearance")
                    SettingsRow(
                        label = "Theme",
                        description = "Placeholder: theme description",
                        enabled = false,
                        onClick = { showThemePicker = true },
                        trailing = { ValueWithChevron(themeDisplayName) },
                    )
                    SettingsRow(
                        label = "Font",
                        description = "Placeholder: font description",
                        onClick = { showFontPicker = true },
                        trailing = { ValueWithChevron(fontDisplayName, fontFamily) },
                    )
                    HorizontalDivider(thickness = 0.4.dp, color = DividerDefaults.color.copy(0.8f))
                    SectionHeader("Editor")
                    SettingsRow(
                        label = "Syntax highlighting",
                        description = "Placeholder: syntax highlighting description",
                        onClick = { viewModel.toggleSyntaxHighlight() },
                        trailing = {
                            Switch(
                                checked = viewModel.syntaxHighlight, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Line numbers",
                        description = "Placeholder: line numbers description",
                        onClick = { viewModel.toggleLineNumbers() },
                        trailing = {
                            Switch(
                                checked = viewModel.lineNumbers, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Minimap",
                        description = "Placeholder: minimap description",
                        onClick = { viewModel.toggleMinimap() },
                        trailing = { Switch(checked = viewModel.miniMap, onCheckedChange = null) },
                    )
                    SettingsRow(
                        label = "Minimap blocks",
                        description = "Placeholder: minimap blocks description",
                        enabled = viewModel.miniMap,
                        onClick = { viewModel.toggleMinimapBlocks() },
                        trailing = {
                            Switch(
                                checked = viewModel.miniMapBlocks,
                                onCheckedChange = null,
                                enabled = viewModel.miniMap
                            )
                        },
                    )
                    SettingsRow(
                        label = "Symbol bar",
                        description = "Placeholder: symbol bar description",
                        onClick = { viewModel.toggleSymbolBar() },
                        trailing = {
                            Switch(
                                checked = viewModel.symbolBar, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Invisible characters",
                        description = "Placeholder: invisible characters description",
                        onClick = { viewModel.toggleInvisibleChars() },
                        trailing = {
                            Switch(
                                checked = viewModel.invisibleChars, onCheckedChange = null
                            )
                        },
                    )
                }
                PaneShadow(false)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        ),
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        trailingContent = trailing,
    )
}

@Composable
private fun ValueWithChevron(value: String, fontFamily: FontFamily? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}
