package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.chess.ChessColor
import com.napolitain.arcade.logic.chess.ChessEngine
import com.napolitain.arcade.logic.chess.ChessEngine.Companion.BOARD_SIZE
import com.napolitain.arcade.logic.chess.ChessEngine.Companion.col
import com.napolitain.arcade.logic.chess.ChessEngine.Companion.findKing
import com.napolitain.arcade.logic.chess.ChessEngine.Companion.idx
import com.napolitain.arcade.logic.chess.ChessEngine.Companion.row
import com.napolitain.arcade.logic.chess.ChessPiece
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Board square colors
private val LightSquare = Color(0x50_F0D9B5)
private val DarkSquare = Color(0x80_B58863)
private val SelectedSquare = Color(0x90_64B5F6)
private val LastMoveSquare = Color(0x60_FDD835)
private val CheckSquare = Color(0x70_EF5350)

// Piece drawing colors
private val WhitePieceFill = Color(0xFFFFFDE7)
private val WhitePieceStroke = Color(0xFF424242)
private val BlackPieceFill = Color(0xFF263238)
private val BlackPieceStroke = Color(0xFF90A4AE)

// Move indicator colors
private val LegalMoveDot = Color(0xB3_66BB6A)
private val CaptureRing = Color(0xCC_EF5350)

