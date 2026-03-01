package com.napolitain.arcade.logic.chess

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class ChessColor { WHITE, BLACK }

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

data class ChessPiece(val type: PieceType, val color: ChessColor) {
    val symbol: String
        get() = when (type) {
            PieceType.KING -> if (color == ChessColor.WHITE) "♔" else "♚"
            PieceType.QUEEN -> if (color == ChessColor.WHITE) "♕" else "♛"
            PieceType.ROOK -> if (color == ChessColor.WHITE) "♖" else "♜"
            PieceType.BISHOP -> if (color == ChessColor.WHITE) "♗" else "♝"
            PieceType.KNIGHT -> if (color == ChessColor.WHITE) "♘" else "♞"
            PieceType.PAWN -> if (color == ChessColor.WHITE) "♙" else "♟"
        }

    val value: Int
        get() = when (type) {
            PieceType.PAWN -> 100
            PieceType.KNIGHT -> 320
            PieceType.BISHOP -> 330
            PieceType.ROOK -> 500
            PieceType.QUEEN -> 900
            PieceType.KING -> 20000
        }
}

data class ChessMove(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val promotion: PieceType? = null,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
)

data class LastChessMove(val from: Int, val to: Int, val token: Int)

class ChessEngine {

    companion object {
        const val BOARD_SIZE = 8

        fun idx(row: Int, col: Int) = row * BOARD_SIZE + col
        fun row(idx: Int) = idx / BOARD_SIZE
        fun col(idx: Int) = idx % BOARD_SIZE
        fun inBounds(r: Int, c: Int) = r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE
        fun opponent(color: ChessColor) =
            if (color == ChessColor.WHITE) ChessColor.BLACK else ChessColor.WHITE

        fun createInitialBoard(): List<ChessPiece?> {
            val board = MutableList<ChessPiece?>(64) { null }
            val backRank = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK,
            )
            for (c in 0 until 8) {
                board[idx(0, c)] = ChessPiece(backRank[c], ChessColor.BLACK)
                board[idx(1, c)] = ChessPiece(PieceType.PAWN, ChessColor.BLACK)
                board[idx(6, c)] = ChessPiece(PieceType.PAWN, ChessColor.WHITE)
                board[idx(7, c)] = ChessPiece(backRank[c], ChessColor.WHITE)
            }
            return board
        }

        // ── Piece-square tables (white perspective, centipawns) ──────

        private val PAWN_PST = intArrayOf(
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5, -5,-10,  0,  0,-10, -5,  5,
             5, 10, 10,-20,-20, 10, 10,  5,
             0,  0,  0,  0,  0,  0,  0,  0,
        )

        private val KNIGHT_PST = intArrayOf(
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50,
        )

        private val BISHOP_PST = intArrayOf(
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -20,-10,-10,-10,-10,-10,-10,-20,
        )

        private val ROOK_PST = intArrayOf(
             0,  0,  0,  0,  0,  0,  0,  0,
             5, 10, 10, 10, 10, 10, 10,  5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
             0,  0,  0,  5,  5,  0,  0,  0,
        )

        private val QUEEN_PST = intArrayOf(
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
             -5,  0,  5,  5,  5,  5,  0, -5,
              0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20,
        )

        private val KING_PST = intArrayOf(
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
             20, 20,  0,  0,  0,  0, 20, 20,
             20, 30, 10,  0,  0, 10, 30, 20,
        )

        private fun pieceSquareValue(piece: ChessPiece, r: Int, c: Int): Int {
            val table = when (piece.type) {
                PieceType.PAWN -> PAWN_PST
                PieceType.KNIGHT -> KNIGHT_PST
                PieceType.BISHOP -> BISHOP_PST
                PieceType.ROOK -> ROOK_PST
                PieceType.QUEEN -> QUEEN_PST
                PieceType.KING -> KING_PST
            }
            val i = if (piece.color == ChessColor.WHITE) r * 8 + c else (7 - r) * 8 + c
            return table[i]
        }

        // ── Move generation ──────────────────────────────────────────

