package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val ExplosionOuter = Color(0xFFFF6B00)
private val ExplosionMiddle = Color(0xFFFF4500)
private val ExplosionInner = Color(0xFFDC2626)
private val CorrectGlow = Color(0xFF4ADE80)
private val ComboColor = Color(0xFFFBBF24)
private val GameAreaBg = Color(0xFF0F172A)

private fun binColorForCategory(category: String): Color = when (category) {
    "Red" -> Color(0xFFEF4444)
    "Blue" -> Color(0xFF3B82F6)
    "Green" -> Color(0xFF22C55E)
    "Yellow" -> Color(0xFFEAB308)
    "Circle" -> Color(0xFF8B5CF6)
    "Square" -> Color(0xFFF97316)
    "Triangle" -> Color(0xFF06B6D4)
    "Diamond" -> Color(0xFFEC4899)
    "Odd" -> Color(0xFFF59E0B)
    "Even" -> Color(0xFF14B8A6)
    else -> Color(0xFF6B7280)
}

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

    // Drag state
    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

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

    // Bin glow/pulse animation
    val glowTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
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
    val roundLabel = stringResource(
        R.string.sos_round,
        engine.currentRoundType.name.lowercase().replaceFirstChar { it.uppercase() },
    )

    GameShell(
        title = stringResource(R.string.game_sortorsplode),
        status = "$livesText  $levelText  $roundLabel" +
            if (comboText.isNotEmpty()) "  $comboText" else "",
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

        // Game canvas with drag-and-drop
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.65f)
                .pointerInput(engine.gameOver, engine.binCount) {
                    if (engine.gameOver) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val bc = engine.binCount.coerceAtLeast(1)
                            val colW = canvasW / bc
                            val hitRadius = colW * 0.32f

                            var bestId: Int? = null
                            var bestDist = hitRadius * hitRadius
                            for (item in engine.items) {
                                val ix = item.x * canvasW
                                val iy = item.y * canvasH
                                val dx = ix - offset.x
                                val dy = iy - offset.y
                                val dist = dx * dx + dy * dy
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestId = item.id
                                }
                            }
                            if (bestId != null) {
                                draggedItemId = bestId
                                engine.draggedItemId = bestId
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val id = draggedItemId
                            if (id != null) {
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val dx = dragAmount.x / canvasW
                                val dy = dragAmount.y / canvasH
                                dragOffsetX += dx
                                dragOffsetY += dy
                                engine.updateItemPosition(id, dx, dy)
                            }
                        },
                        onDragEnd = {
                            val id = draggedItemId
                            if (id != null) {
                                val item = engine.items.firstOrNull { it.id == id }
                                if (item != null) {
                                    val binH = 0.12f
                                    val binYStart = 1f - binH
                                    if (item.y >= binYStart - 0.04f) {
                                        val bc = engine.binCount.coerceAtLeast(1)
                                        val cw = 1f / bc
                                        val binIdx = (item.x / cw).toInt()
                                            .coerceIn(0, bc - 1)
                                        engine.sortItem(id, binIdx)
                                    }
                                    // Otherwise item stays and physics resumes
                                }
                            }
                            draggedItemId = null
                            engine.draggedItemId = null
                        },
                        onDragCancel = {
                            draggedItemId = null
                            engine.draggedItemId = null
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val binCount = engine.bins.size.coerceAtLeast(1)
            val colW = w / binCount
            val binH = h * 0.12f
            val binY = h - binH
            val itemRadius = (w * 0.07f).coerceAtMost(colW * 0.22f)

            // Background
            drawRect(GameAreaBg)

            // Determine hover state for drag-over-bin highlighting
            val dragItem = engine.items.firstOrNull { it.id == draggedItemId }
            val hoveredBinIdx = if (dragItem != null &&
                dragItem.y >= (1f - 0.12f - 0.04f)
            ) {
                (dragItem.x * w / colW).toInt().coerceIn(0, binCount - 1)
            } else {
                -1
            }
            val isCorrectHover = hoveredBinIdx >= 0 && dragItem != null &&
                engine.bins.getOrNull(hoveredBinIdx)?.category == dragItem.category

            // Draw bins
            engine.bins.forEachIndexed { i, bin ->
                val bx = colW * i + colW * 0.05f
                val bw = colW * 0.9f
                val binColor = binColorForCategory(bin.category)
                val isHovered = i == hoveredBinIdx

                // Glow behind bin (pulsing)
                val gAlpha = when {
                    isHovered && isCorrectHover -> 0.6f
                    isHovered -> 0.3f
                    else -> glowAlpha * 0.15f
                }
                drawRoundRect(
                    binColor.copy(alpha = gAlpha),
                    Offset(bx - 4f, binY),
                    Size(bw + 8f, binH),
                    CornerRadius(16f),
                )

                // Bin background
                drawRoundRect(
                    binColor.copy(alpha = 0.2f),
                    Offset(bx, binY + 4f),
                    Size(bw, binH - 8f),
                    CornerRadius(12f),
                )

                // Bin border (pulsing)
                val borderAlpha = when {
                    isHovered && isCorrectHover -> 1f
                    isHovered -> 0.7f
                    else -> glowAlpha
                }
                val borderWidth = if (isHovered && isCorrectHover) 5f else 3f
                drawRoundRect(
                    binColor.copy(alpha = borderAlpha),
                    Offset(bx, binY + 4f),
                    Size(bw, binH - 8f),
                    CornerRadius(12f),
                    style = Stroke(borderWidth),
                )

                // Correct-hover success ring
                if (isHovered && isCorrectHover) {
                    drawRoundRect(
                        CorrectGlow.copy(alpha = 0.5f),
                        Offset(bx - 2f, binY + 2f),
                        Size(bw + 4f, binH - 4f),
                        CornerRadius(14f),
                        style = Stroke(3f),
                    )
                }

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

            // Draw floating items
            for (item in engine.items) {
                val isDragged = item.id == draggedItemId
                val cx = item.x * w
                val cy = item.y * h
                val scale = if (isDragged) 1.3f else 1f
                val r = itemRadius * scale

                // Shadow
                drawCircle(
                    Color.Black.copy(alpha = if (isDragged) 0.4f else 0.25f),
                    r + 3f,
                    Offset(cx + 2f, cy + 4f),
                )

                // Item shape
                drawItemShape(item.shape, Offset(cx, cy), r, item.color, wobble)

                // Item label
                val itemLabelStyle = TextStyle(
                    color = Color.White,
                    fontSize = (if (isDragged) 14 else 11).sp,
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

                // Dragged item glow ring
                if (isDragged) {
                    drawCircle(
                        Color.White.copy(alpha = 0.25f),
                        r + 6f,
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