@Composable
fun ChessGame() {
    val engine = remember { ChessEngine() }
    val isAiTurn = engine.isAiTurn
    val isGameOver = engine.isGameOver

    // AI move trigger
    LaunchedEffect(engine.board.toList(), engine.currentPlayer, isAiTurn) {
        if (!isAiTurn) return@LaunchedEffect
        delay(300)
        engine.triggerAiMove()
    }

    // Slide animation for moved piece
    val slideX = remember { Animatable(0f) }
    val slideY = remember { Animatable(0f) }
    var lastToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(engine.lastMove?.token) {
        val move = engine.lastMove ?: return@LaunchedEffect
        val token = move.token
        if (token == lastToken) return@LaunchedEffect
        lastToken = token

        slideX.snapTo((col(move.from) - col(move.to)).toFloat())
        slideY.snapTo((row(move.from) - row(move.to)).toFloat())

        launch { slideX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
        launch { slideY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVarColor = MaterialTheme.colorScheme.onSurfaceVariant

    GameShell(
        title = stringResource(R.string.game_chess),
        status = engine.statusText,
        score = stringResource(R.string.chess_moves, engine.moveCount),
        onReset = { engine.reset() },
    ) {
        GameModeToggle(mode = engine.gameMode, onModeChange = { engine.setMode(it) })
        if (engine.gameMode == GameMode.AI) {
            GameDifficultyToggle(difficulty = engine.difficulty, onDifficultyChange = { engine.difficulty = it })
        }

        // Captured-pieces panels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val whiteActive = !isGameOver && engine.currentPlayer == ChessColor.WHITE
            val blackActive = !isGameOver && engine.currentPlayer == ChessColor.BLACK

            @Composable
            fun CapturedPanel(
                label: String,
                captured: List<ChessPiece>,
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
                            if (active) activeColor.copy(alpha = 0.12f)
                            else inactiveContainerColor.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = onSurfaceColor)
                    val symbols = captured.sortedByDescending { it.value }.joinToString("") { it.symbol }
                    Text(
                        if (symbols.isEmpty()) "—" else symbols,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVarColor,
                    )
                }
            }

            CapturedPanel(
                stringResource(R.string.chess_white),
                engine.capturedByWhite.toList(),
                whiteActive,
                Modifier.weight(1f),
            )
            CapturedPanel(
                engine.blackLabel,
                engine.capturedByBlack.toList(),
                blackActive,
                Modifier.weight(1f),
            )
        }

        // Board canvas
        val boardSnapshot = engine.board.toList()
        val selectedFrom = engine.selectedSquare
        val movable = engine.movablePieces
        val destinations = engine.destinationSet()
        val lastMoveSnapshot = engine.lastMove
        val checkKingIdx = if (engine.isCheck) findKing(boardSnapshot, engine.currentPlayer) else -1
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current

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
                        val c = (offset.x / cellW).toInt().coerceIn(0, BOARD_SIZE - 1)
                        val r = (offset.y / cellH).toInt().coerceIn(0, BOARD_SIZE - 1)
                        engine.selectSquare(r, c)
                    }
                },
        ) {
            val cellW = size.width / BOARD_SIZE
            val cellH = size.height / BOARD_SIZE
            val fontSize = with(density) { (cellW * 0.75f).toSp() }
            val strokeW = cellW * 0.04f

            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    val i = idx(r, c)
                    val left = c * cellW
                    val top = r * cellH
                    val cx = left + cellW / 2f
                    val cy = top + cellH / 2f
                    val isDark = (r + c) % 2 == 1
                    val isSelected = selectedFrom == i
                    val isLastFrom = lastMoveSnapshot?.from == i
                    val isLastTo = lastMoveSnapshot?.to == i
                    val isCheckKing = i == checkKingIdx

                    // Square background
                    val bgColor = when {
                        isCheckKing -> CheckSquare
                        isSelected -> SelectedSquare
                        isLastFrom || isLastTo -> LastMoveSquare
                        isDark -> DarkSquare
                        else -> LightSquare
                    }
                    drawRect(bgColor, topLeft = Offset(left, top), size = Size(cellW, cellH))

                    // Selected border
                    if (isSelected) {
                        drawRect(
                            color = Color(0xCC_42A5F5),
                            topLeft = Offset(left + strokeW, top + strokeW),
                            size = Size(cellW - strokeW * 2, cellH - strokeW * 2),
                            style = Stroke(width = strokeW * 2),
                        )
                    }

                    val piece = boardSnapshot[i]
                    if (piece != null) {
                        // Slide animation offset for just-moved piece
                        val isMovedPiece = lastMoveSnapshot?.to == i
                        val slideOffX = if (isMovedPiece) slideX.value * cellW else 0f
                        val slideOffY = if (isMovedPiece) slideY.value * cellH else 0f

                        val textStyle = TextStyle(
                            fontSize = fontSize,
                            color = if (piece.color == ChessColor.WHITE) WhitePieceFill else BlackPieceFill,
                            textAlign = TextAlign.Center,
                        )
                        val textResult = textMeasurer.measure(piece.symbol, textStyle)
                        val tw = textResult.size.width.toFloat()
                        val th = textResult.size.height.toFloat()

                        // Outline: draw slightly offset in stroke color for readability
                        val outlineColor = if (piece.color == ChessColor.WHITE) WhitePieceStroke else BlackPieceStroke
                        val outlineStyle = TextStyle(
                            fontSize = fontSize,
                            color = outlineColor,
                            textAlign = TextAlign.Center,
                        )
                        val outlineResult = textMeasurer.measure(piece.symbol, outlineStyle)
                        val ox = cx - tw / 2f + slideOffX
                        val oy = cy - th / 2f + slideOffY
                        val outlineD = cellW * 0.015f
                        for (dx in floatArrayOf(-outlineD, outlineD)) {
                            for (dy in floatArrayOf(-outlineD, outlineD)) {
                                drawText(outlineResult, topLeft = Offset(ox + dx, oy + dy))
                            }
                        }
                        drawText(textResult, topLeft = Offset(ox, oy))

                        // Movable indicator dot
                        if (movable.contains(i) && !isSelected && !isGameOver) {
                            drawCircle(
                                color = LegalMoveDot,
                                radius = cellW * 0.06f,
                                center = Offset(left + cellW * 0.88f, top + cellH * 0.12f),
                            )
                        }
                    } else if (destinations.contains(i)) {
                        // Legal move dot (empty square)
                        drawCircle(
                            color = LegalMoveDot,
                            radius = cellW * 0.14f,
                            center = Offset(cx, cy),
                        )
                    }

                    // Capture target ring (destination with enemy piece)
                    if (destinations.contains(i) && piece != null) {
                        drawCircle(
                            color = CaptureRing,
                            radius = cellW * 0.42f,
                            center = Offset(cx, cy),
                            style = Stroke(width = strokeW * 2.5f),
                        )
                    }
                }
            }
        }

        // Instruction hint
        if (!isGameOver) {
            Text(
                text = stringResource(R.string.chess_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVarColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
