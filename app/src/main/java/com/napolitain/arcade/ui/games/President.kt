package com.napolitain.arcade.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.president.Card
import com.napolitain.arcade.logic.president.PresidentEngine
import com.napolitain.arcade.logic.president.Suit
import com.napolitain.arcade.logic.president.Title
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay

@Composable
fun PresidentGame() {
    val engine = remember { PresidentEngine() }
    val selectedIndices = remember { mutableListOf<Int>().toMutableStateList() }

    val isHumanTurn = engine.currentPlayerIndex == 0 &&
        !engine.players[0].finished &&
        engine.gamePhase == PresidentEngine.GamePhase.PLAYING
    val playableCards = engine.humanPlayableCards

    // AI auto-play
    LaunchedEffect(engine.currentPlayerIndex, engine.gamePhase, engine.roundOver) {
        if (engine.gamePhase != PresidentEngine.GamePhase.PLAYING) return@LaunchedEffect
        val player = engine.players.getOrNull(engine.currentPlayerIndex) ?: return@LaunchedEffect
        if (!player.isHuman && !player.finished) {
            delay(600)
            engine.triggerAiMove()
        }
    }

    // Revolution flash
    var showRevolutionFlash by remember { mutableStateOf(false) }
    LaunchedEffect(engine.isRevolution) {
        if (engine.isRevolution) {
            showRevolutionFlash = true
            delay(1500)
            showRevolutionFlash = false
        }
    }

    val statusText = when {
        engine.gamePhase == PresidentEngine.GamePhase.ROUND_END ->
            "Round ${engine.roundNumber} complete! ${engine.players.firstOrNull { it.title == Title.PRESIDENT }?.name ?: ""} is President"
        isHumanTurn -> stringResource(R.string.turn_label, "You")
        else -> engine.lastAction.ifEmpty {
            stringResource(R.string.ai_thinking)
        }
    }

    GameShell(
        title = stringResource(R.string.game_president),
        status = statusText,
        onReset = {
            engine.reset()
            selectedIndices.clear()
        },
    ) {
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // Revolution indicator
        AnimatedVisibility(
            visible = showRevolutionFlash,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Text(
                "⚡ REVOLUTION! Ranks reversed! ⚡",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (engine.isRevolution && !showRevolutionFlash) {
            Text(
                "Revolution active — ranks reversed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        // AI opponents row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in 1..3) {
                val p = engine.players[i]
                val isCurrentTurn = engine.currentPlayerIndex == i &&
                    engine.gamePhase == PresidentEngine.GamePhase.PLAYING
                AiOpponentCard(
                    name = p.name,
                    cardCount = p.hand.size,
                    title = p.title,
                    finished = p.finished,
                    isCurrentTurn = isCurrentTurn,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Center pile area
        PileArea(
            pile = engine.currentPile.toList(),
            lastAction = engine.lastAction,
            pileCount = engine.pileCount,
        )

        Spacer(Modifier.height(4.dp))

        // Round info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Round ${engine.roundNumber}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val humanTitle = engine.players[0].title
            if (humanTitle != Title.NONE) {
                TitleBadge(humanTitle)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Player's hand
        if (!engine.players[0].finished) {
            Text(
                "Your hand (${engine.players[0].hand.size} cards)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            PlayerHand(
                hand = engine.players[0].hand,
                selectedIndices = selectedIndices,
                playableCards = playableCards,
                enabled = isHumanTurn,
                onToggleSelect = { idx ->
                    if (selectedIndices.contains(idx)) {
                        selectedIndices.remove(idx)
                    } else {
                        // Validate selection: must be same rank
                        val card = engine.players[0].hand[idx]
                        if (selectedIndices.isEmpty() ||
                            engine.players[0].hand[selectedIndices[0]].rank == card.rank
                        ) {
                            selectedIndices.add(idx)
                        }
                    }
                },
            )
        } else {
            Text(
                "You finished! Waiting for others…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        when (engine.gamePhase) {
            PresidentEngine.GamePhase.PLAYING -> {
                if (isHumanTurn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        Button(
                            onClick = {
                                if (selectedIndices.isNotEmpty()) {
                                    engine.playCards(selectedIndices.toList())
                                    selectedIndices.clear()
                                }
                            },
                            enabled = selectedIndices.isNotEmpty() && isValidPlay(engine, selectedIndices),
                        ) {
                            Text("Play")
                        }
                        FilledTonalButton(
                            onClick = {
                                engine.pass()
                                selectedIndices.clear()
                            },
                            // Can always pass unless leading an empty pile
                            enabled = engine.pileRank != null || engine.pileCount != 0,
                        ) {
                            Text("Pass")
                        }
                    }
                }
            }
            PresidentEngine.GamePhase.ROUND_END -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Show final standings
                    engine.players.sortedBy { it.finishOrder }.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TitleBadge(p.title)
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        engine.newRound()
                        selectedIndices.clear()
                    }) {
                        Text("Next Round")
                    }
                }
            }
            PresidentEngine.GamePhase.EXCHANGING -> {
                Text(
                    "Exchanging cards…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun isValidPlay(engine: PresidentEngine, selected: List<Int>): Boolean {
    if (selected.isEmpty()) return false
    val hand = engine.players[0].hand
    val rank = hand[selected[0]].rank
    if (!selected.all { hand[it].rank == rank }) return false
    if (engine.pileCount != 0 && selected.size != engine.pileCount) return false
    return true
}

// ── Sub-composables ─────────────────────────────────────────────

@Composable
private fun AiOpponentCard(
    name: String,
    cardCount: Int,
    title: Title,
    finished: Boolean,
    isCurrentTurn: Boolean,
) {
    val borderColor = if (isCurrentTurn) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isCurrentTurn) 2.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isCurrentTurn) 4.dp else 1.dp,
        modifier = Modifier
            .width(100.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.labelMedium)
            if (title != Title.NONE) {
                TitleBadge(title)
            }
            Text(
                if (finished) "✓ Done" else "$cardCount cards",
                style = MaterialTheme.typography.bodySmall,
                color = if (finished) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TitleBadge(title: Title) {
    val (bgColor, label) = when (title) {
        Title.PRESIDENT -> Color(0xFFFFD700) to "👑 P"
        Title.VICE_PRESIDENT -> Color(0xFFC0C0C0) to "VP"
        Title.NEUTRAL -> Color(0xFFCD7F32) to "N"
        Title.SCUM -> Color(0xFF808080) to "S"
        Title.NONE -> return
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.padding(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.Black,
        )
    }
}

@Composable
private fun PileArea(pile: List<Card>, lastAction: String, pileCount: Int) {
    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val redColor = Color(0xFFDC2626)
    val blackColor = Color(0xFF1A1A1A)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pile display
        if (pile.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (lastAction.isNotEmpty()) lastAction else "Play a card to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                val cardWidth = 60.dp.toPx()
                val cardHeight = 85.dp.toPx()
                val totalWidth = cardWidth + (pile.size - 1) * 25.dp.toPx()
                val startX = (size.width - totalWidth) / 2f
                val startY = (size.height - cardHeight) / 2f

                pile.forEachIndexed { i, card ->
                    val x = startX + i * 25.dp.toPx()
                    drawCardOnCanvas(
                        card = card,
                        topLeft = Offset(x, startY),
                        cardSize = Size(cardWidth, cardHeight),
                        textMeasurer = textMeasurer,
                    )
                }
            }
        }

        if (pileCount > 0) {
            Text(
                when (pileCount) {
                    1 -> "Singles"
                    2 -> "Pairs"
                    3 -> "Triples"
                    4 -> "Quads"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceColor,
            )
        }
    }
}

@Composable
private fun PlayerHand(
    hand: List<Card>,
    selectedIndices: List<Int>,
    playableCards: Set<Int>,
    enabled: Boolean,
    onToggleSelect: (Int) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((-16).dp),
    ) {
        hand.forEachIndexed { index, card ->
            val isSelected = selectedIndices.contains(index)
            val isPlayable = playableCards.contains(index)

            val yOffset = if (isSelected) (-12) else 0

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, yOffset.dp.roundToPx()) }
                    .clickable(enabled = enabled && isPlayable) { onToggleSelect(index) },
            ) {
                Canvas(
                    modifier = Modifier.size(56.dp, 80.dp),
                ) {
                    // Selection highlight
                    if (isSelected) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            size = size,
                        )
                    }

                    drawCardOnCanvas(
                        card = card,
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        cardSize = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        textMeasurer = textMeasurer,
                        dimmed = enabled && !isPlayable,
                    )
                }
            }
        }
    }
}

