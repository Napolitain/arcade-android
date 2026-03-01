package com.napolitain.arcade.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.culdechouette.ComboType
import com.napolitain.arcade.logic.culdechouette.CulDeChouetteEngine
import com.napolitain.arcade.logic.culdechouette.GamePhase
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Gold = Color(0xFFFBBF24)
private val DiceWhite = Color.White
private val DotBlack = Color.Black
private val GlowRed = Color(0xFFEF4444)
private val FlashOrange = Color(0xFFFF8C00)
private val ReactionRed = Color(0xFFDC2626)

// ── Dice dot positions for values 1–6 ──────────────────────────────────
private fun dotPositions(value: Int): List<Pair<Float, Float>> = when (value) {
    1 -> listOf(0.5f to 0.5f)
    2 -> listOf(0.75f to 0.25f, 0.25f to 0.75f)
    3 -> listOf(0.75f to 0.25f, 0.5f to 0.5f, 0.25f to 0.75f)
    4 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f)
    5 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.5f to 0.5f, 0.25f to 0.75f, 0.75f to 0.75f)
    6 -> listOf(
        0.25f to 0.25f, 0.25f to 0.5f, 0.25f to 0.75f,
        0.75f to 0.25f, 0.75f to 0.5f, 0.75f to 0.75f,
    )
    else -> emptyList()
}

private fun DrawScope.drawDie(
    topLeft: Offset,
    dieSize: Float,
    value: Int,
    glow: Boolean,
    grayed: Boolean = false,
) {
    val bgColor = if (grayed) Color.LightGray else DiceWhite
    val dotColor = if (grayed) Color.Gray else DotBlack
    val corner = dieSize * 0.12f

    if (glow) {
        drawRoundRect(
            color = GlowRed.copy(alpha = 0.45f),
            topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
            size = Size(dieSize + 8.dp.toPx(), dieSize + 8.dp.toPx()),
            cornerRadius = CornerRadius(corner + 4.dp.toPx()),
        )
    }

    drawRoundRect(
        color = bgColor,
        topLeft = topLeft,
        size = Size(dieSize, dieSize),
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.15f),
        topLeft = topLeft,
        size = Size(dieSize, dieSize),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = 1.5.dp.toPx()),
    )

    if (value in 1..6) {
        val dotRadius = dieSize * 0.075f
        val padding = dieSize * 0.15f
        val inner = dieSize - 2 * padding
        for ((fx, fy) in dotPositions(value)) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(topLeft.x + padding + inner * fx, topLeft.y + padding + inner * fy),
            )
        }
    }
}

// ── Confetti particle ──────────────────────────────────────────────────
private data class Particle(var x: Float, var y: Float, val color: Color, val speed: Float, val size: Float)

