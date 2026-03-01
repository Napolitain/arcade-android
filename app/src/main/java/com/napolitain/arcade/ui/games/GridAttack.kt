package com.napolitain.arcade.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.gridattack.CELL_COUNT
import com.napolitain.arcade.logic.gridattack.GRID_SIZE
import com.napolitain.arcade.logic.gridattack.GridAttackEngine
import com.napolitain.arcade.logic.gridattack.SHIP_SIZES
import com.napolitain.arcade.logic.gridattack.ShotResult
import com.napolitain.arcade.logic.gridattack.Turn
import com.napolitain.arcade.ui.components.GameDifficultyToggle
import com.napolitain.arcade.ui.components.GameShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/* ── colors ────────────────────────────────────────────────────────── */

private val HitBg = Color(0x38F87171)
private val HitBorder = Color(0xFFFDA4AF)
private val HitCross = Color(0xFFFECACA)
private val MissBg = Color(0x3338BDF8)
private val MissBorder = Color(0xFF67E8F9)
private val MissDot = Color(0xFF67E8F9)
private val DefaultBg = Color(0xB31E293B)
private val DefaultBorder = Color(0xFF334155)
private val ShipFill = Color(0x596EE7B3)
private val ShipStroke = Color(0xFF6EE7B3)
private val SunkRing = Color(0xFFFACC15)

/* ── cell drawing ──────────────────────────────────────────────────── */

private fun DrawScope.drawCell(
    shot: ShotResult?,
    hasShip: Boolean,
    showShip: Boolean,
    isSunk: Boolean,
) {
    val w = size.width
    val h = size.height
    val pad = w * 0.04f
    val rect = Size(w - pad * 2, h - pad * 2)
    val cr = CornerRadius(w * 0.20f)

    val bg = when (shot) {
        ShotResult.HIT -> HitBg
        ShotResult.MISS -> MissBg
        null -> DefaultBg
    }
    val border = when (shot) {
        ShotResult.HIT -> HitBorder
        ShotResult.MISS -> MissBorder
        null -> DefaultBorder
    }

    drawRoundRect(bg, Offset(pad, pad), rect, cr)
    drawRoundRect(border, Offset(pad, pad), rect, cr, style = Stroke(w * 0.06f))

    if (showShip && hasShip) {
        val inner = w * 0.24f
        val innerSize = Size(w - inner * 2, h - inner * 2)
        drawRoundRect(ShipFill, Offset(inner, inner), innerSize, CornerRadius(w * 0.14f))
        drawRoundRect(ShipStroke, Offset(inner, inner), innerSize, CornerRadius(w * 0.14f), style = Stroke(w * 0.05f))
    }

    if (shot == ShotResult.MISS) {
        drawCircle(MissDot, radius = w * 0.11f, center = Offset(w / 2, h / 2))
    }

    if (shot == ShotResult.HIT) {
        val s = w * 0.30f
        val e = w * 0.70f
        val sw = w * 0.10f
        drawLine(HitCross, Offset(s, s), Offset(e, e), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(HitCross, Offset(e, s), Offset(s, e), strokeWidth = sw, cap = StrokeCap.Round)
    }

    if (isSunk) {
        drawCircle(
            SunkRing,
            radius = w * 0.42f,
            center = Offset(w / 2, h / 2),
            style = Stroke(
                width = w * 0.06f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f)),
            ),
        )
    }
}

/* ── animation overlays ────────────────────────────────────────────── */

private fun DrawScope.drawHitExplosion(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val w = size.width
    val center = Offset(w / 2, w / 2)
    val fade = 1f - progress
    val maxR = w * 0.5f
    drawCircle(Color(0xFFFF6B00).copy(alpha = fade * 0.5f), maxR * progress, center)
    drawCircle(Color(0xFFFF4500).copy(alpha = fade * 0.7f), maxR * progress * 0.65f, center)
    drawCircle(Color(0xFFDC2626).copy(alpha = fade), maxR * progress * 0.35f, center)
}

private fun DrawScope.drawMissSplash(ring1: Float, ring2: Float) {
    val w = size.width
    val center = Offset(w / 2, w / 2)
    val maxR = w * 0.45f
    if (ring1 > 0f && ring1 < 1f) {
        drawCircle(
            Color(0xFF38BDF8).copy(alpha = (1f - ring1) * 0.8f),
            maxR * ring1, center, style = Stroke(w * 0.06f),
        )
    }
    if (ring2 > 0f && ring2 < 1f) {
        drawCircle(
            Color(0xFF7DD3FC).copy(alpha = (1f - ring2) * 0.6f),
            maxR * ring2 * 0.75f, center, style = Stroke(w * 0.04f),
        )
    }
}

