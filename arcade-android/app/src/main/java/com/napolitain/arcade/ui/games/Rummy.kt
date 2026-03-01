package com.napolitain.arcade.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.rummy.Card
import com.napolitain.arcade.logic.rummy.Meld
import com.napolitain.arcade.logic.rummy.Phase
import com.napolitain.arcade.logic.rummy.RummyEngine
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay

// Pastel palette for meld highlighting
private val meldPalette = listOf(
    Color(0xFFA5D6A7), // green
    Color(0xFF90CAF9), // blue
    Color(0xFFEF9A9A), // red
    Color(0xFFFFCC80), // orange
    Color(0xFFCE93D8), // purple
)

// ── Main composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RummyGame() {
    val engine = remember { RummyEngine() }

    // Trigger AI turn with a short delay
    LaunchedEffect(engine.phase) {
        if (engine.phase == Phase.OPPONENT_TURN) {
            delay(600)
            engine.triggerAiTurn()
        }
    }

    val statusText = when (engine.phase) {
        Phase.DRAW -> stringResource(R.string.rummy_draw)
        Phase.DISCARD -> stringResource(R.string.rummy_discard)
        Phase.OPPONENT_TURN -> stringResource(R.string.rummy_opponent)
        Phase.KNOCK_DECISION ->
            if (engine.isGin) stringResource(R.string.rummy_gin_choice)
            else stringResource(R.string.rummy_knock_choice)
        Phase.ROUND_OVER, Phase.GAME_OVER -> engine.roundMessage
    }

    GameShell(
        title = stringResource(R.string.game_rummy),
        status = statusText,
        score = "${engine.playerScore} – ${engine.opponentScore}",
        onReset = { engine.reset() },
    ) {
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // ── Opponent area ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.rummy_opp, engine.opponentScore),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${engine.opponentCardCount} cards",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Opponent hand (face-down)
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            repeat(engine.opponentCardCount) {
                CardBack(modifier = Modifier.size(36.dp, 52.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Piles ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stock pile
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (engine.stockCount > 0) {
                    CardBack(
                        modifier = Modifier
                            .size(52.dp, 74.dp)
                            .clickable(enabled = engine.phase == Phase.DRAW) {
                                engine.drawFromStock()
                            },
                    )
                } else {
                    EmptyPile(Modifier.size(52.dp, 74.dp))
                }
                Text(
                    stringResource(R.string.rummy_stock, engine.stockCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(24.dp))

            // Discard pile
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val top = engine.discardTop
                if (top != null) {
                    CardFace(
                        card = top,
                        modifier = Modifier
                            .size(52.dp, 74.dp)
                            .clickable(enabled = engine.phase == Phase.DRAW) {
                                engine.drawFromDiscard()
                            },
                    )
                } else {
                    EmptyPile(Modifier.size(52.dp, 74.dp))
                }
                Text(
                    stringResource(R.string.rummy_discard_pile),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Player hand ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.rummy_you, engine.playerScore),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.rummy_deadwood, engine.playerDeadwood),
                style = MaterialTheme.typography.labelMedium,
                color = if (engine.canKnock) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            engine.playerHand.forEachIndexed { index, card ->
                val meldIdx = engine.playerMelds.indexOfFirst { m -> card in m.cards }
                CardFace(
                    card = card,
                    modifier = Modifier.size(44.dp, 64.dp),
                    isSelected = index == engine.selectedCardIndex,
                    meldColorIndex = meldIdx,
                    onClick = {
                        if (engine.phase == Phase.DISCARD) {
                            if (engine.selectedCardIndex == index) engine.discard(index)
                            else engine.selectCard(index)
                        }
                    },
                )
            }
        }

        // ── Controls ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = engine.phase == Phase.KNOCK_DECISION,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                FilledTonalButton(onClick = { engine.knock() }) {
                    Text(
                        if (engine.isGin) stringResource(R.string.rummy_gin)
                        else stringResource(R.string.rummy_knock),
                    )
                }
                OutlinedButton(onClick = { engine.pass() }) {
                    Text(stringResource(R.string.rummy_continue))
                }
            }
        }

        // ── Round-end / game-over reveal ────────────────────────────────────
        AnimatedVisibility(
            visible = engine.phase == Phase.ROUND_OVER || engine.phase == Phase.GAME_OVER,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.rummy_opp_hand),
                    style = MaterialTheme.typography.titleSmall,
                )
                // Opponent melds
                engine.opponentMelds.forEachIndexed { idx, meld ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        meld.cards.forEach { card ->
                            CardFace(card = card, modifier = Modifier.size(40.dp, 58.dp), meldColorIndex = idx)
                        }
                    }
                }
                // Opponent deadwood
                if (engine.opponentDeadwoodCards.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        engine.opponentDeadwoodCards.forEach { card ->
                            CardFace(card = card, modifier = Modifier.size(40.dp, 58.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (engine.phase == Phase.ROUND_OVER) {
                    FilledTonalButton(onClick = { engine.newRound() }) {
                        Text(stringResource(R.string.rummy_new_round))
                    }
                } else {
                    FilledTonalButton(onClick = { engine.reset() }) {
                        Text(stringResource(R.string.rummy_new_game))
                    }
                }
            }
        }
    }
}

// ── Card composables ────────────────────────────────────────────────────────

@Composable
private fun CardFace(
    card: Card,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    meldColorIndex: Int = -1,
    onClick: () -> Unit = {},
) {
    val surface = MaterialTheme.colorScheme.surface
    val bgColor = if (meldColorIndex >= 0) meldPalette[meldColorIndex % meldPalette.size] else surface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (card.suit.isRed) Color(0xFFDC2626) else Color(0xFF1E293B)
    val yOffset by animateDpAsState(
        targetValue = if (isSelected) (-8).dp else 0.dp,
        label = "cardY",
    )

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(bgColor, cornerRadius = CornerRadius(8f, 8f))
            drawRoundRect(
                borderColor,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(if (isSelected) 3f else 1.5f),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                card.rank.symbol,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.sp,
            )
            Text(card.suit.symbol, color = textColor, fontSize = 16.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF1565C0), cornerRadius = CornerRadius(8f, 8f))
            drawRoundRect(
                Color(0xFF0D47A1),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(2f),
            )
            // Inner decorative rect
            val inset = 6f
            drawRoundRect(
                Color(0xFF1976D2),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(1.5f),
            )
        }
    }
}

@Composable
private fun EmptyPile(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                outline,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                ),
            )
        }
    }
}
