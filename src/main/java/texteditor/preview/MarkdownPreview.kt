package texteditor.preview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable
fun MarkdownPreviewDialog(
    markdown: String,
    title: String,
    baseUrl: String? = null,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                }
                AndroidView(
                    factory = { context ->
                        createPreviewWebView(context, markdown, title, baseUrl)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPreviewWebView(
    context: Context,
    markdown: String,
    title: String,
    baseUrl: String?,
): WebView {
    val webView = WebView(context)
    val viewer = viewerUrlFor(webView.settings.userAgentString)
    webView.setBackgroundColor(PREVIEW_BACKGROUND)
    webView.settings.javaScriptEnabled = true
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(
                "window.previewMarkdown(${jsString(markdown)}, ${jsString(title)}, " +
                        "${jsString(baseUrl)})",
                null,
            )
        }
    }
    webView.loadUrl(viewer)
    return webView
}

private fun viewerUrlFor(userAgent: String?): String {
    val major = userAgent?.let { CHROME_VERSION.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: return COMPAT_VIEWER_URL
    return if (major < COMPAT_BELOW_MAJOR) COMPAT_VIEWER_URL else VIEWER_URL
}

private fun jsString(value: String?): String =
    JSONObject.quote(value ?: "")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

private const val VIEWER_URL = "file:///android_asset/viewer/index.html"
private const val COMPAT_VIEWER_URL = "file:///android_asset/viewer/compat/index.html"
private const val PREVIEW_BACKGROUND = 0xFF1E1E1E.toInt()
private const val COMPAT_BELOW_MAJOR = 66
private val CHROME_VERSION = Regex("""Chrome/(\d+)""")