private fun DrawScope.drawSunkPulse(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val w = size.width
    val pad = w * 0.04f
    val alpha = abs(sin(progress * 2.0 * PI)).toFloat() * 0.5f
    drawRoundRect(
        Color(0xFFFACC15).copy(alpha = alpha),
        Offset(pad, pad),
        Size(w - pad * 2, w - pad * 2),
        CornerRadius(w * 0.20f),
    )
}

/* ── grid composable ───────────────────────────────────────────────── */

@Composable
private fun BattleGrid(
    title: String,
    subtitle: String,
    shots: Map<Int, ShotResult>,
    shipCells: Set<Int>,
    sunkCells: Set<Int>,
    showShips: Boolean,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEnemyGrid: Boolean = false,
    showScanLine: Boolean = false,
) {
    val titleColor = if (showShips || title.contains("Your", ignoreCase = true))
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    /* ── crosshair hover state ── */
    var hoverCell by remember { mutableStateOf<Int?>(null) }
    val currentShots by rememberUpdatedState(shots)
    val infiniteTransition = rememberInfiniteTransition(label = "crosshair")
    val crosshairPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crosshairPulse",
    )
    LaunchedEffect(enabled) { if (!enabled) hoverCell = null }

    /* ── CPU scanning line ── */
    val scanAnim = remember { Animatable(0f) }
    LaunchedEffect(showScanLine) {
        if (showScanLine) {
            scanAnim.snapTo(0f)
            scanAnim.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }

    val spacingDp = 3.dp

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Grid with animation overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .then(
                    if (isEnemyGrid && enabled) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val pos = change.position
                                    val sp = spacingDp.toPx()
                                    val cellW = (size.width - (GRID_SIZE - 1) * sp) / GRID_SIZE
                                    val step = cellW + sp
                                    val col = (pos.x / step).toInt().coerceIn(0, GRID_SIZE - 1)
                                    val row = (pos.y / step).toInt().coerceIn(0, GRID_SIZE - 1)
                                    val idx = row * GRID_SIZE + col
                                    hoverCell = if (currentShots[idx] == null) idx else null
                                } else {
                                    hoverCell = null
                                }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacingDp),
            ) {
                for (row in 0 until GRID_SIZE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                    ) {
                        for (col in 0 until GRID_SIZE) {
                            val index = row * GRID_SIZE + col
                            val shot = shots[index]
                            val hasShip = index in shipCells
                            val isSunk = index in sunkCells
                            val isTargeted = shot != null

                            val animatedBg by animateColorAsState(
                                targetValue = when (shot) {
                                    ShotResult.HIT -> HitBorder
                                    ShotResult.MISS -> MissBorder
                                    null -> Color.Transparent
                                },
                                animationSpec = tween(300),
                                label = "cell-$index",
                            )

                            /* per-cell animations */
                            val hitAnim = remember { Animatable(0f) }
                            val missRing1 = remember { Animatable(0f) }
                            val missRing2 = remember { Animatable(0f) }
                            val sunkAnim = remember { Animatable(0f) }

                            LaunchedEffect(shot) {
                                when (shot) {
                                    ShotResult.HIT -> {
                                        hitAnim.snapTo(0f)
                                        hitAnim.animateTo(1f, tween(500))
                                    }
                                    ShotResult.MISS -> {
                                        missRing1.snapTo(0f)
                                        missRing2.snapTo(0f)
                                        launch { missRing1.animateTo(1f, tween(400)) }
                                        launch {
                                            delay(100)
                                            missRing2.animateTo(1f, tween(400))
                                        }
                                    }
                                    null -> {}
                                }
                            }

                            LaunchedEffect(isSunk) {
                                if (isSunk) {
                                    sunkAnim.snapTo(0f)
                                    sunkAnim.animateTo(1f, tween(800))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .then(
                                        if (enabled && !isTargeted)
                                            Modifier.clickable { onCellTap(index) }
                                        else Modifier,
                                    ),
                            ) {
                                // animatedBg used to trigger recomposition on state changes
                                @Suppress("UNUSED_EXPRESSION")
                                animatedBg

                                Canvas(modifier = Modifier.matchParentSize()) {
                                    drawCell(shot, hasShip, showShips, isSunk)
                                    drawHitExplosion(hitAnim.value)
                                    drawMissSplash(missRing1.value, missRing2.value)
                                    drawSunkPulse(sunkAnim.value)
                                }
                            }
                        }
                    }
                }
            }

            /* grid overlay: crosshair + scanning line */
            Canvas(modifier = Modifier.matchParentSize()) {
                val hc = hoverCell
                if (hc != null && isEnemyGrid && enabled) {
                    val spacingPx = spacingDp.toPx()
                    val cellW = (size.width - (GRID_SIZE - 1) * spacingPx) / GRID_SIZE
                    val step = cellW + spacingPx
                    val cx = (hc % GRID_SIZE) * step + cellW / 2
                    val cy = (hc / GRID_SIZE) * step + cellW / 2
                    val p = crosshairPulse
                    drawCircle(
                        Color.White.copy(alpha = 0.35f * p),
                        cellW * 0.38f * p, Offset(cx, cy),
                        style = Stroke(cellW * 0.05f),
                    )
                    val half = cellW * 0.28f
                    val la = 0.3f * p
                    drawLine(Color.White.copy(alpha = la), Offset(cx - half, cy), Offset(cx + half, cy), cellW * 0.03f)
                    drawLine(Color.White.copy(alpha = la), Offset(cx, cy - half), Offset(cx, cy + half), cellW * 0.03f)
                }

                val scanProg = scanAnim.value
                if (scanProg > 0f && scanProg < 1f && showScanLine) {
                    val y = size.height * scanProg
                    val la = 0.7f * (1f - scanProg)
                    val glowH = 6.dp.toPx()
                    drawLine(
                        Color(0xFF4ADE80).copy(alpha = la),
                        Offset(0f, y), Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawRect(
                        Color(0xFF4ADE80).copy(alpha = la * 0.3f),
                        Offset(0f, (y - glowH / 2).coerceAtLeast(0f)),
                        Size(size.width, glowH),
                    )
                }
            }
        }
    }
}

