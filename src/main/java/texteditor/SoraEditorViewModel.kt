@file:Suppress("SpellCheckingInspection")

package texteditor

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import texteditor.dialogs.data.DialogController
import texteditor.dialogs.data.DialogRequest
import kotlin.time.Duration.Companion.milliseconds
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry as EditorThemeRegistry

private val Context.editorPrefs: SharedPreferences
    get() = getSharedPreferences("text_editor_prefs", Context.MODE_PRIVATE)

enum class SaveButtonState { IDLE, SAVED, ERROR }

data class TopBarAction(
    val label: String,
    val icon: ImageVector,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private const val GRAMMAR_EDIT_DEBOUNCE_MS = 400L

private const val NEW_FILE_NAME = "untitled.txt"

private const val TAG = "SoraEditor"

fun buildColorScheme(): TextMateColorScheme =
    TextMateColorScheme.create(EditorThemeRegistry.getInstance())

val SYMBOLS = listOf(
    ">>" to "\t",
    "{" to "{}",
    "}" to "}",
    "(" to "(",
    ")" to ")",
    "=" to "=",
    "," to ",",
    "." to ".",
    ";" to ";",
    "\"" to "\"",
    "?" to "?",
    "+" to "+",
    "-" to "-",
    "*" to "*",
    "/" to "/",
    "<" to "<",
    ">" to ">",
    "[" to "[",
    "]" to "]",
    ":" to ":",
)

private object PrefKeys {
    const val WORD_WRAP = "word_wrap"
    const val SYNTAX_HIGHLIGHT = "syntax_highlight"
    const val MINIMAP_SHOWN = "minimap_shown"
    const val MINIMAP_BLOCKS = "minimap_blocks"
    const val SYMBOL_BAR = "symbol_bar"
    const val LINE_NUMBERS = "line_numbers"
    const val SELECTED_THEME = "selected_theme"
    const val TEXT_SIZE_PX = "text_size_px"
    const val SELECTED_FONT = "selected_font"
    const val INVISIBLE_CHARS = "invisible_chars"
    const val READ_ONLY = "read_only"
    const val AUTO_COMPLETION = "auto_completion"
}

private object EditorDefaults {
    const val WORD_WRAP = true
    const val SYNTAX_HIGHLIGHT = true
    const val TEXT_SIZE_PX = 0f
    const val MINIMAP_SHOWN = false
    const val MINIMAP_BLOCKS = false
    const val SYMBOL_BAR = true
    const val LINE_NUMBERS = true
    const val INVISIBLE_CHARS = true
    const val READ_ONLY = false
    const val AUTO_COMPLETION = true
    const val SELECTED_THEME = "darcula"
    const val SELECTED_FONT = "fonts/CascadiaCode-Regular.ttf"
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(
        val message: String,
    ) : LoadState
}

@Immutable
data class OpenDocument(
    val id: Long,
    val path: String?,
    val name: String?,
    val directory: String?,
    val isModified: Boolean,
)

class SoraEditorViewModel(
    application: Application,
    private val host: TextEditorHost,
) : AndroidViewModel(application) {
    private var grammarLoadJob: Job? = null
    private var editGrammarJob: Job? = null
    private val prefs get() = getApplication<Application>().editorPrefs
    private var openStartedAt = 0L

    private val activeSession: DocumentSession?
        get() = DocumentSessions.active
    var openDocuments: List<OpenDocument> by mutableStateOf(emptyList())
        private set
    var activeDocumentId: Long by mutableStateOf(0L)
        private set
    val dialogs = DialogController()

    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
    var savedPath: String? by mutableStateOf(null)
        private set
    var isNewFile: Boolean by mutableStateOf(false)
        private set
    var openedName: String? by mutableStateOf(null)
        private set
    var encoding: String by mutableStateOf(Charsets.UTF_8.name())
        private set
    var grammarsReady: Boolean by mutableStateOf(EditorLanguages.isInitialized)
        private set
    var grammarsSettled: Boolean by mutableStateOf(EditorLanguages.isInitialized)
        private set
    var languagesLoaded: Boolean by mutableStateOf(false)
        private set
    val isLoading: Boolean
        get() = loadState is LoadState.Loading || !languagesLoaded
    var grammarEpoch: Int by mutableIntStateOf(0)
        private set
    var isModified: Boolean by mutableStateOf(false)
        private set
    var languageOverride: String? by mutableStateOf(null)
        private set
    var syntaxHighlight: Boolean by mutableStateOf(EditorDefaults.SYNTAX_HIGHLIGHT)
        private set
    var wordWrap: Boolean by mutableStateOf(EditorDefaults.WORD_WRAP)
        private set
    var miniMap: Boolean by mutableStateOf(EditorDefaults.MINIMAP_SHOWN)
        private set
    var miniMapBlocks: Boolean by mutableStateOf(EditorDefaults.MINIMAP_BLOCKS)
        private set
    var symbolBar: Boolean by mutableStateOf(EditorDefaults.SYMBOL_BAR)
        private set
    var lineNumbers: Boolean by mutableStateOf(EditorDefaults.LINE_NUMBERS)
        private set
    var selectedTheme: String by mutableStateOf(EditorDefaults.SELECTED_THEME)
        private set
    var selectedFont: String by mutableStateOf(EditorDefaults.SELECTED_FONT)
        private set
    var prefsLoaded: Boolean by mutableStateOf(false)
        private set
    var textSizePx: Float by mutableFloatStateOf(EditorDefaults.TEXT_SIZE_PX)
        private set
    var fontOptions: List<FontRegistry.FontOption> by mutableStateOf(emptyList())
        private set
    var systemFontOptions: List<FontRegistry.FontOption> by mutableStateOf(emptyList())
        private set
    var systemFontsLoaded: Boolean by mutableStateOf(false)
        private set
    var invisibleChars: Boolean by mutableStateOf(EditorDefaults.INVISIBLE_CHARS)
        private set
    var readOnly: Boolean by mutableStateOf(EditorDefaults.READ_ONLY)
        private set
    var autoCompletion: Boolean by mutableStateOf(EditorDefaults.AUTO_COMPLETION)
        private set
    var content: Content by mutableStateOf(Content())
        private set

    init {
        val kept = DocumentSessions.active
        if (kept != null) {
            activeDocumentId = kept.id
            project(kept)
            publishDocuments()
        }
        viewModelScope.launch {
            prepareEditor()
            if (kept != null && kept.loadState is LoadState.Loading) {
                kept.path?.let { loadInto(kept, it) }
            }
        }
    }

    fun open(path: String, name: String) {
        DocumentSessions.findByPath(path)?.let { alreadyOpen ->
            activate(alreadyOpen.id)
            return
        }
        val session = DocumentSessions.create(
            path = path,
            name = name,
            directory = null,
            isNewFile = false,
        )
        activate(session.id)
        viewModelScope.launch {
            prepareEditor()
            openStartedAt = SystemClock.elapsedRealtime()
            Log.d(
                TAG,
                "open ${fileNameOf(path)}: grammar index warm=${EditorLanguages.isInitialized}" +
                        " theme warm=${ThemeRegistry.isInitialized} prefsLoaded=$prefsLoaded",
            )
            loadInto(session, path)
        }
    }

    fun openNew(directory: String) {
        val session = DocumentSessions.create(
            path = null,
            name = null,
            directory = directory,
            isNewFile = true,
        )
        activate(session.id)
        viewModelScope.launch { prepareEditor() }
    }

    fun activate(id: Long) {
        val session = DocumentSessions.get(id) ?: return
        DocumentSessions.markUsed(id)
        if (activeDocumentId != id) {
            activeDocumentId = id
            languagesLoaded = false
            project(session)
            publishDocuments()
        }
        if (session.loadState is LoadState.Loading) {
            session.path?.let { loadInto(session, it) }
        }
    }

    fun close(id: Long): Boolean {
        val wasActive = activeDocumentId == id
        if (!DocumentSessions.remove(id)) return false
        if (wasActive) {
            val next = DocumentSessions.active
            if (next != null) {
                activeDocumentId = next.id
                languagesLoaded = false
                project(next)
            }
        }
        publishDocuments()
        return openDocuments.isEmpty()
    }

    fun requestClose(id: Long): Boolean {
        val session = DocumentSessions.get(id) ?: return false
        if (session.isModified) {
            dialogs.show(DialogRequest.ConfirmClose(id, displayNameOf(session)))
            return false
        }
        return close(id)
    }

    fun openThemeDialog() {
        dialogs.show(DialogRequest.Theme)
    }

    fun openFontDialog() {
        dialogs.show(DialogRequest.Font)
    }

    fun openLanguageDialog(detectedScope: String?) {
        dialogs.show(DialogRequest.Language(detectedScope))
    }

    fun openReloadDialog() {
        dialogs.show(DialogRequest.Reload)
    }

    fun openPreview(markdown: String) {
        val session = activeSession ?: return
        dialogs.show(
            DialogRequest.Preview(
                markdown = markdown,
                title = displayNameOf(session),
                baseUrl = session.path?.let(::parentOf)?.let { "file://$it/" },
            )
        )
    }

    private fun displayNameOf(session: DocumentSession): String =
        session.name ?: session.path?.let(::fileNameOf) ?: "New file"

    private fun project(session: DocumentSession) {
        savedPath = session.path
        openedName = session.name
        isNewFile = session.isNewFile
        content = session.content
        encoding = session.encoding
        isModified = session.isModified
        languageOverride = session.languageOverride
        loadState = session.loadState
    }

    private fun publishDocuments() {
        openDocuments = DocumentSessions.snapshot()
    }

    private suspend fun prepareEditor() {
        if (!prefsLoaded) {
            // The preloader usually owns this already; only hop off main when it has not run.
            if (!ThemeRegistry.isInitialized) {
                withContext(Dispatchers.IO) { ThemeRegistry.initialize(getApplication()) }
            }
            loadPrefs()
            refreshFontOptions()
        }
        grammarsLoaded()
    }

    private fun grammarsLoaded() {
        if (grammarsSettled) {
            Log.d(TAG, "grammar index already settled; no load job needed")
            return
        }
        if (grammarLoadJob?.isActive == true) return

        grammarLoadJob = viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val started = SystemClock.elapsedRealtime()
                grammarsReady =
                    withContext(Dispatchers.IO) { EditorLanguages.initialize(app) }
                Log.d(
                    TAG,
                    "grammar index loaded in ${SystemClock.elapsedRealtime() - started}ms" +
                            " (ready=$grammarsReady)",
                )
            } finally {
                grammarsSettled = true
                grammarLoadJob = null
            }
        }
    }

    fun onLanguagesLoaded() {
        if (!languagesLoaded && openStartedAt > 0L) {
            Log.d(
                TAG,
                "highlighting ready ${SystemClock.elapsedRealtime() - openStartedAt}ms after open",
            )
        }
        languagesLoaded = true
    }

    fun saveCursorState(editor: CodeEditor) {
        saveCursorState(activeDocumentId, editor)
    }

    fun saveCursorState(id: Long, editor: CodeEditor) {
        val session = DocumentSessions.get(id) ?: return
        val cursor = editor.cursor
        session.selectionLeft = cursor.left
        session.selectionRight = cursor.right
        session.scrollX = editor.scroller.currX
        session.scrollY = editor.scroller.currY
    }

    fun restoreCursorState(id: Long, editor: CodeEditor) {
        val session = DocumentSessions.get(id) ?: return
        if (session.selectionLeft < 0) return
        val len = editor.text.length
        val left = session.selectionLeft.coerceIn(0, len)
        val right = session.selectionRight.coerceIn(0, len)
        val leftPos = editor.text.indexer.getCharPosition(minOf(left, right))
        val rightPos = editor.text.indexer.getCharPosition(maxOf(left, right))
        editor.setSelectionRegion(
            leftPos.line,
            leftPos.column,
            rightPos.line,
            rightPos.column,
            false,
        )
        editor.scroller.startScroll(session.scrollX, session.scrollY, 0, 0, 0)
        editor.scroller.abortAnimation()
        editor.postInvalidate()
    }

    private fun loadPrefs() {
        val prefs = this.prefs
        wordWrap = prefs.getBoolean(PrefKeys.WORD_WRAP, EditorDefaults.WORD_WRAP)
        syntaxHighlight =
            prefs.getBoolean(PrefKeys.SYNTAX_HIGHLIGHT, EditorDefaults.SYNTAX_HIGHLIGHT)
        textSizePx = prefs.getFloat(PrefKeys.TEXT_SIZE_PX, EditorDefaults.TEXT_SIZE_PX)
        miniMap = prefs.getBoolean(PrefKeys.MINIMAP_SHOWN, EditorDefaults.MINIMAP_SHOWN)
        miniMapBlocks = prefs.getBoolean(PrefKeys.MINIMAP_BLOCKS, EditorDefaults.MINIMAP_BLOCKS)
        symbolBar = prefs.getBoolean(PrefKeys.SYMBOL_BAR, EditorDefaults.SYMBOL_BAR)
        lineNumbers = prefs.getBoolean(PrefKeys.LINE_NUMBERS, EditorDefaults.LINE_NUMBERS)
        invisibleChars = prefs.getBoolean(PrefKeys.INVISIBLE_CHARS, EditorDefaults.INVISIBLE_CHARS)
        readOnly = prefs.getBoolean(PrefKeys.READ_ONLY, EditorDefaults.READ_ONLY)
        autoCompletion =
            prefs.getBoolean(PrefKeys.AUTO_COMPLETION, EditorDefaults.AUTO_COMPLETION)

        val app = getApplication<Application>()

        selectedTheme = resolveSelectedId(
            stored = prefs.getString(PrefKeys.SELECTED_THEME, null) ?: "",
            availableIds = ThemeRegistry.availableThemes.map { it.id },
            defaultName = EditorDefaults.SELECTED_THEME,
        )

        selectedFont = resolveSelectedId(
            stored = prefs.getString(PrefKeys.SELECTED_FONT, null) ?: "",
            availableIds = FontRegistry.availableFonts(app, includeSystemFonts = true)
                .map { it.id },
            defaultName = EditorDefaults.SELECTED_FONT,
        )

        prefsLoaded = true
        savePrefs()
    }

    private fun savePrefs() {
        prefs.edit {
            putFloat(PrefKeys.TEXT_SIZE_PX, textSizePx)
            putBoolean(PrefKeys.WORD_WRAP, wordWrap)
            putBoolean(PrefKeys.SYNTAX_HIGHLIGHT, syntaxHighlight)
            putString(PrefKeys.SELECTED_THEME, selectedTheme)
            putBoolean(PrefKeys.MINIMAP_SHOWN, miniMap)
            putBoolean(PrefKeys.MINIMAP_BLOCKS, miniMapBlocks)
            putBoolean(PrefKeys.SYMBOL_BAR, symbolBar)
            putBoolean(PrefKeys.LINE_NUMBERS, lineNumbers)
            putString(PrefKeys.SELECTED_FONT, selectedFont)
            putBoolean(PrefKeys.INVISIBLE_CHARS, invisibleChars)
            putBoolean(PrefKeys.READ_ONLY, readOnly)
            putBoolean(PrefKeys.AUTO_COMPLETION, autoCompletion)
        }
    }

    fun refreshFontOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            fontOptions = FontRegistry.availableFonts(getApplication(), includeSystemFonts = false)
        }
    }

    fun loadSystemFonts() {
        if (systemFontsLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            systemFontOptions =
                FontRegistry.availableFonts(getApplication(), includeSystemFonts = true)
                    .filter { it !in fontOptions }
            systemFontsLoaded = true
        }
    }

    private fun resolveSelectedId(
        stored: String,
        availableIds: List<String>,
        defaultName: String = "",
    ): String = when {
        stored.isNotBlank() && stored in availableIds -> stored
        defaultName.isNotBlank() && defaultName in availableIds -> defaultName
        else -> availableIds.firstOrNull() ?: ""
    }

    /** Sets the language for the open document; `null` returns to detection by file name. */
    fun setLanguage(scope: String?) {
        val session = activeSession ?: return
        session.languageOverride = scope
        languageOverride = scope
        if (scope != null) {
            val text = content
            viewModelScope.launch {
                withContext(Dispatchers.IO) { EditorLanguages.registerDocument(scope, text) }
            }
        }
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
        savePrefs()
    }

    fun toggleSyntaxHighlight() {
        syntaxHighlight = !syntaxHighlight
        savePrefs()
        if (syntaxHighlight) grammarsLoaded()
    }

    fun setTheme(theme: String) {
        selectedTheme = theme
        savePrefs()
    }

    fun saveTextSize(px: Float) {
        textSizePx = px
        savePrefs()
    }

    fun toggleMinimap() {
        miniMap = !miniMap
        savePrefs()
    }

    fun toggleMinimapBlocks() {
        miniMapBlocks = !miniMapBlocks
        savePrefs()
    }

    fun toggleSymbolBar() {
        symbolBar = !symbolBar
        savePrefs()
    }

    fun toggleLineNumbers() {
        lineNumbers = !lineNumbers
        savePrefs()
    }

    fun toggleInvisibleChars() {
        invisibleChars = !invisibleChars
        savePrefs()
    }

    fun toggleReadOnly() {
        readOnly = !readOnly
        savePrefs()
    }

    fun toggleAutoCompletion() {
        autoCompletion = !autoCompletion
        savePrefs()
    }

    fun setFont(font: String) {
        selectedFont = font
        savePrefs()
    }

    fun load(path: String) {
        val session = activeSession ?: return
        loadInto(session, path)
    }

    private fun loadInto(session: DocumentSession, path: String) {
        if (session.loadJob?.isActive == true) return
        if (activeDocumentId == session.id) {
            languagesLoaded = false
            loadState = LoadState.Loading
            isModified = false
        }
        session.loadState = LoadState.Loading
        session.isModified = false
        publishDocuments()
        val job = viewModelScope.launch(Dispatchers.IO) { doLoad(session, path) }
        session.loadJob = job
        job.invokeOnCompletion { if (session.loadJob === job) session.loadJob = null }
    }

    private suspend fun doLoad(session: DocumentSession, path: String) {
        try {
            // Read through the host so a root-only file is opened by the
            // privileged process instead of the app's uid.
            val (builtContent, detectedEncoding) = host.openRead(path).use { stream ->
                val buffered =
                    BufferedInputStream(stream, FileEncodingDetector.SAMPLE_SIZE + 1)
                val detected = FileEncodingDetector.detect(buffered)
                ContentIO.createFrom(buffered.readerFor(detected)) to detected
            }
            if (syntaxHighlight) {
                val app = getApplication<Application>()
                EditorLanguages.initialize(app)
                val scope = session.languageOverride ?: EditorLanguages.scopeFor(session.name)
                val preloaded = EditorLanguages.isScopeRegistered(scope)
                val started = SystemClock.elapsedRealtime()
                val added = EditorLanguages.registerDocument(scope, builtContent)
                if (added) EditorLanguages.rebuildGrammar(scope)
                Log.d(
                    TAG,
                    "load ${fileNameOf(path)}: scope=$scope preloaded=$preloaded" +
                            " newLanguages=$added in ${SystemClock.elapsedRealtime() - started}ms",
                )
            }
            session.content = builtContent
            session.encoding = detectedEncoding.displayName
            session.loadState = LoadState.Success
            withContext(Dispatchers.Main) {
                if (activeDocumentId == session.id) project(session)
                publishDocuments()
            }
        } catch (e: OutOfMemoryError) {
            finishLoad(session, LoadState.Error(e.localizedMessage ?: "Out of memory"))
        } catch (e: IOException) {
            finishLoad(session, LoadState.Error(e.localizedMessage ?: "Read error"))
        }
    }

    private suspend fun finishLoad(session: DocumentSession, state: LoadState) {
        session.loadState = state
        withContext(Dispatchers.Main) {
            if (activeDocumentId == session.id) loadState = state
        }
    }

    fun onContentChanged(editorAction: Int) {
        val session = activeSession ?: return
        if (editorAction == ContentChangeEvent.ACTION_SET_NEW_TEXT || session.isModified) return
        session.isModified = true
        isModified = true
        publishDocuments()
    }

    fun onLinesEdited(lines: List<CharSequence>) {
        val session = activeSession ?: return
        val scope = session.languageOverride ?: EditorLanguages.scopeFor(session.name) ?: return
        val text = lines.joinToString("\n")
        if (text.isBlank()) return
        editGrammarJob?.cancel()
        editGrammarJob = viewModelScope.launch {
            delay(GRAMMAR_EDIT_DEBOUNCE_MS.milliseconds)
            val added = withContext(Dispatchers.IO) {
                EditorLanguages.registerDocument(scope, text)
            }
            if (!added) return@launch
            withContext(Dispatchers.IO) { EditorLanguages.rebuildGrammar(scope) }
            grammarEpoch++
        }
    }

    fun reloadOpen() {
        activeSession?.path?.let { load(it) }
    }

    fun save(
        getText: (() -> String)? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        id: Long = activeDocumentId,
    ) {
        val session = DocumentSessions.get(id)
        if (session == null || (session.path == null && session.directory == null)) {
            onError("No document open")
            return
        }
        if (session.isSaving) return
        session.isSaving = true
        val readText: () -> String = if (activeDocumentId == id && getText != null) {
            getText
        } else {
            { session.content.toString() }
        }
        viewModelScope.launch {
            try {
                val text = readText()
                val target = session.path
                    ?: host.saveAs(NEW_FILE_NAME, session.directory)
                    ?: return@launch
                withContext(Dispatchers.IO) {
                    host.save(target, text)
                }
                if (session.path == null) {
                    session.path = target
                    session.name = fileNameOf(target)
                    session.directory = null
                    session.isNewFile = false
                }
                session.isModified = readText() != text
                withContext(Dispatchers.Main) {
                    if (activeDocumentId == session.id) project(session)
                    publishDocuments()
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Write error")
            } finally {
                session.isSaving = false
            }
        }
    }

    companion object {
        /** Creates the view model with the host that reads and persists documents. */
        fun factory(host: TextEditorHost): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SoraEditorViewModel(app, host)
            }
        }
    }
}

fun deriveScrollbarDrawables(): Pair<ColorDrawable, ColorDrawable> {
    val trackDrawable = 0x29FFFFFF.toDrawable()
    val thumbDrawable = 0x80FFFFFF.toInt().toDrawable()
    return Pair(trackDrawable, thumbDrawable)
}