// ── Main composable ────────────────────────────────────────────────────
@Composable
fun CulDeChouetteGame() {
    val engine = remember { CulDeChouetteEngine() }

    val phase = engine.phase
    val players = engine.players
    val dice = engine.dice
    val currentCombo = engine.currentCombo
    val currentPoints = engine.currentPoints
    val comboText = engine.comboText
    val gameOver = engine.gameOver
    val winnerIndex = engine.winnerIndex
    val currentPlayerIndex = engine.currentPlayerIndex

    // ── Dice roll animation state ──────────────────────────────────────
    var animDice by remember { mutableStateOf(intArrayOf(0, 0, 0)) }
    var rolling by remember { mutableStateOf(false) }
    var rollingIndices by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(rolling, rollingIndices) {
        if (!rolling) {
            animDice = dice
            return@LaunchedEffect
        }
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 500L) {
            animDice = IntArray(3) { i ->
                if (i in rollingIndices) Random.nextInt(1, 7) else dice[i]
            }
            delay(50)
        }
        rolling = false
        animDice = dice
    }

    // ── Dice bounce animation ──────────────────────────────────────────
    val bounceScale = remember { Animatable(1f) }
    LaunchedEffect(rolling) {
        if (!rolling) {
            bounceScale.snapTo(0.85f)
            bounceScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }

    // ── Combo text animation (scale + fade) ────────────────────────────
    var showComboAnim by remember { mutableStateOf(false) }
    val comboScale = remember { Animatable(0f) }
    val comboAlpha = remember { Animatable(0f) }

    LaunchedEffect(phase) {
        if (phase == GamePhase.SHOWING_CUL || phase == GamePhase.SHOWING_RESULT) {
            showComboAnim = true
            comboScale.snapTo(0.3f)
            comboAlpha.snapTo(0f)
            comboScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(showComboAnim) {
        if (showComboAnim) {
            comboAlpha.animateTo(1f, tween(300))
        }
    }

    // ── Points float-up ────────────────────────────────────────────────
    val pointsOffset = remember { Animatable(0f) }
    val pointsAlpha = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == GamePhase.SHOWING_RESULT && currentPoints != 0) {
            pointsOffset.snapTo(0f)
            pointsAlpha.snapTo(1f)
            pointsOffset.animateTo(-40f, tween(800))
            pointsAlpha.animateTo(0f, tween(800))
        }
    }

    // ── Reaction countdown ─────────────────────────────────────────────
    var reactionCountdown by remember { mutableLongStateOf(3000L) }
    var flashEdge by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase == GamePhase.REACTION_CHALLENGE) {
            reactionCountdown = 3000L
            flashEdge = true
            val start = System.currentTimeMillis()
            while (reactionCountdown > 0 && engine.phase == GamePhase.REACTION_CHALLENGE) {
                delay(50)
                reactionCountdown = (3000L - (System.currentTimeMillis() - start)).coerceAtLeast(0)
            }
            flashEdge = false
        } else {
            flashEdge = false
        }
    }
    val countdownFraction by animateFloatAsState(
        targetValue = if (phase == GamePhase.REACTION_CHALLENGE) reactionCountdown / 3000f else 0f,
        animationSpec = tween(50, easing = LinearEasing),
        label = "countdown",
    )

    // ── Pulse animation for Roll button ────────────────────────────────
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(phase) {
        if (phase == GamePhase.WAITING_TO_ROLL) {
            while (true) {
                pulseScale.animateTo(1.06f, tween(600))
                pulseScale.animateTo(1f, tween(600))
            }
        } else {
            pulseScale.snapTo(1f)
        }
    }

    // ── Reaction button pulse ──────────────────────────────────────────
    val reactionPulse = remember { Animatable(1f) }
    LaunchedEffect(phase) {
        if (phase == GamePhase.REACTION_CHALLENGE) {
            while (engine.phase == GamePhase.REACTION_CHALLENGE) {
                reactionPulse.animateTo(1.08f, tween(200))
                reactionPulse.animateTo(0.95f, tween(200))
            }
        } else {
            reactionPulse.snapTo(1f)
        }
    }

    // ── Game log ───────────────────────────────────────────────────────
    val gameLog = remember { mutableStateListOf<String>() }
    LaunchedEffect(phase) {
        if (phase == GamePhase.SHOWING_RESULT && comboText.isNotEmpty()) {
            val playerName = players.getOrNull(currentPlayerIndex)?.name ?: ""
            gameLog.add(0, "$playerName: $comboText")
            if (gameLog.size > 20) gameLog.removeAt(gameLog.lastIndex)
        }
    }

    // ── Confetti particles for GAME_OVER ───────────────────────────────
    val confettiColors = listOf(Gold, Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan)
    val particles = remember { mutableStateListOf<Particle>() }
    var confettiTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(gameOver) {
        if (!gameOver) { particles.clear(); return@LaunchedEffect }
        // Spawn particles
        for (i in 0 until 60) {
            particles.add(
                Particle(
                    x = Random.nextFloat(),
                    y = -Random.nextFloat() * 0.3f,
                    color = confettiColors.random(),
                    speed = 0.002f + Random.nextFloat() * 0.004f,
                    size = 4f + Random.nextFloat() * 6f,
                ),
            )
        }
        while (gameOver) {
            delay(32)
            for (p in particles) p.y += p.speed
            confettiTick++
        }
    }

    // ── AI turn automation ─────────────────────────────────────────────
    LaunchedEffect(phase) {
        if (phase == GamePhase.AI_TURN) {
            delay(700)
            // Roll chouettes visually
            rollingIndices = setOf(0, 1)
            rolling = true
            delay(550)
            // Roll cul
            rollingIndices = setOf(2)
            rolling = true
            delay(550)
            engine.processAiTurn()
            animDice = engine.dice
            showComboAnim = true
            comboScale.snapTo(0.3f)
            comboAlpha.snapTo(0f)
            comboScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            comboAlpha.animateTo(1f, tween(300))
            delay(900)
            engine.advancePhase()
        }
    }

    // ── Auto-advance from SHOWING_CUL → SHOWING_RESULT ────────────────
    LaunchedEffect(phase) {
        if (phase == GamePhase.SHOWING_CUL) {
            delay(800)
            engine.advancePhase()
        }
    }

    // ── Auto-advance from SHOWING_RESULT ───────────────────────────────
    LaunchedEffect(phase) {
        if (phase == GamePhase.SHOWING_RESULT) {
            delay(1500)
            engine.advancePhase()
        }
    }

    // ── Status text ────────────────────────────────────────────────────
    val statusText = when {
        gameOver && winnerIndex != null -> stringResource(R.string.cdc_winner, players[winnerIndex!!].name)
        phase == GamePhase.AI_TURN -> {
            val name = players.getOrNull(currentPlayerIndex)?.name ?: ""
            stringResource(R.string.cdc_ai_turn, name)
        }
        phase == GamePhase.REACTION_CHALLENGE -> stringResource(R.string.cdc_reaction)
        players.getOrNull(currentPlayerIndex)?.isHuman == true -> stringResource(R.string.cdc_your_turn)
        else -> ""
    }

    val scoreText = players.getOrNull(currentPlayerIndex)?.let { "${it.score}/343" }

    // ── Determine which dice glow (matching pair) ──────────────────────
    val glowIndices = remember(dice, currentCombo) {
        val set = mutableSetOf<Int>()
        if (currentCombo == ComboType.CUL_DE_CHOUETTE) {
            set.addAll(listOf(0, 1, 2))
        } else if (currentCombo == ComboType.CHOUETTE || currentCombo == ComboType.CHOUETTE_VELUTE) {
            if (dice[0] == dice[1]) { set.add(0); set.add(1) }
            if (dice[1] == dice[2]) { set.add(1); set.add(2) }
            if (dice[0] == dice[2]) { set.add(0); set.add(2) }
        }
        set
    }

    // ── UI ─────────────────────────────────────────────────────────────
    GameShell(
        title = stringResource(R.string.game_culdechouette),
        status = statusText,
        score = scoreText,
        onReset = {
            engine.reset()
            gameLog.clear()
            showComboAnim = false
            rolling = false
            animDice = intArrayOf(0, 0, 0)
            particles.clear()
        },
    ) {
        // Difficulty toggle
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // ── Scoreboard ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            players.forEachIndexed { idx, player ->
                val isCurrent = idx == currentPlayerIndex && !gameOver
                val isWinner = gameOver && idx == winnerIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isCurrent || isWinner) Modifier.border(
                                2.dp,
                                if (isWinner) Gold else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp),
                            ) else Modifier,
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${player.score}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isWinner) Gold else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Dice area ─────────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            // Flash edges for reaction challenge
            if (flashEdge) {
                val flashColor = if (currentCombo == ComboType.SUITE) FlashOrange else GlowRed
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(4.dp, flashColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .scale(bounceScale.value),
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val dieSize = (canvasH * 0.8f).coerceAtMost(canvasW / 4f)
                val gap = dieSize * 0.25f
                val totalW = 3 * dieSize + 2 * gap
                val startX = (canvasW - totalW) / 2f
                val startY = (canvasH - dieSize) / 2f

                for (i in 0..2) {
                    val showDie = when (phase) {
                        GamePhase.WAITING_TO_ROLL -> false
                        GamePhase.SHOWING_CHOUETTES -> i < 2
                        else -> true
                    }
                    val grayed = !showDie
                    val value = if (showDie) animDice.getOrElse(i) { 0 } else 0
                    val glow = showDie && i in glowIndices && (phase == GamePhase.SHOWING_CUL || phase == GamePhase.SHOWING_RESULT || phase == GamePhase.REACTION_CHALLENGE || phase == GamePhase.AI_TURN)

                    drawDie(
                        topLeft = Offset(startX + i * (dieSize + gap), startY),
                        dieSize = dieSize,
                        value = value,
                        glow = glow,
                        grayed = grayed,
                    )
                }
            }
        }

        // ── Combo announcement ────────────────────────────────────────
        if (showComboAnim && comboText.isNotEmpty() && phase != GamePhase.WAITING_TO_ROLL) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = comboText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Gold.copy(alpha = comboAlpha.value),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(comboScale.value),
                    )
                    if (currentPoints != 0 && pointsAlpha.value > 0.01f) {
                        val pointsText = if (currentPoints > 0) {
                            stringResource(R.string.cdc_points, currentPoints)
                        } else {
                            stringResource(R.string.cdc_suite_lose, -currentPoints)
                        }
                        Text(
                            text = pointsText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPoints > 0) Gold else GlowRed,
                            modifier = Modifier
                                .graphicsLayer {
                                    this.translationY = pointsOffset.value
                                    this.alpha = pointsAlpha.value
                                },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Action area ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                GamePhase.WAITING_TO_ROLL -> {
                    Button(
                        onClick = {
                            rollingIndices = setOf(0, 1)
                            rolling = true
                            showComboAnim = false
                            engine.rollChouettes()
                        },
                        modifier = Modifier.scale(pulseScale.value),
                    ) {
                        Text(
                            text = stringResource(R.string.cdc_roll),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                GamePhase.SHOWING_CHOUETTES -> {
                    Button(
                        onClick = {
                            rollingIndices = setOf(2)
                            rolling = true
                            engine.rollCul()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.cdc_roll_cul),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                GamePhase.REACTION_CHALLENGE -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Countdown circle
                        Canvas(modifier = Modifier.size(40.dp)) {
                            drawArc(
                                color = GlowRed,
                                startAngle = -90f,
                                sweepAngle = 360f * countdownFraction,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx()),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val phrase = if (currentCombo == ComboType.SUITE) {
                            stringResource(R.string.cdc_grelotte)
                        } else {
                            stringResource(R.string.cdc_pas_mou)
                        }
                        Button(
                            onClick = { engine.reactToChallenge() },
                            colors = ButtonDefaults.buttonColors(containerColor = ReactionRed),
                            modifier = Modifier.scale(reactionPulse.value),
                        ) {
                            Text(
                                text = phrase,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                GamePhase.SHOWING_RESULT -> {
                    if (engine.reactionTimeMs > 0) {
                        val winnerName = engine.reactionWinnerIndex?.let { players.getOrNull(it)?.name } ?: ""
                        Text(
                            text = "${engine.reactionTimeMs}ms — $winnerName",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                GamePhase.GAME_OVER -> {
                    val winnerName = winnerIndex?.let { players.getOrNull(it)?.name } ?: ""
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.cdc_winner, winnerName),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                        // Confetti overlay
                        @Suppress("UNUSED_EXPRESSION") confettiTick
                        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            for (p in particles) {
                                drawCircle(
                                    color = p.color,
                                    radius = p.size,
                                    center = Offset(p.x * size.width, p.y * size.height),
                                )
                            }
                        }
                    }
                }

                else -> { /* AI_TURN, SHOWING_CUL handled by LaunchedEffects */ }
            }
        }

        // ── Game log ──────────────────────────────────────────────────
        if (gameLog.isNotEmpty()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(8.dp),
            ) {
                items(gameLog) { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

