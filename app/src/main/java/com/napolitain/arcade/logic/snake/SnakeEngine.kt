package com.napolitain.arcade.logic.snake

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }

    val vector: Pair<Int, Int>
        get() = when (this) {
            UP -> 0 to -1
            DOWN -> 0 to 1
            LEFT -> -1 to 0
            RIGHT -> 1 to 0
        }
}

data class Point(val x: Int, val y: Int)

class SnakeEngine {

    companion object {
        const val GRID_SIZE = 16
        const val SPEED_MS = 140L
        private val INITIAL_SNAKE = listOf(Point(7, 8), Point(6, 8), Point(5, 8))
        private val INITIAL_DIRECTION = Direction.RIGHT
    }

    var snake by mutableStateOf(INITIAL_SNAKE)
        private set

    var direction by mutableStateOf(INITIAL_DIRECTION)
        private set

    var queuedDirection by mutableStateOf(INITIAL_DIRECTION)
        private set

    var food by mutableStateOf(createFood(INITIAL_SNAKE))
        private set

    var gameOver by mutableStateOf(false)
        private set

    var score by mutableIntStateOf(0)
        private set

    val isVictory: Boolean
        get() = snake.size == GRID_SIZE * GRID_SIZE

    val controlsLocked: Boolean
        get() = gameOver || isVictory

    val statusText: String
        get() = when {
            gameOver -> "Game over!"
            isVictory -> "You win!"
            else -> "Swipe or use buttons to move."
        }

    fun queueDirection(next: Direction) {
        if (controlsLocked) return
        if (queuedDirection.opposite == next || direction.opposite == next) return
        queuedDirection = next
    }

    fun reset() {
        snake = INITIAL_SNAKE
        direction = INITIAL_DIRECTION
        queuedDirection = INITIAL_DIRECTION
        food = createFood(INITIAL_SNAKE)
        gameOver = false
        score = 0
    }

    fun tick() {
        if (gameOver || isVictory) return

        val (dx, dy) = queuedDirection.vector
        val head = snake.first()
        val nextHead = Point(head.x + dx, head.y + dy)

        val hitWall = nextHead.x < 0 || nextHead.x >= GRID_SIZE ||
                nextHead.y < 0 || nextHead.y >= GRID_SIZE

        val willEat = nextHead == food
        val bodyToCheck = if (willEat) snake else snake.dropLast(1)
        val hitBody = bodyToCheck.any { it == nextHead }

        if (hitWall || hitBody) {
            gameOver = true
            return
        }

        val nextSnake = buildList {
            add(nextHead)
            addAll(snake)
            if (!willEat) removeAt(size - 1)
        }

        if (willEat) {
            food = createFood(nextSnake)
        }

        direction = queuedDirection
        snake = nextSnake
        score = nextSnake.size - INITIAL_SNAKE.size
    }

    /** Call from a LaunchedEffect coroutine to drive the game loop. */
    suspend fun runLoop() {
        while (!gameOver && !isVictory) {
            delay(SPEED_MS)
            tick()
        }
    }

    private fun createFood(snake: List<Point>): Point {
        val occupied = snake.toHashSet()
        val candidates = mutableListOf<Point>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val p = Point(x, y)
                if (p !in occupied) candidates.add(p)
            }
        }
        return if (candidates.isEmpty()) snake.first()
        else candidates.random()
    }
}
