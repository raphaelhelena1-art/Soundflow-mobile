package com.soundflow.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Barre de lecture et de recherche (Seeker) sous forme d'onde sonore (Waveform).
 *
 * Affiche des barres verticales avec des hauteurs déterministes propres à chaque morceau,
 * un effet d'égaliseur animé lorsque la musique tourne, et la possibilité de cliquer
 * ou glisser pour naviguer dans le morceau.
 */
@Composable
fun WaveformBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    trackId: String = "",
    barCount: Int = 36,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    onSeekTo: ((Long) -> Unit)? = null,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Génère des hauteurs relatives (0.15f à 1.0f) uniques pour chaque morceau
    val heights = remember(trackId, barCount) {
        val seed = trackId.hashCode()
        List(barCount) { index ->
            val n = sin((index + 1) * 0.45 + (seed % 100) * 0.1).toFloat()
            val noise = sin((index + 1) * 1.8 + seed * 0.05).toFloat() * 0.35f
            (abs(n + noise) % 0.8f + 0.2f).coerceIn(0.15f, 1.0f)
        }
    }

    // Animation continue pour faire pulser gentiment l'onde pendant la lecture
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(durationMs, onSeekTo) {
                if (onSeekTo == null || durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekTo((fraction * durationMs).toLong())
                }
            }
            .pointerInput(durationMs, onSeekTo) {
                if (onSeekTo == null || durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekTo((fraction * durationMs).toLong())
                }
            },
    ) {
        val width = size.width
        val height = size.height
        val totalBars = heights.size

        val totalBarWidthPx = barWidth.toPx()
        val totalGapWidthPx = barGap.toPx()
        val neededWidth = totalBars * totalBarWidthPx + (totalBars - 1) * totalGapWidthPx

        val scaleRatio = if (neededWidth > 0) width / neededWidth else 1f
        val actualBarWidth = totalBarWidthPx * scaleRatio
        val actualGapWidth = totalGapWidthPx * scaleRatio

        val currentActiveIndex = (progress * totalBars).toInt().coerceIn(0, totalBars)

        for (i in 0 until totalBars) {
            val baseHeightProp = heights[i]

            val pulse = if (isPlaying) {
                val waveFactor = sin(pulsePhase + i * 0.4f) * 0.08f
                (baseHeightProp + waveFactor).coerceIn(0.15f, 1.0f)
            } else {
                baseHeightProp
            }

            val barHeightPx = (height * pulse).coerceAtLeast(4f)
            val left = i * (actualBarWidth + actualGapWidth)
            val top = (height - barHeightPx) / 2f
            val isPlayed = i < currentActiveIndex

            drawRoundRect(
                color = if (isPlayed) activeColor else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(actualBarWidth, barHeightPx),
                cornerRadius = CornerRadius(actualBarWidth / 2f, actualBarWidth / 2f),
            )
        }
    }
}
