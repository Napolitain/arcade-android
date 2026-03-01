package com.napolitain.arcade.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.snake.Direction
import com.napolitain.arcade.logic.snake.SnakeEngine
import com.napolitain.arcade.ui.components.GameShell
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun SnakeGame() {
    val engine = remember { SnakeEngine() }

    // Game loop
    LaunchedEffect(engine.gameOver, engine.isVictory) {
        if (!engine.gameOver && !engine.isVictory) {
            engine.runLoop()
        }
    }

    val snakeHeadColor = Color(0xFF6EE7B7)
    val snakeBodyColor = Color(0xFF10B981)
    val foodColor = Color(0xFFFB7185)
    val cellBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    // Food pulse animation
    val foodTransition = rememberInfiniteTransition(label = "food")
    val foodPulse = foodTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.46f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "foodPulse",
    ).value

    // Smooth head interpolation
    val headAnimX = remember { Animatable(engine.snake.first().x.toFloat()) }
    val headAnimY = remember { Animatable(engine.snake.first().y.toFloat()) }
    LaunchedEffect(engine.snake.first().x, engine.snake.first().y) {
        val head = engine.snake.first()
        val dx = abs(headAnimX.value - head.x.toFloat())
        val dy = abs(headAnimY.value - head.y.toFloat())
        if (dx > 2f || dy > 2f) {
            headAnimX.snapTo(head.x.toFloat())
            headAnimY.snapTo(head.y.toFloat())
        } else {
            launch { headAnimX.animateTo(head.x.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)) }
            launch { headAnimY.animateTo(head.y.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)) }
        }
    }

    // Flash/ripple when food is eaten
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(engine.score) {
        if (engine.score > 0) {
            flashAlpha.snapTo(0.7f)
            flashAlpha.animateTo(0f, tween(350))
        }
    }

    GameShell(
        title = stringResource(R.string.game_snake),
        status = engine.statusText,
        score = engine.score.toString(),
        onReset = { engine.reset() },
    ) {
        // Game board with swipe detection
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(engine.controlsLocked) {
                    detectDragGestures { _, dragAmount ->
                        if (engine.controlsLocked) return@detectDragGestures
                        val (dx, dy) = dragAmount
                        if (abs(dx) > abs(dy)) {
                            engine.queueDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                        } else {
                            engine.queueDirection(if (dy > 0) Direction.DOWN else Direction.UP)
                        }
                    }
                },
        ) {
            val gridSize = SnakeEngine.GRID_SIZE
            val gap = size.width * 0.006f
            val totalGap = gap * (gridSize + 1)
            val cellSize = (size.width - totalGap) / gridSize
            val cornerRadius = CornerRadius(cellSize * 0.15f)

            val snakeSet = HashMap<Long, Int>(engine.snake.size)
            engine.snake.forEachIndexed { index, p ->
                snakeSet[p.x.toLong() shl 32 or p.y.toLong()] = index
            }

            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val left = gap + x * (cellSize + gap)
                    val top = gap + y * (cellSize + gap)

                    // Cell background
                    drawRoundRect(
                        color = cellBgColor,
                        topLeft = Offset(left, top),
                        size = Size(cellSize, cellSize),
                        cornerRadius = cornerRadius,
                    )

                    val key = x.toLong() shl 32 or y.toLong()
                    val snakeIndex = snakeSet[key]

                    when {
                        snakeIndex == 0 -> { /* head drawn separately with interpolation */ }
                        snakeIndex != null -> {
                            // Snake body with tapered segments
                            val t = snakeIndex.toFloat() / engine.snake.size.coerceAtLeast(1)
                            val shrink = cellSize * 0.12f * t
                            drawRoundRect(
                                color = snakeBodyColor,
                                topLeft = Offset(left + shrink, top + shrink),
                                size = Size(cellSize - shrink * 2, cellSize - shrink * 2),
                                cornerRadius = CornerRadius(cellSize * 0.3f),
                            )
                        }
                        engine.food.x == x && engine.food.y == y -> {
                            // Food with pulse animation
                            drawCircle(
                                color = foodColor,
                                radius = cellSize * foodPulse,
                                center = Offset(left + cellSize / 2f, top + cellSize / 2f),
                            )
                            // Highlight
                            drawCircle(
                                color = Color(0xFFFECDD3).copy(alpha = 0.6f),
                                radius = cellSize * 0.16f,
                                center = Offset(left + cellSize * 0.38f, top + cellSize * 0.38f),
                            )
                        }
                    }
                }
            }

            // Smoothly interpolated snake head
            val headLeft = gap + headAnimX.value * (cellSize + gap)
            val headTop = gap + headAnimY.value * (cellSize + gap)
            drawRoundRect(
                color = snakeHeadColor,
                topLeft = Offset(headLeft, headTop),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(cellSize * 0.35f),
            )
            // Eyes
            val eyeRadius = cellSize * 0.08f
            val cx = headLeft + cellSize / 2f
            val cy = headTop + cellSize / 2f
            val eyeOffset = cellSize * 0.2f
            when (engine.direction) {
                Direction.RIGHT -> {
                    drawCircle(Color.Black, eyeRadius, Offset(cx + eyeOffset, cy - eyeOffset))
                    drawCircle(Color.Black, eyeRadius, Offset(cx + eyeOffset, cy + eyeOffset))
                }
                Direction.LEFT -> {
                    drawCircle(Color.Black, eyeRadius, Offset(cx - eyeOffset, cy - eyeOffset))
                    drawCircle(Color.Black, eyeRadius, Offset(cx - eyeOffset, cy + eyeOffset))
                }
                Direction.UP -> {
                    drawCircle(Color.Black, eyeRadius, Offset(cx - eyeOffset, cy - eyeOffset))
                    drawCircle(Color.Black, eyeRadius, Offset(cx + eyeOffset, cy - eyeOffset))
                }
                Direction.DOWN -> {
                    drawCircle(Color.Black, eyeRadius, Offset(cx - eyeOffset, cy + eyeOffset))
                    drawCircle(Color.Black, eyeRadius, Offset(cx + eyeOffset, cy + eyeOffset))
                }
            }

            // Flash ripple when food is eaten
            if (flashAlpha.value > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = flashAlpha.value),
                    radius = cellSize * 1.2f,
                    center = Offset(headLeft + cellSize / 2f, headTop + cellSize / 2f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // D-pad controls
        DPad(
            currentDirection = engine.queuedDirection,
            enabled = !engine.controlsLocked,
            onDirection = { engine.queueDirection(it) },
        )
    }
}

