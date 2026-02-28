package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
        delay(180)
        engine.triggerAiMove()
    }

    // Winning-line animation
    val winLineProgress = remember { Animatable(0f) }
    LaunchedEffect(engine.winningLine) {
        if (engine.winningLine != null) {
            winLineProgress.snapTo(0f)
            winLineProgress.animateTo(1f, tween(350))
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
                // vertical
                drawLine(gridColor, Offset(cellW * i, 0f), Offset(cellW * i, size.height), lineStroke, StrokeCap.Round)
                // horizontal
                drawLine(gridColor, Offset(0f, cellH * i), Offset(size.width, cellH * i), lineStroke, StrokeCap.Round)
            }

            // Marks
            val board = engine.board
            for (idx in 0 until 9) {
                val mark = board[idx] ?: continue
                val col = idx % 3
                val row = idx / 3
                val cx = cellW * col + cellW / 2f
                val cy = cellH * row + cellH / 2f
                val pad = cellW * 0.22f
                when (mark) {
                    Mark.X -> drawX(cx, cy, cellW / 2f - pad, xColor, lineStroke * 1.8f)
                    Mark.O -> drawO(cx, cy, cellW / 2f - pad, oColor, lineStroke * 1.8f)
                }
            }

            // Winning line
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

private fun DrawScope.drawX(cx: Float, cy: Float, half: Float, color: Color, strokeWidth: Float) {
    drawLine(color, Offset(cx - half, cy - half), Offset(cx + half, cy + half), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(cx + half, cy - half), Offset(cx - half, cy + half), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawO(cx: Float, cy: Float, radius: Float, color: Color, strokeWidth: Float) {
    drawCircle(color, radius, Offset(cx, cy), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
