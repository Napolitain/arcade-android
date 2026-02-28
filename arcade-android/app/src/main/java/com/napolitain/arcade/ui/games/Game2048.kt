package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.game2048.Direction
import com.napolitain.arcade.logic.game2048.Game2048Engine
import com.napolitain.arcade.ui.components.GameShell
import kotlin.math.abs

// ── Tile colors ────────────────────────────────────────────────────────

private fun tileBackground(value: Int): Color = when (value) {
    0    -> Color(0xCC1E293B)          // slate-800/80
    2    -> Color(0xFF334155)          // slate-700
    4    -> Color(0xFF475569)          // slate-600
    8    -> Color(0xFFF59E0B)          // amber-500
    16   -> Color(0xFFF97316)          // orange-500
    32   -> Color(0xFFEA580C)          // orange-600
    64   -> Color(0xFFF43F5E)          // rose-500
    else -> Color(0xFFA855F7)          // purple-500
}

private fun tileTextColor(value: Int): Color = when (value) {
    0              -> Color.Transparent
    2, 4           -> Color(0xFFF1F5F9)  // slate-100
    8, 16          -> Color(0xFF020617)  // slate-950
    else           -> Color(0xFFF8FAFC)  // slate-50
}

// ── Grid drawing helper ────────────────────────────────────────────────

private fun DrawScope.drawGrid(
    grid: IntArray,
    textMeasurer: TextMeasurer,
    tileScale: Float,
) {
    val gridSize = Game2048Engine.SIZE
    val padding = size.minDimension * 0.03f
    val gap = size.minDimension * 0.02f
    val boardSize = size.minDimension - padding * 2
    val tileSize = (boardSize - gap * (gridSize - 1)) / gridSize
    val cornerRadius = CornerRadius(tileSize * 0.12f)

    // Board background
    drawRoundRect(
        color = Color(0x664A2410),       // amber-950/40-ish
        topLeft = Offset(padding * 0.5f, padding * 0.5f),
        size = Size(size.minDimension - padding, size.minDimension - padding),
        cornerRadius = CornerRadius(tileSize * 0.2f),
    )

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val index = row * gridSize + col
            val value = grid[index]
            val x = padding + col * (tileSize + gap)
            val y = padding + row * (tileSize + gap)

            // Scale from centre for spawn/merge animation
            val scale = if (value != 0) tileScale else 1f
            val scaledSize = tileSize * scale
            val offset = (tileSize - scaledSize) / 2f

            drawRoundRect(
                color = tileBackground(value),
                topLeft = Offset(x + offset, y + offset),
                size = Size(scaledSize, scaledSize),
                cornerRadius = cornerRadius,
            )

            if (value != 0) {
                val fontPx = when {
                    value < 100   -> tileSize * 0.42f
                    value < 1000  -> tileSize * 0.34f
                    else          -> tileSize * 0.26f
                }
                val style = TextStyle(
                    color = tileTextColor(value),
                    fontSize = (fontPx / density).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                val layoutResult = textMeasurer.measure(
                    text = value.toString(),
                    style = style,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = scaledSize.toInt(),
                    ),
                )
                val textX = x + offset + (scaledSize - layoutResult.size.width) / 2f
                val textY = y + offset + (scaledSize - layoutResult.size.height) / 2f
                drawText(layoutResult, topLeft = Offset(textX, textY))
            }
        }
    }
}

// ── Main composable ────────────────────────────────────────────────────

@Composable
fun Game2048() {
    val engine = remember { Game2048Engine() }
    val textMeasurer = rememberTextMeasurer()

    val statusText = when {
        engine.won && engine.gameOver -> stringResource(R.string.g2048_win_over, engine.score)
        engine.won                    -> stringResource(R.string.g2048_win_continue)
        engine.gameOver               -> stringResource(R.string.g2048_game_over, engine.score)
        else                          -> stringResource(R.string.g2048_instruction)
    }

    // Tile pop animation on every grid change
    val tileAnim = remember { Animatable(0.85f) }
    val gridSnapshot = engine.grid   // read state so LaunchedEffect re-triggers
    LaunchedEffect(gridSnapshot) {
        tileAnim.snapTo(0.85f)
        tileAnim.animateTo(1f, animationSpec = tween(durationMillis = 120))
    }

    GameShell(
        title = stringResource(R.string.game_2048),
        status = statusText,
        score = engine.score.toString(),
        onReset = { engine.reset() },
    ) {
        // ── Swipe-enabled canvas ───────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    var totalDx = 0f
                    var totalDy = 0f
                    var fired = false
                    detectDragGestures(
                        onDragStart = {
                            totalDx = 0f; totalDy = 0f; fired = false
                        },
                        onDrag = { _, dragAmount ->
                            totalDx += dragAmount.x
                            totalDy += dragAmount.y
                            if (!fired) {
                                val threshold = size.width * 0.05f
                                if (abs(totalDx) > threshold || abs(totalDy) > threshold) {
                                    fired = true
                                    val direction = if (abs(totalDx) > abs(totalDy)) {
                                        if (totalDx > 0) Direction.RIGHT else Direction.LEFT
                                    } else {
                                        if (totalDy > 0) Direction.DOWN else Direction.UP
                                    }
                                    engine.move(direction)
                                }
                            }
                        },
                    )
                },
        ) {
            drawGrid(grid = engine.grid, textMeasurer = textMeasurer, tileScale = tileAnim.value)
        }

        // ── Direction buttons ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data class DirBtn(val dir: Direction, val label: String)

            val buttons = listOf(
                DirBtn(Direction.LEFT, stringResource(R.string.dir_left)),
                DirBtn(Direction.RIGHT, stringResource(R.string.dir_right)),
                DirBtn(Direction.UP, stringResource(R.string.dir_up)),
                DirBtn(Direction.DOWN, stringResource(R.string.dir_down)),
            )
            buttons.forEach { btn ->
                FilledTonalButton(
                    onClick = { engine.move(btn.dir) },
                    enabled = !engine.gameOver,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(btn.label, fontSize = 12.sp)
                }
            }
        }
    }
}
