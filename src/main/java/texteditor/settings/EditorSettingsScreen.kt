package texteditor.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.FontRegistry
import texteditor.SoraEditorViewModel
import texteditor.ThemeRegistry
import texteditor.dialogs.FontChoiceDialog
import texteditor.dialogs.ThemeChoiceDialog
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
        modifier = Modifier.fillMaxSize()
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                SectionHeader("Appearance")
                SettingsClickRow(
                    label = "Theme",
                    value = themeDisplayName,
                    onClick = { showThemePicker = true },
                )
                SettingsClickRow(
                    label = "Font",
                    value = fontDisplayName,
                    valueFontFamily = fontFamily,
                    onClick = { showFontPicker = true },
                )
                SectionHeader("Editor")
                SettingsSwitchRow(
                    label = "Syntax highlighting",
                    checked = viewModel.syntaxHighlight,
                    onCheckedChange = { viewModel.toggleSyntaxHighlight() },
                )
                SettingsSwitchRow(
                    label = "Line numbers",
                    checked = viewModel.lineNumbers,
                    onCheckedChange = { viewModel.toggleLineNumbers() },
                )
                SettingsSwitchRow(
                    label = "Minimap",
                    checked = viewModel.miniMap,
                    onCheckedChange = { viewModel.toggleMinimap() },
                )
                SettingsSwitchRow(
                    label = "Minimap blocks",
                    checked = viewModel.miniMapBlocks,
                    enabled = viewModel.miniMap,
                    onCheckedChange = { viewModel.toggleMinimapBlocks() },
                )
                SettingsSwitchRow(
                    label = "Symbol bar",
                    checked = viewModel.symbolBar,
                    onCheckedChange = { viewModel.toggleSymbolBar() },
                )
                SettingsSwitchRow(
                    label = "Invisible characters",
                    checked = viewModel.invisibleChars,
                    onCheckedChange = { viewModel.toggleInvisibleChars() },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider(thickness = 0.4.dp, color = DividerDefaults.color.copy(0.8f))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsClickRow(
    label: String,
    value: String,
    valueFontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = valueFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onCheckedChange)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            enabled = enabled,
        )
    }
}
