package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.tictactoe.Mark
import com.napolitain.arcade.logic.tictactoe.TicTacToeEngine
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-cell animation state: draw progress (stroke/arc) + bounce scale. */
private class CellAnimState {
    val drawProgress = Animatable(0f)
    val scale = Animatable(0f)
}

@Composable
fun TicTacToeGame() {
    val engine = remember { TicTacToeEngine() }

    val winner = engine.winner
    val isDraw = engine.isDraw
    val isAiTurn = engine.isAiTurn

    val statusText = when {
        winner != null -> stringResource(R.string.winner_label, winner)
        isDraw -> stringResource(R.string.draw)
        isAiTurn -> stringResource(R.string.ai_thinking)
        else -> {
            val label = if (engine.gameMode == GameMode.AI && engine.currentPlayer == Mark.O) stringResource(R.string.ttt_o_ai) else engine.currentPlayer.name
            stringResource(R.string.turn_label, label)
        }
    }

    // AI move trigger with small delay matching the React version
    LaunchedEffect(engine.board.toList(), engine.isAiTurn) {
        if (!engine.isAiTurn) return@LaunchedEffect
        delay(900)
        engine.triggerAiMove()
    }

    // --- Per-cell animations ---
    val cellAnimations = remember { Array(9) { CellAnimState() } }
    val previousBoard = remember { arrayOfNulls<Mark>(9) }

    // Watch the board for new moves / resets and fire cell animations
    LaunchedEffect(Unit) {
        val scope = this
        snapshotFlow { engine.board.toList() }
            .collect { currentBoard ->
                for (i in 0 until 9) {
                    if (currentBoard[i] != null && previousBoard[i] == null) {
                        val anim = cellAnimations[i]
                        val mark = currentBoard[i]!!
                        // Bounce scale: 0 → 1 with spring overshoot
                        scope.launch {
                            anim.scale.snapTo(0f)
                            anim.scale.animateTo(
                                1f,
                                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                            )
                        }
                        // Stroke / arc draw progress
                        scope.launch {
                            anim.drawProgress.snapTo(0f)
                            when (mark) {
                                Mark.X -> {
                                    // First stroke 0→0.5 over 300ms, 100ms gap, second stroke 0.5→1
                                    anim.drawProgress.animateTo(0.5f, tween(300))
                                    delay(100)
                                    anim.drawProgress.animateTo(1f, tween(300))
                                }
                                Mark.O -> {
                                    // Arc sweep with slight spring overshoot
                                    anim.drawProgress.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.7f),
                                    )
                                }
                            }
                        }
                    } else if (currentBoard[i] == null && previousBoard[i] != null) {
                        // Board was reset — snap animations back to zero
                        cellAnimations[i].drawProgress.snapTo(0f)
                        cellAnimations[i].scale.snapTo(0f)
                    }
                }
                currentBoard.forEachIndexed { idx, v -> previousBoard[idx] = v }
            }
    }

    // --- Winning-line sweep animation ---
    val winLineProgress = remember { Animatable(0f) }
    LaunchedEffect(engine.winningLine) {
        if (engine.winningLine != null) {
            winLineProgress.snapTo(0f)
            winLineProgress.animateTo(1f, tween(500, easing = EaseInOut))
        } else {
            winLineProgress.snapTo(0f)
        }
    }

    val xColor = MaterialTheme.colorScheme.error          // rose-ish
    val oColor = MaterialTheme.colorScheme.primary         // cyan-ish
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val winColor = MaterialTheme.colorScheme.tertiary

    GameShell(title = stringResource(R.string.game_tictactoe), status = statusText, onReset = { engine.reset() }) {
        GameModeToggle(mode = engine.gameMode, onModeChange = { engine.setMode(it) })
        if (engine.gameMode == GameMode.AI) {
            GameDifficultyToggle(difficulty = engine.difficulty, onDifficultyChange = { engine.difficulty = it })
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(winner, isDraw, isAiTurn) {
                    detectTapGestures { offset ->
                        val cellW = size.width / 3f
                        val cellH = size.height / 3f
                        val col = (offset.x / cellW).toInt().coerceIn(0, 2)
                        val row = (offset.y / cellH).toInt().coerceIn(0, 2)
                        engine.makeMove(row * 3 + col)
                    }
                },
        ) {
            val cellW = size.width / 3f
            val cellH = size.height / 3f
            val lineStroke = size.width * 0.012f

            // Grid lines
            for (i in 1..2) {
                drawLine(gridColor, Offset(cellW * i, 0f), Offset(cellW * i, size.height), lineStroke, StrokeCap.Round)
                drawLine(gridColor, Offset(0f, cellH * i), Offset(size.width, cellH * i), lineStroke, StrokeCap.Round)
            }

            // Marks — animated per cell
            val board = engine.board
            for (idx in 0 until 9) {
                val mark = board[idx] ?: continue
                val anim = cellAnimations[idx]
                val scaleVal = anim.scale.value
                if (scaleVal == 0f) continue
                val drawProg = anim.drawProgress.value

                val col = idx % 3
                val row = idx / 3
                val cx = cellW * col + cellW / 2f
                val cy = cellH * row + cellH / 2f
                val pad = cellW * 0.22f
                val half = (cellW / 2f - pad) * scaleVal
                val sw = lineStroke * 1.8f

                when (mark) {
                    Mark.X -> drawAnimatedX(cx, cy, half, xColor, sw, drawProg)
                    Mark.O -> drawAnimatedO(cx, cy, half, oColor, sw, drawProg)
                }
            }

            // Winning line sweep
            val wl = engine.winningLine
            if (wl != null && winLineProgress.value > 0f) {
                val (a, _, c) = wl
                val startCol = a % 3; val startRow = a / 3
                val endCol = c % 3; val endRow = c / 3
                val sx = cellW * startCol + cellW / 2f
                val sy = cellH * startRow + cellH / 2f
                val ex = cellW * endCol + cellW / 2f
                val ey = cellH * endRow + cellH / 2f
                val progress = winLineProgress.value
                drawLine(
                    winColor,
                    Offset(sx, sy),
                    Offset(sx + (ex - sx) * progress, sy + (ey - sy) * progress),
                    lineStroke * 2.5f,
                    StrokeCap.Round,
                )
            }
        }
    }
}

