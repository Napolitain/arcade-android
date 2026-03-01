package com.napolitain.arcade.logic.checkers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.random.Random

enum class PieceColor { B, R }

data class Piece(val color: PieceColor, val king: Boolean)

data class Move(val from: Int, val to: Int, val captured: List<Int>)

data class LastMove(val from: Int, val to: Int, val captured: List<Int>, val token: Int)

class CheckersEngine {

    companion object {
        const val BOARD_SIZE = 8
        const val TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE
        const val STARTING_PIECES = 12

        private val KING_DIRECTIONS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val MOVE_DIRECTIONS = mapOf(
            PieceColor.B to listOf(1 to -1, 1 to 1),
            PieceColor.R to listOf(-1 to -1, -1 to 1),
        )

        fun getCellIndex(row: Int, col: Int) = row * BOARD_SIZE + col
        fun getRow(index: Int) = index / BOARD_SIZE
        fun getCol(index: Int) = index % BOARD_SIZE
        fun isInsideBoard(row: Int, col: Int) = row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE
        fun isPlayableSquare(row: Int, col: Int) = (row + col) % 2 == 1
        fun getOpponent(player: PieceColor) = if (player == PieceColor.B) PieceColor.R else PieceColor.B
        fun getPlayerLabel(player: PieceColor) = if (player == PieceColor.B) "Black" else "Red"

        private fun getDirections(piece: Piece): List<Pair<Int, Int>> =
            if (piece.king) KING_DIRECTIONS else MOVE_DIRECTIONS[piece.color]!!

        private fun shouldPromote(piece: Piece, destRow: Int): Boolean =
            !piece.king && ((piece.color == PieceColor.B && destRow == BOARD_SIZE - 1) ||
                    (piece.color == PieceColor.R && destRow == 0))

        fun createInitialBoard(): List<Piece?> {
            val board = MutableList<Piece?>(TOTAL_CELLS) { null }
            for (row in 0 until 3) {
                for (col in 0 until BOARD_SIZE) {
                    if (isPlayableSquare(row, col)) {
                        board[getCellIndex(row, col)] = Piece(PieceColor.B, false)
                    }
                }
            }
            for (row in BOARD_SIZE - 3 until BOARD_SIZE) {
                for (col in 0 until BOARD_SIZE) {
                    if (isPlayableSquare(row, col)) {
                        board[getCellIndex(row, col)] = Piece(PieceColor.R, false)
                    }
                }
            }
            return board
        }

        fun getCaptureMovesForPiece(board: List<Piece?>, index: Int): List<Move> {
            val piece = board[index] ?: return emptyList()
            val row = getRow(index)
            val col = getCol(index)
            val moves = mutableListOf<Move>()
            for ((dr, dc) in getDirections(piece)) {
                val midRow = row + dr
                val midCol = col + dc
                val landRow = row + dr * 2
                val landCol = col + dc * 2
                if (!isInsideBoard(midRow, midCol) || !isInsideBoard(landRow, landCol)) continue
                val midIndex = getCellIndex(midRow, midCol)
                val landIndex = getCellIndex(landRow, landCol)
                val midPiece = board[midIndex]
                if (midPiece != null && midPiece.color != piece.color && board[landIndex] == null) {
                    moves.add(Move(index, landIndex, listOf(midIndex)))
                }
            }
            return moves
        }

        fun getStepMovesForPiece(board: List<Piece?>, index: Int): List<Move> {
            val piece = board[index] ?: return emptyList()
            val row = getRow(index)
            val col = getCol(index)
            val moves = mutableListOf<Move>()
            for ((dr, dc) in getDirections(piece)) {
                val destRow = row + dr
                val destCol = col + dc
                if (!isInsideBoard(destRow, destCol)) continue
                val destIndex = getCellIndex(destRow, destCol)
                if (board[destIndex] == null) {
                    moves.add(Move(index, destIndex, emptyList()))
                }
            }
            return moves
        }

        fun getLegalMoves(board: List<Piece?>, player: PieceColor, forcedFromIndex: Int?): List<Move> {
            if (forcedFromIndex != null) {
                val piece = board[forcedFromIndex]
                if (piece == null || piece.color != player) return emptyList()
                return getCaptureMovesForPiece(board, forcedFromIndex)
            }
            val captureMoves = mutableListOf<Move>()
            val stepMoves = mutableListOf<Move>()
            for (i in board.indices) {
                val piece = board[i] ?: continue
                if (piece.color != player) continue
                captureMoves.addAll(getCaptureMovesForPiece(board, i))
                stepMoves.addAll(getStepMovesForPiece(board, i))
            }
            return if (captureMoves.isNotEmpty()) captureMoves else stepMoves
        }

        fun countPieces(board: List<Piece?>, player: PieceColor): Int =
            board.count { it?.color == player }

        fun countKings(board: List<Piece?>, player: PieceColor): Int =
            board.count { it?.color == player && it.king }

        fun chooseAiMove(board: List<Piece?>, legalMoves: List<Move>, difficulty: Difficulty): Move? {
            if (legalMoves.isEmpty()) return null
            if (difficulty == Difficulty.EASY) {
                return legalMoves[Random.nextInt(legalMoves.size)]
            }
            var bestMove: Move? = null
            var bestScore = Int.MIN_VALUE
            for (move in legalMoves) {
                val piece = board[move.from] ?: continue
                val destRow = getRow(move.to)
                val promotes = shouldPromote(piece, destRow)
                var score = move.captured.size * 100 + if (promotes) 20 else 0

                if (difficulty == Difficulty.HARD) {
                    val nextBoard = board.toMutableList()
                    nextBoard[move.from] = null
                    for (ci in move.captured) nextBoard[ci] = null
                    nextBoard[move.to] = if (promotes) piece.copy(king = true) else piece

                    val continuationCaptures = if (move.captured.isNotEmpty())
                        getCaptureMovesForPiece(nextBoard, move.to).size else 0
                    val opponentMoves = getLegalMoves(nextBoard, getOpponent(piece.color), null)
                    val opponentCaptureMoves = opponentMoves.filter { it.captured.isNotEmpty() }
                    val isExposed = opponentCaptureMoves.any { move.to in it.captured }
                    val advancement = if (piece.king) 0
                    else if (piece.color == PieceColor.B) destRow
                    else BOARD_SIZE - 1 - destRow

                    score = move.captured.size * 140 +
                            (if (promotes) 70 else 0) +
                            continuationCaptures * 45 +
                            advancement * 3 -
                            opponentMoves.size * 3 -
                            opponentCaptureMoves.size * 35 -
                            if (isExposed) 60 else 0
                }

                if (bestMove == null || score > bestScore || (score == bestScore && move.to < bestMove.to)) {
                    bestMove = move
                    bestScore = score
                }
            }
            return bestMove
        }
    }

