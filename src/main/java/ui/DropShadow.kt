package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PaneDropShadow() {
    val shadow = MaterialTheme.colorScheme.scrim
    val paneShadowSize = 4.dp
    val paneShadowAlpha = 0.4f
    Canvas(Modifier.fillMaxSize()) {
        val band = paneShadowSize.toPx()
        if (band <= 0f) return@Canvas
        val paneWidth = size.width
        fun edge(alpha: Float) = shadow.copy(alpha = alpha)
        drawRect(
            brush = Brush.verticalGradient(
                0f to edge(paneShadowAlpha),
                0.5f to edge(paneShadowAlpha * 0.25f),
                1f to Color.Transparent,
                endY = band,
            ),
            size = Size(paneWidth, band),
        )
    }
}
