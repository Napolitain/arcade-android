package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.napolitain.arcade.logic.balance.BalanceEngine
import com.napolitain.arcade.logic.balance.BalanceEngine.Companion.SAFE_TORQUE_LIMIT
import com.napolitain.arcade.logic.balance.BalanceEngine.Companion.SLOT_POSITIONS
import com.napolitain.arcade.logic.balance.BalanceEngine.Companion.formatTorque
import com.napolitain.arcade.logic.balance.BalanceEngine.Companion.getSlotLabel
import com.napolitain.arcade.logic.balance.Player
import com.napolitain.arcade.logic.balance.RoundResult
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameMode
import com.napolitain.arcade.ui.components.GameModeToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private val PlayerAFill = Color(0xFF22D3EE)
private val PlayerAStroke = Color(0xFF0E7490)
private val PlayerAText = Color(0xFF083344)
private val PlayerBFill = Color(0xFFFB7185)
private val PlayerBStroke = Color(0xFFBE123C)
private val PlayerBText = Color(0xFF4C0519)

@Composable
fun BalanceGame() {
    val engine = remember { BalanceEngine() }
    val textMeasurer = rememberTextMeasurer()

    val animatedAngle by animateFloatAsState(
        targetValue = engine.beamAngle,
        animationSpec = tween(durationMillis = 400),
        label = "beamAngle",
    )

    // AI auto-play
    LaunchedEffect(engine.isAiTurn, engine.placements) {
        if (engine.isAiTurn) {
            delay(BalanceEngine.AI_DELAY_MS)
            engine.performAiMove()
        }
    }

    GameShell(
        title = stringResource(R.string.game_balance),
        status = engine.statusText,
        onReset = engine::resetSession,
    ) {
        GameModeToggle(mode = engine.mode, onModeChange = engine::setGameMode)
        if (engine.mode == GameMode.AI) {
            GameDifficultyToggle(
                difficulty = engine.difficulty,
                onDifficultyChange = engine::setGameDifficulty,
            )
        }

        // Scoreboard
        PlayerScoreboard(engine)

        // Torque bar
        TorqueBar(engine)

        // Canvas board
        BalanceBoard(engine, animatedAngle, textMeasurer)

        // Controls
        WeightAndSlotControls(engine)

        // Next round button
        if (engine.isRoundOver) {
            Button(
                onClick = engine::startNextRound,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                Text(stringResource(R.string.balance_start_round, engine.roundNumber + 1))
            }
        }
    }
}

