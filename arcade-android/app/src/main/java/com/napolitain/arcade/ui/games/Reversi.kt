package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.reversi.Disc
import com.napolitain.arcade.logic.reversi.ReversiEngine
import com.napolitain.arcade.logic.reversi.ReversiEngine.Companion.BOARD_SIZE
import com.napolitain.arcade.logic.reversi.ReversiEngine.Companion.TOTAL_CELLS
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlin.math.abs

// Board colours
private val BoardBg = Color(0xFF064E3B)
private val GridLine = Color(0xFF047857)
private val CellDefault = Color(0x73065F46)
private val CellLegal = Color(0x80047857)
private val CellLastPlayed = Color(0x99065F46)
private val LastPlayedBorder = Color(0xB3FCD34D)
private val BlackFill = Color(0xFF0F172A)
private val BlackStroke = Color(0xFF334155)
private val BlackShine = Color(0x7394A3B8)
private val WhiteFill = Color(0xFFE2E8F0)
private val WhiteStroke = Color(0xFFCBD5E1)
private val WhiteShine = Color(0xBFFFFFFF)
private val LegalRing = Color(0xCCE0F2FE)
private val LegalDot = Color(0xCCA5F3FC)

@Composable
fun ReversiGame() {
    val engine = remember { ReversiEngine() }

    // AI move with small delay
    LaunchedEffect(engine.board.toList(), engine.isAiTurn) {
        if (!engine.isAiTurn) return@LaunchedEffect
        delay(280)
        engine.performAiMove()
    }

    // Disc place / flip animation (0 → 1)
    val animProgress = remember { Animatable(1f) }
    LaunchedEffect(engine.animationCycle) {
        if (engine.animationCycle > 0) {
            animProgress.snapTo(0f)
            animProgress.animateTo(1f, tween(300))
        }
    }

    val whiteLabel = if (engine.mode == GameMode.AI) stringResource(R.string.reversi_white_ai) else stringResource(R.string.reversi_white)

    GameShell(
        title = stringResource(R.string.game_reversi),
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

        // ── Scoreboard ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScoreCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.reversi_black),
                count = engine.blackCount,
                isActive = !engine.isGameOver && engine.currentPlayer == Disc.B,
                discColor = BlackFill,
                discBorder = BlackStroke,
            )
            ScoreCard(
                modifier = Modifier.weight(1f),
                label = whiteLabel,
                count = engine.whiteCount,
                isActive = !engine.isGameOver && engine.currentPlayer == Disc.W,
                discColor = WhiteFill,
                discBorder = WhiteStroke,
            )
        }

        // ── Pass banner ──────────────────────────────────────────────
        if (engine.passMessage != null && !engine.isGameOver) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x1AFBBF24),
                border = BorderStroke(1.dp, Color(0x66FDE68A)),
            ) {
                Text(
                    text = stringResource(R.string.reversi_pass_turn, engine.passMessage ?: ""),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFEF3C7),
                )
            }
        }

        // ── Board ────────────────────────────────────────────────────
        val board = engine.board
        val legalMoveSet = engine.legalMoveSet
        val gameOver = engine.isGameOver
        val lastPlaced = engine.lastPlacedIndex
        val flippedSet = remember(engine.lastFlippedIndices) { engine.lastFlippedIndices.toSet() }
        val progress = animProgress.value

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(gameOver, engine.isAiTurn) {
                    detectTapGestures { offset ->
                        val cs = size.width / BOARD_SIZE.toFloat()
                        val col = (offset.x / cs).toInt().coerceIn(0, BOARD_SIZE - 1)
                        val row = (offset.y / cs).toInt().coerceIn(0, BOARD_SIZE - 1)
                        engine.handleCellClick(ReversiEngine.getCellIndex(row, col))
                    }
                },
        ) {
            val cs = size.width / BOARD_SIZE

            // Background
            drawRect(BoardBg)

            for (index in 0 until TOTAL_CELLS) {
                val row = index / BOARD_SIZE
                val col = index % BOARD_SIZE
                val x = col * cs
                val y = row * cs
                val center = Offset(x + cs / 2, y + cs / 2)
                val disc = board[index]
                val isLegal = legalMoveSet.contains(index) && !gameOver
                val isPlaced = index == lastPlaced
                val isFlipped = flippedSet.contains(index)

                // Cell fill
                drawRect(
                    color = when {
                        isLegal -> CellLegal
                        isPlaced -> CellLastPlayed
                        else -> CellDefault
                    },
                    topLeft = Offset(x + 1f, y + 1f),
                    size = Size(cs - 2f, cs - 2f),
                )

                // Last-placed amber border
                if (isPlaced) {
                    drawRect(
                        color = LastPlayedBorder,
                        topLeft = Offset(x + 1f, y + 1f),
                        size = Size(cs - 2f, cs - 2f),
                        style = Stroke(cs * 0.06f),
                    )
                }

                // Disc with animation
                if (disc != null) {
                    val isBlack = disc == Disc.B
                    val fill = if (isBlack) BlackFill else WhiteFill
                    val stroke = if (isBlack) BlackStroke else WhiteStroke
                    val shine = if (isBlack) BlackShine else WhiteShine

                    val scale = when {
                        isPlaced && progress < 1f -> progress
                        isFlipped && progress < 1f -> abs(2f * progress - 1f)
                        else -> 1f
                    }

                    val r = cs * 0.38f * scale
                    if (r > 0.5f) {
                        drawCircle(fill, r, center)
                        drawCircle(stroke, r, center, style = Stroke(cs * 0.07f))
                        drawCircle(
                            shine,
                            r * 0.2f,
                            Offset(center.x - r * 0.28f, center.y - r * 0.28f),
                        )
                    }
                }

                // Legal-move indicator
                if (isLegal && disc == null) {
                    drawCircle(LegalRing, cs * 0.12f, center, style = Stroke(cs * 0.025f))
                    drawCircle(LegalDot, cs * 0.07f, center)
                }
            }

            // Grid lines on top
            for (i in 0..BOARD_SIZE) {
                val pos = i * cs
                drawLine(GridLine, Offset(pos, 0f), Offset(pos, size.height), 1.5f)
                drawLine(GridLine, Offset(0f, pos), Offset(size.width, pos), 1.5f)
            }
        }
    }
}

// ── Score card ────────────────────────────────────────────────────────

@Composable
private fun ScoreCard(
    label: String,
    count: Int,
    isActive: Boolean,
    discColor: Color,
    discBorder: Color,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    val bgColor =
        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(discColor, size.minDimension / 2f)
                        drawCircle(discBorder, size.minDimension / 2f, style = Stroke(2f))
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = if (isActive) stringResource(R.string.to_move) else stringResource(R.string.waiting),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
