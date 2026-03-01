package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.checkers.CheckersEngine
import com.napolitain.arcade.logic.checkers.CheckersEngine.Companion.BOARD_SIZE
import com.napolitain.arcade.logic.checkers.CheckersEngine.Companion.STARTING_PIECES
import com.napolitain.arcade.logic.checkers.CheckersEngine.Companion.getCol
import com.napolitain.arcade.logic.checkers.CheckersEngine.Companion.getRow
import com.napolitain.arcade.logic.checkers.CheckersEngine.Companion.isPlayableSquare
import com.napolitain.arcade.logic.checkers.PieceColor
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Piece colors matching the React source
private val BlackPieceFill = Color(0xFF0F172A)
private val BlackPieceStroke = Color(0xFF334155)
private val BlackPieceHighlight = Color(0x80_94A3B8)
private val RedPieceFill = Color(0xFFE11D48)
private val RedPieceStroke = Color(0xFFFECDD3)
private val RedPieceHighlight = Color(0xBF_FFF1F2)
private val CrownColor = Color(0xFFFACC15)
private val CrownStroke = Color(0xFFFEF08A)

// Board colors
private val LightSquare = Color(0x1A_E2E8F0) // slate-200/10
private val DarkSquare = Color(0x8C_064E3B)   // emerald-900/55
private val SelectedSquare = Color(0x73_047857) // emerald-700/45
private val DestinationSquare = Color(0x8C_047857) // emerald-700/55
private val LastToSquare = Color(0xA6_065F46)  // emerald-800/65

private val CyanHighlight = Color(0xE6_67E8F9)
private val CyanGlow = Color(0x40_67E8F9)
private val AmberBorder = Color(0xB3_FCD34D)
private val RoseBorder = Color(0xB3_FDA4AF)
private val MovableDot = Color(0xD9_A5F3FC) // cyan-200/85
private val DestDotFill = Color(0xFF_A5F3FC)

