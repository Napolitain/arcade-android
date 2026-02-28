package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.BOARD_SIZE
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.BOX_DEFINITIONS
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.CELL_SIZE
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.DOT_COORDINATES
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.DOT_RADIUS
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.EDGE_DEFINITIONS
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.EDGE_HIT_STROKE_WIDTH
import com.napolitain.arcade.logic.dotsandboxes.DotsAndBoxesEngine.Companion.EDGE_STROKE_WIDTH
import com.napolitain.arcade.logic.dotsandboxes.EdgeDefinition
import com.napolitain.arcade.logic.dotsandboxes.Player
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import com.napolitain.arcade.ui.components.Difficulty
import kotlinx.coroutines.delay

// Player colours
private val PlayerALine = Color(0xFF22D3EE)
private val PlayerAFill = Color(0x4722D3EE) // ~28% alpha
private val PlayerAText = Color(0xFFCFFAFE)
private val PlayerBLine = Color(0xFFFB7185)
private val PlayerBFill = Color(0x47FB7185)
private val PlayerBText = Color(0xFFFFE4E6)
private val DotFill = Color(0xFFE2E8F0)
private val DotStroke = Color(0xFF020617)

private fun lineColor(player: Player) = if (player == Player.A) PlayerALine else PlayerBLine
private fun fillColor(player: Player) = if (player == Player.A) PlayerAFill else PlayerBFill
private fun textColor(player: Player) = if (player == Player.A) PlayerAText else PlayerBText

/**
 * Returns the squared distance from point (px, py) to the closest point on segment (x1,y1)-(x2,y2).
 */
private fun distSqToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) {
        val ex = px - x1; val ey = py - y1; return ex * ex + ey * ey
    }
    val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
    val clamped = t.coerceIn(0f, 1f)
    val cx = x1 + clamped * dx
    val cy = y1 + clamped * dy
    val ex = px - cx; val ey = py - cy
    return ex * ex + ey * ey
}

