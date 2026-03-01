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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    val isPopped = wrongGuesses >= WordBalloonEngine.MAX_WRONG_GUESSES

    // ── 1. Balloon wobble / sway ───────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "sway")
    val swayDeg = when {
        isPopped -> 0f
        wrongGuesses >= 4 -> 6f
        wrongGuesses >= 2 -> 4.5f
        else -> 3f
    }
    val swayMs = when {
        isPopped -> 2000
        wrongGuesses >= 4 -> 1200
        wrongGuesses >= 2 -> 1600
        else -> 2000
    }
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -swayDeg,
        targetValue = swayDeg,
        animationSpec = infiniteRepeatable(
            animation = tween(swayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swayAngle",
    )

    // ── 2. Wrong-guess shake ───────────────────────────────────
    val shakeOffset = remember { Animatable(0f) }
    var prevWrong by remember { mutableIntStateOf(wrongGuesses) }
    LaunchedEffect(wrongGuesses) {
        val shouldShake = wrongGuesses > prevWrong && !isPopped
        prevWrong = wrongGuesses
        if (shouldShake) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    (-8f) at 40
                    8f at 80
                    (-6f) at 130
                    6f at 180
                    (-3f) at 230
                    3f at 260
                    0f at 300
                },
            )
        }
    }

    // ── 3. Stress color ────────────────────────────────────────
    val stressColor by animateColorAsState(
        targetValue = when {
            wrongGuesses >= 5 -> Color(0xFFEF4444)
            wrongGuesses >= 4 -> BalloonRed
            wrongGuesses >= 3 -> Color(0xFFE879A0)
            wrongGuesses >= 2 -> BalloonBlue
            wrongGuesses >= 1 -> Color(0xFF5BC4D8)
            else -> BalloonCyan
        },
        animationSpec = tween(400),
        label = "stressColor",
    )

    // ── 4. Pop explosion ───────────────────────────────────────
    val popProgress = remember { Animatable(0f) }
    LaunchedEffect(isPopped) {
        if (isPopped) {
            popProgress.snapTo(0f)
            popProgress.animateTo(1f, tween(600))
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val sx = size.width / 220f
        val sy = size.height / 280f

        if (!isPopped) {
            withTransform({
                translate(left = shakeOffset.value * sx)
                rotate(swayAngle, pivot = Offset(110f * sx, 86f * sy))
            }) {
                drawBalloon(sx, sy, wrongGuesses, balloonScale, stressColor)
            }
        } else {
            drawPoppedBalloon(sx, sy, popProgress.value)
        }
    }
}

private fun DrawScope.drawBalloon(sx: Float, sy: Float, wrongGuesses: Int, scale: Float, stressColor: Color) {
    val showRightRope = wrongGuesses < 2
    val showLeftRope = wrongGuesses < 3
    val showBasket = wrongGuesses < 5
    val isBasketDamaged = wrongGuesses >= 4

    // Balloon ellipse (cx=110, cy=86, rx=56, ry=68)
    val bCx = 110f * sx
    val bCy = 86f * sy
    val bRx = 56f * sx * scale
    val bRy = 68f * sy * scale

    // Balloon body – color animated externally via stressColor
    drawOval(stressColor, topLeft = Offset(bCx - bRx, bCy - bRy), size = Size(bRx * 2, bRy * 2))
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

    // Progressive cracks – appear one by one as wrong guesses increase
    val crackStroke = Stroke(width = 3f * sx, cap = StrokeCap.Round)
    if (wrongGuesses >= 1) {
        drawLine(CrackColor, Offset(123f * sx, 79f * sy), Offset(131f * sx, 89f * sy), strokeWidth = crackStroke.width)
    }
    if (wrongGuesses >= 2) {
        drawLine(CrackColor, Offset(123f * sx, 86f * sy), Offset(133f * sx, 94f * sy), strokeWidth = crackStroke.width)
    }
    if (wrongGuesses >= 3) {
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

private fun DrawScope.drawPoppedBalloon(sx: Float, sy: Float, popProgress: Float) {
    val centerX = 110f * sx
    val centerY = 90f * sy

    // Center dot – flash then fade
    val dotAlpha = if (popProgress < 0.3f) popProgress / 0.3f else (1f - popProgress) / 0.7f
    drawCircle(
        BurstCenter.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
        radius = (8f + 6f * popProgress) * sx,
        center = Offset(centerX, centerY),
    )

    // 12 burst rays expanding outward with fading alpha
    val rayCount = 12
    val maxLen = 55f * sx
    val rayLength = maxLen * popProgress
    val rayAlpha = (1f - popProgress).coerceIn(0f, 1f)
    val rayStroke = 6f * sx
    for (i in 0 until rayCount) {
        val angle = (2.0 * PI * i / rayCount).toFloat()
        val innerR = 12f * sx * popProgress
        val outerR = innerR + rayLength
        drawLine(
            BurstRay.copy(alpha = rayAlpha),
            Offset(centerX + cos(angle) * innerR, centerY + sin(angle) * innerR),
            Offset(centerX + cos(angle) * outerR, centerY + sin(angle) * outerR),
            strokeWidth = rayStroke,
            cap = StrokeCap.Round,
        )
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

    val wordSize = maskedWord.size

    // ── Flip-reveal animation state ────────────────────────────
    val flipScales = remember { mutableStateListOf<Animatable<Float, AnimationVector1D>>() }
    val displayChars = remember { mutableStateListOf<Char>() }

    // Reset when word length changes or a brand-new round starts
    val needsReset = flipScales.size != wordSize ||
        (maskedWord.all { it == '\u2022' } && displayChars.any { it != '\u2022' })
    if (needsReset) {
        flipScales.clear()
        repeat(wordSize) { flipScales.add(Animatable(1f)) }
        displayChars.clear()
        displayChars.addAll(maskedWord)
    }

    // Detect newly revealed letters and run staggered flip
    LaunchedEffect(maskedWord.toList()) {
        if (flipScales.size != wordSize) return@LaunchedEffect
        val revealed = (0 until wordSize).filter {
            it < displayChars.size && displayChars[it] == '\u2022' && maskedWord[it] != '\u2022'
        }
        if (revealed.isEmpty()) {
            // Sync without animation (e.g., game reset)
            for (i in 0 until wordSize) {
                if (i < displayChars.size) displayChars[i] = maskedWord[i]
            }
            return@LaunchedEffect
        }
        revealed.forEachIndexed { stagger, idx ->
            launch {
                delay(stagger * 80L)
                // Scale X → 0 (first half of flip)
                flipScales[idx].animateTo(0f, tween(150))
                // Swap to revealed letter at midpoint
                displayChars[idx] = maskedWord[idx]
                // Scale X → 1 (second half of flip)
                flipScales[idx].animateTo(1f, tween(150))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val letterCount = wordSize
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

        for (i in 0 until letterCount) {
            val x = startX + i * (cellW + gap)
            val flipScale = if (i < flipScales.size) flipScales[i].value else 1f
            val ch = if (i < displayChars.size) displayChars[i] else maskedWord[i]

            withTransform({
                scale(
                    scaleX = flipScale,
                    scaleY = 1f,
                    pivot = Offset(x + cellW / 2f, startY + cellH / 2f),
                )
            }) {
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
                val measured = textMeasurer.measure(ch.toString(), style)
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
