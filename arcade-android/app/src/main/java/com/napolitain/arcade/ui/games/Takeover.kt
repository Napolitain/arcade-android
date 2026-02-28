package com.napolitain.arcade.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.takeover.MoveKind
import com.napolitain.arcade.logic.takeover.Player
import com.napolitain.arcade.logic.takeover.TakeoverEngine
import com.napolitain.arcade.logic.takeover.TakeoverEngine.Companion.BOARD_SIZE
import com.napolitain.arcade.logic.takeover.TakeoverEngine.Companion.getCol
import com.napolitain.arcade.logic.takeover.TakeoverEngine.Companion.getRow
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay

// Token colours
private val BlueFill = Color(0xFF38BDF8)
private val BlueStroke = Color(0xFFBAE6FD)
private val BlueHighlight = Color(0x60FFFFFF)
private val OrangeFill = Color(0xFFFB923C)
private val OrangeStroke = Color(0xFFFED7AA)
private val OrangeHighlight = Color(0x60FFFFFF)

// Board / cell colours
private val CellDefault = Color(0xFF1E293B)
private val CellSelectedSource = Color(0xFF164E63)
private val CellSelectableSource = Color(0xFF1E3A5F)
private val CellCloneTarget = Color(0xFF14532D)
private val CellJumpTarget = Color(0xFF3B0764)
private val CellLastTarget = Color(0xFF78350F)
private val GridBackground = Color(0xFF0F172A)
private val GridLine = Color(0xFF334155)
private val CloneDot = Color(0xFFBBF7D0)
private val JumpDot = Color(0xFFDDD6FE)
private val LastTargetRing = Color(0xFFFCD34D)

