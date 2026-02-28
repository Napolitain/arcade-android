package com.napolitain.arcade.logic.reversi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.random.Random

enum class Disc { B, W }

class ReversiEngine {

    companion object {
        const val BOARD_SIZE = 8
        const val TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE

        private val DIRECTIONS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
            intArrayOf(0, -1), intArrayOf(0, 1),
            intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
        )

        fun getCellIndex(row: Int, column: Int): Int = row * BOARD_SIZE + column

        private fun isInsideBoard(row: Int, column: Int): Boolean =
            row in 0 until BOARD_SIZE && column in 0 until BOARD_SIZE

        private fun getOpponent(player: Disc): Disc =
            if (player == Disc.B) Disc.W else Disc.B

        fun getPlayerLabel(player: Disc): String =
            if (player == Disc.B) "Black" else "White"

        private fun createInitialBoard(): Array<Disc?> {
            val board = arrayOfNulls<Disc>(TOTAL_CELLS)
            val mid = BOARD_SIZE / 2
            board[getCellIndex(mid - 1, mid - 1)] = Disc.W
            board[getCellIndex(mid - 1, mid)] = Disc.B
            board[getCellIndex(mid, mid - 1)] = Disc.B
            board[getCellIndex(mid, mid)] = Disc.W
            return board
        }

        private fun getCapturedDiscs(
            board: Array<Disc?>,
            row: Int,
            column: Int,
            player: Disc,
        ): List<Int> {
            if (board[getCellIndex(row, column)] != null) return emptyList()

            val opponent = getOpponent(player)
            val captured = mutableListOf<Int>()

            for (dir in DIRECTIONS) {
                val rowStep = dir[0]
                val colStep = dir[1]
                val line = mutableListOf<Int>()
                var nr = row + rowStep
                var nc = column + colStep

                while (isInsideBoard(nr, nc)) {
                    val idx = getCellIndex(nr, nc)
                    when (board[idx]) {
                        opponent -> {
                            line.add(idx)
                            nr += rowStep
                            nc += colStep
                        }
                        player -> {
                            if (line.isNotEmpty()) captured.addAll(line)
                            break
                        }
                        else -> break
                    }
                }
            }
            return captured
        }

