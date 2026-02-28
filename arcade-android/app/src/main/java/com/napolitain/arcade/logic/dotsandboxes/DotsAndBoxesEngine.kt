package com.napolitain.arcade.logic.dotsandboxes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty

enum class Player(val label: String) { A("A"), B("B") }

data class EdgeDefinition(
    val id: String,
    val orientation: EdgeOrientation,
    val row: Int,
    val column: Int,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val adjacentBoxes: List<String>,
)

enum class EdgeOrientation { HORIZONTAL, VERTICAL }

data class BoxDefinition(
    val id: String,
    val row: Int,
    val column: Int,
    val x: Float,
    val y: Float,
    val edgeIds: List<String>,
)

class DotsAndBoxesEngine {

    companion object {
        const val DOTS_PER_SIDE = 5
        const val BOXES_PER_SIDE = DOTS_PER_SIDE - 1
        const val CELL_SIZE = 72f
        const val BOARD_PADDING = 24f
        const val EDGE_STROKE_WIDTH = 8f
        const val EDGE_HIT_STROKE_WIDTH = 28f
        const val DOT_RADIUS = 6f
        const val BOARD_SIZE = BOARD_PADDING * 2 + CELL_SIZE * BOXES_PER_SIDE

        private fun horizontalEdgeId(row: Int, col: Int) = "h-$row-$col"
        private fun verticalEdgeId(row: Int, col: Int) = "v-$row-$col"
        private fun boxId(row: Int, col: Int) = "b-$row-$col"

        val EDGE_DEFINITIONS: List<EdgeDefinition> = buildList {
            // Horizontal edges
            for (row in 0 until DOTS_PER_SIDE) {
                for (col in 0 until BOXES_PER_SIDE) {
                    val adj = buildList {
                        if (row > 0) add(boxId(row - 1, col))
                        if (row < BOXES_PER_SIDE) add(boxId(row, col))
                    }
                    add(
                        EdgeDefinition(
                            id = horizontalEdgeId(row, col),
                            orientation = EdgeOrientation.HORIZONTAL,
                            row = row,
                            column = col,
                            x1 = BOARD_PADDING + col * CELL_SIZE,
                            y1 = BOARD_PADDING + row * CELL_SIZE,
                            x2 = BOARD_PADDING + (col + 1) * CELL_SIZE,
                            y2 = BOARD_PADDING + row * CELL_SIZE,
                            adjacentBoxes = adj,
                        )
                    )
                }
            }
            // Vertical edges
            for (row in 0 until BOXES_PER_SIDE) {
                for (col in 0 until DOTS_PER_SIDE) {
                    val adj = buildList {
                        if (col > 0) add(boxId(row, col - 1))
                        if (col < BOXES_PER_SIDE) add(boxId(row, col))
                    }
                    add(
                        EdgeDefinition(
                            id = verticalEdgeId(row, col),
                            orientation = EdgeOrientation.VERTICAL,
                            row = row,
                            column = col,
                            x1 = BOARD_PADDING + col * CELL_SIZE,
                            y1 = BOARD_PADDING + row * CELL_SIZE,
                            x2 = BOARD_PADDING + col * CELL_SIZE,
                            y2 = BOARD_PADDING + (row + 1) * CELL_SIZE,
                            adjacentBoxes = adj,
                        )
                    )
                }
            }
        }

        val BOX_DEFINITIONS: List<BoxDefinition> = buildList {
            for (row in 0 until BOXES_PER_SIDE) {
                for (col in 0 until BOXES_PER_SIDE) {
                    add(
                        BoxDefinition(
                            id = boxId(row, col),
                            row = row,
                            column = col,
                            x = BOARD_PADDING + col * CELL_SIZE,
                            y = BOARD_PADDING + row * CELL_SIZE,
                            edgeIds = listOf(
                                horizontalEdgeId(row, col),
                                horizontalEdgeId(row + 1, col),
                                verticalEdgeId(row, col),
                                verticalEdgeId(row, col + 1),
                            ),
                        )
                    )
                }
            }
        }

        val BOXES_BY_ID: Map<String, BoxDefinition> = BOX_DEFINITIONS.associateBy { it.id }

        data class DotCoordinate(val id: String, val x: Float, val y: Float)

        val DOT_COORDINATES: List<DotCoordinate> = buildList {
            for (row in 0 until DOTS_PER_SIDE) {
                for (col in 0 until DOTS_PER_SIDE) {
                    add(
                        DotCoordinate(
                            id = "dot-$row-$col",
                            x = BOARD_PADDING + col * CELL_SIZE,
                            y = BOARD_PADDING + row * CELL_SIZE,
                        )
                    )
                }
            }
        }
    }

