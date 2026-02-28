package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.wordballoon.RoundStatus
import com.napolitain.arcade.logic.wordballoon.WordBalloonEngine
import com.napolitain.arcade.ui.components.GameShell

// ── Colors matching the React SVG ──────────────────────────────

private val BalloonCyan = Color(0xFF22D3EE)
private val BalloonBlue = Color(0xFF38BDF8)
private val BalloonRed = Color(0xFFFB7185)
private val BalloonShine = Color(0xFFE0F2FE)
private val RopeColor = Color(0xFFCBD5E1)
private val BasketBrown = Color(0xFFB45309)
private val BasketStroke = Color(0xFFFDBA74)
private val BasketLine = Color(0xFFFCD34D)
private val DamagedBasketBrown = Color(0xFF7C2D12)
private val BurstCenter = Color(0xFFF8FAFC)
private val BurstRay = Color(0xFFFDA4AF)
private val CrackColor = Color(0xFFF8FAFC)
private val HoleStroke = Color(0xFFF8FAFC)
private val DamageZig = Color(0xFFFECACA)

// ── Balloon Canvas ─────────────────────────────────────────────

@Composable
private fun BalloonCanvas(wrongGuesses: Int, modifier: Modifier = Modifier) {
    // Animate balloon vertical scale to simulate inflation / stress
    val balloonScale by animateFloatAsState(
        targetValue = when {
            wrongGuesses >= WordBalloonEngine.MAX_WRONG_GUESSES -> 0f
            wrongGuesses >= 4 -> 1.12f
            wrongGuesses >= 2 -> 1.06f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 400),
        label = "balloonScale",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        // Map SVG viewBox (220×280) into canvas size
        val sx = size.width / 220f
        val sy = size.height / 280f

        val isPopped = wrongGuesses >= WordBalloonEngine.MAX_WRONG_GUESSES

        if (!isPopped) {
            drawBalloon(sx, sy, wrongGuesses, balloonScale)
        } else {
            drawPoppedBalloon(sx, sy)
        }
    }
}

