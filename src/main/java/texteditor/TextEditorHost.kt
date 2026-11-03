package texteditor

import java.io.IOException
import java.io.InputStream

/**
 * Provider-agnostic access to the document being edited. Paths are opaque strings owned by the
 * host, so an app can supply them without sharing its file API with the editor.
 */
fun interface TextEditorHost {

    /** Persist [text] as the complete content of [path]. */
    fun save(path: String, text: String)

    fun openRead(path: String): InputStream =
        throw IOException("No read host installed for ${fileNameOf(path)}")

    /**
     * Ask the user where to create a document named [suggestedName], starting in
     * [initialDirectory]. Returns the chosen path, or `null` when the user cancels.
     */
    suspend fun saveAs(suggestedName: String, initialDirectory: String?): String? = null
}

/** The last segment of [path], e.g. "notes.txt" for "/sdcard/notes.txt". */
fun fileNameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

/** The parent directory of [path], or `null` when it has none. */
fun parentOf(path: String): String? =
    path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
