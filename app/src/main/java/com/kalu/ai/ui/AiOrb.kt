package com.kalu.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Animated blue orb. Pulses faster while KALU is "thinking". */
@Composable
fun AiOrb(modifier: Modifier = Modifier, size: Dp = 44.dp, thinking: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (thinking) 600 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ring by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Canvas(modifier = modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f

        // Soft outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x884DA3FF), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        // Expanding ring
        drawCircle(
            color = Color(0xFF4DA3FF).copy(alpha = (1f - ring) * 0.8f),
            radius = r * ring,
            center = c,
            style = Stroke(width = r * 0.06f)
        )
        // Core
        val coreR = r * 0.55f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE6F4FF), Color(0xFF4DA3FF), Color(0xFF1B4F8A)),
                center = c,
                radius = coreR
            ),
            radius = coreR,
            center = c
        )
    }
}
