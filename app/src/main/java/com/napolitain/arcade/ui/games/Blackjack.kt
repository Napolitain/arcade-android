package com.napolitain.arcade.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.blackjack.BlackjackEngine
import com.napolitain.arcade.logic.blackjack.Card
import com.napolitain.arcade.logic.blackjack.GamePhase
import com.napolitain.arcade.logic.blackjack.GameResult
import com.napolitain.arcade.ui.components.GameShell

private val FeltGreen = Color(0xFF1B5E20)
private val CardBack = Color(0xFF1565C0)
private val CardBackAccent = Color(0xFF1E88E5)

@Composable
fun BlackjackGame() {
    val engine = remember { BlackjackEngine() }
    val textMeasurer = rememberTextMeasurer()

    val statusText = when (engine.phase) {
        GamePhase.BETTING -> stringResource(R.string.bj_place_bet)
        GamePhase.PLAYER_TURN -> stringResource(R.string.bj_your_turn)
        GamePhase.DEALER_TURN -> stringResource(R.string.bj_dealer_turn)
        GamePhase.RESULT -> when (engine.result) {
            GameResult.PLAYER_BLACKJACK -> stringResource(R.string.bj_blackjack)
            GameResult.PLAYER_WIN -> stringResource(R.string.bj_you_win)
            GameResult.DEALER_WIN -> stringResource(R.string.bj_dealer_wins)
            GameResult.PUSH -> stringResource(R.string.bj_push)
            GameResult.PLAYER_BUST -> stringResource(R.string.bj_bust)
            GameResult.DEALER_BUST -> stringResource(R.string.bj_dealer_busts)
            else -> ""
        }
    }

    // Card deal animation
    val dealAnim = remember { Animatable(0f) }
    LaunchedEffect(engine.dealSequence) {
        if (engine.dealSequence > 0) {
            dealAnim.snapTo(0f)
            dealAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    // Card flip animation for dealer reveal
    val flipAnim = remember { Animatable(1f) }
    LaunchedEffect(engine.dealerCardHidden) {
        if (!engine.dealerCardHidden) {
            flipAnim.snapTo(0f)
            flipAnim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        } else {
            flipAnim.snapTo(1f)
        }
    }

    GameShell(
        title = stringResource(R.string.game_blackjack),
        status = statusText,
        score = stringResource(R.string.bj_chips, engine.chips),
        onReset = { engine.reset() },
    ) {
        // Table canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(FeltGreen, RoundedCornerShape(16.dp)),
        ) {
            val cardW = size.width * 0.13f
            val cardH = cardW * 1.45f
            val offsetStep = cardW * 0.7f

            // Dealer hand — top area
            val dealerY = size.height * 0.08f
            val dealerStartX = (size.width - (engine.dealerHand.size * offsetStep - offsetStep + cardW)) / 2f
            engine.dealerHand.forEachIndexed { i, card ->
                val x = dealerStartX + i * offsetStep
                if (i == 1 && engine.dealerCardHidden) {
                    drawCardBack(x, dealerY, cardW, cardH)
                } else {
                    drawCard(card, x, dealerY, cardW, cardH, textMeasurer)
                }
            }

            // Dealer total label
            if (engine.dealerHand.isNotEmpty()) {
                val dealerLabel = if (engine.dealerCardHidden) "?" else engine.dealerTotal.toString()
                drawText(
                    textMeasurer = textMeasurer,
                    text = dealerLabel,
                    topLeft = Offset(dealerStartX - cardW * 0.6f, dealerY + cardH * 0.3f),
                    style = TextStyle(color = Color.White, fontSize = (cardW * 0.22f).sp, fontWeight = FontWeight.Bold),
                )
            }

            // Player hand — bottom area
            val playerY = size.height - cardH - size.height * 0.08f
            val playerStartX = (size.width - (engine.playerHand.size * offsetStep - offsetStep + cardW)) / 2f
            engine.playerHand.forEachIndexed { i, card ->
                val x = playerStartX + i * offsetStep
                drawCard(card, x, playerY, cardW, cardH, textMeasurer)
            }

            // Player total label
            if (engine.playerHand.isNotEmpty()) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = engine.playerTotal.toString(),
                    topLeft = Offset(playerStartX - cardW * 0.6f, playerY + cardH * 0.3f),
                    style = TextStyle(color = Color.White, fontSize = (cardW * 0.22f).sp, fontWeight = FontWeight.Bold),
                )
            }

            // Center bet display
            if (engine.currentBet > 0) {
                val chipRadius = cardW * 0.35f
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(Color(0xFFD32F2F), chipRadius, Offset(cx, cy))
                drawCircle(Color.White, chipRadius * 0.7f, Offset(cx, cy), style = Stroke(chipRadius * 0.08f))
                drawText(
                    textMeasurer = textMeasurer,
                    text = engine.currentBet.toString(),
                    topLeft = Offset(cx - chipRadius * 0.5f, cy - chipRadius * 0.3f),
                    style = TextStyle(color = Color.White, fontSize = (chipRadius * 0.5f).sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Result overlay
        AnimatedVisibility(
            visible = engine.phase == GamePhase.RESULT,
            enter = scaleIn(tween(300)) + fadeIn(),
            exit = scaleOut(tween(200)) + fadeOut(),
        ) {
            val resultColor = when (engine.result) {
                GameResult.PLAYER_BLACKJACK, GameResult.PLAYER_WIN, GameResult.DEALER_BUST ->
                    MaterialTheme.colorScheme.primary
                GameResult.PUSH -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            }
            val amountText = when (engine.result) {
                GameResult.PLAYER_BLACKJACK -> "+${(engine.currentBet * 3) / 2}"
                GameResult.PLAYER_WIN, GameResult.DEALER_BUST -> "+${engine.currentBet}"
                GameResult.PUSH -> "±0"
                else -> "-${engine.currentBet}"
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = resultColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    color = resultColor,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { engine.newHand() }) {
                    Text(stringResource(R.string.bj_new_hand))
                }
            }
        }

        // Betting phase
        if (engine.phase == GamePhase.BETTING) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.bj_place_bet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (amount in listOf(10, 25, 50, 100)) {
                        ChipButton(
                            amount = amount,
                            enabled = engine.chips >= amount,
                            onClick = { engine.placeBet(amount) },
                        )
                    }
                }
            }
        }

        // Player action buttons
        if (engine.phase == GamePhase.PLAYER_TURN) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = { engine.hit() }) {
                    Text(stringResource(R.string.bj_hit))
                }
                Button(onClick = { engine.stand() }) {
                    Text(stringResource(R.string.bj_stand))
                }
                Button(
                    onClick = { engine.doubleDown() },
                    enabled = engine.chips >= engine.currentBet,
                ) {
                    Text(stringResource(R.string.bj_double_down))
                }
            }
        }
    }
}