// ── Animated drawing helpers ────────────────────────────────────────────────

/** Draw X stroke-by-stroke: first diagonal at progress 0→0.5, second at 0.5→1. */
private fun DrawScope.drawAnimatedX(
    cx: Float, cy: Float, half: Float, color: Color, strokeWidth: Float, progress: Float,
) {
    if (progress > 0f) {
        val p1 = (progress / 0.5f).coerceIn(0f, 1f)
        val x0 = cx - half; val y0 = cy - half
        val x1 = cx + half; val y1 = cy + half
        drawLine(
            color,
            Offset(x0, y0),
            Offset(x0 + (x1 - x0) * p1, y0 + (y1 - y0) * p1),
            strokeWidth, StrokeCap.Round,
        )
    }
    if (progress > 0.5f) {
        val p2 = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val x0 = cx + half; val y0 = cy - half
        val x1 = cx - half; val y1 = cy + half
        drawLine(
            color,
            Offset(x0, y0),
            Offset(x0 + (x1 - x0) * p2, y0 + (y1 - y0) * p2),
            strokeWidth, StrokeCap.Round,
        )
    }
}

/** Draw O as an arc whose sweep angle grows from 0° to 360° (with spring overshoot). */
private fun DrawScope.drawAnimatedO(
    cx: Float, cy: Float, radius: Float, color: Color, strokeWidth: Float, progress: Float,
) {
    if (progress <= 0f) return
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = progress * 360f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
