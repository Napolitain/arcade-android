package com.napolitain.arcade.logic.tictactoe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.random.Random

enum class Mark { X, O }

class TicTacToeEngine {

    companion object {
        val WINNING_LINES: List<Triple<Int, Int, Int>> = listOf(
            Triple(0, 1, 2), Triple(3, 4, 5), Triple(6, 7, 8),
            Triple(0, 3, 6), Triple(1, 4, 7), Triple(2, 5, 8),
            Triple(0, 4, 8), Triple(2, 4, 6),
        )
        private val CORNER_INDICES = listOf(0, 2, 6, 8)
        private val SIDE_INDICES = listOf(1, 3, 5, 7)
    }

    val board = mutableStateListOf<Mark?>().apply { addAll(List(9) { null }) }
    var currentPlayer by mutableStateOf(Mark.X)
        private set
    var winner by mutableStateOf<Mark?>(null)
        private set
    var isDraw by mutableStateOf(false)
        private set
    var gameMode by mutableStateOf(GameMode.LOCAL)
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)
    var winningLine by mutableStateOf<Triple<Int, Int, Int>?>(null)
        private set
    var isAiTurn by mutableStateOf(false)
        private set

    // ── public API ──────────────────────────────────────────────

    fun makeMove(index: Int) {
        if (board[index] != null || winner != null || isDraw || isAiTurn) return
        applyMove(index, currentPlayer)
    }

    fun triggerAiMove() {
        if (!isAiTurn) return
        val move = getAiMove(board.toList(), difficulty) ?: return
        applyMove(move, Mark.O)
    }

    fun reset() {
        for (i in board.indices) board[i] = null
        currentPlayer = Mark.X
        winner = null
        isDraw = false
        winningLine = null
        isAiTurn = false
    }

    fun setMode(mode: GameMode) {
        if (mode == gameMode) return
        gameMode = mode
        reset()
    }

    // ── internals ───────────────────────────────────────────────

    private fun applyMove(index: Int, mark: Mark) {
        board[index] = mark
        val w = getWinner(board)
        if (w != null) {
            winner = w
            winningLine = findWinningLine(board)
            isAiTurn = false
            return
        }
        if (board.all { it != null }) {
            isDraw = true
            isAiTurn = false
            return
        }
        currentPlayer = if (mark == Mark.X) Mark.O else Mark.X
        isAiTurn = gameMode == GameMode.AI && currentPlayer == Mark.O
    }

    // ── win detection ───────────────────────────────────────────

    private fun getWinner(board: List<Mark?>): Mark? {
        for ((a, b, c) in WINNING_LINES) {
            val m = board[a]
            if (m != null && m == board[b] && m == board[c]) return m
        }
        return null
    }

    private fun findWinningLine(board: List<Mark?>): Triple<Int, Int, Int>? {
        for (line in WINNING_LINES) {
            val (a, b, c) = line
            val m = board[a]
            if (m != null && m == board[b] && m == board[c]) return line
        }
        return null
    }

    // ── AI helpers ──────────────────────────────────────────────

    private fun getWinningMove(board: List<Mark?>, mark: Mark): Int? {
        for (i in board.indices) {
            if (board[i] != null) continue
            val next = board.toMutableList()
            next[i] = mark
            if (getWinner(next) == mark) return i
        }
        return null
    }

    private fun getAvailableMoves(board: List<Mark?>): List<Int> =
        board.indices.filter { board[it] == null }

    private fun getRandomMove(board: List<Mark?>): Int? {
        val moves = getAvailableMoves(board)
        return if (moves.isEmpty()) null else moves[Random.nextInt(moves.size)]
    }

    private fun getNormalAiMove(board: List<Mark?>): Int? {
        getWinningMove(board, Mark.O)?.let { return it }
        getWinningMove(board, Mark.X)?.let { return it }
        if (board[4] == null) return 4
        CORNER_INDICES.firstOrNull { board[it] == null }?.let { return it }
        return SIDE_INDICES.firstOrNull { board[it] == null }
    }

    private fun getEasyAiMove(board: List<Mark?>): Int? {
        val randomMove = getRandomMove(board) ?: return null
        return if (Random.nextFloat() < 0.7f) randomMove else (getNormalAiMove(board) ?: randomMove)
    }

    private fun minimax(board: MutableList<Mark?>, isMaximizing: Boolean, depth: Int): Int {
        val w = getWinner(board)
        if (w == Mark.O) return 10 - depth
        if (w == Mark.X) return depth - 10
        val moves = getAvailableMoves(board)
        if (moves.isEmpty()) return 0

        if (isMaximizing) {
            var best = Int.MIN_VALUE
            for (m in moves) {
                board[m] = Mark.O
                val score = minimax(board, false, depth + 1)
                board[m] = null
                if (score > best) best = score
            }
            return best
        }

        var best = Int.MAX_VALUE
        for (m in moves) {
            board[m] = Mark.X
            val score = minimax(board, true, depth + 1)
            board[m] = null
            if (score < best) best = score
        }
        return best
    }

    private fun getHardAiMove(board: List<Mark?>): Int? {
        val moves = getAvailableMoves(board)
        if (moves.isEmpty()) return null
        var bestScore = Int.MIN_VALUE
        var bestMove = moves[0]
        for (m in moves) {
            val next = board.toMutableList()
            next[m] = Mark.O
            val score = minimax(next, false, 1)
            if (score > bestScore) {
                bestScore = score
                bestMove = m
            }
        }
        return bestMove
    }

    private fun getAiMove(board: List<Mark?>, difficulty: Difficulty): Int? = when (difficulty) {
        Difficulty.EASY -> getEasyAiMove(board)
        Difficulty.NORMAL -> getNormalAiMove(board)
        Difficulty.HARD -> getHardAiMove(board)
    }
}
