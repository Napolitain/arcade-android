package com.napolitain.arcade.logic.connectfour

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.math.floor
import kotlin.random.Random

enum class Disc { R, Y }

class ConnectFourEngine {

    companion object {
        const val ROWS = 6
        const val COLUMNS = 7
        const val WIN_LENGTH = 4
        const val HARD_SEARCH_DEPTH = 5

        private val DIRECTIONS = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(0, 1),
            intArrayOf(1, 1),
            intArrayOf(1, -1),
        )

        private val CENTER_PRIORITY_COLUMNS = intArrayOf(3, 2, 4, 1, 5, 0, 6)

        fun getCellIndex(row: Int, column: Int): Int = row * COLUMNS + column
    }

    var board by mutableStateOf(arrayOfNulls<Disc>(ROWS * COLUMNS))
        private set

    var isRedTurn by mutableStateOf(true)
        private set

    var lastDropIndex by mutableIntStateOf(-1)
        private set

    var mode by mutableStateOf(GameMode.LOCAL)
        private set

    var difficulty by mutableStateOf(Difficulty.NORMAL)
        private set

    val winner: Disc?
        get() = getWinner(board)

    val isDraw: Boolean
        get() = board.all { it != null } && winner == null

    val isAiTurn: Boolean
        get() = mode == GameMode.AI && !isRedTurn && winner == null && !isDraw

    val statusText: String
        get() {
            val w = winner
            return when {
                w != null -> "Winner: ${if (w == Disc.R) "Red" else "Yellow"}"
                isDraw -> "It's a draw!"
                isAiTurn -> "AI is thinking..."
                else -> "Turn: ${if (isRedTurn) "Red" else if (mode == GameMode.AI) "Yellow (AI)" else "Yellow"}"
            }
        }

    fun setGameMode(newMode: GameMode) {
        if (newMode == mode) return
        mode = newMode
        resetGame()
    }

    fun setGameDifficulty(newDifficulty: Difficulty) {
        difficulty = newDifficulty
    }

    fun resetGame() {
        board = arrayOfNulls(ROWS * COLUMNS)
        isRedTurn = true
        lastDropIndex = -1
    }

    fun dropDisc(column: Int) {
        if (winner != null || isDraw || isAiTurn || isColumnFull(column)) return

        val disc = if (isRedTurn) Disc.R else Disc.Y
        val result = simulateDrop(board, column, disc) ?: return

        board = result.first
        lastDropIndex = result.second
        isRedTurn = !isRedTurn
    }

    fun performAiMove() {
        if (!isAiTurn) return

        val aiColumn = getAiColumn(board, difficulty) ?: return
        val result = simulateDrop(board, aiColumn, Disc.Y) ?: return

        board = result.first
        lastDropIndex = result.second
        isRedTurn = true
    }

    fun isColumnFull(column: Int): Boolean = getDropRow(board, column) < 0

    // --- Pure game logic ---

    private fun getDropRow(board: Array<Disc?>, column: Int): Int {
        for (row in ROWS - 1 downTo 0) {
            if (board[getCellIndex(row, column)] == null) return row
        }
        return -1
    }

    private fun simulateDrop(
        board: Array<Disc?>,
        column: Int,
        disc: Disc,
    ): Pair<Array<Disc?>, Int>? {
        val targetRow = getDropRow(board, column)
        if (targetRow < 0) return null

        val targetIndex = getCellIndex(targetRow, column)
        val nextBoard = board.copyOf()
        nextBoard[targetIndex] = disc
        return Pair(nextBoard, targetIndex)
    }

    private fun getAvailableColumns(board: Array<Disc?>): List<Int> {
        return CENTER_PRIORITY_COLUMNS.filter { getDropRow(board, it) >= 0 }
    }

    private fun getWinner(board: Array<Disc?>): Disc? {
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS) {
                val currentDisc = board[getCellIndex(row, column)] ?: continue

                for (dir in DIRECTIONS) {
                    val rowStep = dir[0]
                    val columnStep = dir[1]
                    var matches = 1

                    while (matches < WIN_LENGTH) {
                        val nextRow = row + rowStep * matches
                        val nextColumn = column + columnStep * matches

                        if (nextRow < 0 || nextRow >= ROWS || nextColumn < 0 || nextColumn >= COLUMNS) break
                        if (board[getCellIndex(nextRow, nextColumn)] != currentDisc) break

                        matches++
                    }

                    if (matches == WIN_LENGTH) return currentDisc
                }
            }
        }
        return null
    }

    private fun getWinningColumn(board: Array<Disc?>, disc: Disc): Int? {
        for (column in 0 until COLUMNS) {
            val result = simulateDrop(board, column, disc) ?: continue
            if (getWinner(result.first) == disc) return column
        }
        return null
    }

    private fun getNormalAiColumn(board: Array<Disc?>): Int? {
        getWinningColumn(board, Disc.Y)?.let { return it }
        getWinningColumn(board, Disc.R)?.let { return it }

        val available = getAvailableColumns(board)
        return available.firstOrNull()
    }

    private fun getEasyAiColumn(board: Array<Disc?>): Int? {
        val available = getAvailableColumns(board)
        if (available.isEmpty()) return null

        val randomColumn = available[Random.nextInt(available.size)]

        return if (Random.nextFloat() < 0.75f) {
            randomColumn
        } else {
            getNormalAiColumn(board) ?: randomColumn
        }
    }

    private fun scoreWindow(window: Array<Disc?>): Int {
        var aiCount = 0
        var playerCount = 0
        var emptyCount = 0

        for (disc in window) {
            when (disc) {
                Disc.Y -> aiCount++
                Disc.R -> playerCount++
                null -> emptyCount++
            }
        }

        if (aiCount == 4) return 100_000
        if (playerCount == 4) return -100_000

        var score = 0
        if (aiCount == 3 && emptyCount == 1) {
            score += 120
        } else if (aiCount == 2 && emptyCount == 2) {
            score += 18
        }

        if (playerCount == 3 && emptyCount == 1) {
            score -= 110
        } else if (playerCount == 2 && emptyCount == 2) {
            score -= 14
        }

        return score
    }

    private fun scoreBoard(board: Array<Disc?>): Int {
        var score = 0
        val centerColumn = floor(COLUMNS / 2.0).toInt()

        for (row in 0 until ROWS) {
            if (board[getCellIndex(row, centerColumn)] == Disc.Y) {
                score += 9
            }
        }

        // Horizontal
        for (row in 0 until ROWS) {
            for (column in 0..COLUMNS - WIN_LENGTH) {
                val window = Array(WIN_LENGTH) { step -> board[getCellIndex(row, column + step)] }
                score += scoreWindow(window)
            }
        }

        // Vertical
        for (column in 0 until COLUMNS) {
            for (row in 0..ROWS - WIN_LENGTH) {
                val window = Array(WIN_LENGTH) { step -> board[getCellIndex(row + step, column)] }
                score += scoreWindow(window)
            }
        }

        // Diagonal down-right
        for (row in 0..ROWS - WIN_LENGTH) {
            for (column in 0..COLUMNS - WIN_LENGTH) {
                val window = Array(WIN_LENGTH) { step -> board[getCellIndex(row + step, column + step)] }
                score += scoreWindow(window)
            }
        }

        // Diagonal down-left
        for (row in 0..ROWS - WIN_LENGTH) {
            for (column in WIN_LENGTH - 1 until COLUMNS) {
                val window = Array(WIN_LENGTH) { step -> board[getCellIndex(row + step, column - step)] }
                score += scoreWindow(window)
            }
        }

        return score
    }

    private fun minimax(
        board: Array<Disc?>,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
    ): Int {
        val w = getWinner(board)
        if (w == Disc.Y) return 1_000_000 + depth
        if (w == Disc.R) return -1_000_000 - depth

        val availableColumns = getAvailableColumns(board)
        if (depth == 0 || availableColumns.isEmpty()) return scoreBoard(board)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var value = Int.MIN_VALUE

            for (column in availableColumns) {
                val result = simulateDrop(board, column, Disc.Y) ?: continue
                val score = minimax(result.first, depth - 1, currentAlpha, currentBeta, false)
                if (score > value) value = score
                if (value > currentAlpha) currentAlpha = value
                if (currentAlpha >= currentBeta) break
            }

            return value
        }

        var value = Int.MAX_VALUE

        for (column in availableColumns) {
            val result = simulateDrop(board, column, Disc.R) ?: continue
            val score = minimax(result.first, depth - 1, currentAlpha, currentBeta, true)
            if (score < value) value = score
            if (value < currentBeta) currentBeta = value
            if (currentAlpha >= currentBeta) break
        }

        return value
    }

    private fun getHardAiColumn(board: Array<Disc?>): Int? {
        val availableColumns = getAvailableColumns(board)
        if (availableColumns.isEmpty()) return null

        getWinningColumn(board, Disc.Y)?.let { return it }
        getWinningColumn(board, Disc.R)?.let { return it }

        var bestScore = Int.MIN_VALUE
        var bestColumn: Int? = availableColumns.firstOrNull()

        for (column in availableColumns) {
            val result = simulateDrop(board, column, Disc.Y) ?: continue
            val score = minimax(result.first, HARD_SEARCH_DEPTH - 1, Int.MIN_VALUE, Int.MAX_VALUE, false)
            if (score > bestScore) {
                bestScore = score
                bestColumn = column
            }
        }

        return bestColumn
    }

    private fun getAiColumn(board: Array<Disc?>, difficulty: Difficulty): Int? {
        return when (difficulty) {
            Difficulty.EASY -> getEasyAiColumn(board)
            Difficulty.HARD -> getHardAiColumn(board)
            Difficulty.NORMAL -> getNormalAiColumn(board)
        }
    }
}