private fun DrawScope.drawBalloon(sx: Float, sy: Float, wrongGuesses: Int, scale: Float) {
    val showRightRope = wrongGuesses < 2
    val showLeftRope = wrongGuesses < 3
    val showBasket = wrongGuesses < 5
    val isBasketDamaged = wrongGuesses >= 4
    val balloonFill = when {
        wrongGuesses >= 4 -> BalloonRed
        wrongGuesses >= 2 -> BalloonBlue
        else -> BalloonCyan
    }

    // Balloon ellipse (cx=110, cy=86, rx=56, ry=68)
    val bCx = 110f * sx
    val bCy = 86f * sy
    val bRx = 56f * sx * scale
    val bRy = 68f * sy * scale

    // Balloon body
    drawOval(balloonFill, topLeft = Offset(bCx - bRx, bCy - bRy), size = Size(bRx * 2, bRy * 2))
    drawOval(
        Color(0xFFE2E8F0),
        topLeft = Offset(bCx - bRx, bCy - bRy),
        size = Size(bRx * 2, bRy * 2),
        style = Stroke(width = 6f * sx),
    )

    // Shine highlight
    drawOval(
        BalloonShine.copy(alpha = 0.5f),
        topLeft = Offset((92f - 13f) * sx, (62f - 18f) * sy),
        size = Size(26f * sx, 36f * sy),
    )

    // Cracks (wrongGuesses >= 1)
    if (wrongGuesses >= 1) {
        val crackStroke = Stroke(width = 3f * sx, cap = StrokeCap.Round)
        drawLine(CrackColor, Offset(123f * sx, 79f * sy), Offset(131f * sx, 89f * sy), strokeWidth = crackStroke.width)
        drawLine(CrackColor, Offset(123f * sx, 86f * sy), Offset(133f * sx, 94f * sy), strokeWidth = crackStroke.width)
        drawLine(CrackColor, Offset(115f * sx, 90f * sy), Offset(124f * sx, 99f * sy), strokeWidth = crackStroke.width)
    }

    // Hole (wrongGuesses >= 4)
    if (wrongGuesses >= 4) {
        drawCircle(
            Color(0xFF1E293B).copy(alpha = 0.55f),
            radius = 7f * sx,
            center = Offset(124f * sx, 102f * sy),
        )
        drawCircle(
            HoleStroke,
            radius = 7f * sx,
            center = Offset(124f * sx, 102f * sy),
            style = Stroke(width = 1.5f * sx),
        )
    }

    // Ropes
    val ropeStroke = 4f * sx
    if (showLeftRope) {
        drawLine(RopeColor, Offset(92f * sx, 152f * sy), Offset(86f * sx, 206f * sy), strokeWidth = ropeStroke)
    }
    if (showRightRope) {
        drawLine(RopeColor, Offset(128f * sx, 152f * sy), Offset(134f * sx, 206f * sy), strokeWidth = ropeStroke)
    }

    // Basket
    if (showBasket) {
        val basketX = 78f * sx
        val basketY = 206f * sy
        val basketW = 64f * sx
        val basketH = 42f * sy

        drawRoundRect(
            BasketBrown,
            topLeft = Offset(basketX, basketY),
            size = Size(basketW, basketH),
            cornerRadius = CornerRadius(10f * sx, 10f * sy),
        )
        drawRoundRect(
            BasketStroke,
            topLeft = Offset(basketX, basketY),
            size = Size(basketW, basketH),
            cornerRadius = CornerRadius(10f * sx, 10f * sy),
            style = Stroke(width = 4f * sx),
        )
        // Horizontal weave lines
        val weaveStroke = 3f * sx
        drawLine(BasketLine.copy(alpha = 0.5f), Offset(basketX, 220f * sy), Offset(basketX + basketW, 220f * sy), strokeWidth = weaveStroke)
        drawLine(BasketLine.copy(alpha = 0.5f), Offset(basketX, 234f * sy), Offset(basketX + basketW, 234f * sy), strokeWidth = weaveStroke)

        // Basket damage zigzag (wrongGuesses >= 4)
        if (isBasketDamaged) {
            val zigPath = Path().apply {
                moveTo(101f * sx, 220f * sy)
                lineTo(111f * sx, 231f * sy)
                lineTo(119f * sx, 222f * sy)
            }
            drawPath(zigPath, DamageZig, style = Stroke(width = 3f * sx, cap = StrokeCap.Round))
        }
    } else {
        // Fallen basket
        drawRoundRect(
            DamagedBasketBrown.copy(alpha = 0.8f),
            topLeft = Offset(84f * sx, 236f * sy),
            size = Size(52f * sx, 32f * sy),
            cornerRadius = CornerRadius(8f * sx, 8f * sy),
        )
        drawRoundRect(
            BasketStroke,
            topLeft = Offset(84f * sx, 236f * sy),
            size = Size(52f * sx, 32f * sy),
            cornerRadius = CornerRadius(8f * sx, 8f * sy),
            style = Stroke(width = 3f * sx),
        )
    }
}

private fun DrawScope.drawPoppedBalloon(sx: Float, sy: Float) {
    // Center dot
    drawCircle(BurstCenter, radius = 8f * sx, center = Offset(110f * sx, 90f * sy))

    // Burst rays
    val rayStroke = 6f * sx
    val rays = listOf(
        Offset(110f, 38f) to Offset(110f, 58f),
        Offset(110f, 122f) to Offset(110f, 142f),
        Offset(58f, 90f) to Offset(78f, 90f),
        Offset(142f, 90f) to Offset(162f, 90f),
        Offset(74f, 54f) to Offset(88f, 68f),
        Offset(132f, 112f) to Offset(146f, 126f),
        Offset(146f, 54f) to Offset(132f, 68f),
        Offset(88f, 112f) to Offset(74f, 126f),
    )
    for ((start, end) in rays) {
        drawLine(BurstRay, Offset(start.x * sx, start.y * sy), Offset(end.x * sx, end.y * sy), strokeWidth = rayStroke, cap = StrokeCap.Round)
    }

    // Dangling ropes (dashed)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f * sx, 7f * sx), 0f)
    drawLine(RopeColor, Offset(102f * sx, 154f * sy), Offset(94f * sx, 196f * sy), strokeWidth = 4f * sx, cap = StrokeCap.Round, pathEffect = dashEffect)
    drawLine(RopeColor, Offset(118f * sx, 154f * sy), Offset(126f * sx, 196f * sy), strokeWidth = 4f * sx, cap = StrokeCap.Round, pathEffect = dashEffect)

    // Fallen basket
    drawRoundRect(
        DamagedBasketBrown,
        topLeft = Offset(82f * sx, 212f * sy),
        size = Size(56f * sx, 36f * sy),
        cornerRadius = CornerRadius(9f * sx, 9f * sy),
    )
    drawRoundRect(
        BasketStroke,
        topLeft = Offset(82f * sx, 212f * sy),
        size = Size(56f * sx, 36f * sy),
        cornerRadius = CornerRadius(9f * sx, 9f * sy),
        style = Stroke(width = 3f * sx),
    )
}

