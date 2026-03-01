package com.napolitain.arcade.logic.game2048

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Direction { LEFT, RIGHT, UP, DOWN }

private const val GRID_SIZE = 4
private const val TOTAL_CELLS = GRID_SIZE * GRID_SIZE

class Game2048Engine {

    var grid by mutableStateOf(IntArray(TOTAL_CELLS))
        private set

    var score by mutableIntStateOf(0)
        private set

    var gameOver by mutableStateOf(false)
        private set

    var won by mutableStateOf(false)
        private set

    init {
        reset()
    }

    fun reset() {
        grid = addRandomTile(addRandomTile(IntArray(TOTAL_CELLS)))
        score = 0
        gameOver = false
        won = false
    }

    /** Attempt a move. Returns true if the grid changed. */
    fun move(direction: Direction): Boolean {
        if (gameOver) return false

        val result = moveGrid(grid, direction)
        if (!result.moved) return false

        val nextGrid = addRandomTile(result.grid)
        grid = nextGrid
        score += result.scoreDelta

        if (!won && nextGrid.any { it == 2048 }) {
            won = true
        }
        if (!hasMoves(nextGrid)) {
            gameOver = true
        }
        return true
    }

    // ── Line positions for each direction ──────────────────────────────

    private fun getLinePositions(lineIndex: Int, direction: Direction): IntArray =
        when (direction) {
            Direction.LEFT ->
                IntArray(GRID_SIZE) { col -> lineIndex * GRID_SIZE + col }

            Direction.RIGHT ->
                IntArray(GRID_SIZE) { col -> lineIndex * GRID_SIZE + (GRID_SIZE - 1 - col) }

            Direction.UP ->
                IntArray(GRID_SIZE) { row -> row * GRID_SIZE + lineIndex }

            Direction.DOWN ->
                IntArray(GRID_SIZE) { row -> (GRID_SIZE - 1 - row) * GRID_SIZE + lineIndex }
        }

    // ── Slide & merge a single line ────────────────────────────────────

    private data class LineMergeResult(
        val line: IntArray,
        val scoreDelta: Int,
    )

    private fun slideAndMergeLine(line: IntArray): LineMergeResult {
        // Compact non-zero values
        val compacted = line.filter { it != 0 }.toMutableList()
        val merged = mutableListOf<Int>()
        var scoreDelta = 0
        var i = 0

        while (i < compacted.size) {
            if (i + 1 < compacted.size && compacted[i] == compacted[i + 1]) {
                val mergedValue = compacted[i] * 2
                merged.add(mergedValue)
                scoreDelta += mergedValue
                i += 2
            } else {
                merged.add(compacted[i])
                i++
            }
        }

        // Pad with zeros
        while (merged.size < GRID_SIZE) merged.add(0)

        return LineMergeResult(merged.toIntArray(), scoreDelta)
    }

    // ── Move the entire grid ───────────────────────────────────────────

    private data class MoveResult(
        val grid: IntArray,
        val moved: Boolean,
        val scoreDelta: Int,
    )

    private fun moveGrid(grid: IntArray, direction: Direction): MoveResult {
        val nextGrid = grid.copyOf()
        var moved = false
        var scoreDelta = 0

        for (lineIndex in 0 until GRID_SIZE) {
            val positions = getLinePositions(lineIndex, direction)
            val currentLine = IntArray(GRID_SIZE) { grid[positions[it]] }
            val result = slideAndMergeLine(currentLine)

            scoreDelta += result.scoreDelta

            result.line.forEachIndexed { idx, value ->
                nextGrid[positions[idx]] = value
            }

            if (!moved && !result.line.contentEquals(currentLine)) {
                moved = true
            }
        }

        return MoveResult(nextGrid, moved, scoreDelta)
    }

    // ── Random tile spawning ───────────────────────────────────────────

    private fun addRandomTile(grid: IntArray): IntArray {
        val emptyPositions = grid.indices.filter { grid[it] == 0 }
        if (emptyPositions.isEmpty()) return grid

        val nextGrid = grid.copyOf()
        val pos = emptyPositions[Random.nextInt(emptyPositions.size)]
        nextGrid[pos] = if (Random.nextFloat() < 0.9f) 2 else 4
        return nextGrid
    }

    // ── Game-over detection ────────────────────────────────────────────

    private fun hasMoves(grid: IntArray): Boolean {
        if (grid.any { it == 0 }) return true

        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val index = row * GRID_SIZE + col
                val current = grid[index]
                if (col + 1 < GRID_SIZE && grid[index + 1] == current) return true
                if (row + 1 < GRID_SIZE && grid[index + GRID_SIZE] == current) return true
            }
        }
        return false
    }

    companion object {
        const val SIZE = GRID_SIZE
    }
}
