package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
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
private val HoverHighlightColor = Color(0x20FFFFFF)
private val WinGlowRed = Color(0xFFFB7185)
private val WinGlowYellow = Color(0xFFFCD34D)

@Composable
fun ConnectFourGame() {
    val engine = remember { ConnectFourEngine() }

    // Spring-based drop animation with bounce
    val dropAnim = remember { Animatable(0f) }
    var animatingIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(engine.lastDropIndex) {
        if (engine.lastDropIndex >= 0) {
            animatingIndex = engine.lastDropIndex
            dropAnim.snapTo(0f)
            dropAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    // AI move with delay
    LaunchedEffect(engine.isAiTurn, engine.board) {
        if (engine.isAiTurn) {
            delay(700)
            engine.performAiMove()
        }
    }

    // Column hover highlight
    var hoverCol by remember { mutableIntStateOf(-1) }

    // Winning cells pulse animation
    val winningCells = remember(engine.board.toList(), engine.winner) {
        if (engine.winner != null) findWinningCells(engine.board) else emptySet()
    }
    val infiniteTransition = rememberInfiniteTransition(label = "winPulse")
    val winPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "winPulse",
    )

    val density = LocalDensity.current
    val boardPadding = with(density) { 12.dp.toPx() }
    val cellSpacing = with(density) { 4.dp.toPx() }

    GameShell(
        title = stringResource(R.string.game_connectfour),
        status = engine.statusText,
        onReset = {
            engine.resetGame()
            hoverCol = -1
        },
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
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val totalWidth = size.width.toFloat()
                            val cellSize =
                                (totalWidth - 2 * boardPadding - (COLUMNS - 1) * cellSpacing) / COLUMNS
                            when (event.type) {
                                PointerEventType.Press, PointerEventType.Move -> {
                                    hoverCol =
                                        ((pos.x - boardPadding) / (cellSize + cellSpacing))
                                            .toInt().coerceIn(0, COLUMNS - 1)
                                }
                                PointerEventType.Release -> {
                                    val col =
                                        ((pos.x - boardPadding) / (cellSize + cellSpacing))
                                            .toInt().coerceIn(0, COLUMNS - 1)
                                    if (engine.winner == null && !engine.isDraw && !engine.isAiTurn) {
                                        engine.dropDisc(col)
                                    }
                                    hoverCol = -1
                                }
                                else -> hoverCol = -1
                            }
                        }
                    }
                },
        ) {
            drawBoard(
                board = engine.board,
                animatingIndex = animatingIndex,
                animProgress = dropAnim.value,
                padding = boardPadding,
                spacing = cellSpacing,
                hoverCol = hoverCol,
                winningCells = winningCells,
                winPulse = winPulse,
                hasWinner = engine.winner != null,
            )
        }
    }
}

private fun DrawScope.drawBoard(
    board: Array<Disc?>,
    animatingIndex: Int,
    animProgress: Float,
    padding: Float,
    spacing: Float,
    hoverCol: Int,
    winningCells: Set<Int>,
    winPulse: Float,
    hasWinner: Boolean,
) {
    // Board background
    drawRoundRect(
        color = BoardColor,
        size = size,
        cornerRadius = CornerRadius(24f, 24f),
    )

    val cellWidth = (size.width - 2 * padding - (COLUMNS - 1) * spacing) / COLUMNS
    val cellHeight = (size.height - 2 * padding - (ROWS - 1) * spacing) / ROWS
    val cellSize = minOf(cellWidth, cellHeight)
    val discRadius = cellSize * 0.42f
    val strokeWidth = cellSize * 0.08f
    val highlightRadius = cellSize * 0.08f

    // Column hover highlight
    if (hoverCol in 0 until COLUMNS && !hasWinner) {
        val colLeft = padding + hoverCol * (cellSize + spacing)
        drawRoundRect(
            color = HoverHighlightColor,
            topLeft = Offset(colLeft - spacing / 2f, padding - spacing / 2f),
            size = Size(cellSize + spacing, size.height - 2 * padding + spacing),
            cornerRadius = CornerRadius(12f, 12f),
        )
    }

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
                val isAnimating = index == animatingIndex && animProgress < 2f
                val yOffset: Float
                val scaleX: Float
                val scaleY: Float

                if (isAnimating) {
                    val startY = padding + cellSize / 2f // top row center
                    val targetY = cy
                    yOffset = startY + (targetY - startY) * animProgress
                    // Squash-and-stretch on landing
                    val overshoot = (animProgress - 1f).coerceIn(-0.3f, 0.3f)
                    scaleX = 1f + overshoot * 0.35f
                    scaleY = 1f - overshoot * 0.3f
                } else {
                    yOffset = cy
                    scaleX = 1f
                    scaleY = 1f
                }

                val (fillColor, strokeColor, highlightColor) = when (disc) {
                    Disc.R -> Triple(RedDisc, RedStroke, RedHighlight)
                    Disc.Y -> Triple(YellowDisc, YellowStroke, YellowHighlight)
                }

                // Winning glow/pulse
                if (winningCells.contains(index)) {
                    val glowColor = when (disc) {
                        Disc.R -> WinGlowRed
                        Disc.Y -> WinGlowYellow
                    }
                    drawCircle(
                        color = glowColor.copy(alpha = 0.25f + 0.2f * winPulse),
                        radius = discRadius * (1.2f + 0.15f * winPulse),
                        center = Offset(cx, yOffset),
                    )
                }

                // Stroke oval (squash-and-stretch)
                drawOval(
                    color = strokeColor,
                    topLeft = Offset(
                        cx - (discRadius + strokeWidth / 2f) * scaleX,
                        yOffset - (discRadius + strokeWidth / 2f) * scaleY,
                    ),
                    size = Size(
                        (discRadius + strokeWidth / 2f) * 2f * scaleX,
                        (discRadius + strokeWidth / 2f) * 2f * scaleY,
                    ),
                )

                // Fill oval (squash-and-stretch)
                drawOval(
                    color = fillColor,
                    topLeft = Offset(
                        cx - discRadius * scaleX,
                        yOffset - discRadius * scaleY,
                    ),
                    size = Size(
                        discRadius * 2f * scaleX,
                        discRadius * 2f * scaleY,
                    ),
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

/** Compute the set of cell indices forming the winning 4-in-a-row, if any. */
private fun findWinningCells(board: Array<Disc?>): Set<Int> {
    val directions = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1),
    )
    for (row in 0 until ROWS) {
        for (col in 0 until COLUMNS) {
            val disc = board[getCellIndex(row, col)] ?: continue
            for (dir in directions) {
                val cells = mutableListOf<Int>()
                for (k in 0 until 4) {
                    val r = row + dir[0] * k
                    val c = col + dir[1] * k
                    if (r !in 0 until ROWS || c !in 0 until COLUMNS) break
                    val idx = getCellIndex(r, c)
                    if (board[idx] != disc) break
                    cells.add(idx)
                }
                if (cells.size == 4) return cells.toSet()
            }
        }
    }
    return emptySet()
}
