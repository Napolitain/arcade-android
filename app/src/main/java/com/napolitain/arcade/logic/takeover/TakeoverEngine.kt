package com.napolitain.arcade.logic.takeover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

enum class Player(val label: String) {
    B("Blue"),
    O("Orange"),
}

enum class MoveKind { CLONE, JUMP }

data class Move(val from: Int, val to: Int, val kind: MoveKind)

data class ApplyResult(val nextBoard: Array<Player?>, val converted: List<Int>)

data class TurnResolution(
    val nextPlayer: Player,
    val passMessage: String?,
    val isGameOver: Boolean,
)

class TakeoverEngine {

    companion object {
        const val BOARD_SIZE = 7
        const val TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE
        const val MOVE_RANGE = 2
        const val AI_DELAY_MS = 550L

        private val DIRECTIONS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
            intArrayOf(0, -1), intArrayOf(0, 1),
            intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
        )

        fun getCellIndex(row: Int, col: Int): Int = row * BOARD_SIZE + col
        fun getRow(index: Int): Int = index / BOARD_SIZE
        fun getCol(index: Int): Int = index % BOARD_SIZE
        fun isInside(row: Int, col: Int): Boolean =
            row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE

        fun opponent(p: Player): Player = if (p == Player.B) Player.O else Player.B

        fun createInitialBoard(): Array<Player?> {
            val b = arrayOfNulls<Player>(TOTAL_CELLS)
            b[getCellIndex(0, 0)] = Player.B
            b[getCellIndex(BOARD_SIZE - 1, BOARD_SIZE - 1)] = Player.B
            b[getCellIndex(0, BOARD_SIZE - 1)] = Player.O
            b[getCellIndex(BOARD_SIZE - 1, 0)] = Player.O
            return b
        }

        fun getMovesFromCell(board: Array<Player?>, from: Int, player: Player): List<Move> {
            if (board[from] != player) return emptyList()
            val row = getRow(from)
            val col = getCol(from)
            val moves = mutableListOf<Move>()
            for (dr in -MOVE_RANGE..MOVE_RANGE) {
                for (dc in -MOVE_RANGE..MOVE_RANGE) {
                    val dist = max(abs(dr), abs(dc))
                    if (dist == 0 || dist > MOVE_RANGE) continue
                    val tr = row + dr
                    val tc = col + dc
                    if (!isInside(tr, tc)) continue
                    val ti = getCellIndex(tr, tc)
                    if (board[ti] != null) continue
                    moves.add(Move(from, ti, if (dist == 1) MoveKind.CLONE else MoveKind.JUMP))
                }
            }
            return moves
        }

        fun getLegalMoves(board: Array<Player?>, player: Player): List<Move> {
            val moves = mutableListOf<Move>()
            for (i in 0 until TOTAL_CELLS) {
                if (board[i] != player) continue
                moves.addAll(getMovesFromCell(board, i, player))
            }
            return moves
        }

        fun applyMove(board: Array<Player?>, move: Move, player: Player): ApplyResult? {
            if (board[move.from] != player || board[move.to] != null) return null
            val next = board.copyOf()
            if (move.kind == MoveKind.JUMP) next[move.from] = null
            next[move.to] = player
            val opp = opponent(player)
            val converted = mutableListOf<Int>()
            val row = getRow(move.to)
            val col = getCol(move.to)
            for (dir in DIRECTIONS) {
                val tr = row + dir[0]
                val tc = col + dir[1]
                if (!isInside(tr, tc)) continue
                val ai = getCellIndex(tr, tc)
                if (next[ai] == opp) {
                    next[ai] = player
                    converted.add(ai)
                }
            }
            return ApplyResult(next, converted)
        }

        fun countPieces(board: Array<Player?>, player: Player): Int =
            board.count { it == player }