@Composable
fun CheckersGame() {
    val engine = remember { CheckersEngine() }

    val isAiTurn = engine.isAiTurn
    val isGameOver = engine.isGameOver

    // AI move trigger
    LaunchedEffect(engine.board.toList(), engine.currentPlayer, engine.forcedFromIndex, isAiTurn) {
        if (!isAiTurn) return@LaunchedEffect
        delay(280)
        engine.triggerAiMove()
    }

    // Slide animation for moved piece
    val slideX = remember { Animatable(0f) }
    val slideY = remember { Animatable(0f) }
    var lastToken by remember { mutableStateOf(0) }

    // Capture poof animation
    val poofProgress = remember { Animatable(0f) }
    var poofIndices by remember { mutableStateOf(emptySet<Int>()) }

    // King promotion bounce
    val crownBounce = remember { Animatable(1f) }
    var promotedIndex by remember { mutableIntStateOf(-1) }

    // Track king counts for promotion detection
    var prevBlackKings by remember { mutableIntStateOf(engine.blackKings) }
    var prevRedKings by remember { mutableIntStateOf(engine.redKings) }

    LaunchedEffect(engine.lastMove?.token) {
        val move = engine.lastMove ?: run {
            prevBlackKings = engine.blackKings
            prevRedKings = engine.redKings
            return@LaunchedEffect
        }
        val token = move.token
        if (token == lastToken) return@LaunchedEffect
        lastToken = token

        // Slide from source to destination
        slideX.snapTo((getCol(move.from) - getCol(move.to)).toFloat())
        slideY.snapTo((getRow(move.from) - getRow(move.to)).toFloat())

        // Poof for captures
        val hasCap = move.captured.isNotEmpty()
        if (hasCap) {
            poofIndices = move.captured.toSet()
            poofProgress.snapTo(0f)
        }

        // King promotion detection
        val kingPromoted = engine.blackKings > prevBlackKings || engine.redKings > prevRedKings
        prevBlackKings = engine.blackKings
        prevRedKings = engine.redKings
        if (kingPromoted) {
            promotedIndex = move.to
            crownBounce.snapTo(0f)
        }

        // Launch concurrent animations
        launch { slideX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
        launch { slideY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
        if (hasCap) {
            launch { poofProgress.animateTo(1f, tween(350)) }
        }
        if (kingPromoted) {
            launch { crownBounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
        }
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVarColor = MaterialTheme.colorScheme.onSurfaceVariant

    GameShell(
        title = stringResource(R.string.game_checkers),
        status = engine.statusText,
        score = stringResource(R.string.checkers_moves, engine.moveCount),
        onReset = { engine.reset() },
    ) {
        GameModeToggle(mode = engine.gameMode, onModeChange = { engine.setMode(it) })
        if (engine.gameMode == GameMode.AI) {
            GameDifficultyToggle(difficulty = engine.difficulty, onDifficultyChange = { engine.difficulty = it })
        }

        // Piece count panels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val blackActive = !isGameOver && engine.currentPlayer == PieceColor.B
            val redActive = !isGameOver && engine.currentPlayer == PieceColor.R

            @Composable
            fun PieceCountPanel(
                label: String,
                pieces: Int,
                kings: Int,
                captured: Int,
                active: Boolean,
                modifier: Modifier = Modifier,
            ) {
                @Suppress("DEPRECATION")
                Column(
                    modifier = modifier
                        .border(
                            1.dp,
                            if (active) activeColor else inactiveContainerColor,
                            RoundedCornerShape(8.dp),
                        )
                        .background(
                            if (active) activeColor.copy(alpha = 0.12f) else inactiveContainerColor.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = onSurfaceColor)
                    Text(
                        stringResource(R.string.checkers_pieces_kings, pieces, kings),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVarColor,
                    )
                    Text(
                        stringResource(R.string.checkers_captured, captured),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVarColor,
                    )
                }
            }

            PieceCountPanel(stringResource(R.string.checkers_black), engine.blackPieces, engine.blackKings, STARTING_PIECES - engine.redPieces, blackActive, Modifier.weight(1f))
            PieceCountPanel(engine.redLabel, engine.redPieces, engine.redKings, STARTING_PIECES - engine.blackPieces, redActive, Modifier.weight(1f))
        }

        // Hint text
        if (!isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (engine.captureRequired) stringResource(R.string.checkers_capture_rule)
                    else stringResource(R.string.checkers_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVarColor,
                )
            }
        }

        // Board canvas
        val boardSnapshot = engine.board.toList()
        val selectedFrom = engine.selectedFromIndex
        val movable = engine.movablePieces
        val destinations = engine.destinationSet()
        val lastMoveSnapshot = engine.lastMove
        val capturedSet = lastMoveSnapshot?.captured?.toSet() ?: emptySet()
        val winner = engine.winner

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(isGameOver, isAiTurn) {
                    detectTapGestures { offset ->
                        val cellW = size.width / BOARD_SIZE.toFloat()
                        val cellH = size.height / BOARD_SIZE.toFloat()
                        val col = (offset.x / cellW).toInt().coerceIn(0, BOARD_SIZE - 1)
                        val row = (offset.y / cellH).toInt().coerceIn(0, BOARD_SIZE - 1)
                        engine.handleCellClick(row * BOARD_SIZE + col)
                    }
                },
        ) {
            val cellW = size.width / BOARD_SIZE
            val cellH = size.height / BOARD_SIZE
            val pieceRadius = cellW * 0.38f
            val strokeW = cellW * 0.06f

            for (row in 0 until BOARD_SIZE) {
                for (col in 0 until BOARD_SIZE) {
                    val idx = row * BOARD_SIZE + col
                    val isDark = isPlayableSquare(row, col)
                    val left = col * cellW
                    val top = row * cellH
                    val cx = left + cellW / 2f
                    val cy = top + cellH / 2f

                    // Square background
                    val isSelected = selectedFrom == idx
                    val isDest = destinations.contains(idx)
                    val isLastTo = lastMoveSnapshot?.to == idx
                    val bgColor = when {
                        !isDark -> LightSquare
                        isDest -> DestinationSquare
                        isSelected -> SelectedSquare
                        isLastTo -> LastToSquare
                        else -> DarkSquare
                    }
                    drawRect(bgColor, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(cellW, cellH))

                    // Last-from border
                    if (lastMoveSnapshot?.from == idx) {
                        drawRectBorder(left, top, cellW, cellH, AmberBorder, strokeW * 0.7f)
                    }
                    // Captured square border
                    if (capturedSet.contains(idx)) {
                        drawRectBorder(left, top, cellW, cellH, RoseBorder, strokeW * 0.7f)
                    }
                    // Selected glow
                    if (isSelected) {
                        drawRectBorder(left, top, cellW, cellH, CyanHighlight, strokeW)
                    }

                    val piece = boardSnapshot[idx]
                    if (piece != null) {
                        // Smooth slide animation for the moved piece
                        val isMovedPiece = lastMoveSnapshot?.to == idx
                        val slideOffX = if (isMovedPiece) slideX.value * cellW else 0f
                        val slideOffY = if (isMovedPiece) slideY.value * cellH else 0f
                        val crownAnim = if (isMovedPiece && promotedIndex == idx) crownBounce.value else 1f

                        drawCheckerPiece(cx + slideOffX, cy + slideOffY, pieceRadius, strokeW, piece, crownAnim)

                        // Movable indicator dot
                        if (movable.contains(idx) && !isSelected && !isGameOver) {
                            drawCircle(
                                color = MovableDot,
                                radius = cellW * 0.06f,
                                center = Offset(left + cellW * 0.85f, top + cellH * 0.15f),
                            )
                        }
                    } else {
                        // Capture poof effect
                        if (poofIndices.contains(idx) && poofProgress.value < 1f) {
                            val p = poofProgress.value
                            val poofAlpha = (1f - p).coerceIn(0f, 1f)
                            drawCircle(
                                color = Color.White.copy(alpha = poofAlpha * 0.5f),
                                radius = pieceRadius * (1f + p * 0.8f),
                                center = Offset(cx, cy),
                            )
                            drawCircle(
                                color = RoseBorder.copy(alpha = poofAlpha * 0.6f),
                                radius = pieceRadius * (1f + p * 0.8f),
                                center = Offset(cx, cy),
                                style = Stroke(width = strokeW * 1.5f),
                            )
                        }

                        if (isDest) {
                            // Destination dot
                            drawCircle(
                                color = CyanGlow,
                                radius = cellW * 0.14f,
                                center = Offset(cx, cy),
                                style = Stroke(width = strokeW * 0.5f),
                            )
                            drawCircle(
                                color = DestDotFill,
                                radius = cellW * 0.08f,
                                center = Offset(cx, cy),
                            )
                        }
                    }
                }
            }

            // Board depth gradient for subtle shadow/tilt effect
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f)),
                    startY = size.height * 0.7f,
                    endY = size.height,
                ),
            )
        }
    }
}