@Composable
private fun PlayerScoreboard(engine: BalanceEngine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Player.entries.forEach { player ->
            val isActive = !engine.isRoundOver && engine.currentPlayer == player
            val isWinner = (engine.roundResult as? RoundResult.Tip)?.winner == player
            val label = if (player == Player.B && engine.mode == GameMode.AI) stringResource(R.string.balance_ai_player)
            else stringResource(R.string.player_label, player.toString())
            val wins = engine.sessionWins[player] ?: 0
            val weights = engine.weightPool[player] ?: emptyList()

            val containerColor = when {
                isWinner -> MaterialTheme.colorScheme.tertiaryContainer
                isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                tonalElevation = if (isActive) 4.dp else 0.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.balance_session_wins, wins),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.balance_weights, if (weights.isNotEmpty()) weights.joinToString(", ") else stringResource(R.string.balance_weights_none)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TorqueBar(engine: BalanceEngine) {
    val torque = engine.torque
    val fill = min(1f, abs(torque).toFloat() / SAFE_TORQUE_LIMIT)
    val barColor = when {
        abs(torque) > SAFE_TORQUE_LIMIT -> MaterialTheme.colorScheme.error
        abs(torque) >= (SAFE_TORQUE_LIMIT * 0.75).toInt() -> Color(0xFFFBBF24) // amber
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.balance_torque, formatTorque(torque)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.balance_safe_range, SAFE_TORQUE_LIMIT),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LinearProgressIndicator(
                progress = { fill },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(
                stringResource(R.string.balance_draw_rules, engine.drawRounds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BalanceBoard(engine: BalanceEngine, beamAngle: Float, textMeasurer: TextMeasurer) {
    val placements = engine.placements
    val roundResult = engine.roundResult
    val isTip = roundResult is RoundResult.Tip

    val beamColor = MaterialTheme.colorScheme.primary
    val slotTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val slotDotColor = MaterialTheme.colorScheme.primaryContainer
    val fulcrumFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val fulcrumStroke = MaterialTheme.colorScheme.outline
    val baseLineColor = MaterialTheme.colorScheme.outlineVariant
    val boardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val stalkColor = MaterialTheme.colorScheme.outlineVariant
    val tipTextColor = Color(0xFFFDA4AF)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = boardBg,
        border = null,
        tonalElevation = 2.dp,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(520f / 280f)
                .padding(4.dp),
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * (118f / 280f)
            val slotSpacing = w * (42f / 520f)

            // Background rect
            drawRoundRect(
                color = boardBg,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f),
                topLeft = Offset(w * 0.04f, h * 0.11f),
                size = androidx.compose.ui.geometry.Size(w * 0.915f, h * 0.786f),
            )

            // Base line
            drawLine(
                color = baseLineColor,
                start = Offset(w * (64f / 520f), h * (226f / 280f)),
                end = Offset(w * (456f / 520f), h * (226f / 280f)),
                strokeWidth = w * (8f / 520f),
                cap = StrokeCap.Round,
            )

            // Fulcrum triangle
            val fulcrumPath = Path().apply {
                moveTo(cx, h * (162f / 280f))
                lineTo(cx - w * (46f / 520f), h * (226f / 280f))
                lineTo(cx + w * (46f / 520f), h * (226f / 280f))
                close()
            }
            drawPath(fulcrumPath, fulcrumFill, style = Fill)
            drawPath(fulcrumPath, fulcrumStroke, style = Stroke(width = w * (3f / 520f)))

            // Beam group (rotated)
            rotate(beamAngle, pivot = Offset(cx, cy)) {
                // Beam rect
                val beamLeft = w * (70f / 520f)
                val beamTop = h * (108f / 280f)
                val beamW = w * (380f / 520f)
                val beamH = h * (20f / 280f)
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(beamLeft, beamTop),
                    size = androidx.compose.ui.geometry.Size(beamW, beamH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(beamH / 2),
                )
                drawRoundRect(
                    color = beamColor,
                    topLeft = Offset(beamLeft, beamTop),
                    size = androidx.compose.ui.geometry.Size(beamW, beamH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(beamH / 2),
                    style = Stroke(width = w * (3f / 520f)),
                )

                // Slot ticks and dots
                for (slot in SLOT_POSITIONS) {
                    val slotX = cx + slot * slotSpacing
                    drawLine(
                        color = slotTickColor,
                        start = Offset(slotX, h * (101f / 280f)),
                        end = Offset(slotX, h * (134f / 280f)),
                        strokeWidth = w * (2f / 520f),
                    )
                    drawCircle(
                        color = slotDotColor,
                        radius = w * (4f / 520f),
                        center = Offset(slotX, cy),
                    )
                }

                // Placed weights
                for (p in placements) {
                    val slotX = cx + p.slot * slotSpacing
                    val (pFill, pStroke, pText) = when (p.player) {
                        Player.A -> Triple(PlayerAFill, PlayerAStroke, PlayerAText)
                        Player.B -> Triple(PlayerBFill, PlayerBStroke, PlayerBText)
                    }

                    // Stalk
                    drawLine(
                        color = stalkColor,
                        start = Offset(slotX, h * (128f / 280f)),
                        end = Offset(slotX, h * (162f / 280f)),
                        strokeWidth = w * (2.5f / 520f),
                    )

                    // Weight circle
                    val radius = w * ((14f + p.weight * 2f) / 520f)
                    val circleCenter = Offset(slotX, h * (176f / 280f))
                    drawCircle(pFill, radius, circleCenter)
                    drawCircle(pStroke, radius, circleCenter, style = Stroke(width = w * (3f / 520f)))

                    // Weight number
                    drawWeightText(textMeasurer, p.weight.toString(), circleCenter, pText, w)
                }
            }

            // TIP! text
            if (isTip) {
                val tipLayout = textMeasurer.measure(
                    text = "TIP!",
                    style = TextStyle(
                        fontSize = (w * (30f / 520f)).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tipTextColor,
                        textAlign = TextAlign.Center,
                    ),
                )
                drawText(
                    tipLayout,
                    topLeft = Offset(
                        cx - tipLayout.size.width / 2f,
                        h * (64f / 280f) - tipLayout.size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawWeightText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    canvasWidth: Float,
) {
    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = (canvasWidth * (15f / 520f)).sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
        ),
    )
    drawText(
        layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f + canvasWidth * (3f / 520f),
        ),
    )
}

@Composable
private fun WeightAndSlotControls(engine: BalanceEngine) {
    val isDisabledBase = engine.isRoundOver || engine.isAiTurn
    val currentWeights = engine.currentPlayerWeights
    val placementMap = engine.placementBySlot

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (engine.isAiTurn) stringResource(R.string.balance_ai_choosing)
                else stringResource(R.string.balance_choose_weight, engine.currentPlayer.toString()),
                style = MaterialTheme.typography.bodySmall,
            )

            // Weight selection row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currentWeights.forEach { weight ->
                    val isSelected = engine.selectedWeight == weight
                    if (isSelected) {
                        Button(
                            onClick = { engine.selectWeight(weight) },
                            enabled = !isDisabledBase,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("$weight")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { engine.selectWeight(weight) },
                            enabled = !isDisabledBase,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("$weight")
                        }
                    }
                }
            }

            // Slot buttons grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(SLOT_POSITIONS.toList()) { slot ->
                    val placement = placementMap[slot]
                    val isOccupied = placement != null
                    val isDisabled = isDisabledBase || isOccupied

                    OutlinedButton(
                        onClick = { engine.placeWeight(slot) },
                        enabled = !isDisabled,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                getSlotLabel(slot),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                if (placement != null) "${placement.player}:${placement.weight}" else stringResource(R.string.empty_slot),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
