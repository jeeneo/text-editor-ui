package texteditor

import java.io.IOException
import java.io.InputStream

fun interface TextEditorHost {

    /**
     * Persist exactly [bytes] as the complete content of [path].
     *
     * The editor owns encoding (charset and BOM), so the host must write the payload as given
     * and must not transcode or wrap it in a text writer.
     */
    fun save(path: String, bytes: ByteArray)

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