private fun DrawScope.drawCheckerPiece(
    cx: Float,
    cy: Float,
    radius: Float,
    strokeW: Float,
    piece: com.napolitain.arcade.logic.checkers.Piece,
    crownScale: Float = 1f,
) {
    val isBlack = piece.color == PieceColor.B
    val fill = if (isBlack) BlackPieceFill else RedPieceFill
    val stroke = if (isBlack) BlackPieceStroke else RedPieceStroke
    val highlight = if (isBlack) BlackPieceHighlight else RedPieceHighlight

    // Main circle
    drawCircle(fill, radius, Offset(cx, cy))
    drawCircle(stroke, radius, Offset(cx, cy), style = Stroke(width = strokeW))
    // Specular highlight
    drawCircle(highlight, radius * 0.2f, Offset(cx - radius * 0.35f, cy - radius * 0.4f))

    // Crown for kings (with scale bounce on promotion)
    if (piece.king) {
        val cs = crownScale
        val crownW = radius * 1.3f * cs
        val crownH = radius * 0.55f * cs
        val crownTop = cy + radius * 0.05f - crownH / 2f
        val crownBottom = crownTop + crownH
        val crownLeft = cx - crownW / 2f
        val crownRight = cx + crownW / 2f

        val path = Path().apply {
            moveTo(crownLeft, crownBottom)
            lineTo(crownLeft + crownW * 0.19f, crownTop)
            lineTo(cx, crownTop + crownH * 0.55f)
            lineTo(crownRight - crownW * 0.19f, crownTop)
            lineTo(crownRight, crownBottom)
            close()
        }
        drawPath(path, CrownColor, style = Fill)
        drawPath(path, CrownStroke, style = Stroke(width = strokeW * 0.6f * cs))

        // Crown base band
        val bandH = radius * 0.17f * cs
        drawRoundRect(
            color = CrownColor,
            topLeft = Offset(crownLeft, crownBottom - bandH * 0.3f),
            size = androidx.compose.ui.geometry.Size(crownW, bandH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH / 2f, bandH / 2f),
        )
        drawRoundRect(
            color = CrownStroke,
            topLeft = Offset(crownLeft, crownBottom - bandH * 0.3f),
            size = androidx.compose.ui.geometry.Size(crownW, bandH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH / 2f, bandH / 2f),
            style = Stroke(width = strokeW * 0.6f * cs),
        )
    }
}

private fun DrawScope.drawRectBorder(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    strokeWidth: Float,
) {
    val half = strokeWidth / 2f
    drawRect(
        color = color,
        topLeft = Offset(left + half, top + half),
        size = androidx.compose.ui.geometry.Size(width - strokeWidth, height - strokeWidth),
        style = Stroke(width = strokeWidth),
    )
}