    // Observable state
    val drawnEdges = mutableStateMapOf<String, Player>()
    val claimedBoxes = mutableStateMapOf<String, Player>()
    var currentPlayer by mutableStateOf(Player.A)
        private set
    var lastEdgeId by mutableStateOf<String?>(null)
        private set
    var lastClaimedBoxIds by mutableStateOf<List<String>>(emptyList())
        private set

    var scoreA by mutableIntStateOf(0)
        private set
    var scoreB by mutableIntStateOf(0)
        private set

    val edgesRemaining: Int get() = EDGE_DEFINITIONS.size - drawnEdges.size
    val isGameOver: Boolean get() = edgesRemaining == 0

    fun isAiTurn(mode: com.napolitain.arcade.ui.components.GameMode): Boolean =
        mode == com.napolitain.arcade.ui.components.GameMode.AI && !isGameOver && currentPlayer == Player.B

    fun statusText(mode: com.napolitain.arcade.ui.components.GameMode): String {
        val playerBLabel = if (mode == com.napolitain.arcade.ui.components.GameMode.AI) "Player B (AI)" else "Player B"
        if (isGameOver) {
            return if (scoreA == scoreB) {
                "Game over! Tie game at $scoreA-$scoreB."
            } else {
                val winningPlayer = if (scoreA > scoreB) "A" else "B"
                val winningScore = maxOf(scoreA, scoreB)
                val losingScore = minOf(scoreA, scoreB)
                "Game over! Player $winningPlayer wins $winningScore-$losingScore."
            }
        }
        if (isAiTurn(mode)) {
            val rem = edgesRemaining
            return "$playerBLabel is thinking. $rem edge${if (rem == 1) "" else "s"} left."
        }
        val rem = edgesRemaining
        return "Turn: Player ${currentPlayer.label}. $rem edge${if (rem == 1) "" else "s"} left."
    }

    /**
     * Returns true if the move was accepted.
     */
    fun selectEdge(edge: EdgeDefinition, mode: com.napolitain.arcade.ui.components.GameMode, initiatedByAi: Boolean = false): Boolean {
        if (isGameOver || drawnEdges.containsKey(edge.id)) return false
        if (isAiTurn(mode) && !initiatedByAi) return false

        drawnEdges[edge.id] = currentPlayer
        lastEdgeId = edge.id

        val completedBoxIds = edge.adjacentBoxes.filter { boxId ->
            if (claimedBoxes.containsKey(boxId)) return@filter false
            val box = BOXES_BY_ID[boxId] ?: return@filter false
            box.edgeIds.all { edgeId -> drawnEdges.containsKey(edgeId) }
        }

        if (completedBoxIds.isEmpty()) {
            currentPlayer = if (currentPlayer == Player.A) Player.B else Player.A
            lastClaimedBoxIds = emptyList()
        } else {
            for (boxId in completedBoxIds) {
                claimedBoxes[boxId] = currentPlayer
            }
            lastClaimedBoxIds = completedBoxIds
            recalcScores()
        }
        recalcScores()
        return true
    }