    // ── observable state ─────────────────────────────────────────

    val board = mutableStateListOf<Piece?>().apply { addAll(createInitialBoard()) }
    var currentPlayer by mutableStateOf(PieceColor.B)
        private set
    var selectedIndex by mutableStateOf<Int?>(null)
        private set
    var forcedFromIndex by mutableStateOf<Int?>(null)
        private set
    var winner by mutableStateOf<PieceColor?>(null)
        private set
    var isDraw by mutableStateOf(false)
        private set
    var moveCount by mutableIntStateOf(0)
        private set
    var lastMove by mutableStateOf<LastMove?>(null)
        private set
    var gameMode by mutableStateOf(GameMode.LOCAL)
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    // ── derived helpers (recomputed on access) ───────────────────

    val isGameOver: Boolean get() = winner != null || isDraw
    val isAiTurn: Boolean get() = gameMode == GameMode.AI && !isGameOver && currentPlayer == PieceColor.R

    val legalMoves: List<Move>
        get() = if (isGameOver) emptyList() else getLegalMoves(board.toList(), currentPlayer, forcedFromIndex)

    val movablePieces: Set<Int> get() = legalMoves.map { it.from }.toSet()

    val selectedFromIndex: Int? get() = forcedFromIndex ?: selectedIndex

    val captureRequired: Boolean get() = legalMoves.any { it.captured.isNotEmpty() }

    val blackPieces: Int get() = countPieces(board, PieceColor.B)
    val redPieces: Int get() = countPieces(board, PieceColor.R)
    val blackKings: Int get() = countKings(board, PieceColor.B)
    val redKings: Int get() = countKings(board, PieceColor.R)

