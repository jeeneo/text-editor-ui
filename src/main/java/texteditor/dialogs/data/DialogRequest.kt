package texteditor.dialogs.data

/**
 * A question the editor is asking, written as data rather than as a composable call so the view
 * model can decide which dialog is open without knowing how any of them is drawn.
 */
sealed interface DialogRequest {
    data object Theme : DialogRequest

    data object Font : DialogRequest

    /** Picks the language of the open document; [detectedScope] labels the "Auto" choice. */
    data class Language(
        val detectedScope: String?,
    ) : DialogRequest

    /** The document [documentId] is being closed with unsaved changes. */
    data class ConfirmClose(
        val documentId: Long,
        val documentName: String,
    ) : DialogRequest

    data object Reload : DialogRequest

    data class FileInfo(
        val byteCount: Long,
        val characterCount: Int,
        val wordCount: Int,
        val lineCount: Int,
    ) : DialogRequest

    /** Shows [markdown] as rendered HTML, resolved against [baseUrl]. */
    data class Preview(
        val markdown: String,
        val title: String,
        val baseUrl: String?,
    ) : DialogRequest
}