    private fun recalcScores() {
        scoreA = claimedBoxes.values.count { it == Player.A }
        scoreB = claimedBoxes.values.count { it == Player.B }
    }

    fun reset() {
        drawnEdges.clear()
        claimedBoxes.clear()
        currentPlayer = Player.A
        lastEdgeId = null
        lastClaimedBoxIds = emptyList()
        scoreA = 0
        scoreB = 0
    }

    // ---- AI ----

    fun chooseAiEdge(difficulty: Difficulty): EdgeDefinition? {
        val availableEdges = EDGE_DEFINITIONS.filter { !drawnEdges.containsKey(it.id) }
        if (availableEdges.isEmpty()) return null

        if (difficulty == Difficulty.EASY) {
            return availableEdges.random()
        }

        var bestCompletingEdge: Pair<EdgeDefinition, Int>? = null // edge, boxes
        val safeEdges = mutableListOf<EdgeDefinition>()
        var hardFallbackEdge: Pair<EdgeDefinition, Int>? = null // edge, riskCount

        for (edge in availableEdges) {
            var completedBoxes = 0
            var riskCount = 0

            for (boxId in edge.adjacentBoxes) {
                if (claimedBoxes.containsKey(boxId)) continue
                val box = BOXES_BY_ID[boxId] ?: continue
                val drawnCount = box.edgeIds.count { edgeId ->
                    edgeId == edge.id || drawnEdges.containsKey(edgeId)
                }
                if (drawnCount == 4) completedBoxes++
                else if (drawnCount == 3) riskCount++
            }

            if (completedBoxes > 0) {
                val current = bestCompletingEdge
                if (current == null ||
                    completedBoxes > current.second ||
                    (completedBoxes == current.second && edge.id < current.first.id)
                ) {
                    bestCompletingEdge = edge to completedBoxes
                }
                continue
            }

            if (riskCount == 0) {
                safeEdges.add(edge)
            } else if (difficulty == Difficulty.HARD) {
                val current = hardFallbackEdge
                if (current == null ||
                    riskCount < current.second ||
                    (riskCount == current.second && edge.id < current.first.id)
                ) {
                    hardFallbackEdge = edge to riskCount
                }
            }
        }

        if (bestCompletingEdge != null) return bestCompletingEdge.first

        if (difficulty == Difficulty.HARD && safeEdges.isNotEmpty()) {
            var bestSafeEdge: Pair<EdgeDefinition, Int>? = null // edge, futureSafeEdges

            for (edge in safeEdges) {
                val simulatedDrawnEdges = HashMap(drawnEdges).apply { put(edge.id, Player.B) }
                val futureSafeEdges = EDGE_DEFINITIONS.count { candidateEdge ->
                    if (simulatedDrawnEdges.containsKey(candidateEdge.id)) return@count false
                    val createsRisk = candidateEdge.adjacentBoxes.any { boxId ->
                        if (claimedBoxes.containsKey(boxId)) return@any false
                        val box = BOXES_BY_ID[boxId] ?: return@any false
                        val drawnCount = box.edgeIds.count { edgeId ->
                            edgeId == candidateEdge.id || simulatedDrawnEdges.containsKey(edgeId)
                        }
                        drawnCount == 3
                    }
                    !createsRisk
                }

                val current = bestSafeEdge
                if (current == null ||
                    futureSafeEdges > current.second ||
                    (futureSafeEdges == current.second && edge.id < current.first.id)
                ) {
                    bestSafeEdge = edge to futureSafeEdges
                }
            }

            if (bestSafeEdge != null) return bestSafeEdge.first
        }

        if (difficulty == Difficulty.HARD && hardFallbackEdge != null) {
            return hardFallbackEdge.first
        }

        val fallbackList = if (safeEdges.isNotEmpty()) safeEdges else availableEdges
        return fallbackList.sortedBy { it.id }.firstOrNull()
    }
}