// ── Word display ───────────────────────────────────────────────

@Composable
private fun WordDisplay(maskedWord: List<Char>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val letterCount = maskedWord.size
        val cellW = 36.dp.toPx()
        val cellH = 40.dp.toPx()
        val gap = 6.dp.toPx()
        val totalWidth = letterCount * cellW + (letterCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val startY = (size.height - cellH) / 2f

        val style = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )

        for (i in maskedWord.indices) {
            val x = startX + i * (cellW + gap)
            // Cell background
            drawRoundRect(
                surfaceColor,
                topLeft = Offset(x, startY),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            // Cell border
            drawRoundRect(
                borderColor,
                topLeft = Offset(x, startY),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Letter
            val measured = textMeasurer.measure(maskedWord[i].toString(), style)
            drawText(
                measured,
                topLeft = Offset(
                    x + (cellW - measured.size.width) / 2f,
                    startY + (cellH - measured.size.height) / 2f,
                ),
            )
        }
    }
}

// ── Letter keyboard ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterKeyboard(
    engine: WordBalloonEngine,
    modifier: Modifier = Modifier,
) {
    val guessed = engine.guessedLetters.toSet()
    val word = engine.targetWord.word
    val playing = engine.roundStatus == RoundStatus.PLAYING

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 7,
    ) {
        for (letter in WordBalloonEngine.ALPHABET) {
            val alreadyGuessed = letter in guessed
            val isCorrect = alreadyGuessed && letter in word
            val isWrong = alreadyGuessed && letter !in word
            val enabled = !alreadyGuessed && playing

            val containerColor = when {
                isCorrect -> Color(0xFF065F46).copy(alpha = 0.6f)
                isWrong -> Color(0xFF9F1239).copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when {
                isCorrect -> Color(0xFFA7F3D0)
                isWrong -> Color(0xFFFECDD3)
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onSurface
            }

            Button(
                onClick = { engine.submitGuess(letter) },
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = containerColor.copy(alpha = containerColor.alpha * 0.6f),
                    disabledContentColor = contentColor,
                ),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.size(width = 42.dp, height = 40.dp),
            ) {
                Text(
                    letter.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

// ── Main game composable ───────────────────────────────────────

@Composable
fun WordBalloonGame() {
    val engine = remember { WordBalloonEngine() }

    val missesText = engine.misses.let { if (it.isEmpty()) stringResource(R.string.wb_none) else it.joinToString(", ") }

    GameShell(
        title = stringResource(R.string.game_wordballoon),
        status = engine.statusText,
        score = stringResource(R.string.wb_wins, engine.wins),
        onReset = { engine.reset() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Balloon art
            BalloonCanvas(wrongGuesses = engine.wrongGuesses)

            // Word to guess
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.wb_word_to_guess),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                WordDisplay(maskedWord = engine.maskedWord)
            }

            // Misses
            Row {
                Text(stringResource(R.string.wb_misses), style = MaterialTheme.typography.bodyMedium)
                Text(
                    missesText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Categories info
            Text(
                stringResource(R.string.wb_categories, WordBalloonEngine.CATEGORY_NAMES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Letter keyboard
            LetterKeyboard(engine = engine)

            // Hint
            Text(
                stringResource(R.string.wb_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
