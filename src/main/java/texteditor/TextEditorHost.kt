package texteditor

import java.io.IOException
import java.io.InputStream
import java8.nio.file.Path

fun interface TextEditorHost {

    /** Persist [text] as the complete content of [path]. */
    fun save(path: Path, text: String)

    fun openRead(path: Path): InputStream =
        throw IOException("No read host installed for ${path.fileName}")

    /**
     * Ask the user where to create a document named [suggestedName], starting in
     * [initialDirectory]. Returns the chosen path, or `null` when the user cancels.
     */
    suspend fun saveAs(suggestedName: String, initialDirectory: Path?): Path? = null
}