@Composable
private fun ChipButton(amount: Int, enabled: Boolean, onClick: () -> Unit) {
    val chipColor = when (amount) {
        10 -> Color(0xFF1E88E5)
        25 -> Color(0xFF43A047)
        50 -> Color(0xFFE53935)
        else -> Color(0xFF6A1B9A)
    }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = chipColor,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(50),
    ) {
        Text("$$amount", fontWeight = FontWeight.Bold)
    }
}

// ── Card drawing helpers ────────────────────────────────────────────────

private fun DrawScope.drawCard(
    card: Card, x: Float, y: Float, w: Float, h: Float, textMeasurer: TextMeasurer,
) {
    val cornerR = w * 0.1f
    // Card shadow
    drawRoundRect(
        Color(0x40000000),
        topLeft = Offset(x + 2f, y + 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerR),
    )
    // Card body
    drawRoundRect(
        Color.White,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerR),
    )
    // Border
    drawRoundRect(
        Color(0xFFBDBDBD),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerR),
        style = Stroke(1.5f),
    )

    val textColor = if (card.suit.isRed) Color(0xFFD32F2F) else Color(0xFF212121)
    val fontSize = (w * 0.22f).sp

    // Top-left rank + suit
    clipRect(x, y, x + w, y + h) {
        drawText(
            textMeasurer = textMeasurer,
            text = card.rank.display,
            topLeft = Offset(x + w * 0.1f, y + h * 0.06f),
            style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
        )
        drawText(
            textMeasurer = textMeasurer,
            text = card.suit.symbol,
            topLeft = Offset(x + w * 0.1f, y + h * 0.2f),
            style = TextStyle(color = textColor, fontSize = fontSize),
        )
        // Center suit large
        drawText(
            textMeasurer = textMeasurer,
            text = card.suit.symbol,
            topLeft = Offset(x + w * 0.28f, y + h * 0.35f),
            style = TextStyle(color = textColor, fontSize = (w * 0.4f).sp),
        )
    }
}

private fun DrawScope.drawCardBack(x: Float, y: Float, w: Float, h: Float) {
    val cornerR = w * 0.1f
    // Shadow
    drawRoundRect(
        Color(0x40000000),
        topLeft = Offset(x + 2f, y + 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerR),
    )
    // Body
    drawRoundRect(
        CardBack,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerR),
    )
    // Inner rect pattern
    val inset = w * 0.12f
    drawRoundRect(
        CardBackAccent,
        topLeft = Offset(x + inset, y + inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(cornerR * 0.6f),
        style = Stroke(2f),
    )
    // Diamond pattern — center diamond
    val cx = x + w / 2f
    val cy = y + h / 2f
    val dw = w * 0.18f
    val dh = h * 0.14f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, cy - dh)
        lineTo(cx + dw, cy)
        lineTo(cx, cy + dh)
        lineTo(cx - dw, cy)
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.3f))
}
