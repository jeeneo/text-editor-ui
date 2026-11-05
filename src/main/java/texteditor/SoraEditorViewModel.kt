@file:Suppress("SpellCheckingInspection")

package texteditor

import org.json.JSONArray
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import texteditor.dialogs.data.DialogController
import texteditor.dialogs.data.DialogRequest
import texteditor.encoding.AndroidLegacyDetector
import texteditor.encoding.EncodingCatalog
import texteditor.encoding.EncodingDetection
import texteditor.encoding.EncodingDetector
import texteditor.encoding.EncodingPickerMode
import texteditor.encoding.EncodingScheme
import texteditor.encoding.LineEnding
import texteditor.encoding.LineEndingInfo
import texteditor.encoding.LineEndings
import texteditor.encoding.RecordingReader
import texteditor.encoding.TextEncoder
import texteditor.encoding.UnencodableCharacterException
import texteditor.encoding.bomLengthIn
import texteditor.encoding.readerFor
import texteditor.screens.preview.PreviewState
import java.io.BufferedInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry as EditorThemeRegistry

private val Context.editorPrefs: SharedPreferences
    get() = getSharedPreferences("text_editor_prefs", Context.MODE_PRIVATE)

enum class SaveButtonState { IDLE, SAVED, ERROR }

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
    const val DEFAULT_ENCODING = "default_encoding"
    const val DEFAULT_LINE_ENDING = "default_line_ending"
    const val DETECT_ENCODING = "detect_encoding"
    const val PINNED_FILES = "pinned_files"
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
    const val DEFAULT_ENCODING = "utf8"
    const val DEFAULT_LINE_ENDING = "LF"
    const val DETECT_ENCODING = true
    const val SELECTED_THEME = "default"
    const val SELECTED_FONT = "fonts/CascadiaCode-Regular.ttf"
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(
        val message: String,
    ) : LoadState
}