@Composable
private fun DPad(
    currentDirection: Direction,
    enabled: Boolean,
    onDirection: (Direction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Up
        DPadButton(
            icon = Icons.Filled.KeyboardArrowUp,
            label = stringResource(R.string.dir_up),
            direction = Direction.UP,
            currentDirection = currentDirection,
            enabled = enabled && currentDirection.opposite != Direction.UP,
            onClick = { onDirection(Direction.UP) },
        )
        // Left, Down, Right
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DPadButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = stringResource(R.string.dir_left),
                direction = Direction.LEFT,
                currentDirection = currentDirection,
                enabled = enabled && currentDirection.opposite != Direction.LEFT,
                onClick = { onDirection(Direction.LEFT) },
            )
            DPadButton(
                icon = Icons.Filled.KeyboardArrowDown,
                label = stringResource(R.string.dir_down),
                direction = Direction.DOWN,
                currentDirection = currentDirection,
                enabled = enabled && currentDirection.opposite != Direction.DOWN,
                onClick = { onDirection(Direction.DOWN) },
            )
            DPadButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = stringResource(R.string.dir_right),
                direction = Direction.RIGHT,
                currentDirection = currentDirection,
                enabled = enabled && currentDirection.opposite != Direction.RIGHT,
                onClick = { onDirection(Direction.RIGHT) },
            )
        }
    }
}

@Composable
private fun DPadButton(
    icon: ImageVector,
    label: String,
    direction: Direction,
    currentDirection: Direction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = stringResource(R.string.move_label, label))
        }
    }
}
