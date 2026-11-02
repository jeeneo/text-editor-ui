package texteditor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object EditorPreloader {
    private const val TAG = "EditorPreloader"
    private const val MAX_SCAN_NAMES = 5_000
    private const val MAX_PRIME_SCOPES = 8
    private const val DEFAULT_KEY = "default"
    private const val OPENING_KEY = "opening"
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val handledScopes = ConcurrentHashMap.newKeySet<String>()
    private val newestByKey = ConcurrentHashMap<Any, Long>()

    private val scanJobsByKey = ConcurrentHashMap<Any, Job>()

    @Volatile
    private var warmJob: Job? = null

    fun warm(context: Context) {
        if (EditorLanguages.isInitialized && ThemeRegistry.isInitialized) {
            Log.d(TAG, "warm: language stack already loaded, nothing to do")
            return
        }
        val app = context.applicationContext
        synchronized(this) {
            if (warmJob?.isActive == true) {
                Log.d(TAG, "warm: already in flight")
                return
            }
            Log.d(TAG, "warm: loading the theme and the grammar index")
            warmJob = scope.launch {
                val started = SystemClock.elapsedRealtime()
                runCatching { prepare(app) }
                    .onFailure { Log.e(TAG, "Failed to warm the language stack", it) }
                Log.d(TAG, "warm: settled in ${SystemClock.elapsedRealtime() - started}ms")
            }
        }
    }

    fun onDirectoryLoaded(
        context: Context,
        names: List<String>,
        key: Any = DEFAULT_KEY,
        generation: Long = nextGeneration(key),
    ) {
        if (names.isEmpty()) return
        val app = context.applicationContext
        val scanned = names.take(MAX_SCAN_NAMES)
        newestByKey.merge(key, generation) { current, requested -> maxOf(current, requested) }
        if (scanJobsByKey[key]?.isActive == true) {
            Log.d(TAG, "prime[$key] #$generation replaces the scan still running")
        }
        scanJobsByKey[key]?.cancel()
        val job = scope.launch {
            // The grammar index answers the mapping, so this has to settle first.
            runCatching { prepare(app) }
                .onFailure { Log.e(TAG, "Failed to prepare for ${scanned.size} names", it) }
            if (isStale(key, generation)) {
                Log.d(TAG, "prime[$key] #$generation is stale, dropping it")
                return@launch
            }
            val mapped = EditorLanguages.scopesFor(scanned)
            val pending = mapped.filter { it !in handledScopes }
            Log.d(
                TAG,
                "prime[$key] #$generation: ${scanned.size} names -> ${mapped.size} scopes" +
                        " ${mapped.map { EditorLanguages.displayName(it) }}, ${pending.size} to prime",
            )
            if (pending.isEmpty()) return@launch
            val started = SystemClock.elapsedRealtime()
            var skipped = 0
            val registered = mutableListOf<String>()
            for (candidate in pending) {
                // Register one grammar at a time: a navigation that lands mid-scan should not
                // pay for the languages of the directory it left.
                if (!isActive || isStale(key, generation) || registered.size >= MAX_PRIME_SCOPES) {
                    break
                }
                handledScopes += candidate
                val added = EditorLanguages.registerScopes(listOf(candidate))
                if (added.isEmpty()) skipped++ else registered += added
            }
            val unavailable = if (skipped > 0) " ($skipped unavailable)" else ""
            Log.d(
                TAG,
                "Primed ${registered.size} of ${pending.size} scopes$unavailable" +
                        " for ${scanned.size} names in ${SystemClock.elapsedRealtime() - started}ms:" +
                        " ${registered.map { EditorLanguages.displayName(it) }}",
            )
        }
        scanJobsByKey[key] = job
        job.invokeOnCompletion { scanJobsByKey.remove(key, job) }
    }

    fun onFileOpening(context: Context, name: String) {
        Log.d(TAG, "priming the language of $name before it opens")
        onDirectoryLoaded(context, listOf(name), key = OPENING_KEY)
    }

    private fun prepare(app: Context) {
        EditorAssets.ensure(app)
        val started = SystemClock.elapsedRealtime()
        val themeLoaded = ThemeRegistry.isInitialized
        ThemeRegistry.initialize(app)
        val themed = SystemClock.elapsedRealtime()
        EditorLanguages.initialize(app)
        Log.d(
            TAG,
            "prepare: theme ${themed - started}ms${if (themeLoaded) " (already loaded)" else ""}," +
                    " grammar index ${SystemClock.elapsedRealtime() - themed}ms," +
                    " ${EditorLanguages.scopes.size} languages",
        )
    }

    private fun nextGeneration(key: Any): Long = (newestByKey[key] ?: 0L) + 1

    private fun isStale(key: Any, generation: Long): Boolean =
        generation < (newestByKey[key] ?: generation)
}