/* ── stat row ──────────────────────────────────────────────────────── */

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/* ── legend ─────────────────────────────────────────────────────────── */

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(14.dp)) {
            drawCircle(color, radius = size.minDimension / 2)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ── main composable ───────────────────────────────────────────────── */

@Composable
fun GridAttackGame() {
    val engine = remember { GridAttackEngine() }

    // CPU turn automation
    LaunchedEffect(engine.turn, engine.winner) {
        if (engine.turn == Turn.CPU && engine.winner == null) {
            delay(engine.cpuDelayMs)
            engine.executeCpuTurn()
        }
    }

    GameShell(
        title = stringResource(R.string.game_gridattack),
        status = engine.statusText,
        score = engine.playerWins.toString(),
        onReset = { engine.reset() },
    ) {
        GameDifficultyToggle(
            difficulty = engine.difficulty,
            onDifficultyChange = { engine.difficulty = it },
        )

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(ShipStroke, stringResource(R.string.ga_ship))
            LegendItem(HitBorder, stringResource(R.string.ga_hit))
            LegendItem(MissDot, stringResource(R.string.ga_miss))
            LegendItem(SunkRing, stringResource(R.string.ga_sunk))
        }

        // Boards
        val revealEnemy = engine.winner != null

        BattleGrid(
            title = stringResource(R.string.ga_your_fleet),
            subtitle = stringResource(R.string.ga_your_fleet_desc),
            shots = engine.cpuShots,
            shipCells = engine.playerShipCells,
            sunkCells = engine.playerSunkCells,
            showShips = true,
            enabled = false,
            onCellTap = {},
            showScanLine = engine.turn == Turn.CPU && engine.winner == null,
        )

        BattleGrid(
            title = stringResource(R.string.ga_enemy_waters),
            subtitle = stringResource(R.string.ga_enemy_waters_desc),
            shots = engine.playerShots,
            shipCells = engine.enemyShipCells,
            sunkCells = engine.enemySunkCells,
            showShips = revealEnemy,
            enabled = engine.canTargetEnemy,
            onCellTap = { engine.attackEnemy(it) },
            isEnemyGrid = true,
        )

        // Stats
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatItem(stringResource(R.string.ga_your_hits), "${engine.playerHits} / ${engine.playerMisses}", Modifier.weight(1f))
                StatItem(stringResource(R.string.ga_cpu_hits), "${engine.cpuHits} / ${engine.cpuMisses}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatItem(stringResource(R.string.ga_enemy_sunk), "${engine.enemyShipsSunk} / ${SHIP_SIZES.size}", Modifier.weight(1f))
                StatItem(stringResource(R.string.ga_your_sunk), "${engine.playerShipsSunk} / ${SHIP_SIZES.size}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatItem(stringResource(R.string.ga_enemy_cells), "${CELL_COUNT - engine.playerShots.size}", Modifier.weight(1f))
                StatItem(stringResource(R.string.ga_your_cells), "${CELL_COUNT - engine.cpuShots.size}", Modifier.weight(1f))
            }
        }
    }
}
