@file:Suppress("SpellCheckingInspection")

package texteditor

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.line.LineBackground
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import texteditor.bottombar.CustomSearchBar
import texteditor.bottombar.SymbolInputBar
import texteditor.dialogs.data.DialogHost
import texteditor.drawer.EditorDrawerSheet
import texteditor.settings.EditorSettingsScreen
import texteditor.topbar.TopBarActions
import kotlin.time.Duration.Companion.milliseconds

private const val HIGHLIGHT_MAX_CHARS = 10_000_000
private const val EMBEDDED_SCAN_LINES = 40
private const val EMBEDDED_SCAN_CHARS = 200_000
private const val REVEAL_TIMEOUT_MS = 3_000L
private const val BOTTOM_SCROLL_EXTRA_DP = 94f
private val CURRENT_LINE_TINT = "#323232".toColorInt()
val PRELOAD_BG_DARK = "#2B2B2B".toColorInt() // change when themed light

@SuppressLint("ClickableViewAccessibility")
@Composable
fun TextEditorScreen(
    onNavigateUp: () -> Unit,
    onMinimize: () -> Unit = {},
    viewModel: SoraEditorViewModel = viewModel(),
    initialSearchQuery: String? = null,
) {
    val context = LocalContext.current
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val canUndo = remember { mutableStateOf(false) }
    val canRedo = remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var cursorLine by remember { mutableIntStateOf(1) }
    var cursorColumn by remember { mutableIntStateOf(1) }
    var lineCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var shownDocumentId by remember { mutableLongStateOf(viewModel.activeDocumentId) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val displayPath = viewModel.savedPath
    val typefaceToLoad = remember(viewModel.selectedFont) {
        FontRegistry.loadTypeface(context, viewModel.selectedFont)
    }
    val saveButtonState = remember { mutableStateOf(SaveButtonState.IDLE) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(saveButtonState.value) {
        if (saveButtonState.value == SaveButtonState.SAVED) {
            delay(1500.milliseconds)
            saveButtonState.value = SaveButtonState.IDLE
        } else if (saveButtonState.value == SaveButtonState.ERROR) {
            delay(2500.milliseconds)
            saveButtonState.value = SaveButtonState.IDLE
        }
    }
    LaunchedEffect(viewModel.isModified) {
        if (viewModel.isModified && saveButtonState.value != SaveButtonState.IDLE) {
            saveButtonState.value = SaveButtonState.IDLE
        }
    }

    fun refreshUndoRedo() {
        canUndo.value = editorRef?.canUndo() ?: false
        canRedo.value = editorRef?.canRedo() ?: false
    }

    fun saveFile() {
        val editor = editorRef ?: return
        viewModel.save(
            getText = { editor.text.toString() },
            onSuccess = { saveButtonState.value = SaveButtonState.SAVED },
            onError = { message ->
                saveButtonState.value = SaveButtonState.ERROR
                scope.launch {
                    AppToasts.show(context, message)
                }
            },
        )
    }

    fun saveCopyAsFile() {
        viewModel.saveCopyAs(
            getText = { editorRef?.text?.toString() ?: viewModel.content.toString() },
            onSuccess = {
                AppToasts.show(context, "Saved copy")
            },
            onError = { message ->
                scope.launch {
                    AppToasts.show(context, message)
                }
            },
        )
    }

    /** Saves [id], which may be a document that is only kept in the drawer right now. */
    fun saveDocument(id: Long, onSaved: () -> Unit) {
        viewModel.save(
            getText = { editorRef?.text?.toString() ?: viewModel.content.toString() },
            onSuccess = {
                saveButtonState.value = SaveButtonState.SAVED
                onSaved()
            },
            onError = { message ->
                saveButtonState.value = SaveButtonState.ERROR
                scope.launch {
                    AppToasts.show(context, message)
                }
            },
            id = id,
        )
    }

    fun switchToDocument(id: Long) {
        if (id == viewModel.activeDocumentId) return
        // The editor is about to be handed to another document, so keep where this one stood.
        editorRef?.let { viewModel.saveCursorState(it) }
        viewModel.activate(id)
    }

    /** Closes [id], and leaves the editor when that was its last document. */
    fun closeDocument(id: Long) {
        if (viewModel.requestClose(id)) onNavigateUp()
    }

    fun minimize() {
        editorRef?.let { viewModel.saveCursorState(it) }
        scope.launch { drawerState.close() }
        onMinimize()
    }

    var layoutReady by remember { mutableStateOf(false) }
    val detectedScope = remember(viewModel.openedName, viewModel.grammarsReady) {
        EditorLanguages.scopeFor(viewModel.openedName)
    }
    val highlightActive =
        viewModel.syntaxHighlight && viewModel.grammarsReady && viewModel.loadState is LoadState.Success && viewModel.content.length <= HIGHLIGHT_MAX_CHARS
    val activeLanguage = remember(
        viewModel.languageOverride,
        detectedScope,
        highlightActive,
        viewModel.selectedTheme,
    ) {
        if (highlightActive) {
            EditorLanguages.languageForScope(viewModel.languageOverride ?: detectedScope)
        } else {
            null
        }
    }
    LaunchedEffect(viewModel.activeDocumentId) {
        if (shownDocumentId != viewModel.activeDocumentId) {
            shownDocumentId = viewModel.activeDocumentId
            // A search belongs to the document it was opened in.
            showSearchPanel = false
            searchQuery = null
            editorRef?.searcher?.stopSearch()
        }
    }
    LaunchedEffect(initialSearchQuery) {
        if (initialSearchQuery != null) searchQuery = initialSearchQuery
    }
    LaunchedEffect(searchQuery, layoutReady) {
        if (layoutReady && !searchQuery.isNullOrBlank() && !viewModel.readOnly) {
            showSearchPanel = true
        }
    }
    LaunchedEffect(editorRef, viewModel.selectedTheme) {
        editorRef?.let { applyTheme(it, viewModel.selectedTheme) }
    }
    LaunchedEffect(editorRef, viewModel.miniMap, viewModel.miniMapBlocks) {
        editorRef?.let { applyMinimap(it, viewModel.miniMap, viewModel.miniMapBlocks) }
    }
    LaunchedEffect(editorRef, viewModel.invisibleChars) {
        editorRef?.let { applyInvisibleChars(it, viewModel.invisibleChars) }
    }
    LaunchedEffect(editorRef, activeLanguage) {
        val editor = editorRef ?: return@LaunchedEffect
        if (editor.editorLanguage !== activeLanguage) {
            editor.setEditorLanguage(activeLanguage)
        }
        if (activeLanguage != null && editor.styles == null) {
            withTimeoutOrNull(REVEAL_TIMEOUT_MS.milliseconds) {
                while (editor.styles == null) {
                    withFrameNanos { }
                }
            }
        }
        viewModel.onLanguagesLoaded()
    }
    LaunchedEffect(editorRef, viewModel.grammarEpoch) {
        if (viewModel.grammarEpoch == 0) return@LaunchedEffect
        val language = editorRef?.editorLanguage as? TextMateLanguage ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { language.reloadGrammar() }
    }

    LaunchedEffect(viewModel.reloadEpoch) {
        if (viewModel.reloadEpoch == 0) return@LaunchedEffect
        val editor = editorRef ?: return@LaunchedEffect
        if (viewModel.loadState !is LoadState.Success) return@LaunchedEffect
        editor.setText(viewModel.content)
        refreshUndoRedo()
    }

    LaunchedEffect(editorRef, viewModel.wordWrap) {
        if (editorRef?.isWordwrap != viewModel.wordWrap) {
            editorRef?.isWordwrap = viewModel.wordWrap
        }
    }
    LaunchedEffect(editorRef, viewModel.lineNumbers) {
        editorRef?.isLineNumberEnabled = viewModel.lineNumbers
    }
    LaunchedEffect(editorRef, viewModel.readOnly) {
        editorRef?.setEditable(!viewModel.readOnly)
    }
    LaunchedEffect(editorRef, viewModel.autoCompletion) {
        editorRef?.getComponent<EditorAutoCompletion>()?.setEnabled(viewModel.autoCompletion)
    }
    LaunchedEffect(viewModel.readOnly) {
        // Search and replace have no place in a read-only document.
        if (viewModel.readOnly) {
            showSearchPanel = false
            editorRef?.searcher?.stopSearch()
        }
    }
    LaunchedEffect(editorRef, typefaceToLoad) {
        editorRef?.let {
            it.typefaceText = typefaceToLoad
            it.typefaceLineNumber = typefaceToLoad
            it.invalidate()
        }
    }
    LaunchedEffect(showSettings) {
        if (showSettings) editorRef?.hideSoftInput()
    }
    BackHandler(enabled = !showSettings) {
        when {
            !drawerState.isClosed -> scope.launch { drawerState.close() }
            showSearchPanel -> {
                showSearchPanel = false
                editorRef?.searcher?.stopSearch()
            }

            else -> closeDocument(viewModel.activeDocumentId)
        }
    }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        if (viewModel.textSizePx > 0f) editor.setTextSizePx(viewModel.textSizePx)
        val receipt = editor.subscribeEvent(ScrollEvent::class.java) { _, _ ->
            viewModel.saveTextSize(editor.textSizePx)
        }
        onDispose {
            receipt.unsubscribe()
            viewModel.saveTextSize(editor.textSizePx)
        }
    }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        fun refreshPosition() {
            val caret = editor.cursor
            cursorLine = caret.leftLine + 1
            cursorColumn = caret.leftColumn + 1
            lineCount = editor.text.lineCount
        }
        refreshPosition()
        val selectionReceipt =
            editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> refreshPosition() }
        val contentReceipt =
            editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> refreshPosition() }
        onDispose {
            selectionReceipt.unsubscribe()
            contentReceipt.unsubscribe()
        }
    }
    DialogHost(
        viewModel = viewModel,
        onSaveAndClose = { id ->
            saveDocument(id) {
                if (viewModel.close(id)) onNavigateUp()
            }
        },
        onMinimize = ::minimize,
        onNothingLeft = onNavigateUp,
    )

    val title = buildString {
        if (viewModel.isModified) append("*")
        when {
            viewModel.isNewFile -> append("New file")
            viewModel.openedName != null -> append(viewModel.openedName)
            else -> append(displayPath?.let(::fileNameOf) ?: "")
        }
    }
    val editorOffset by animateDpAsState(
        targetValue = if (showSettings) (-48).dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "editorPush",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !drawerState.isClosed,
            drawerContent = {
                EditorDrawerSheet(
                    documents = viewModel.openDocuments,
                    activeDocumentId = viewModel.activeDocumentId,
                    onSelect = { id ->
                        scope.launch { drawerState.close() }
                        switchToDocument(id)
                    },
                    onClose = { id -> closeDocument(id) },
                    onMinimize = ::minimize,
                    width = 270.dp,
                )
            },
        ) {
            Scaffold(
                modifier = Modifier.offset(x = editorOffset),
                contentWindowInsets = WindowInsets.ime,
                topBar = {
                    TopBarActions(
                        title = title,
                        cursorLine = cursorLine,
                        cursorColumn = cursorColumn,
                        lineCount = lineCount,
                        editorRef = editorRef,
                        viewModel = viewModel,
                        saveButtonState = saveButtonState,
                        onSave = ::saveFile,
                        onSaveAs = ::saveCopyAsFile,
                        onReload = { viewModel.openReloadDialog() },
                        onPreview = {
                            viewModel.openPreview(
                                editorRef?.text?.toString() ?: viewModel.content.toString()
                            )
                        },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        refreshUndoRedo = ::refreshUndoRedo,
                        showOverflowMenu = showOverflowMenu,
                        onShowOverflowMenuChange = { showOverflowMenu = it },
                        showSearchPanel = showSearchPanel,
                        onShowSearchPanelChange = { showSearchPanel = it },
                        onShowSettingsChange = { showSettings = it },
                        onOpenDrawer = {
                            editorRef?.hideSoftInput()
                            scope.launch { drawerState.open() }
                        },
                        onCloseFile = { closeDocument(viewModel.activeDocumentId) },
                        onShowLanguagePicker = { viewModel.openLanguageDialog(detectedScope) },
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                ) {
                    if (viewModel.isLoading) {
                        EditorLoadingIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                        )
                    }
                    when (val state = viewModel.loadState) {
                        is LoadState.Loading -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(PRELOAD_BG_DARK),
                                content = { })
                        }

                        is LoadState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp),
                            )
                        }

                        is LoadState.Success -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                if (viewModel.prefsLoaded && viewModel.grammarsSettled) {
                                    AnimatedContent(
                                        targetState = viewModel.activeDocumentId,
                                        transitionSpec = {
                                            val documents = viewModel.openDocuments
                                            val from = documents.indexOfFirst {
                                                it.id == initialState
                                            }
                                            val to = documents.indexOfFirst {
                                                it.id == targetState
                                            }
                                            val direction = if (from in 0..to) 1 else -1
                                            slideInHorizontally(tween(240)) { width ->
                                                direction * width
                                            } togetherWith slideOutHorizontally(
                                                tween(240)
                                            ) { width -> -direction * width }
                                        },
                                        label = "documentSwitch",
                                    ) { shownId ->
                                        var laidOut by remember(shownId) {
                                            mutableStateOf(false)
                                        }
                                        AndroidView(
                                            factory = { ctx ->
                                                CodeEditor(ctx).apply {
                                                    applyTheme(this, viewModel.selectedTheme)
                                                    isWordwrap = viewModel.wordWrap
                                                    isLineNumberEnabled = viewModel.lineNumbers
                                                    typefaceText = typefaceToLoad
                                                    typefaceLineNumber = typefaceToLoad
                                                    applyMinimap(
                                                        this,
                                                        viewModel.miniMap,
                                                        viewModel.miniMapBlocks
                                                    )
                                                    applyInvisibleChars(
                                                        this,
                                                        viewModel.invisibleChars
                                                    )
                                                    @Suppress("UsePropertyAccessSyntax")
                                                    setText(viewModel.content)
                                                    setEditorLanguage(activeLanguage)
                                                    setEditable(!viewModel.readOnly)
                                                    subscribeEvent(
                                                        LayoutStateChangeEvent::class.java
                                                    ) { event, _ ->
                                                        if (!event.isLayoutBusy) {
                                                            laidOut = true
                                                            layoutReady = true
                                                        }
                                                    }
                                                    if (viewModel.textSizePx > 0f) {
                                                        setTextSizePx(viewModel.textSizePx)
                                                    }
                                                    registerInlayHintRenderers(
                                                        TextInlayHintRenderer.DefaultInstance,
                                                        ColorInlayHintRenderer.DefaultInstance,
                                                    )
                                                    registerExtraStylesProvider { line, styles ->
                                                        val caret = cursor
                                                        if (!caret.isSelected && line == caret.leftLine) {
                                                            styles.add(
                                                                LineBackground(
                                                                    line,
                                                                    ConstColor(CURRENT_LINE_TINT),
                                                                )
                                                            )
                                                        }
                                                    }
                                                    setLineSpacing(2f, 1.1f)
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                                        if (height > 0) {
                                                            verticalExtraSpaceFactor =
                                                                (BOTTOM_SCROLL_EXTRA_DP * dpUnit / height)
                                                                    .coerceIn(
                                                                        0f, 1f
                                                                    )
                                                        }
                                                    }
                                                    searcher.replaceOptions =
                                                        EditorSearcher.ReplaceOptions(true)
                                                    EditorSpanInteractionHandler(this)
                                                    getComponent<EditorAutoCompletion>().apply {
                                                        setEnabledAnimation(true)
                                                        setEnabled(viewModel.autoCompletion)
                                                    }
                                                    subscribeEvent(
                                                        ContentChangeEvent::class.java
                                                    ) { event, _ ->
                                                        canUndo.value = canUndo()
                                                        canRedo.value = canRedo()
                                                        viewModel.onContentChanged(event.action)
                                                        val start = event.changeStart.line
                                                        val end = event.changeEnd.line
                                                        if (start <= end && end < text.lineCount && end - start <= EMBEDDED_SCAN_LINES) {
                                                            viewModel.onLinesEdited(
                                                                (start..end).map { text.getLine(it) },
                                                            )
                                                        } else if (text.isNotEmpty()) {
                                                            viewModel.onLinesEdited(
                                                                listOf(
                                                                    text.subSequence(
                                                                        0,
                                                                        minOf(
                                                                            text.length,
                                                                            EMBEDDED_SCAN_CHARS
                                                                        ),
                                                                    ),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                    viewModel.restoreCursorState(
                                                        shownId,
                                                        this,
                                                    )

                                                    fun isImeVisible(): Boolean {
                                                        val insets =
                                                            ViewCompat.getRootWindowInsets(this)
                                                                ?: return false
                                                        return insets.isVisible(WindowInsetsCompat.Type.ime())
                                                    }

                                                    var selectionTap = false
                                                    var forwardingToEditor = false
                                                    var pendingDown: MotionEvent? = null
                                                    var downX = 0f
                                                    var downY = 0f
                                                    val touchSlop =
                                                        ViewConfiguration.get(ctx).scaledTouchSlop

                                                    setOnTouchListener { view, event ->
                                                        if (isImeVisible()) return@setOnTouchListener false
                                                        when (event.actionMasked) {
                                                            MotionEvent.ACTION_DOWN -> {
                                                                val cur = cursor
                                                                val onHandle =
                                                                    leftHandleDescriptor.position.contains(
                                                                        event.x,
                                                                        event.y,
                                                                    ) || rightHandleDescriptor.position.contains(
                                                                        event.x,
                                                                        event.y,
                                                                    ) || insertHandleDescriptor.position.contains(
                                                                        event.x,
                                                                        event.y,
                                                                    )
                                                                forwardingToEditor = false
                                                                selectionTap = false
                                                                if (!onHandle && cur.isSelected && isScreenPointOnText(
                                                                        event.x,
                                                                        event.y,
                                                                    )
                                                                ) {
                                                                    val pos =
                                                                        getPointPositionOnScreen(
                                                                            event.x, event.y
                                                                        )
                                                                    val line = IntPair.getFirst(pos)
                                                                    val column =
                                                                        IntPair.getSecond(pos)
                                                                    val tapIndex =
                                                                        text.getCharIndex(
                                                                            line,
                                                                            column
                                                                        )
                                                                    val withinLines =
                                                                        line in cur.leftLine..cur.rightLine
                                                                    if (withinLines && tapIndex in cur.left..cur.right) {
                                                                        selectionTap = true
                                                                        downX = event.x
                                                                        downY = event.y
                                                                        pendingDown =
                                                                            MotionEvent.obtain(event)
                                                                        return@setOnTouchListener true
                                                                    }
                                                                }
                                                                false
                                                            }

                                                            MotionEvent.ACTION_MOVE -> {
                                                                if (selectionTap && !forwardingToEditor) {
                                                                    val dx = event.x - downX
                                                                    val dy = event.y - downY
                                                                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                                                        forwardingToEditor = true
                                                                        selectionTap = false
                                                                        pendingDown?.let {
                                                                            view.onTouchEvent(
                                                                                it
                                                                            )
                                                                        }
                                                                        pendingDown?.recycle()
                                                                        pendingDown = null
                                                                    } else {
                                                                        return@setOnTouchListener true
                                                                    }
                                                                }
                                                                if (forwardingToEditor) {
                                                                    view.onTouchEvent(event)
                                                                    true
                                                                } else {
                                                                    false
                                                                }
                                                            }

                                                            MotionEvent.ACTION_UP -> {
                                                                val consumed = when {
                                                                    forwardingToEditor -> {
                                                                        view.onTouchEvent(event)
                                                                        true
                                                                    }

                                                                    selectionTap -> {
                                                                        selectionTap = false
                                                                        view.performClick()
                                                                        showSoftInput()
                                                                        true
                                                                    }

                                                                    else -> {
                                                                        false
                                                                    }
                                                                }
                                                                forwardingToEditor = false
                                                                pendingDown?.recycle()
                                                                pendingDown = null
                                                                consumed
                                                            }

                                                            MotionEvent.ACTION_CANCEL -> {
                                                                if (forwardingToEditor) view.onTouchEvent(
                                                                    event
                                                                )
                                                                selectionTap = false
                                                                forwardingToEditor = false
                                                                pendingDown?.recycle()
                                                                pendingDown = null
                                                                false
                                                            }

                                                            else -> {
                                                                forwardingToEditor
                                                            }
                                                        }
                                                    }
                                                }.also { editorRef = it }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            onRelease = { editor ->
                                                viewModel.saveCursorState(shownId, editor)
                                                if (editorRef === editor) editorRef = null
                                                editor.release()
                                            },
                                        )
                                        if (!laidOut) {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                color = Color(PRELOAD_BG_DARK),
                                                content = { })
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color(PRELOAD_BG_DARK),
                                        content = { })
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = (viewModel.symbolBar && layoutReady && !viewModel.readOnly),
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Bottom,
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 220),
                            shrinkTowards = Alignment.Bottom,
                        ),
                    ) {
                        SymbolInputBar(
                            editor = editorRef,
                            typeface = typefaceToLoad,
                            applyNavigationBarPadding = !showSearchPanel,
                        )
                    }
                    AnimatedVisibility(
                        visible = (showSearchPanel && layoutReady && !viewModel.readOnly),
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Top,
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 220),
                            shrinkTowards = Alignment.Top,
                        ),
                    ) {
                        CustomSearchBar(editor = editorRef, initialQuery = searchQuery)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = 220),
                initialOffsetX = { it },
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 220),
                targetOffsetX = { it },
            ),
        ) {
            EditorSettingsScreen(
                viewModel = viewModel,
                onNavigateUp = { showSettings = false },
            )
        }
    }
}