        fun resolveTurn(board: Array<Player?>, candidatePlayer: Player): TurnResolution {
            if (board.all { it != null }) {
                return TurnResolution(candidatePlayer, null, isGameOver = true)
            }
            if (getLegalMoves(board, candidatePlayer).isNotEmpty()) {
                return TurnResolution(candidatePlayer, null, isGameOver = false)
            }
            val other = opponent(candidatePlayer)
            if (getLegalMoves(board, other).isNotEmpty()) {
                return TurnResolution(
                    other,
                    "${candidatePlayer.label} has no legal moves. ${other.label} plays.",
                    isGameOver = false,
                )
            }
            return TurnResolution(candidatePlayer, "Neither player can make a legal move.", isGameOver = true)
        }

        private fun scoreMove(board: Array<Player?>, move: Move, player: Player): Pair<Double, Array<Player?>>? {
            val result = applyMove(board, move, player) ?: return null
            val opp = opponent(player)
            val conversionScore = result.converted.size * 4
            val cloneBonus = if (move.kind == MoveKind.CLONE) 2 else 0
            val control = countPieces(result.nextBoard, player) - countPieces(result.nextBoard, opp)
            return Pair(conversionScore + cloneBonus + control * 0.1, result.nextBoard)
        }

        fun pickAiMove(
            board: Array<Player?>,
            legalMoves: List<Move>,
            player: Player,
            difficulty: Difficulty,
        ): Move? {
            data class Scored(val move: Move, val score: Double, val nextBoard: Array<Player?>)

            val scored = legalMoves.mapNotNull { move ->
                val (s, nb) = scoreMove(board, move, player) ?: return@mapNotNull null
                Scored(move, s, nb)
            }

            if (scored.isEmpty()) return legalMoves.firstOrNull()

            if (difficulty == Difficulty.EASY) {
                val sorted = scored.sortedBy { it.score }
                val weakerHalf = sorted.subList(0, max(1, ceil(sorted.size / 2.0).toInt()))
                return weakerHalf[Random.nextInt(weakerHalf.size)].move
            }

            if (difficulty == Difficulty.HARD) {
                var bestMove: Move? = null
                var bestHardScore = Double.NEGATIVE_INFINITY
                var bestBaselineScore = Double.NEGATIVE_INFINITY

                for ((move, score, nextBoard) in scored) {
                    val opp = opponent(player)
                    val opponentMoves = getLegalMoves(nextBoard, opp)
                    val ownMobility = getLegalMoves(nextBoard, player).size
                    val opponentMobility = opponentMoves.size
                    var opponentBestReply = 0.0

                    for (reply in opponentMoves) {
                        val eval = scoreMove(nextBoard, reply, opp)
                        if (eval != null && eval.first > opponentBestReply) {
                            opponentBestReply = eval.first
                        }
                    }

                    val turnState = resolveTurn(nextBoard, opp)
                    val terminalBonus = if (turnState.isGameOver) {
                        val pc = countPieces(nextBoard, player)
                        val oc = countPieces(nextBoard, opp)
                        when {
                            pc > oc -> 500.0
                            pc < oc -> -500.0
                            else -> 0.0
                        }
                    } else 0.0

                    val hardScore = score * 1.4 +
                        (ownMobility - opponentMobility) * 0.35 -
                        opponentBestReply * 0.9 +
                        (if (opponentMoves.isEmpty()) 8.0 else 0.0) +
                        terminalBonus

                    if (hardScore > bestHardScore ||
                        (hardScore == bestHardScore && score > bestBaselineScore)
                    ) {
                        bestHardScore = hardScore
                        bestBaselineScore = score
                        bestMove = move
                    }
                }
                return bestMove ?: scored[0].move
            }

            // Normal difficulty: pick the highest-scored move
            var bestMove: Move? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for ((move, score, _) in scored) {
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
            }
            return bestMove ?: legalMoves.firstOrNull()
        }
    }

    // --- Observable state ---

    var board by mutableStateOf(createInitialBoard())
        private set

    var currentPlayer by mutableStateOf(Player.B)
        private set

    var selectedSource by mutableStateOf<Int?>(null)
        private set

    var isGameOver by mutableStateOf(false)
        private set

    var passMessage by mutableStateOf<String?>(null)
        private set

    var lastMove by mutableStateOf<Move?>(null)
        private set

    var lastConverted by mutableStateOf<List<Int>>(emptyList())
        private set

    var animationCycle by mutableIntStateOf(0)
        private set

    var mode by mutableStateOf(GameMode.LOCAL)
        private set

    var difficulty by mutableStateOf(Difficulty.NORMAL)
        private set

    // --- Derived state ---

    val blueCount: Int get() = countPieces(board, Player.B)
    val orangeCount: Int get() = countPieces(board, Player.O)

    val legalMoves: List<Move>
        get() = if (isGameOver) emptyList() else getLegalMoves(board, currentPlayer)

    val isAiTurn: Boolean
        get() = mode == GameMode.AI && currentPlayer == Player.O && !isGameOver

    val movesBySource: Map<Int, List<Move>>
        get() = legalMoves.groupBy { it.from }

    val selectableSources: Set<Int>
        get() = movesBySource.keys

    val selectedMoves: List<Move>
        get() = if (selectedSource == null) emptyList() else movesBySource[selectedSource] ?: emptyList()

    val selectedTargets: Map<Int, MoveKind>
        get() = selectedMoves.associate { it.to to it.kind }

    val convertedSet: Set<Int>
        get() = lastConverted.toSet()

    val winner: Player?
        get() {
            if (!isGameOver) return null
            val bc = blueCount; val oc = orangeCount
            return when {
                bc > oc -> Player.B
                oc > bc -> Player.O
                else -> null
            }
        }

    val statusText: String
        get() {
            val bc = blueCount; val oc = orangeCount
            val moveCount = legalMoves.size
            val moveSuffix = if (moveCount == 1) "" else "s"
            if (isGameOver) {
                val w = winner
                return if (w != null) "Game over! ${w.label} wins $bc-$oc."
                else "Game over! Draw at $bc-$oc."
            }
            val active = currentPlayer.label
            return if (passMessage != null)
                "Pass: $passMessage $active to move ($moveCount legal move$moveSuffix)."
            else
                "$active to move ($moveCount legal move$moveSuffix)."
        }

    val selectionHint: String
        get() {
            if (selectedSource == null) {
                return if (isAiTurn) "AI (Orange) is selecting a move..."
                else "${currentPlayer.label}: select a highlighted piece to move."
            }
            val r = getRow(selectedSource!!) + 1
            val c = getCol(selectedSource!!) + 1
            return "Selected row $r, column $c. Choose a highlighted destination."
        }

    // --- Actions ---

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
        currentPlayer = Player.B
        selectedSource = null
        isGameOver = false
        passMessage = null
        lastMove = null
        lastConverted = emptyList()
        animationCycle = 0
    }

    fun handleCellClick(index: Int) {
        if (isGameOver || isAiTurn) return

        val cell = board[index]

        if (selectedSource == null) {
            if (cell == currentPlayer && selectableSources.contains(index)) {
                selectedSource = index
            }
            return
        }

        if (index == selectedSource) {
            selectedSource = null
            return
        }

        if (cell == currentPlayer && selectableSources.contains(index)) {
            selectedSource = index
            return
        }

        val moveKind = selectedTargets[index] ?: return
        val move = Move(selectedSource!!, index, moveKind)
        executeMove(move, currentPlayer)
    }

    fun performAiMove() {
        if (!isAiTurn) return
        val moves = legalMoves
        val aiMove = pickAiMove(board, moves, Player.O, difficulty) ?: return
        executeMove(aiMove, Player.O)
    }

    private fun executeMove(move: Move, player: Player) {
        val result = applyMove(board, move, player) ?: return
        val nextTurn = resolveTurn(result.nextBoard, opponent(player))
        board = result.nextBoard
        currentPlayer = nextTurn.nextPlayer
        isGameOver = nextTurn.isGameOver
        passMessage = nextTurn.passMessage
        selectedSource = null
        lastMove = move
        lastConverted = result.converted
        animationCycle++
    }
}