        private val BISHOP_DIRS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val ROOK_DIRS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val QUEEN_DIRS = BISHOP_DIRS + ROOK_DIRS
        private val KNIGHT_OFFSETS = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )

        private fun addPawnMoves(
            board: List<ChessPiece?>, r: Int, c: Int, color: ChessColor,
            epTarget: Int?, out: MutableList<ChessMove>,
        ) {
            val dir = if (color == ChessColor.WHITE) -1 else 1
            val startRow = if (color == ChessColor.WHITE) 6 else 1
            val promoRow = if (color == ChessColor.WHITE) 0 else 7
            val nr = r + dir

            if (inBounds(nr, c) && board[idx(nr, c)] == null) {
                if (nr == promoRow) {
                    for (p in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
                        out.add(ChessMove(r, c, nr, c, promotion = p))
                } else {
                    out.add(ChessMove(r, c, nr, c))
                }
                val nr2 = r + dir * 2
                if (r == startRow && board[idx(nr2, c)] == null)
                    out.add(ChessMove(r, c, nr2, c))
            }

            for (dc in intArrayOf(-1, 1)) {
                val nc = c + dc
                if (!inBounds(nr, nc)) continue
                val target = board[idx(nr, nc)]
                if (target != null && target.color != color) {
                    if (nr == promoRow) {
                        for (p in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
                            out.add(ChessMove(r, c, nr, nc, promotion = p))
                    } else {
                        out.add(ChessMove(r, c, nr, nc))
                    }
                }
                if (epTarget != null && idx(nr, nc) == epTarget)
                    out.add(ChessMove(r, c, nr, nc, isEnPassant = true))
            }
        }

        private fun addKnightMoves(
            board: List<ChessPiece?>, r: Int, c: Int, color: ChessColor,
            out: MutableList<ChessMove>,
        ) {
            for ((dr, dc) in KNIGHT_OFFSETS) {
                val nr = r + dr; val nc = c + dc
                if (!inBounds(nr, nc)) continue
                val t = board[idx(nr, nc)]
                if (t == null || t.color != color) out.add(ChessMove(r, c, nr, nc))
            }
        }

        private fun addSlidingMoves(
            board: List<ChessPiece?>, r: Int, c: Int, color: ChessColor,
            dirs: List<Pair<Int, Int>>, out: MutableList<ChessMove>,
        ) {
            for ((dr, dc) in dirs) {
                var nr = r + dr; var nc = c + dc
                while (inBounds(nr, nc)) {
                    val t = board[idx(nr, nc)]
                    if (t == null) {
                        out.add(ChessMove(r, c, nr, nc))
                    } else {
                        if (t.color != color) out.add(ChessMove(r, c, nr, nc))
                        break
                    }
                    nr += dr; nc += dc
                }
            }
        }

        private fun addKingMoves(
            board: List<ChessPiece?>, r: Int, c: Int, color: ChessColor,
            out: MutableList<ChessMove>,
        ) {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val nc = c + dc
                if (!inBounds(nr, nc)) continue
                val t = board[idx(nr, nc)]
                if (t == null || t.color != color) out.add(ChessMove(r, c, nr, nc))
            }
        }

        private fun addCastlingMoves(
            board: List<ChessPiece?>, color: ChessColor,
            kingMoved: Boolean, rookAMoved: Boolean, rookHMoved: Boolean,
            out: MutableList<ChessMove>,
        ) {
            if (kingMoved) return
            val row = if (color == ChessColor.WHITE) 7 else 0
            val opp = opponent(color)
            val king = board[idx(row, 4)]
            if (king?.type != PieceType.KING || king.color != color) return
            if (isSquareAttacked(board, row, 4, opp)) return

            // Kingside
            if (!rookHMoved) {
                val rook = board[idx(row, 7)]
                if (rook?.type == PieceType.ROOK && rook.color == color &&
                    board[idx(row, 5)] == null && board[idx(row, 6)] == null &&
                    !isSquareAttacked(board, row, 5, opp) &&
                    !isSquareAttacked(board, row, 6, opp)
                ) {
                    out.add(ChessMove(row, 4, row, 6, isCastling = true))
                }
            }
            // Queenside
            if (!rookAMoved) {
                val rook = board[idx(row, 0)]
                if (rook?.type == PieceType.ROOK && rook.color == color &&
                    board[idx(row, 1)] == null && board[idx(row, 2)] == null &&
                    board[idx(row, 3)] == null &&
                    !isSquareAttacked(board, row, 3, opp) &&
                    !isSquareAttacked(board, row, 2, opp)
                ) {
                    out.add(ChessMove(row, 4, row, 2, isCastling = true))
                }
            }
        }

        fun isSquareAttacked(board: List<ChessPiece?>, r: Int, c: Int, by: ChessColor): Boolean {
            // Knights
            for ((dr, dc) in KNIGHT_OFFSETS) {
                val nr = r + dr; val nc = c + dc
                if (inBounds(nr, nc)) {
                    val p = board[idx(nr, nc)]
                    if (p != null && p.color == by && p.type == PieceType.KNIGHT) return true
                }
            }
            // Pawns
            val pDir = if (by == ChessColor.WHITE) 1 else -1
            for (dc in intArrayOf(-1, 1)) {
                val pr = r + pDir; val pc = c + dc
                if (inBounds(pr, pc)) {
                    val p = board[idx(pr, pc)]
                    if (p != null && p.color == by && p.type == PieceType.PAWN) return true
                }
            }
            // King
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val nc = c + dc
                if (inBounds(nr, nc)) {
                    val p = board[idx(nr, nc)]
                    if (p != null && p.color == by && p.type == PieceType.KING) return true
                }
            }
            // Diagonal sliders (Bishop / Queen)
            for ((dr, dc) in BISHOP_DIRS) {
                var nr = r + dr; var nc = c + dc
                while (inBounds(nr, nc)) {
                    val p = board[idx(nr, nc)]
                    if (p != null) {
                        if (p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                        break
                    }
                    nr += dr; nc += dc
                }
            }
            // Straight sliders (Rook / Queen)
            for ((dr, dc) in ROOK_DIRS) {
                var nr = r + dr; var nc = c + dc
                while (inBounds(nr, nc)) {
                    val p = board[idx(nr, nc)]
                    if (p != null) {
                        if (p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                        break
                    }
                    nr += dr; nc += dc
                }
            }
            return false
        }

        fun findKing(board: List<ChessPiece?>, color: ChessColor): Int {
            for (i in board.indices) {
                val p = board[i]
                if (p != null && p.type == PieceType.KING && p.color == color) return i
            }
            return -1
        }

        fun isInCheck(board: List<ChessPiece?>, color: ChessColor): Boolean {
            val ki = findKing(board, color)
            if (ki == -1) return false
            return isSquareAttacked(board, row(ki), col(ki), opponent(color))
        }

        fun applyMoveOnBoard(board: List<ChessPiece?>, move: ChessMove): MutableList<ChessPiece?> {
            val b = board.toMutableList()
            val piece = b[idx(move.fromRow, move.fromCol)] ?: return b
            b[idx(move.fromRow, move.fromCol)] = null

            if (move.isEnPassant) {
                b[idx(move.fromRow, move.toCol)] = null
            }
            if (move.isCastling) {
                val rookFromCol = if (move.toCol == 6) 7 else 0
                val rookToCol = if (move.toCol == 6) 5 else 3
                b[idx(move.toRow, rookToCol)] = b[idx(move.toRow, rookFromCol)]
                b[idx(move.toRow, rookFromCol)] = null
            }

            b[idx(move.toRow, move.toCol)] =
                if (move.promotion != null) ChessPiece(move.promotion, piece.color) else piece
            return b
        }

        private fun pseudoLegalMoves(
            board: List<ChessPiece?>, color: ChessColor, epTarget: Int?,
            wkm: Boolean, wra: Boolean, wrh: Boolean,
            bkm: Boolean, bra: Boolean, brh: Boolean,
        ): List<ChessMove> {
            val moves = mutableListOf<ChessMove>()
            for (i in board.indices) {
                val piece = board[i] ?: continue
                if (piece.color != color) continue
                val r = row(i); val c = col(i)
                when (piece.type) {
                    PieceType.PAWN -> addPawnMoves(board, r, c, color, epTarget, moves)
                    PieceType.KNIGHT -> addKnightMoves(board, r, c, color, moves)
                    PieceType.BISHOP -> addSlidingMoves(board, r, c, color, BISHOP_DIRS, moves)
                    PieceType.ROOK -> addSlidingMoves(board, r, c, color, ROOK_DIRS, moves)
                    PieceType.QUEEN -> addSlidingMoves(board, r, c, color, QUEEN_DIRS, moves)
                    PieceType.KING -> {
                        addKingMoves(board, r, c, color, moves)
                        val km = if (color == ChessColor.WHITE) wkm else bkm
                        val ra = if (color == ChessColor.WHITE) wra else bra
                        val rh = if (color == ChessColor.WHITE) wrh else brh
                        addCastlingMoves(board, color, km, ra, rh, moves)
                    }
                }
            }
            return moves
        }

        fun legalMoves(
            board: List<ChessPiece?>, color: ChessColor, epTarget: Int?,
            wkm: Boolean, wra: Boolean, wrh: Boolean,
            bkm: Boolean, bra: Boolean, brh: Boolean,
        ): List<ChessMove> = pseudoLegalMoves(board, color, epTarget, wkm, wra, wrh, bkm, bra, brh)
            .filter { move ->
                val nb = applyMoveOnBoard(board, move)
                !isInCheck(nb, color)
            }

        // ── Evaluation ───────────────────────────────────────────────

        fun evaluate(board: List<ChessPiece?>): Int {
            var score = 0
            for (i in board.indices) {
                val p = board[i] ?: continue
                val sign = if (p.color == ChessColor.WHITE) 1 else -1
                score += sign * (p.value + pieceSquareValue(p, row(i), col(i)))
            }
            return score
        }

        fun evaluateSimple(board: List<ChessPiece?>): Int {
            var score = 0
            for (i in board.indices) {
                val p = board[i] ?: continue
                val sign = if (p.color == ChessColor.WHITE) 1 else -1
                score += sign * p.value
                val r = row(i); val c = col(i)
                if (r in 3..4 && c in 3..4) score += sign * 10
                else if (r in 2..5 && c in 2..5) score += sign * 5
            }
            return score
        }
    }

    // ── Observable state ─────────────────────────────────────────

    val board = mutableStateListOf<ChessPiece?>().apply { addAll(createInitialBoard()) }
    var currentPlayer by mutableStateOf(ChessColor.WHITE)
        private set
    var selectedSquare by mutableStateOf<Int?>(null)
        private set
    var isCheck by mutableStateOf(false)
        private set
    var isCheckmate by mutableStateOf(false)
        private set
    var isStalemate by mutableStateOf(false)
        private set
    var isDraw by mutableStateOf(false)
        private set
    var winner by mutableStateOf<ChessColor?>(null)
        private set
    var moveCount by mutableIntStateOf(0)
        private set
    var lastMove by mutableStateOf<LastChessMove?>(null)
        private set
    var gameMode by mutableStateOf(GameMode.LOCAL)
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    val capturedByWhite = mutableStateListOf<ChessPiece>()
    val capturedByBlack = mutableStateListOf<ChessPiece>()

    // Internal castling / en-passant tracking
    private var enPassantTarget: Int? = null
    private var whiteKingMoved = false
    private var whiteRookAMoved = false
    private var whiteRookHMoved = false
    private var blackKingMoved = false
    private var blackRookAMoved = false
    private var blackRookHMoved = false
    private var halfMoveClock = 0

    // ── Derived helpers ──────────────────────────────────────────

    val isGameOver: Boolean get() = isCheckmate || isStalemate || isDraw
    val isAiTurn: Boolean get() = gameMode == GameMode.AI && !isGameOver && currentPlayer == ChessColor.BLACK

    val legalMoves: List<ChessMove>
        get() = if (isGameOver) emptyList()
        else Companion.legalMoves(
            board.toList(), currentPlayer, enPassantTarget,
            whiteKingMoved, whiteRookAMoved, whiteRookHMoved,
            blackKingMoved, blackRookAMoved, blackRookHMoved,
        )

    val movablePieces: Set<Int> get() = legalMoves.map { idx(it.fromRow, it.fromCol) }.toSet()

    fun selectedMoves(): List<ChessMove> {
        val sel = selectedSquare ?: return emptyList()
        val sr = row(sel); val sc = col(sel)
        return legalMoves.filter { it.fromRow == sr && it.fromCol == sc }
    }

    fun destinationSet(): Set<Int> = selectedMoves().map { idx(it.toRow, it.toCol) }.toSet()

    val blackLabel: String get() = if (gameMode == GameMode.AI) "Black (AI)" else "Black"

    val statusText: String
        get() = when {
            isCheckmate -> "Checkmate! ${if (winner == ChessColor.WHITE) "White" else "Black"} wins."
            isStalemate -> "Stalemate! It's a draw."
            isDraw -> "Draw by 50-move rule."
            isCheck && isAiTurn -> "Check! Black (AI) is thinking…"
            isAiTurn -> "Black (AI) is thinking…"
            isCheck -> "${if (currentPlayer == ChessColor.WHITE) "White" else "Black"} is in check!"
            else -> "${if (currentPlayer == ChessColor.WHITE) "White" else "Black"} to move."
        }

    // ── Public API ───────────────────────────────────────────────

    fun selectSquare(row: Int, col: Int) {
        if (isGameOver || isAiTurn) return
        val index = idx(row, col)
        val piece = board[index]

        if (piece != null && piece.color == currentPlayer) {
            if (movablePieces.contains(index)) {
                selectedSquare = if (selectedSquare == index) null else index
            }
            return
        }

        val sel = selectedSquare ?: return
        val move = selectedMoves().firstOrNull { idx(it.toRow, it.toCol) == index } ?: return
        applyMove(move)
    }

    fun triggerAiMove() {
        if (!isAiTurn) return
        val moves = legalMoves
        if (moves.isEmpty()) return
        val aiMove = chooseAiMove(board.toList(), moves) ?: return
        applyMove(aiMove)
    }

    fun reset() {
        val initial = createInitialBoard()
        for (i in board.indices) board[i] = initial[i]
        currentPlayer = ChessColor.WHITE
        selectedSquare = null
        isCheck = false
        isCheckmate = false
        isStalemate = false
        isDraw = false
        winner = null
        moveCount = 0
        lastMove = null
        capturedByWhite.clear()
        capturedByBlack.clear()
        enPassantTarget = null
        whiteKingMoved = false
        whiteRookAMoved = false
        whiteRookHMoved = false
        blackKingMoved = false
        blackRookAMoved = false
        blackRookHMoved = false
        halfMoveClock = 0
    }

    fun setMode(mode: GameMode) {
        if (mode == gameMode) return
        gameMode = mode
        reset()
    }

    // ── Move execution ───────────────────────────────────────────

    private fun applyMove(move: ChessMove) {
        val piece = board[idx(move.fromRow, move.fromCol)] ?: return
        if (piece.color != currentPlayer || isGameOver) return

        // Track captured piece
        val captured = if (move.isEnPassant) {
            board[idx(move.fromRow, move.toCol)]
        } else {
            board[idx(move.toRow, move.toCol)]
        }
        if (captured != null) {
            if (currentPlayer == ChessColor.WHITE) capturedByWhite.add(captured)
            else capturedByBlack.add(captured)
        }

        // Apply on board
        val newBoard = applyMoveOnBoard(board.toList(), move)
        for (i in board.indices) board[i] = newBoard[i]

        // Update castling rights
        if (piece.type == PieceType.KING) {
            if (piece.color == ChessColor.WHITE) whiteKingMoved = true else blackKingMoved = true
        }
        if (piece.type == PieceType.ROOK) {
            if (piece.color == ChessColor.WHITE) {
                if (move.fromRow == 7 && move.fromCol == 0) whiteRookAMoved = true
                if (move.fromRow == 7 && move.fromCol == 7) whiteRookHMoved = true
            } else {
                if (move.fromRow == 0 && move.fromCol == 0) blackRookAMoved = true
                if (move.fromRow == 0 && move.fromCol == 7) blackRookHMoved = true
            }
        }
        // Rook captured on starting square
        if (move.toRow == 7 && move.toCol == 0) whiteRookAMoved = true
        if (move.toRow == 7 && move.toCol == 7) whiteRookHMoved = true
        if (move.toRow == 0 && move.toCol == 0) blackRookAMoved = true
        if (move.toRow == 0 && move.toCol == 7) blackRookHMoved = true

        // En passant target
        enPassantTarget = if (piece.type == PieceType.PAWN && abs(move.toRow - move.fromRow) == 2) {
            idx((move.fromRow + move.toRow) / 2, move.fromCol)
        } else null

        // 50-move clock
        halfMoveClock = if (piece.type == PieceType.PAWN || captured != null) 0 else halfMoveClock + 1

        lastMove = LastChessMove(
            idx(move.fromRow, move.fromCol),
            idx(move.toRow, move.toCol),
            (lastMove?.token ?: 0) + 1,
        )
        moveCount++
        selectedSquare = null

        // Switch and evaluate game state
        val next = opponent(currentPlayer)
        val nextMoves = Companion.legalMoves(
            board.toList(), next, enPassantTarget,
            whiteKingMoved, whiteRookAMoved, whiteRookHMoved,
            blackKingMoved, blackRookAMoved, blackRookHMoved,
        )
        val inCheck = isInCheck(board.toList(), next)

        currentPlayer = next
        isCheck = inCheck

        when {
            nextMoves.isEmpty() && inCheck -> { isCheckmate = true; winner = opponent(next) }
            nextMoves.isEmpty() -> isStalemate = true
            halfMoveClock >= 100 -> isDraw = true
        }
    }

    // ── AI ────────────────────────────────────────────────────────

    private fun chooseAiMove(boardState: List<ChessPiece?>, moves: List<ChessMove>): ChessMove? {
        if (moves.isEmpty()) return null
        return when (difficulty) {
            Difficulty.EASY -> moves[Random.nextInt(moves.size)]
            Difficulty.NORMAL -> {
                var best = moves[0]; var bestScore = Int.MIN_VALUE
                for (m in moves) {
                    val nb = applyMoveOnBoard(boardState, m)
                    val s = -minimaxSimple(nb, 1, ChessColor.WHITE)
                    if (s > bestScore) { bestScore = s; best = m }
                }
                best
            }
            Difficulty.HARD -> {
                var best = moves[0]; var bestScore = Int.MIN_VALUE
                var alpha = Int.MIN_VALUE
                for (m in moves) {
                    val nb = applyMoveOnBoard(boardState, m)
                    val s = -alphaBeta(nb, 2, -Int.MAX_VALUE, -alpha, ChessColor.WHITE)
                    if (s > bestScore) { bestScore = s; best = m }
                    alpha = max(alpha, s)
                }
                best
            }
        }
    }

    private fun minimaxSimple(boardState: List<ChessPiece?>, depth: Int, color: ChessColor): Int {
        if (depth == 0) {
            val e = evaluateSimple(boardState)
            return if (color == ChessColor.WHITE) e else -e
        }
        val moves = Companion.legalMoves(
            boardState, color, null,
            whiteKingMoved, whiteRookAMoved, whiteRookHMoved,
            blackKingMoved, blackRookAMoved, blackRookHMoved,
        )
        if (moves.isEmpty()) {
            return if (isInCheck(boardState, color)) -20000 + (2 - depth) * 100 else 0
        }
        var best = Int.MIN_VALUE
        for (m in moves) {
            val s = -minimaxSimple(applyMoveOnBoard(boardState, m), depth - 1, opponent(color))
            best = max(best, s)
        }
        return best
    }

    private fun alphaBeta(
        boardState: List<ChessPiece?>, depth: Int, a: Int, b: Int, color: ChessColor,
    ): Int {
        if (depth == 0) {
            val e = evaluate(boardState)
            return if (color == ChessColor.WHITE) e else -e
        }
        val moves = Companion.legalMoves(
            boardState, color, null,
            whiteKingMoved, whiteRookAMoved, whiteRookHMoved,
            blackKingMoved, blackRookAMoved, blackRookHMoved,
        )
        if (moves.isEmpty()) {
            return if (isInCheck(boardState, color)) -20000 + (3 - depth) * 100 else 0
        }
        var alpha = a
        for (m in moves) {
            val s = -alphaBeta(applyMoveOnBoard(boardState, m), depth - 1, -b, -alpha, opponent(color))
            alpha = max(alpha, s)
            if (alpha >= b) break
        }
        return alpha
    }
}
