package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.connectfour.ConnectFourEngine
import com.napolitain.arcade.logic.connectfour.ConnectFourEngine.Companion.COLUMNS
import com.napolitain.arcade.logic.connectfour.ConnectFourEngine.Companion.ROWS
import com.napolitain.arcade.logic.connectfour.ConnectFourEngine.Companion.getCellIndex
import com.napolitain.arcade.logic.connectfour.Disc
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay

private val BoardColor = Color(0xFF1E1B4B) // indigo-950
private val SlotColor = Color(0xFF334155)   // slate-700
private val RedDisc = Color(0xFFFB7185)     // rose-400
private val RedStroke = Color(0xFFFECDD3)   // rose-200
private val RedHighlight = Color(0xB3FFE4E6)
private val YellowDisc = Color(0xFFFCD34D)  // amber-300
private val YellowStroke = Color(0xFFFEF3C7) // amber-100
private val YellowHighlight = Color(0xB8FEF3C7)

@Composable
fun ConnectFourGame() {
    val engine = remember { ConnectFourEngine() }

    // Trigger for drop animation
    var animatingIndex by remember { mutableIntStateOf(-1) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animatingIndex >= 0) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "discDrop",
    )

    // Sync animatingIndex with lastDropIndex
    LaunchedEffect(engine.lastDropIndex) {
        if (engine.lastDropIndex >= 0) {
            animatingIndex = -1 // reset to trigger re-animation
            delay(16) // one frame
            animatingIndex = engine.lastDropIndex
        }
    }

    // AI move with delay
    LaunchedEffect(engine.isAiTurn, engine.board) {
        if (engine.isAiTurn) {
            delay(180)
            engine.performAiMove()
        }
    }

    val density = LocalDensity.current
    val boardPadding = with(density) { 12.dp.toPx() }
    val cellSpacing = with(density) { 4.dp.toPx() }

    GameShell(
        title = stringResource(R.string.game_connectfour),
        status = engine.statusText,
        onReset = { engine.resetGame() },
    ) {
        GameModeToggle(
            mode = engine.mode,
            onModeChange = { engine.setGameMode(it) },
        )

        if (engine.mode == GameMode.AI) {
            GameDifficultyToggle(
                difficulty = engine.difficulty,
                onDifficultyChange = { engine.setGameDifficulty(it) },
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COLUMNS.toFloat() / ROWS)
                .pointerInput(engine.winner, engine.isDraw, engine.isAiTurn) {
                    detectTapGestures { offset ->
                        val totalWidth = size.width.toFloat()
                        val cellSize =
                            (totalWidth - 2 * boardPadding - (COLUMNS - 1) * cellSpacing) / COLUMNS
                        val col =
                            ((offset.x - boardPadding) / (cellSize + cellSpacing)).toInt()
                                .coerceIn(0, COLUMNS - 1)
                        engine.dropDisc(col)
                    }
                },
        ) {
            drawBoard(
                board = engine.board,
                animatingIndex = animatingIndex,
                animationProgress = animationProgress,
                padding = boardPadding,
                spacing = cellSpacing,
            )
        }
    }
}

private fun DrawScope.drawBoard(
    board: Array<Disc?>,
    animatingIndex: Int,
    animationProgress: Float,
    padding: Float,
    spacing: Float,
) {
    val totalWidth = size.width
    val totalHeight = size.height

    // Board background
    drawRoundRect(
        color = BoardColor,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
    )

    val cellWidth = (totalWidth - 2 * padding - (COLUMNS - 1) * spacing) / COLUMNS
    val cellHeight = (totalHeight - 2 * padding - (ROWS - 1) * spacing) / ROWS
    val cellSize = minOf(cellWidth, cellHeight)
    val discRadius = cellSize * 0.42f
    val strokeWidth = cellSize * 0.08f
    val highlightRadius = cellSize * 0.08f

    for (row in 0 until ROWS) {
        for (col in 0 until COLUMNS) {
            val cx = padding + col * (cellSize + spacing) + cellSize / 2f
            val cy = padding + row * (cellSize + spacing) + cellSize / 2f

            // Slot background circle
            drawCircle(
                color = SlotColor,
                radius = discRadius + strokeWidth,
                center = Offset(cx, cy),
            )

            val index = getCellIndex(row, col)
            val disc = board[index]

            if (disc != null) {
                val isAnimating = index == animatingIndex
                val yOffset = if (isAnimating) {
                    val startY = padding + cellSize / 2f // top row center
                    val targetY = cy
                    val currentY = startY + (targetY - startY) * animationProgress
                    currentY
                } else {
                    cy
                }

                val (fillColor, strokeColor, highlightColor) = when (disc) {
                    Disc.R -> Triple(RedDisc, RedStroke, RedHighlight)
                    Disc.Y -> Triple(YellowDisc, YellowStroke, YellowHighlight)
                }

                // Stroke circle
                drawCircle(
                    color = strokeColor,
                    radius = discRadius + strokeWidth / 2f,
                    center = Offset(cx, yOffset),
                )

                // Fill circle
                drawCircle(
                    color = fillColor,
                    radius = discRadius,
                    center = Offset(cx, yOffset),
                )

                // Highlight / shine
                drawCircle(
                    color = highlightColor,
                    radius = highlightRadius,
                    center = Offset(cx - cellSize * 0.12f, yOffset - cellSize * 0.16f),
                )
            }
        }
    }
}
