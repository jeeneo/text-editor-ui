package texteditor.dialogs.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import texteditor.FontRegistry
import texteditor.SoraEditorViewModel
import texteditor.dialogs.appearance.FontChoiceDialog
import texteditor.dialogs.appearance.ThemeChoiceDialog
import texteditor.dialogs.close.UnsavedChangesDialog
import texteditor.dialogs.info.FileInfoDialog
import texteditor.dialogs.language.LanguagePickerDialog
import texteditor.dialogs.preview.MarkdownPreviewDialog
import texteditor.dialogs.reload.ReloadFileDialog

@Composable
fun DialogHost(
    viewModel: SoraEditorViewModel,
    onSaveAndClose: (Long) -> Unit,
    onMinimize: () -> Unit,
    onNothingLeft: () -> Unit,
) {
    val controller = viewModel.dialogs
    val request = controller.requests.lastOrNull() ?: return
    val context = LocalContext.current
    when (request) {
        is DialogRequest.Theme -> ThemeChoiceDialog(
            selected = viewModel.selectedTheme,
            onThemeSelected = viewModel::setTheme,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Font -> FontChoiceDialog(
            fontOptions = viewModel.fontOptions,
            systemFontOptions = viewModel.systemFontOptions,
            onShowSystemFonts = viewModel::loadSystemFonts,
            selected = viewModel.selectedFont,
            onSelect = viewModel::setFont,
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
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Language -> LanguagePickerDialog(
            detectedScope = request.detectedScope,
            selected = viewModel.languageOverride,
            onSelect = viewModel::setLanguage,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.ConfirmClose -> UnsavedChangesDialog(
            documentName = request.documentName,
            onDiscard = {
                controller.dismissTop()
                if (viewModel.close(request.documentId)) onNothingLeft()
            },
            onSave = {
                controller.dismissTop()
                onSaveAndClose(request.documentId)
            },
            onMinimize = {
                controller.dismissTop()
                onMinimize()
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Reload -> ReloadFileDialog(
            onReload = {
                controller.dismissTop()
                viewModel.reloadOpen()
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.FileInfo -> FileInfoDialog(
            info = request,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Preview -> MarkdownPreviewDialog(
            markdown = request.markdown,
            title = request.title,
            baseUrl = request.baseUrl,
            onDismiss = controller::dismissTop,
        )
    }
}
