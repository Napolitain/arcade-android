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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.logic.snake.Direction
import com.napolitain.arcade.logic.snake.SnakeEngine
import com.napolitain.arcade.ui.components.GameShell
import kotlin.math.abs

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
                        snakeIndex == 0 -> {
                            // Snake head
                            drawRoundRect(
                                color = snakeHeadColor,
                                topLeft = Offset(left, top),
                                size = Size(cellSize, cellSize),
                                cornerRadius = CornerRadius(cellSize * 0.35f),
                            )
                            // Eyes
                            val eyeRadius = cellSize * 0.08f
                            val cx = left + cellSize / 2f
                            val cy = top + cellSize / 2f
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
                        }
                        snakeIndex != null -> {
                            // Snake body
                            drawRoundRect(
                                color = snakeBodyColor,
                                topLeft = Offset(left, top),
                                size = Size(cellSize, cellSize),
                                cornerRadius = CornerRadius(cellSize * 0.3f),
                            )
                        }
                        engine.food.x == x && engine.food.y == y -> {
                            // Food (circle)
                            drawCircle(
                                color = foodColor,
                                radius = cellSize * 0.42f,
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
