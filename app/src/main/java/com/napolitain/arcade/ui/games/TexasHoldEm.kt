package com.napolitain.arcade.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.napolitain.arcade.logic.texasholdem.Card
import com.napolitain.arcade.logic.texasholdem.Phase
import com.napolitain.arcade.logic.texasholdem.PlayerAction
import com.napolitain.arcade.logic.texasholdem.TexasHoldEmEngine
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay

@Composable
fun TexasHoldEmGame() {
    val engine = remember { TexasHoldEmEngine() }
    val textMeasurer = rememberTextMeasurer()

    // Card deal animation progress
    val dealAnim = remember { Animatable(0f) }
    var lastPhase by remember { mutableStateOf(engine.phase) }

    LaunchedEffect(engine.phase) {
        if (engine.phase != lastPhase) {
            dealAnim.snapTo(0f)
            dealAnim.animateTo(1f, tween(400, easing = EaseInOut))
            lastPhase = engine.phase
        }
    }

    // AI turn processing
    LaunchedEffect(engine.waitingForAi, engine.activePlayerIndex, engine.handOver) {
        if (engine.handOver) return@LaunchedEffect
        while (engine.waitingForAi && !engine.handOver) {
            delay(500)
            val acted = engine.processAiTurn()
            if (!acted) break
            delay(300)
        }
    }

    val statusText = when {
        engine.handOver -> engine.message
        engine.isHumanTurn -> "Your turn"
        engine.waitingForAi -> engine.message.ifEmpty { "AI is thinking…" }
        else -> engine.message
    }

    GameShell(
        title = stringResource(R.string.game_texasholdem),
        status = statusText,
        onReset = { engine.reset() },
    ) {
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // ── AI opponents row ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            engine.players.filter { !it.isHuman }.forEach { player ->
                AiPlayerCard(
                    name = player.name,
                    chips = player.chips,
                    folded = player.folded,
                    isDealer = player.isDealer,
                    isActive = engine.players.indexOf(player) == engine.activePlayerIndex && !engine.handOver,
                    cards = player.hand,
                    showCards = engine.phase == Phase.SHOWDOWN && !player.folded,
                    textMeasurer = textMeasurer,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Community cards + pot ────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Pot: ${engine.pot}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            CommunityCardsRow(
                cards = engine.communityCards.toList(),
                phase = engine.phase,
                textMeasurer = textMeasurer,
                animProgress = dealAnim.value,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                engine.phase.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Showdown results ────────────────────────────────
        if (engine.phase == Phase.SHOWDOWN && engine.showdownResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    "Showdown",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                engine.showdownResults.forEachIndexed { idx, (player, eval) ->
                    val prefix = if (idx == 0) "🏆 " else "   "
                    Text(
                        "$prefix${player.name}: ${eval?.handRank?.display ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Human player area ───────────────────────────────
        val human = engine.players.firstOrNull { it.isHuman }
        if (human != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (human.isDealer) {
                        DealerChip()
                    }
                    Text(
                        "${human.name} — Chips: ${human.chips}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Player's hole cards
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    human.hand.forEach { card ->
                        CardCanvas(
                            card = card,
                            faceUp = true,
                            textMeasurer = textMeasurer,
                            modifier = Modifier.size(width = 60.dp, height = 84.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action buttons
                ActionButtons(engine = engine)
            }
        }
    }
}

// ── Card rendering ──────────────────────────────────────────────

@Composable
private fun CardCanvas(
    card: Card?,
    faceUp: Boolean,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val backColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val cornerR = 6.dp.toPx()

        if (!faceUp || card == null) {
            // Card back
            drawRoundRect(
                color = backColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerR),
            )
            drawRoundRect(
                color = backColor.copy(alpha = 0.6f),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                cornerRadius = CornerRadius(cornerR - 2),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        } else {
            // Card face
            drawRoundRect(
                color = cardBg,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerR),
            )
            drawRoundRect(
                color = cardBorder,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerR),
                style = Stroke(width = 1.dp.toPx()),
            )

            val textColor = if (card.suit.isRed) Color(0xFFDC2626) else Color(0xFF1E293B)
            val rankText = card.rank.display
            val suitText = card.suit.symbol

            // Top-left rank
            val rankStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            val rankResult = textMeasurer.measure(rankText, rankStyle)
            drawText(rankResult, topLeft = Offset(4.dp.toPx(), 2.dp.toPx()))

            // Suit below rank
            val suitStyle = TextStyle(fontSize = 12.sp, color = textColor)
            val suitResult = textMeasurer.measure(suitText, suitStyle)
            drawText(suitResult, topLeft = Offset(4.dp.toPx(), 16.dp.toPx()))

            // Center suit (larger)
            val centerSuitStyle = TextStyle(fontSize = 22.sp, color = textColor)
            val centerResult = textMeasurer.measure(suitText, centerSuitStyle)
            drawText(
                centerResult,
                topLeft = Offset(
                    (size.width - centerResult.size.width) / 2f,
                    (size.height - centerResult.size.height) / 2f,
                ),
            )
        }
    }
}

@Composable
private fun EmptyCardSlot(modifier: Modifier = Modifier) {
    val slotColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = slotColor.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

// ── Community cards ─────────────────────────────────────────────

@Composable
private fun CommunityCardsRow(
    cards: List<Card>,
    phase: Phase,
    textMeasurer: TextMeasurer,
    animProgress: Float,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 5) {
            val card = cards.getOrNull(i)
            val shouldShow = when {
                phase == Phase.PRE_FLOP -> false
                i < 3 -> phase.ordinal >= Phase.FLOP.ordinal
                i == 3 -> phase.ordinal >= Phase.TURN.ordinal
                else -> phase.ordinal >= Phase.RIVER.ordinal
            }
            if (shouldShow && card != null) {
                CardCanvas(
                    card = card,
                    faceUp = true,
                    textMeasurer = textMeasurer,
                    modifier = Modifier.size(width = 52.dp, height = 72.dp),
                )
            } else {
                EmptyCardSlot(modifier = Modifier.size(width = 52.dp, height = 72.dp))
            }
        }
    }
}

// ── AI player display ───────────────────────────────────────────

@Composable
private fun AiPlayerCard(
    name: String,
    chips: Int,
    folded: Boolean,
    isDealer: Boolean,
    isActive: Boolean,
    cards: List<Card>,
    showCards: Boolean,
    textMeasurer: TextMeasurer,
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        folded -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = when {
        folded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .width(100.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDealer) {
                DealerChip()
                Spacer(Modifier.width(2.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (folded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "$chips",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(4.dp))

        if (folded) {
            Text(
                "Folded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                cards.forEach { card ->
                    CardCanvas(
                        card = card,
                        faceUp = showCards,
                        textMeasurer = textMeasurer,
                        modifier = Modifier.size(width = 32.dp, height = 44.dp),
                    )
                }
                // Show empty slots if no cards dealt yet
                if (cards.isEmpty()) {
                    repeat(2) {
                        EmptyCardSlot(modifier = Modifier.size(width = 32.dp, height = 44.dp))
                    }
                }
            }
        }
    }
}

// ── Dealer chip ─────────────────────────────────────────────────

@Composable
private fun DealerChip() {
    val chipColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onTertiary
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(color = chipColor, radius = size.minDimension / 2f)
        drawCircle(
            color = textColor.copy(alpha = 0.3f),
            radius = size.minDimension / 2f,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

// ── Action buttons ──────────────────────────────────────────────

@Composable
private fun ActionButtons(engine: TexasHoldEmEngine) {
    val actions = engine.humanActions
    var raiseSliderVisible by remember { mutableStateOf(false) }
    var raiseAmount by remember { mutableFloatStateOf(20f) }

    if (engine.handOver) {
        FilledTonalButton(onClick = { engine.newHand() }) {
            Text("Next Hand")
        }
        return
    }

    if (actions.isEmpty()) return

    AnimatedVisibility(
        visible = actions.isNotEmpty(),
        enter = fadeIn() + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (PlayerAction.FOLD in actions) {
                    OutlinedButton(
                        onClick = {
                            raiseSliderVisible = false
                            engine.fold()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Fold")
                    }
                }

                if (PlayerAction.CHECK in actions) {
                    FilledTonalButton(onClick = {
                        raiseSliderVisible = false
                        engine.check()
                    }) {
                        Text("Check")
                    }
                }

                if (PlayerAction.CALL in actions) {
                    val human = engine.players.first { it.isHuman }
                    val toCall = engine.currentBet - human.currentBet
                    Button(onClick = {
                        raiseSliderVisible = false
                        engine.call()
                    }) {
                        Text("Call $toCall")
                    }
                }

                if (PlayerAction.RAISE in actions) {
                    Button(onClick = { raiseSliderVisible = !raiseSliderVisible }) {
                        Text("Raise")
                    }
                }
            }

            if (raiseSliderVisible && PlayerAction.RAISE in actions) {
                val human = engine.players.first { it.isHuman }
                val toCall = engine.currentBet - human.currentBet
                val maxRaise = (human.chips - toCall).toFloat().coerceAtLeast(20f)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Raise: ${raiseAmount.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = raiseAmount,
                    onValueChange = { raiseAmount = it },
                    valueRange = 20f..maxRaise,
                    steps = ((maxRaise - 20f) / 20f).toInt().coerceAtLeast(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Button(onClick = {
                    raiseSliderVisible = false
                    engine.raise(raiseAmount.toInt())
                    raiseAmount = 20f
                }) {
                    Text("Confirm Raise ${raiseAmount.toInt()}")
                }
            }
        }
    }
}