@Composable
private fun EditorLoadingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        color = MaterialTheme.colorScheme.onTertiary,
        trackColor = Color.Transparent,
        gapSize = 0.dp,
        modifier = modifier,
    )
}

internal fun applyTheme(
    editor: CodeEditor,
    themeName: String,
) {
    ThemeRegistry.setTheme(themeName)
    val scheme = editor.colorScheme as? TextMateColorScheme ?: buildColorScheme().also {
        editor.colorScheme = it
    }
    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, "#e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.SELECTION_INSERT, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, "#36383a".toColorInt())
    scheme.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, "#545454".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER, "#919191".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, "#919191".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, "#4a4a4a".toColorInt())
    editor.isHighlightCurrentLine = false
    val dp = editor.dpUnit
    editor.setLineNumberMarginLeft(8f * dp)
    editor.setDividerMargin(8f * dp, 4f * dp)
    val (track, thumb) = deriveScrollbarDrawables()
    editor.renderer.verticalScrollbarTrackDrawable = track
    editor.renderer.verticalScrollbarThumbDrawable = thumb
    editor.renderer.horizontalScrollbarTrackDrawable = track
    editor.renderer.horizontalScrollbarThumbDrawable = thumb
}

private fun applyMinimap(
    editor: CodeEditor,
    showMinimap: Boolean,
    blocks: Boolean,
) {
    editor.props.showMinimap = showMinimap
    editor.props.minimapConfig = MinimapConfig(minimapDrawTextAsBlocks = blocks)
    editor.invalidate()
}

private fun applyInvisibleChars(
    editor: CodeEditor,
    enabled: Boolean,
) {
    editor.nonPrintablePaintingFlags = if (enabled) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
    } else {
        0
    }
}