private data class LoadedDocument(
    val content: Content,
    val scheme: EncodingScheme,
    val lineEnding: LineEndingInfo,
)

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
    private var saveCopyInProgress = false
    private var unencodableSessionId = 0L
    private var retrySave: (() -> Unit)? = null

    private val activeSession: DocumentSession?
        get() = DocumentSessions.active
    var openDocuments: List<OpenDocument> by mutableStateOf(emptyList())
        private set
    var activeDocumentId: Long by mutableStateOf(0L)
        private set
    val dialogs = DialogController()
    var preview: PreviewState? by mutableStateOf(null)
        private set
    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
    var savedPath: String? by mutableStateOf(null)
        private set
    var pinnedPaths: List<String> by mutableStateOf(emptyList())
        private set
    var isNewFile: Boolean by mutableStateOf(false)
        private set
    var openedName: String? by mutableStateOf(null)
        private set
    var encodingLabel: String by mutableStateOf(EncodingCatalog.default.label)
        private set
    var lineEndingLabel: String by mutableStateOf(LineEnding.LF.name)
        private set
    var lineEndingMixed: Boolean by mutableStateOf(false)
        private set
    var defaultEncodingId: String by mutableStateOf(EditorDefaults.DEFAULT_ENCODING)
        private set
    var defaultLineEnding: LineEnding by mutableStateOf(LineEnding.LF)
        private set
    var detectEncoding: Boolean by mutableStateOf(EditorDefaults.DETECT_ENCODING)
        private set
    var grammarsReady: Boolean by mutableStateOf(EditorLanguages.isInitialized)
        private set
    var grammarsSettled: Boolean by mutableStateOf(EditorLanguages.isInitialized)
        private set
    var languagesLoaded: Boolean by mutableStateOf(false)
        private set
    val isLoading: Boolean
        get() = loadState is LoadState.Loading || !languagesLoaded

    /** True while a write is in flight, which can take a while on slow storage. */
    var isSaving: Boolean by mutableStateOf(false)
        private set
    var grammarEpoch: Int by mutableIntStateOf(0)
        private set
    var reloadEpoch: Int by mutableIntStateOf(0)
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
        session.encoding = defaultEncoding()
        session.lineEnding = defaultLineEnding
        session.originalLineEnding = defaultLineEnding
        activate(session.id)
        viewModelScope.launch { prepareEditor() }
    }

    fun openNewFile() {
        val directory = activeSession?.let { it.path?.let(::parentOf) ?: it.directory }
        if (directory == null) {
            AppToasts.show(getApplication(), "No folder for a new file")
            return
        }
        openNew(directory)
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

    fun openJumpToLineDialog(currentLine: Int, lineCount: Int) {
        dialogs.show(
            DialogRequest.JumpToLine(
                currentLine,
                lineCount
            )
        )
    }

    fun openReloadDialog() {
        val session = activeSession
        if (session?.path == null) return
        if (!session.isModified) {
            reloadOpen()
            return
        }
        dialogs.show(DialogRequest.Reload)
    }

    fun openFileInfo(text: CharSequence) {
        val snapshot = text.toString()
        val session = activeSession
        val scheme = session?.encoding ?: defaultEncoding()
        viewModelScope.launch {
            val info = withContext(Dispatchers.Default) {
                var words = 0
                var lines = 1
                var inWord = false
                for (char in snapshot) {
                    when {
                        char == '\n' -> {
                            lines++
                            inWord = false
                        }

                        char.isWhitespace() -> inWord = false
                        !inWord -> {
                            words++
                            inWord = true
                        }
                    }
                }
                val byteCount = try {
                    TextEncoder.encode(snapshot, scheme).size.toLong()
                } catch (e: UnencodableCharacterException) {
                    Log.e(TAG, "openFileInfo: $e")
                    snapshot.toByteArray(Charsets.UTF_8).size.toLong()
                }
                DialogRequest.FileInfo(
                    byteCount = byteCount,
                    characterCount = snapshot.length,
                    wordCount = words,
                    lineCount = lines,
                    encodingLabel = scheme.label,
                    lineEndingLabel = session?.lineEnding?.name ?: LineEnding.LF.name,
                    mixedLineEndings = session?.mixedLineEndings == true,
                )
            }
            dialogs.show(info)
        }
    }

    fun openPreview(markdown: String) {
        val session = activeSession ?: return
        preview = PreviewState(
            markdown = markdown,
            title = displayNameOf(session),
            baseUrl = session.path?.let(::parentOf)?.let { "file://$it/" },
            monoFontId = selectedFont,
        )
    }

    fun closePreview() {
        preview = null
    }

    private fun displayNameOf(session: DocumentSession): String =
        session.name ?: session.path?.let(::fileNameOf) ?: "New file"

    private fun project(session: DocumentSession) {
        savedPath = session.path
        openedName = session.name
        isNewFile = session.isNewFile
        content = session.content
        encodingLabel = session.encoding.label
        lineEndingLabel = session.lineEnding.name
        lineEndingMixed = session.mixedLineEndings
        if (session.mixedLineEndings) lineEndingLabel += " (mixed)"
        isModified = session.isModified
        languageOverride = session.languageOverride
        loadState = session.loadState
    }

    private fun publishDocuments() {
        openDocuments = DocumentSessions.snapshot()
    }

    private suspend fun prepareEditor() {
        if (!prefsLoaded) {
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
        defaultEncodingId =
            prefs.getString(PrefKeys.DEFAULT_ENCODING, EditorDefaults.DEFAULT_ENCODING)
                ?: EditorDefaults.DEFAULT_ENCODING
        if (EncodingCatalog.byId(defaultEncodingId) == null) {
            defaultEncodingId = EditorDefaults.DEFAULT_ENCODING
        }
        defaultLineEnding = runCatching {
            LineEnding.valueOf(
                prefs.getString(PrefKeys.DEFAULT_LINE_ENDING, EditorDefaults.DEFAULT_LINE_ENDING)
                    ?: EditorDefaults.DEFAULT_LINE_ENDING
            )
        }.getOrDefault(LineEnding.LF)
        detectEncoding = prefs.getBoolean(PrefKeys.DETECT_ENCODING, EditorDefaults.DETECT_ENCODING)
        pinnedPaths = parsePinnedPaths(prefs.getString(PrefKeys.PINNED_FILES, null))

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
            putString(PrefKeys.DEFAULT_ENCODING, defaultEncodingId)
            putString(PrefKeys.DEFAULT_LINE_ENDING, defaultLineEnding.name)
            putBoolean(PrefKeys.DETECT_ENCODING, detectEncoding)
            putString(PrefKeys.PINNED_FILES, JSONArray(pinnedPaths).toString())
        }
    }

    private fun parsePinnedPaths(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
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

    fun togglePin() {
        val path = activeSession?.path
        if (path == null) {
            AppToasts.show(getApplication(), "Save the file to pin it")
            return
        }
        pinnedPaths = if (path in pinnedPaths) pinnedPaths - path else pinnedPaths + path
        savePrefs()
    }

    fun unpin(path: String) {
        if (path !in pinnedPaths) return
        pinnedPaths = pinnedPaths - path
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

    private fun loadInto(
        session: DocumentSession,
        path: String,
        isReload: Boolean = false,
        forcedEncoding: EncodingScheme? = null,
    ) {
        if (session.loadJob?.isActive == true) return
        if (activeDocumentId == session.id) {
            if (!isReload) languagesLoaded = false
            loadState = LoadState.Loading
            isModified = false
        }
        session.loadState = LoadState.Loading
        session.isModified = false
        publishDocuments()
        val job = viewModelScope.launch(Dispatchers.IO) {
            doLoad(session, path, isReload, forcedEncoding)
        }
        session.loadJob = job
        job.invokeOnCompletion { if (session.loadJob === job) session.loadJob = null }
    }

    private suspend fun doLoad(
        session: DocumentSession,
        path: String,
        isReload: Boolean,
        forcedEncoding: EncodingScheme? = null,
    ) {
        try {
            // Read through the host so a root-only file is opened by the
            // privileged process instead of the app's uid.
            val loaded = readDocument(path, forcedEncoding)
            if (syntaxHighlight) {
                val app = getApplication<Application>()
                EditorLanguages.initialize(app)
                val scope = session.languageOverride ?: EditorLanguages.scopeFor(session.name)
                val preloaded = EditorLanguages.isScopeRegistered(scope)
                val started = SystemClock.elapsedRealtime()
                val added = EditorLanguages.registerDocument(scope, loaded.content)
                if (added) EditorLanguages.rebuildGrammar(scope)
                Log.d(
                    TAG,
                    "load ${fileNameOf(path)}: scope=$scope preloaded=$preloaded" +
                            " newLanguages=$added in ${SystemClock.elapsedRealtime() - started}ms",
                )
            }
            session.content = loaded.content
            session.encoding = loaded.scheme
            session.lineEnding = loaded.lineEnding.lineEnding
            session.originalLineEnding = loaded.lineEnding.lineEnding
            session.mixedLineEndings = loaded.lineEnding.mixed
            session.loadState = LoadState.Success
            withContext(Dispatchers.Main) {
                if (activeDocumentId == session.id) {
                    project(session)
                    if (isReload) {
                        // The editor has to put the reloaded text in and re-tokenize it. The grammar
                        // is left to the debounced edit pass, because reloading it now would restart
                        // the analyzer from two threads at once.
                        reloadEpoch++
                    }
                }
                publishDocuments()
            }
        } catch (e: OutOfMemoryError) {
            finishLoad(session, LoadState.Error(e.localizedMessage ?: "Out of memory"))
        } catch (e: IOException) {
            finishLoad(session, LoadState.Error(e.localizedMessage ?: "Read error"))
        }
    }

    private fun readDocument(path: String, forced: EncodingScheme?): LoadedDocument {
        return host.openRead(path).use { stream ->
            val buffered = BufferedInputStream(stream, EncodingDetector.SAMPLE_SIZE + 1)
            val sample = EncodingDetector.readSample(buffered)
            val detection = when {
                forced != null -> EncodingDetection(
                    scheme = forced,
                    bomLength = forced.bomLengthIn(sample),
                )

                !detectEncoding -> {
                    val scheme = defaultEncoding()
                    EncodingDetection(
                        scheme = scheme,
                        bomLength = scheme.bomLengthIn(sample),
                    )
                }

                else -> EncodingDetector.detect(
                    sample = sample,
                    default = defaultEncoding(),
                    legacyDetector = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        AndroidLegacyDetector
                    } else {
                        null
                    },
                )
            }
            val recording = RecordingReader(
                buffered.readerFor(detection.scheme, detection.bomLength),
                EncodingDetector.SAMPLE_SIZE,
            )
            val content = ContentIO.createFrom(recording)
            LoadedDocument(
                content = content,
                scheme = detection.scheme,
                lineEnding = LineEndings.detect(recording.recorded, defaultLineEnding),
            )
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
        val session = activeSession ?: return
        val path = session.path ?: return
        loadInto(session, path, isReload = true)
    }

    fun reopenWithEncoding(schemeId: String) {
        val session = activeSession ?: return
        val path = session.path ?: return
        val scheme = EncodingCatalog.byId(schemeId) ?: return
        loadInto(session, path, isReload = true, forcedEncoding = scheme)
    }

    fun openEncodingPicker(mode: EncodingPickerMode) {
        val session = activeSession ?: return
        if (mode == EncodingPickerMode.REOPEN && session.path == null) return
        dialogs.show(
            DialogRequest.EncodingPicker(
                mode = mode,
                currentId = session.encoding.id,
            )
        )
    }

    fun applyEncoding(request: DialogRequest.EncodingPicker, schemeId: String) {
        val session = activeSession ?: return
        val scheme = EncodingCatalog.byId(schemeId) ?: return
        dialogs.dismissTop()
        when (request.mode) {
            EncodingPickerMode.REOPEN -> {
                if (session.path == null) return
                if (session.isModified) {
                    dialogs.show(
                        DialogRequest.ConfirmReopenEncoding(schemeId, displayNameOf(session))
                    )
                } else {
                    reopenWithEncoding(schemeId)
                }
            }

            EncodingPickerMode.SAVE -> {
                session.encoding = scheme
                session.isModified = true
                if (activeDocumentId == session.id) project(session)
                publishDocuments()
            }
        }
    }

    fun openLineEndingPicker() {
        val session = activeSession ?: return
        dialogs.show(
            DialogRequest.LineEndingPicker(session.lineEnding, session.mixedLineEndings)
        )
    }

    /** Rewrites the open document's separators; the replace is a single undoable edit. */
    fun setLineEnding(lineEnding: LineEnding) {
        val session = activeSession ?: return
        val content = session.content
        val current = content.toString()
        val converted = LineEndings.convert(current, lineEnding)
        session.lineEnding = lineEnding
        session.mixedLineEndings = false
        if (converted != current) {
            content.replace(0, content.length, converted)
            session.isModified = true
        }
        if (activeDocumentId == session.id) project(session)
        publishDocuments()
    }

    fun openDocumentOptions() {
        if (activeSession == null) return
        dialogs.show(DialogRequest.DocumentOptions)
    }

    fun saveAsUtf8() {
        val session = DocumentSessions.get(unencodableSessionId) ?: activeSession ?: return
        val retry = retrySave
        retrySave = null
        unencodableSessionId = 0L
        dialogs.dismissTop()
        session.encoding = EncodingCatalog.utf8
        if (activeDocumentId == session.id) project(session)
        retry?.invoke()
    }

    fun cancelUnencodableSave() {
        retrySave = null
        unencodableSessionId = 0L
        dialogs.dismissTop()
    }

    private fun defaultEncoding(): EncodingScheme =
        EncodingCatalog.byId(defaultEncodingId) ?: EncodingCatalog.default

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
        isSaving = true
        val readText: () -> String = if (activeDocumentId == id && getText != null) {
            getText
        } else {
            { session.content.toString() }
        }
        viewModelScope.launch {
            try {
                val text = readText()
                val bytes = withContext(Dispatchers.Default) {
                    TextEncoder.encode(text, session.encoding)
                }
                val target = session.path
                    ?: host.saveAs(NEW_FILE_NAME, session.directory)
                    ?: return@launch
                withContext(Dispatchers.IO) {
                    host.save(target, bytes)
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
            } catch (e: UnencodableCharacterException) {
                unencodableSessionId = session.id
                retrySave = { save(getText, onSuccess, onError, id) }
                withContext(Dispatchers.Main) {
                    dialogs.show(DialogRequest.UnrepresentableCharacters(e.encodingLabel))
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Write error")
            } finally {
                session.isSaving = false
                isSaving = false
            }
        }
    }

    /**
     * Writes [getText] to a new file that the user picks. The document and its file are left
     * untouched, so unsaved changes only ever reach the copy.
     */
    fun saveCopyAs(
        getText: () -> String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val session = activeSession
        if (session == null) {
            onError("No document open")
            return
        }
        if (saveCopyInProgress) return
        saveCopyInProgress = true
        viewModelScope.launch {
            try {
                val text = getText()
                val bytes = withContext(Dispatchers.Default) {
                    TextEncoder.encode(text, session.encoding)
                }
                val suggestedName = session.name
                    ?: session.path?.let(::fileNameOf)
                    ?: NEW_FILE_NAME
                val initialDirectory = session.path?.let(::parentOf) ?: session.directory
                val target = host.saveAs(suggestedName, initialDirectory) ?: return@launch
                withContext(Dispatchers.IO) {
                    host.save(target, bytes)
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnencodableCharacterException) {
                unencodableSessionId = session.id
                retrySave = { saveCopyAs(getText, onSuccess, onError) }
                withContext(Dispatchers.Main) {
                    dialogs.show(DialogRequest.UnrepresentableCharacters(e.encodingLabel))
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Write error")
            } finally {
                saveCopyInProgress = false
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