/** Draw a playing card with Canvas: white rounded rect with rank + suit. */
private fun DrawScope.drawCardOnCanvas(
    card: Card,
    topLeft: Offset,
    cardSize: Size,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    dimmed: Boolean = false,
) {
    val isRed = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val cardColor = if (dimmed) Color(0xFFE0E0E0) else Color.White
    val textColor = if (dimmed) Color(0xFFBBBBBB) else if (isRed) Color(0xFFDC2626) else Color(0xFF1A1A1A)

    // Card background
    drawRoundRect(
        color = cardColor,
        topLeft = topLeft,
        size = cardSize,
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    // Card border
    drawRoundRect(
        color = Color(0xFFCCCCCC),
        topLeft = topLeft,
        size = cardSize,
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )

    // Rank + suit text
    val label = card.label
    val style = TextStyle(fontSize = 11.sp, color = textColor)
    val layoutResult = textMeasurer.measure(label, style)

    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            topLeft.x + 4.dp.toPx(),
            topLeft.y + 4.dp.toPx(),
        ),
    )

    // Bottom-right (inverted)
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            topLeft.x + cardSize.width - layoutResult.size.width - 4.dp.toPx(),
            topLeft.y + cardSize.height - layoutResult.size.height - 4.dp.toPx(),
        ),
    )

    // Center suit
    val suitStyle = TextStyle(fontSize = 18.sp, color = textColor)
    val suitResult = textMeasurer.measure(card.suit.symbol, suitStyle)
    drawText(
        textLayoutResult = suitResult,
        topLeft = Offset(
            topLeft.x + (cardSize.width - suitResult.size.width) / 2f,
            topLeft.y + (cardSize.height - suitResult.size.height) / 2f,
        ),
    )
}
