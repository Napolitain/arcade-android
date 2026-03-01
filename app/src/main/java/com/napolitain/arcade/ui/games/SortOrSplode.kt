package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.sortorsplode.Explosion
import com.napolitain.arcade.logic.sortorsplode.ItemShape
import com.napolitain.arcade.logic.sortorsplode.SortOrSplodeEngine
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ── colors ─────────────────────────────────────────────────────────── */

private val BinColors = listOf(
    Color(0xFF1E293B),
    Color(0xFF1A2332),
    Color(0xFF1E2A1E),
    Color(0xFF2A1E1E),
)
private val BinBorders = listOf(
    Color(0xFF3B82F6),
    Color(0xFF22C55E),
    Color(0xFFEF4444),
    Color(0xFFEAB308),
)
private val ExplosionOuter = Color(0xFFFF6B00)
private val ExplosionMiddle = Color(0xFFFF4500)
private val ExplosionInner = Color(0xFFDC2626)
private val CorrectGlow = Color(0xFF4ADE80)
private val ComboColor = Color(0xFFFBBF24)
private val GameAreaBg = Color(0xFF0F172A)

/* ── shape drawing helpers ─────────────────────────────────────────── */

private fun DrawScope.drawItemShape(
    shape: ItemShape,
    center: Offset,
    radius: Float,
    color: Color,
    wobble: Float,
) {
    withTransform({
        rotate(wobble * 8f, center)
    }) {
        when (shape) {
            ItemShape.CIRCLE -> {
                drawCircle(color, radius, center)
                drawCircle(Color.White.copy(alpha = 0.3f), radius * 0.35f, Offset(center.x - radius * 0.25f, center.y - radius * 0.25f))
            }
            ItemShape.SQUARE -> {
                val half = radius * 0.85f
                drawRoundRect(
                    color,
                    Offset(center.x - half, center.y - half),
                    Size(half * 2, half * 2),
                    CornerRadius(radius * 0.2f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.15f),
                    Offset(center.x - half + 3, center.y - half + 3),
                    Size(half * 0.6f, half * 0.4f),
                    CornerRadius(radius * 0.15f),
                )
            }
            ItemShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x - radius * 0.9f, center.y + radius * 0.7f)
                    lineTo(center.x + radius * 0.9f, center.y + radius * 0.7f)
                    close()
                }
                drawPath(path, color)
                drawPath(path, Color.White.copy(alpha = 0.1f), style = Stroke(2f))
            }
            ItemShape.DIAMOND -> {
                val path = Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x + radius * 0.75f, center.y)
                    lineTo(center.x, center.y + radius)
                    lineTo(center.x - radius * 0.75f, center.y)
                    close()
                }
                drawPath(path, color)
                drawPath(path, Color.White.copy(alpha = 0.15f), style = Stroke(2f))
            }
        }
    }
}

private fun DrawScope.drawExplosionEffect(exp: Explosion, canvasW: Float, canvasH: Float) {
    val cx = exp.x * canvasW
    val cy = exp.y * canvasH
    val p = exp.progress
    val fade = (1f - p).coerceIn(0f, 1f)

    if (exp.isCorrect) {
        // Green success ring
        val radius = 40f + 60f * p
        drawCircle(CorrectGlow.copy(alpha = fade * 0.6f), radius, Offset(cx, cy), style = Stroke(4f))
        drawCircle(CorrectGlow.copy(alpha = fade * 0.2f), radius * 0.6f, Offset(cx, cy))
    } else {
        // Explosion particles
        val particleCount = 12
        for (i in 0 until particleCount) {
            val angle = (2.0 * PI * i / particleCount).toFloat()
            val dist = 20f + 80f * p
            val px = cx + cos(angle) * dist
            val py = cy + sin(angle) * dist
            val r = (6f - 4f * p).coerceAtLeast(1f)
            val color = when (i % 3) {
                0 -> ExplosionOuter
                1 -> ExplosionMiddle
                else -> ExplosionInner
            }
            drawCircle(color.copy(alpha = fade), r, Offset(px, py))
        }
        // Central flash
        drawCircle(Color.White.copy(alpha = fade * 0.8f), 18f * (1f - p), Offset(cx, cy))
        drawCircle(ExplosionOuter.copy(alpha = fade * 0.5f), 30f * p, Offset(cx, cy))
    }
}

/* ── main composable ───────────────────────────────────────────────── */

