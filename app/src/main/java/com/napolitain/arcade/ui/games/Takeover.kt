package com.napolitain.arcade.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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
    val scope = rememberCoroutineScope()

    // AI move with delay
    LaunchedEffect(engine.board.toList(), engine.isAiTurn) {
        if (!engine.isAiTurn) return@LaunchedEffect
        delay(TakeoverEngine.AI_DELAY_MS)
        engine.performAiMove()
    }

    // ── Per-piece conversion animations (ripple/wave outward from placed piece) ──
    // Maps cell index → animation progress 0..1 for converted pieces
    val conversionProgresses = remember { mutableStateMapOf<Int, Float>() }

    // Move animation progress: for clone growing / jump sliding
    val moveAnim = remember { Animatable(1f) }

    LaunchedEffect(engine.animationCycle) {
        if (engine.animationCycle == 0) return@LaunchedEffect
        val targetIdx = engine.lastMove?.to ?: -1
        val targetRow = if (targetIdx >= 0) getRow(targetIdx) else 0
        val targetCol = if (targetIdx >= 0) getCol(targetIdx) else 0

        // Animate the move itself (clone grow / jump slide)
        scope.launch {
            moveAnim.snapTo(0f)
            moveAnim.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }

        // Stagger conversion animations spreading outward from the placed piece
        val converted = engine.lastConverted
        val sorted = converted.sortedBy { idx ->
            val dr = getRow(idx) - targetRow
            val dc = getCol(idx) - targetCol
            sqrt((dr * dr + dc * dc).toFloat())
        }
        sorted.forEachIndexed { i, idx ->
            conversionProgresses[idx] = 0f
            scope.launch {
                delay(i * 60L) // sequential wave timing
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) {
                    conversionProgresses[idx] = value
                }
            }
        }
    }

    // Gentle pulse on selectable pieces
    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pulseAnim.animateTo(
            1f,
            infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        )
    }
    val pulseValue = pulseAnim.value

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

    // ── Smooth animated score counters ───────────────────────────
    val animBlueCount by animateIntAsState(
        targetValue = engine.blueCount,
        animationSpec = tween(350),
        label = "blueCount",
    )
    val animOrangeCount by animateIntAsState(
        targetValue = engine.orangeCount,
        animationSpec = tween(350),
        label = "orangeCount",
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
                            "$animBlueCount",
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
                            "$animOrangeCount",
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
        val moveP = moveAnim.value
        val convSnap = conversionProgresses.toMap()

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

                // Draw token with animations
                if (cell != null) {
                    val isConverted = convertedSet.contains(idx)
                    val convP = if (isConverted) convSnap[idx] ?: 1f else 1f

                    if (isLastTarget && moveP < 1f) {
                        // Animate the newly placed/moved piece
                        val moveKind = lastMoveState?.kind
                        if (moveKind == MoveKind.CLONE) {
                            // Clone: piece grows from 0 to full size with spring
                            val scale = moveP
                            drawToken(cx, cy, cellSize * 0.36f * scale, cell, false, 1f)
                        } else if (moveKind == MoveKind.JUMP) {
                            // Jump: piece slides from source position with a trail
                            val fromIdx = lastMoveState?.from ?: idx
                            val fromRow = getRow(fromIdx)
                            val fromCol = getCol(fromIdx)
                            val fromCx = fromCol * cellSize + cellSize / 2f
                            val fromCy = fromRow * cellSize + cellSize / 2f
                            val curX = fromCx + (cx - fromCx) * moveP
                            val curY = fromCy + (cy - fromCy) * moveP
                            // Trail: fading circles along the path
                            val trailSteps = 3
                            for (t in 0 until trailSteps) {
                                val trailP = (moveP - t * 0.08f).coerceIn(0f, 1f)
                                val tx = fromCx + (cx - fromCx) * trailP
                                val ty = fromCy + (cy - fromCy) * trailP
                                val trailAlpha = (0.15f - t * 0.04f).coerceAtLeast(0f)
                                val trailFill = if (cell == Player.B) BlueFill else OrangeFill
                                drawCircle(trailFill.copy(alpha = trailAlpha), cellSize * 0.28f, Offset(tx, ty))
                            }
                            drawToken(curX, curY, cellSize * 0.36f, cell, false, 1f)
                        } else {
                            drawToken(cx, cy, cellSize * 0.36f, cell, isConverted, convP)
                        }
                    } else if (isConverted && convP < 1f) {
                        // Crossfade colour transition for converted pieces
                        drawToken(cx, cy, cellSize * 0.36f, cell, true, convP)
                    } else {
                        // Selectable piece pulse
                        val scale = if (isSelectableSrc && !isSelectedSrc) {
                            1f + 0.06f * pulseValue
                        } else 1f
                        drawToken(cx, cy, cellSize * 0.36f * scale, cell, false, 1f)
                    }
                } else if (isTarget) {
                    // Draw target dot
                    val dotColor = if (targetKind == MoveKind.CLONE) CloneDot else JumpDot
                    val outerR = cellSize * 0.16f
                    val innerR = cellSize * 0.10f
                    drawCircle(dotColor.copy(alpha = 0.5f), outerR, Offset(cx, cy), style = Stroke(cellSize * 0.025f))
                    drawCircle(dotColor, innerR, Offset(cx, cy))
                }

                // Selectable source subtle border with pulse
                if (isSelectableSrc && !isSelectedSrc) {
                    val borderAlpha = 0.3f + 0.2f * pulseValue
                    drawRoundRect(
                        color = Color(0xFF22D3EE).copy(alpha = borderAlpha),
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

private fun DrawScope.drawToken(cx: Float, cy: Float, radius: Float, player: Player, isConverted: Boolean, conversionProgress: Float) {
    val fill: Color
    val stroke: Color
    val highlight: Color

    if (player == Player.B) {
        fill = BlueFill; stroke = BlueStroke; highlight = BlueHighlight
    } else {
        fill = OrangeFill; stroke = OrangeStroke; highlight = OrangeHighlight
    }

    // Crossfade: lerp from old colour to new colour during conversion
    val drawFill: Color
    val drawStroke: Color
    val drawHighlight: Color
    if (isConverted && conversionProgress < 1f) {
        val oldFill = if (player == Player.B) OrangeFill else BlueFill
        val oldStroke = if (player == Player.B) OrangeStroke else BlueStroke
        val oldHighlight = if (player == Player.B) OrangeHighlight else BlueHighlight
        drawFill = lerpColor(oldFill, fill, conversionProgress)
        drawStroke = lerpColor(oldStroke, stroke, conversionProgress)
        drawHighlight = lerpColor(oldHighlight, highlight, conversionProgress)
    } else {
        drawFill = fill; drawStroke = stroke; drawHighlight = highlight
    }

    // Outer ring (stroke)
    drawCircle(drawStroke, radius, Offset(cx, cy), style = Stroke(width = radius * 0.2f))
    // Filled body
    drawCircle(drawFill, radius * 0.88f, Offset(cx, cy), style = Fill)
    // Specular highlight
    drawCircle(drawHighlight, radius * 0.22f, Offset(cx - radius * 0.3f, cy - radius * 0.35f))

    // Conversion ripple wave
    if (isConverted && conversionProgress < 1f) {
        val rippleRadius = radius * (1f + 0.5f * conversionProgress)
        val rippleAlpha = 0.4f * (1f - conversionProgress)
        drawCircle(
            color = drawFill.copy(alpha = rippleAlpha),
            radius = rippleRadius,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.08f),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.padding(top = 4.dp).size(12.dp)) {
            drawCircle(color, size.minDimension / 2f)
        }
        @Suppress("DEPRECATION")
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