@Composable
fun DotsAndBoxesGame() {
    val engine = remember { DotsAndBoxesEngine() }
    var mode by remember { mutableStateOf(GameMode.LOCAL) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }

    // Track which edges have been animated already (keyed by drawRevision to reset on game reset)
    var drawRevision by remember { mutableStateOf(0) }

    // Animate each new edge via an Animatable keyed on lastEdgeId
    val lineAnimProgress = remember { Animatable(1f) }
    val animatingEdgeId = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(engine.lastEdgeId, drawRevision) {
        val eid = engine.lastEdgeId
        if (eid != null) {
            animatingEdgeId.value = eid
            lineAnimProgress.snapTo(0f)
            lineAnimProgress.animateTo(1f, tween(180))
            animatingEdgeId.value = null
        }
    }

    // AI turn
    LaunchedEffect(engine.currentPlayer, engine.drawnEdges.size, mode, drawRevision) {
        if (!engine.isAiTurn(mode)) return@LaunchedEffect
        delay(260)
        val edge = engine.chooseAiEdge(difficulty)
        if (edge != null) {
            engine.selectEdge(edge, mode, initiatedByAi = true)
        }
    }

    val textMeasurer = rememberTextMeasurer()

    GameShell(
        title = stringResource(R.string.game_dotsandboxes),
        status = engine.statusText(mode),
        onReset = {
            engine.reset()
            drawRevision++
        },
    ) {
        GameModeToggle(
            mode = mode,
            onModeChange = { newMode ->
                if (newMode != mode) {
                    mode = newMode
                    engine.reset()
                    drawRevision++
                }
            },
        )

        if (mode == GameMode.AI) {
            GameDifficultyToggle(difficulty = difficulty, onDifficultyChange = { difficulty = it })
        }

        // Score cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScoreCard(
                label = stringResource(R.string.player_a),
                score = engine.scoreA,
                isActive = !engine.isGameOver && engine.currentPlayer == Player.A,
                activeColor = PlayerALine,
                modifier = Modifier.weight(1f),
            )
            ScoreCard(
                label = if (mode == GameMode.AI) stringResource(R.string.dab_player_b_ai) else stringResource(R.string.player_b),
                score = engine.scoreB,
                isActive = !engine.isGameOver && engine.currentPlayer == Player.B,
                activeColor = PlayerBLine,
                modifier = Modifier.weight(1f),
            )
        }

        // Board
        val isAiTurn = engine.isAiTurn(mode)
        val isGameOver = engine.isGameOver

        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(isAiTurn, isGameOver, engine.currentPlayer, drawRevision) {
                            if (isAiTurn || isGameOver) return@pointerInput
                            val scale = size.width / BOARD_SIZE
                            detectTapGestures { offset ->
                                val bx = offset.x / scale
                                val by = offset.y / scale
                                val hitThreshold = EDGE_HIT_STROKE_WIDTH * EDGE_HIT_STROKE_WIDTH
                                var bestEdge: EdgeDefinition? = null
                                var bestDist = Float.MAX_VALUE
                                for (edge in EDGE_DEFINITIONS) {
                                    if (engine.drawnEdges.containsKey(edge.id)) continue
                                    val d = distSqToSegment(bx, by, edge.x1, edge.y1, edge.x2, edge.y2)
                                    if (d < hitThreshold && d < bestDist) {
                                        bestDist = d
                                        bestEdge = edge
                                    }
                                }
                                bestEdge?.let { engine.selectEdge(it, mode) }
                            }
                        },
                ) {
                    val scale = size.width / BOARD_SIZE

                    // Filled boxes
                    for (box in BOX_DEFINITIONS) {
                        val owner = engine.claimedBoxes[box.id] ?: continue
                        val inset = EDGE_STROKE_WIDTH / 2f
                        val isRecent = box.id in engine.lastClaimedBoxIds
                        drawRoundRect(
                            color = fillColor(owner),
                            topLeft = Offset((box.x + inset) * scale, (box.y + inset) * scale),
                            size = Size(
                                (CELL_SIZE - EDGE_STROKE_WIDTH) * scale,
                                (CELL_SIZE - EDGE_STROKE_WIDTH) * scale,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale),
                        )
                        // Border
                        drawRoundRect(
                            color = lineColor(owner),
                            topLeft = Offset((box.x + inset) * scale, (box.y + inset) * scale),
                            size = Size(
                                (CELL_SIZE - EDGE_STROKE_WIDTH) * scale,
                                (CELL_SIZE - EDGE_STROKE_WIDTH) * scale,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = (if (isRecent) 3f else 1.5f) * scale,
                            ),
                        )
                        // Owner label
                        drawOwnerLabel(
                            textMeasurer = textMeasurer,
                            label = owner.label,
                            cx = (box.x + CELL_SIZE / 2f) * scale,
                            cy = (box.y + CELL_SIZE / 2f) * scale,
                            color = textColor(owner),
                            scale = scale,
                        )
                    }

                    // Undrawn edges (ghost lines)
                    val previewColor = lineColor(engine.currentPlayer)
                    for (edge in EDGE_DEFINITIONS) {
                        if (engine.drawnEdges.containsKey(edge.id)) continue
                        drawLine(
                            color = previewColor.copy(alpha = 0.28f),
                            start = Offset(edge.x1 * scale, edge.y1 * scale),
                            end = Offset(edge.x2 * scale, edge.y2 * scale),
                            strokeWidth = (EDGE_STROKE_WIDTH - 2f) * scale,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Drawn edges
                    for (edge in EDGE_DEFINITIONS) {
                        val owner = engine.drawnEdges[edge.id] ?: continue
                        val isLast = edge.id == engine.lastEdgeId
                        val sw = if (isLast) EDGE_STROKE_WIDTH + 1.5f else EDGE_STROKE_WIDTH

                        val progress = if (isLast && animatingEdgeId.value == edge.id) lineAnimProgress.value else 1f
                        val ex = edge.x1 + (edge.x2 - edge.x1) * progress
                        val ey = edge.y1 + (edge.y2 - edge.y1) * progress

                        drawLine(
                            color = lineColor(owner),
                            start = Offset(edge.x1 * scale, edge.y1 * scale),
                            end = Offset(ex * scale, ey * scale),
                            strokeWidth = sw * scale,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Dots
                    for (dot in DOT_COORDINATES) {
                        drawCircle(
                            color = DotFill,
                            radius = DOT_RADIUS * scale,
                            center = Offset(dot.x * scale, dot.y * scale),
                        )
                        drawCircle(
                            color = DotStroke,
                            radius = DOT_RADIUS * scale,
                            center = Offset(dot.x * scale, dot.y * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale),
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.dab_instruction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawOwnerLabel(
    textMeasurer: TextMeasurer,
    label: String,
    cx: Float,
    cy: Float,
    color: Color,
    scale: Float,
) {
    val style = TextStyle(
        fontSize = (20f * scale).sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
    )
    val result = textMeasurer.measure(label, style)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(cx - result.size.width / 2f, cy - result.size.height / 2f),
    )
}

@Composable
private fun ScoreCard(
    label: String,
    score: Int,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.score_label, score.toString()), style = MaterialTheme.typography.bodySmall)
        }
    }
}