        private fun getLegalMoves(board: Array<Disc?>, player: Disc): List<Int> {
            val moves = mutableListOf<Int>()
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    val idx = getCellIndex(r, c)
                    if (board[idx] != null) continue
                    if (getCapturedDiscs(board, r, c, player).isNotEmpty()) {
                        moves.add(idx)
                    }
                }
            }
            return moves
        }

        private fun applyMove(
            board: Array<Disc?>,
            index: Int,
            player: Disc,
        ): Pair<Array<Disc?>, List<Int>>? {
            if (board[index] != null) return null
            val row = index / BOARD_SIZE
            val col = index % BOARD_SIZE
            val captured = getCapturedDiscs(board, row, col, player)
            if (captured.isEmpty()) return null

            val next = board.copyOf()
            next[index] = player
            for (ci in captured) next[ci] = player
            return Pair(next, captured)
        }

        private fun countDiscs(board: Array<Disc?>, player: Disc): Int =
            board.count { it == player }

        private val RISKY_CORNER_BY_MOVE: Map<Int, Int> = mapOf(
            getCellIndex(0, 1) to getCellIndex(0, 0),
            getCellIndex(1, 0) to getCellIndex(0, 0),
            getCellIndex(1, 1) to getCellIndex(0, 0),
            getCellIndex(0, BOARD_SIZE - 2) to getCellIndex(0, BOARD_SIZE - 1),
            getCellIndex(1, BOARD_SIZE - 2) to getCellIndex(0, BOARD_SIZE - 1),
            getCellIndex(1, BOARD_SIZE - 1) to getCellIndex(0, BOARD_SIZE - 1),
            getCellIndex(BOARD_SIZE - 2, 0) to getCellIndex(BOARD_SIZE - 1, 0),
            getCellIndex(BOARD_SIZE - 2, 1) to getCellIndex(BOARD_SIZE - 1, 0),
            getCellIndex(BOARD_SIZE - 1, 1) to getCellIndex(BOARD_SIZE - 1, 0),
            getCellIndex(BOARD_SIZE - 2, BOARD_SIZE - 1) to getCellIndex(BOARD_SIZE - 1, BOARD_SIZE - 1),
            getCellIndex(BOARD_SIZE - 2, BOARD_SIZE - 2) to getCellIndex(BOARD_SIZE - 1, BOARD_SIZE - 1),
            getCellIndex(BOARD_SIZE - 1, BOARD_SIZE - 2) to getCellIndex(BOARD_SIZE - 1, BOARD_SIZE - 1),
        )

        private fun chooseAiMove(
            board: Array<Disc?>,
            legalMoves: List<Int>,
            player: Disc,
            difficulty: Difficulty,
        ): Int? {
            if (legalMoves.isEmpty()) return null

            if (difficulty == Difficulty.EASY) {
                return legalMoves[Random.nextInt(legalMoves.size)]
            }

            var bestIndex: Int? = null
            var bestScore = Int.MIN_VALUE

            for (index in legalMoves) {
                val row = index / BOARD_SIZE
                val col = index % BOARD_SIZE
                val captures = getCapturedDiscs(board, row, col, player).size
                val isCorner =
                    (row == 0 || row == BOARD_SIZE - 1) && (col == 0 || col == BOARD_SIZE - 1)
                var score = (if (isCorner) 1000 else 0) + captures

                if (difficulty == Difficulty.HARD) {
                    val move = applyMove(board, index, player) ?: continue
                    val opponent = getOpponent(player)
                    val opponentMobility = getLegalMoves(move.first, opponent).size
                    val playerLead =
                        countDiscs(move.first, player) - countDiscs(move.first, opponent)
                    val isEdge =
                        row == 0 || row == BOARD_SIZE - 1 || col == 0 || col == BOARD_SIZE - 1
                    val riskyCorner = RISKY_CORNER_BY_MOVE[index]
                    val cornerPenalty =
                        if (riskyCorner != null && board[riskyCorner] == null) 80 else 0

                    score = (if (isCorner) 1200 else 0) +
                        captures * 8 +
                        (if (isEdge) 10 else 0) +
                        playerLead * 3 -
                        opponentMobility * 6 -
                        cornerPenalty
                }

                if (bestIndex == null || score > bestScore ||
                    (score == bestScore && index < bestIndex)
                ) {
                    bestIndex = index
                    bestScore = score
                }
            }

            return bestIndex
        }
    }

    // ── Observable state ─────────────────────────────────────────────

    var board by mutableStateOf(createInitialBoard())
        private set

    var currentPlayer by mutableStateOf(Disc.B)
        private set

    var isGameOver by mutableStateOf(false)
        private set

    var passMessage by mutableStateOf<String?>(null)
        private set

    var lastPlacedIndex by mutableIntStateOf(-1)
        private set

    var lastFlippedIndices by mutableStateOf(emptyList<Int>())
        private set

    var animationCycle by mutableIntStateOf(0)
        private set

    var mode by mutableStateOf(GameMode.LOCAL)
        private set

    var difficulty by mutableStateOf(Difficulty.NORMAL)
        private set

    // ── Derived properties ───────────────────────────────────────────

    val blackCount: Int get() = countDiscs(board, Disc.B)

    val whiteCount: Int get() = countDiscs(board, Disc.W)

    val legalMoveIndexes: List<Int> get() = getLegalMoves(board, currentPlayer)

    val legalMoveSet: Set<Int> get() = legalMoveIndexes.toSet()

    val winner: Disc?
        get() {
            if (!isGameOver || blackCount == whiteCount) return null
            return if (blackCount > whiteCount) Disc.B else Disc.W
        }

    val isAiTurn: Boolean
        get() = mode == GameMode.AI && !isGameOver && currentPlayer == Disc.W

    val statusText: String
        get() {
            val label = getPlayerLabel(currentPlayer)
            val whiteLabel = if (mode == GameMode.AI) "White (AI)" else "White"
            val n = legalMoveIndexes.size
            val movesLabel = "$n legal move${if (n == 1) "" else "s"}"
            val prefix = if (passMessage != null) "Pass: $passMessage " else ""

            return when {
                isGameOver && winner != null -> "Game over! ${getPlayerLabel(winner!!)} wins."
                isGameOver -> "Game over! It's a tie."
                isAiTurn -> "$prefix$whiteLabel is thinking ($movesLabel)."
                passMessage != null -> "Pass: $passMessage $label to move ($movesLabel)."
                else -> "$label to move ($movesLabel)."
            }
        }

    // ── Actions ──────────────────────────────────────────────────────

    fun setGameMode(newMode: GameMode) {
        if (newMode == mode) return
        mode = newMode
        resetGame()
    }

    fun setGameDifficulty(d: Difficulty) {
        difficulty = d
    }

    fun resetGame() {
        board = createInitialBoard()
        currentPlayer = Disc.B
        isGameOver = false
        passMessage = null
        lastPlacedIndex = -1
        lastFlippedIndices = emptyList()
        animationCycle = 0
    }

    fun playMove(index: Int) {
        if (isGameOver) return

        val move = applyMove(board, index, currentPlayer) ?: return

        val opponent = getOpponent(currentPlayer)
        val oppMoves = getLegalMoves(move.first, opponent)
        val curMoves = getLegalMoves(move.first, currentPlayer)

        board = move.first
        lastPlacedIndex = index
        lastFlippedIndices = move.second
        animationCycle += 1

        when {
            oppMoves.isNotEmpty() -> {
                currentPlayer = opponent
                passMessage = null
                isGameOver = false
            }
            curMoves.isNotEmpty() -> {
                passMessage = "${getPlayerLabel(opponent)} has no legal moves. " +
                    "${getPlayerLabel(currentPlayer)} plays again."
                isGameOver = false
            }
            else -> {
                passMessage = null
                isGameOver = true
            }
        }
    }

    fun handleCellClick(index: Int) {
        if (isAiTurn) return
        playMove(index)
    }

    fun performAiMove() {
        if (!isAiTurn) return
        val moves = legalMoveIndexes
        if (moves.isEmpty()) return
        val ai = chooseAiMove(board, moves, currentPlayer, difficulty) ?: return
        playMove(ai)
    }
}