@Composable
fun TakeoverGame() {
    val engine = remember { TakeoverEngine() }

    // AI move with delay
    LaunchedEffect(engine.board.toList(), engine.isAiTurn) {
        if (!engine.isAiTurn) return@LaunchedEffect
        delay(TakeoverEngine.AI_DELAY_MS)
        engine.performAiMove()
    }

    // Animate conversion colours
    val blueCardColor by animateColorAsState(
        targetValue = if (!engine.isGameOver && engine.currentPlayer == Player.B)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "blueCard",
    )
    val orangeCardColor by animateColorAsState(
        targetValue = if (!engine.isGameOver && engine.currentPlayer == Player.O)
            Color(0xFF7C2D12).copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "orangeCard",
    )

    GameShell(
        title = stringResource(R.string.game_takeover),
        status = engine.statusText,
        onReset = { engine.resetGame() },
    ) {
        GameModeToggle(mode = engine.mode, onModeChange = { engine.setGameMode(it) })
        if (engine.mode == GameMode.AI) {
            GameDifficultyToggle(
                difficulty = engine.difficulty,
                onDifficultyChange = { engine.setGameDifficulty(it) },
            )
        }

        // Scoreboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = blueCardColor),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.takeover_blue), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${engine.blueCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        if (!engine.isGameOver && engine.currentPlayer == Player.B) stringResource(R.string.to_move) else stringResource(R.string.waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = orangeCardColor),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.takeover_orange), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${engine.orangeCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFB923C),
                        )
                    }
                    Text(
                        when {
                            engine.isGameOver -> stringResource(R.string.waiting)
                            engine.currentPlayer == Player.O && engine.mode == GameMode.AI -> stringResource(R.string.ai_turn)
                            engine.currentPlayer == Player.O -> stringResource(R.string.to_move)
                            else -> stringResource(R.string.waiting)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Pass message
        val pm = engine.passMessage
        if (pm != null && !engine.isGameOver) {
            Text(
                pm,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFDE68A),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Hint
        Text(
            engine.selectionHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendItem(color = Color(0xFF22D3EE).copy(alpha = 0.4f), label = stringResource(R.string.takeover_source))
            LegendItem(color = CloneDot, label = stringResource(R.string.takeover_clone))
            LegendItem(color = JumpDot, label = stringResource(R.string.takeover_jump))
        }

        // Board canvas
        val boardState = engine.board
        val selectedSource = engine.selectedSource
        val selectedTargets = engine.selectedTargets
        val selectableSources = engine.selectableSources
        val lastMoveState = engine.lastMove
        val convertedSet = engine.convertedSet
        val gameOver = engine.isGameOver
        val aiTurn = engine.isAiTurn
        val animCycle = engine.animationCycle

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(gameOver, aiTurn, animCycle) {
                    detectTapGestures { offset ->
                        val cellSize = size.width / BOARD_SIZE.toFloat()
                        val col = (offset.x / cellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
                        val row = (offset.y / cellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
                        engine.handleCellClick(row * BOARD_SIZE + col)
                    }
                },
        ) {
            val cellSize = size.width / BOARD_SIZE
            val cornerRadius = cellSize * 0.12f
            val padding = cellSize * 0.06f

            // Background
            drawRoundRect(
                color = GridBackground,
                cornerRadius = CornerRadius(cellSize * 0.2f),
                size = size,
            )

            for (idx in 0 until TakeoverEngine.TOTAL_CELLS) {
                val row = getRow(idx)
                val col = getCol(idx)
                val x = col * cellSize
                val y = row * cellSize
                val cell = boardState[idx]

                // Cell background
                val isSelectedSrc = selectedSource == idx
                val isTarget = selectedTargets.containsKey(idx)
                val targetKind = selectedTargets[idx]
                val isSelectableSrc = !gameOver && !aiTurn && cell == engine.currentPlayer &&
                    selectableSources.contains(idx)
                val isLastTarget = lastMoveState?.to == idx

                val bgColor = when {
                    isSelectedSrc -> CellSelectedSource
                    isTarget && targetKind == MoveKind.CLONE -> CellCloneTarget
                    isTarget && targetKind == MoveKind.JUMP -> CellJumpTarget
                    isSelectableSrc -> CellSelectableSource
                    isLastTarget -> CellLastTarget
                    else -> CellDefault
                }

                drawRoundRect(
                    color = bgColor,
                    topLeft = Offset(x + padding, y + padding),
                    size = Size(cellSize - padding * 2, cellSize - padding * 2),
                    cornerRadius = CornerRadius(cornerRadius),
                )

                // Last-move ring
                if (isLastTarget) {
                    drawRoundRect(
                        color = LastTargetRing.copy(alpha = 0.6f),
                        topLeft = Offset(x + padding, y + padding),
                        size = Size(cellSize - padding * 2, cellSize - padding * 2),
                        cornerRadius = CornerRadius(cornerRadius),
                        style = Stroke(width = cellSize * 0.04f),
                    )
                }

                val cx = x + cellSize / 2f
                val cy = y + cellSize / 2f

                // Draw token
                if (cell != null) {
                    val isConverted = convertedSet.contains(idx)
                    drawToken(cx, cy, cellSize * 0.36f, cell, isConverted)
                } else if (isTarget) {
                    // Draw target dot
                    val dotColor = if (targetKind == MoveKind.CLONE) CloneDot else JumpDot
                    val outerR = cellSize * 0.16f
                    val innerR = cellSize * 0.10f
                    drawCircle(dotColor.copy(alpha = 0.5f), outerR, Offset(cx, cy), style = Stroke(cellSize * 0.025f))
                    drawCircle(dotColor, innerR, Offset(cx, cy))
                }

                // Selectable source subtle border
                if (isSelectableSrc && !isSelectedSrc) {
                    drawRoundRect(
                        color = Color(0xFF22D3EE).copy(alpha = 0.45f),
                        topLeft = Offset(x + padding, y + padding),
                        size = Size(cellSize - padding * 2, cellSize - padding * 2),
                        cornerRadius = CornerRadius(cornerRadius),
                        style = Stroke(width = cellSize * 0.03f),
                    )
                }
            }

            // Grid lines
            val lineWidth = cellSize * 0.012f
            for (i in 1 until BOARD_SIZE) {
                val pos = i * cellSize
                drawLine(GridLine, Offset(pos, 0f), Offset(pos, size.height), lineWidth)
                drawLine(GridLine, Offset(0f, pos), Offset(size.width, pos), lineWidth)
            }
        }
    }
}

private fun DrawScope.drawToken(cx: Float, cy: Float, radius: Float, player: Player, isConverted: Boolean) {
    val fill: Color
    val stroke: Color
    val highlight: Color

    if (player == Player.B) {
        fill = BlueFill; stroke = BlueStroke; highlight = BlueHighlight
    } else {
        fill = OrangeFill; stroke = OrangeStroke; highlight = OrangeHighlight
    }

    // Outer ring (stroke)
    drawCircle(stroke, radius, Offset(cx, cy), style = Stroke(width = radius * 0.2f))
    // Filled body
    drawCircle(fill, radius * 0.88f, Offset(cx, cy), style = Fill)
    // Specular highlight
    drawCircle(highlight, radius * 0.22f, Offset(cx - radius * 0.3f, cy - radius * 0.35f))

    // Conversion ripple
    if (isConverted) {
        drawCircle(
            color = fill.copy(alpha = 0.25f),
            radius = radius * 1.3f,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.08f),
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.padding(top = 4.dp).size(12.dp)) {
            drawCircle(color, size.minDimension / 2f)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