    val redLabel: String get() = if (gameMode == GameMode.AI) "Red (AI)" else "Red"

    fun selectedMoves(): List<Move> {
        val idx = selectedFromIndex ?: return emptyList()
        return legalMoves.filter { it.from == idx }
    }

    fun destinationSet(): Set<Int> = selectedMoves().map { it.to }.toSet()

    fun moveLookup(): Map<String, Move> = legalMoves.associateBy { "${it.from}-${it.to}" }

    val statusText: String
        get() = when {
            winner != null -> "Game over! ${getPlayerLabel(winner!!)} wins."
            isDraw -> "Game over! It's a draw."
            isAiTurn -> "$redLabel is thinking (${legalMoves.size} legal move${if (legalMoves.size == 1) "" else "s"})."
            forcedFromIndex != null -> "${getPlayerLabel(currentPlayer)} must continue capturing with the selected piece."
            captureRequired -> "${getPlayerLabel(currentPlayer)} to move. Capture required (${legalMoves.size} legal move${if (legalMoves.size == 1) "" else "s"})."
            else -> "${getPlayerLabel(currentPlayer)} to move (${legalMoves.size} legal move${if (legalMoves.size == 1) "" else "s"})."
        }

    // ── public API ───────────────────────────────────────────────

    fun handleCellClick(index: Int) {
        if (isGameOver || isAiTurn) return
        val row = getRow(index)
        val col = getCol(index)
        if (!isPlayableSquare(row, col)) return

        val piece = board[index]
        val activeSelection = forcedFromIndex ?: selectedIndex

        if (piece != null && piece.color == currentPlayer) {
            if (forcedFromIndex != null) {
                if (index == forcedFromIndex) selectedIndex = index
                return
            }
            if (movablePieces.contains(index)) {
                selectedIndex = if (selectedIndex == index) null else index
            }
            return
        }

        if (activeSelection == null) return
        val move = moveLookup()["$activeSelection-$index"] ?: return
        applyMove(move)
    }

    fun triggerAiMove() {
        if (!isAiTurn) return
        val moves = legalMoves
        if (moves.isEmpty()) return
        val aiMove = chooseAiMove(board.toList(), moves, difficulty) ?: return
        applyMove(aiMove)
    }

    fun reset() {
        val initial = createInitialBoard()
        for (i in board.indices) board[i] = initial[i]
        currentPlayer = PieceColor.B
        selectedIndex = null
        forcedFromIndex = null
        winner = null
        isDraw = false
        moveCount = 0
        lastMove = null
    }

    fun setMode(mode: GameMode) {
        if (mode == gameMode) return
        gameMode = mode
        reset()
    }

    // ── internals ────────────────────────────────────────────────

    private fun applyMove(move: Move) {
        val movingPiece = board[move.from] ?: return
        if (movingPiece.color != currentPlayer || isGameOver) return

        val snapshot = board.toMutableList()
        snapshot[move.from] = null
        for (ci in move.captured) snapshot[ci] = null

        val destRow = getRow(move.to)
        snapshot[move.to] = if (shouldPromote(movingPiece, destRow))
            movingPiece.copy(king = true) else movingPiece

        val continuationMoves = if (move.captured.isNotEmpty())
            getCaptureMovesForPiece(snapshot, move.to) else emptyList()

        // commit board
        for (i in board.indices) board[i] = snapshot[i]
        lastMove = LastMove(move.from, move.to, move.captured, (lastMove?.token ?: 0) + 1)
        moveCount++

        // multi-jump continuation
        if (move.captured.isNotEmpty() && continuationMoves.isNotEmpty()) {
            forcedFromIndex = move.to
            selectedIndex = move.to
            return
        }

        val nextPlayer = getOpponent(currentPlayer)
        val nextPlayerPieces = countPieces(snapshot, nextPlayer)
        val currentPlayerPieces = countPieces(snapshot, currentPlayer)
        val nextPlayerMoves = getLegalMoves(snapshot, nextPlayer, null)

        forcedFromIndex = null
        selectedIndex = null

        if (nextPlayerPieces == 0 || nextPlayerMoves.isEmpty()) {
            if (currentPlayerPieces == 0) isDraw = true else winner = currentPlayer
            return
        }

        currentPlayer = nextPlayer
    }
}