@Composable
fun SortOrSplodeGame() {
    val engine = remember { SortOrSplodeEngine() }
    val textMeasurer = rememberTextMeasurer()

    // Game loop
    LaunchedEffect(engine.gameOver) {
        if (engine.gameOver) return@LaunchedEffect
        var lastFrame = withFrameMillis { it }
        while (!engine.gameOver) {
            val now = withFrameMillis { it }
            val delta = (now - lastFrame).coerceIn(1, 50)
            lastFrame = now
            engine.tick(delta)
        }
    }

    // Wobble animation
    val wobbleTransition = rememberInfiniteTransition(label = "wobble")
    val wobble by wobbleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )

    // Combo flash
    val comboFlash = remember { Animatable(0f) }
    LaunchedEffect(engine.combo) {
        if (engine.combo > 1) {
            comboFlash.snapTo(1f)
            comboFlash.animateTo(0f, tween(400))
        }
    }

    val livesText = stringResource(R.string.sos_lives, engine.lives)
    val levelText = stringResource(R.string.sos_level, engine.level)
    val comboText = if (engine.combo > 1) stringResource(R.string.sos_combo, engine.combo) else ""
    val roundLabel = stringResource(R.string.sos_round, engine.currentRoundType.name.lowercase()
        .replaceFirstChar { it.uppercase() })

    GameShell(
        title = stringResource(R.string.game_sortorsplode),
        status = "$livesText  $levelText  $roundLabel" + if (comboText.isNotEmpty()) "  $comboText" else "",
        score = engine.score.toString(),
        onReset = { engine.reset() },
    ) {
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // Game Over overlay text
        if (engine.gameOver) {
            Text(
                text = stringResource(R.string.sos_game_over),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = stringResource(R.string.sos_tap_to_sort),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Game canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.65f)
                .pointerInput(engine.gameOver, engine.binCount) {
                    if (engine.gameOver) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            if (event.changes.firstOrNull()?.pressed == true) {
                                event.changes.forEach { it.consume() }
                                val colWidth = size.width.toFloat() / engine.binCount
                                val binIdx = (pos.x / colWidth).toInt().coerceIn(0, engine.binCount - 1)
                                val lowest = engine.getLowestItem()
                                if (lowest != null) {
                                    engine.sortItem(lowest.id, binIdx)
                                }
                            }
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val binCount = engine.bins.size.coerceAtLeast(1)
            val colW = w / binCount
            val binH = h * 0.12f
            val binY = h - binH
            val itemRadius = colW * 0.16f

            // Background
            drawRect(GameAreaBg)

            // Column separators
            for (i in 1 until binCount) {
                val x = colW * i
                drawLine(
                    Color.White.copy(alpha = 0.06f),
                    Offset(x, 0f),
                    Offset(x, binY),
                    strokeWidth = 1f,
                )
            }

            // Danger zone gradient near bins
            drawRect(
                Color(0x18FF4444),
                Offset(0f, binY - h * 0.08f),
                Size(w, h * 0.08f),
            )

            // Draw bins
            engine.bins.forEachIndexed { i, bin ->
                val bx = colW * i + colW * 0.05f
                val bw = colW * 0.9f
                val borderColor = BinBorders[i % BinBorders.size]

                // Bin background
                drawRoundRect(
                    BinColors[i % BinColors.size],
                    Offset(bx, binY + 4f),
                    Size(bw, binH - 8f),
                    CornerRadius(12f),
                )
                // Bin border
                drawRoundRect(
                    borderColor,
                    Offset(bx, binY + 4f),
                    Size(bw, binH - 8f),
                    CornerRadius(12f),
                    style = Stroke(3f),
                )

                // Bin label
                val labelStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                val measured = textMeasurer.measure(bin.label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        bx + (bw - measured.size.width) / 2f,
                        binY + (binH - measured.size.height) / 2f,
                    ),
                )
            }

            // Draw falling items
            for (item in engine.items) {
                val cx = colW * item.xSlot + colW * 0.5f
                val cy = item.yProgress * (binY - itemRadius * 2) + itemRadius

                // Shadow
                drawCircle(
                    Color.Black.copy(alpha = 0.25f),
                    itemRadius + 3f,
                    Offset(cx + 2f, cy + 2f),
                )

                // Item shape
                drawItemShape(item.shape, Offset(cx, cy), itemRadius, item.color, wobble * (1f + item.yProgress))

                // Item label
                val itemLabelStyle = TextStyle(
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                val labelMeasured = textMeasurer.measure(item.label, itemLabelStyle)
                drawText(
                    labelMeasured,
                    topLeft = Offset(
                        cx - labelMeasured.size.width / 2f,
                        cy - labelMeasured.size.height / 2f,
                    ),
                )

                // Highlight the lowest item
                if (item == engine.getLowestItem()) {
                    drawCircle(
                        Color.White.copy(alpha = 0.15f + 0.1f * wobble),
                        itemRadius + 5f,
                        Offset(cx, cy),
                        style = Stroke(2.5f),
                    )
                }
            }

            // Draw explosions
            for (exp in engine.explosions) {
                drawExplosionEffect(exp, w, h)

                // Points text for correct sorts
                if (exp.pointsText.isNotEmpty() && exp.isCorrect) {
                    val pStyle = TextStyle(
                        color = CorrectGlow.copy(alpha = (1f - exp.progress).coerceIn(0f, 1f)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    val pMeasured = textMeasurer.measure(exp.pointsText, pStyle)
                    drawText(
                        pMeasured,
                        topLeft = Offset(
                            exp.x * w - pMeasured.size.width / 2f,
                            exp.y * h - 40f - 30f * exp.progress,
                        ),
                    )
                }
            }

            // Combo popup
            if (engine.combo > 1 && comboFlash.value > 0f) {
                val cStyle = TextStyle(
                    color = ComboColor.copy(alpha = comboFlash.value),
                    fontSize = (22 + engine.combo.coerceAtMost(10) * 2).sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                val cText = "x${engine.combo}"
                val cMeasured = textMeasurer.measure(cText, cStyle)
                drawText(
                    cMeasured,
                    topLeft = Offset(
                        (w - cMeasured.size.width) / 2f,
                        h * 0.35f - cMeasured.size.height / 2f,
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Lives display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val heartsText = buildString {
                repeat(engine.lives) { append("❤\uFE0F") }
                repeat(SortOrSplodeEngine.MAX_LIVES - engine.lives) { append("🖤") }
            }
            Text(
                text = heartsText,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
