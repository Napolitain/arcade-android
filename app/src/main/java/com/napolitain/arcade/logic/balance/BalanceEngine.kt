package com.napolitain.arcade.logic.balance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import com.napolitain.arcade.ui.components.GameMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Player { A, B }

data class PlacedWeight(
    val slot: Int,
    val weight: Int,
    val player: Player,
)

sealed class RoundResult(val finalTorque: Int) {
    class Tip(val winner: Player, val loser: Player, finalTorque: Int) : RoundResult(finalTorque)
    class Stable(finalTorque: Int) : RoundResult(finalTorque)
}

class BalanceEngine {

    companion object {
        val SLOT_POSITIONS = intArrayOf(-4, -3, -2, -1, 1, 2, 3, 4)
        val INITIAL_WEIGHTS = intArrayOf(1, 2, 3, 4)
        const val SAFE_TORQUE_LIMIT = 14
        const val AI_DELAY_MS = 650L

        fun computeTorque(placements: List<PlacedWeight>): Int =
            placements.sumOf { it.slot * it.weight }

        fun getSlotLabel(slot: Int): String {
            val side = if (slot < 0) "L" else "R"
            return "$side${abs(slot)}"
        }

        fun formatTorque(torque: Int): String =
            if (torque > 0) "+$torque" else "$torque"

        private fun opponent(player: Player): Player =
            if (player == Player.A) Player.B else Player.A

        private fun createInitialWeightPool(): Map<Player, List<Int>> = mapOf(
            Player.A to INITIAL_WEIGHTS.toList(),
            Player.B to INITIAL_WEIGHTS.toList(),
        )

        private fun scoreMove(currentTorque: Int, slot: Int, weight: Int): Pair<Int, Int> {
            val nextTorque = currentTorque + slot * weight
            val overflow = max(0, abs(nextTorque) - SAFE_TORQUE_LIMIT)
            val score = if (overflow == 0) abs(nextTorque)
            else 100 + overflow * 20 + abs(nextTorque)
            return score to nextTorque
        }

        fun pickAiMove(
            placements: List<PlacedWeight>,
            availableWeights: List<Int>,
            opponentWeights: List<Int>,
            difficulty: Difficulty,
        ): Pair<Int, Int>? { // slot to weight
            val occupiedSlots = placements.map { it.slot }.toSet()
            val currentTorque = computeTorque(placements)

            data class Candidate(
                val slot: Int,
                val weight: Int,
                val score: Int,
                val nextTorque: Int,
            )

            val candidates = mutableListOf<Candidate>()
            for (weight in availableWeights) {
                for (slot in SLOT_POSITIONS) {
                    if (slot in occupiedSlots) continue
                    val (score, nextTorque) = scoreMove(currentTorque, slot, weight)
                    candidates.add(Candidate(slot, weight, score, nextTorque))
                }
            }

            if (candidates.isEmpty()) return null

            if (difficulty == Difficulty.EASY) {
                val sorted = candidates.sortedBy { it.score }
                val weakPool = sorted.drop(sorted.size / 2)
                val choices = weakPool.ifEmpty { sorted }
                val pick = choices[Random.nextInt(choices.size)]
                return pick.slot to pick.weight
            }

            if (difficulty == Difficulty.HARD) {
                data class HardCandidate(
                    val slot: Int,
                    val weight: Int,
                    val score: Int,
                    val hardScore: Double,
                )

                var bestHard: HardCandidate? = null

                for (c in candidates) {
                    val remainingSlots = SLOT_POSITIONS.filter { it !in occupiedSlots && it != c.slot }
                    var opponentBestScore = Double.POSITIVE_INFINITY

                    for (w in opponentWeights) {
                        for (s in remainingSlots) {
                            val (score, _) = scoreMove(c.nextTorque, s, w)
                            if (score < opponentBestScore) opponentBestScore = score.toDouble()
                        }
                    }

                    val pressure = if (opponentBestScore.isFinite()) opponentBestScore else 180.0
                    val hardScore = pressure - c.score * 1.2

                    val best = bestHard
                    if (best == null ||
                        hardScore > best.hardScore ||
                        (hardScore == best.hardScore && c.score < best.score) ||
                        (hardScore == best.hardScore && c.score == best.score && abs(c.slot) < abs(best.slot))
                    ) {
                        bestHard = HardCandidate(c.slot, c.weight, c.score, hardScore)
                    }
                }

                bestHard?.let { return it.slot to it.weight }
            }

            // Normal difficulty: pick the best scoring move
            var best = candidates[0]
            for (c in candidates) {
                if (c.score < best.score ||
                    (c.score == best.score && abs(c.slot) < abs(best.slot))
                ) {
                    best = c
                }
            }
            return best.slot to best.weight
        }
    }

    // --- Observable state ---

    var mode by mutableStateOf(GameMode.LOCAL)
        private set

    var difficulty by mutableStateOf(Difficulty.NORMAL)
        private set

    var placements by mutableStateOf(listOf<PlacedWeight>())
        private set

    var currentPlayer by mutableStateOf(Player.A)
        private set

    var startingPlayer by mutableStateOf(Player.A)
        private set

    var selectedWeightByPlayer by mutableStateOf(
        mapOf(Player.A to INITIAL_WEIGHTS[0], Player.B to INITIAL_WEIGHTS[0])
    )
        private set

    var weightPool by mutableStateOf(createInitialWeightPool())
        private set

    var roundResult by mutableStateOf<RoundResult?>(null)
        private set

    var sessionWins by mutableStateOf(mapOf(Player.A to 0, Player.B to 0))
        private set

    var drawRounds by mutableIntStateOf(0)
        private set

    var roundNumber by mutableIntStateOf(1)
        private set

    var lastPlacedSlot by mutableStateOf<Int?>(null)
        private set

    var animationCycle by mutableIntStateOf(0)
        private set

    // --- Derived state ---

    val torque: Int get() = computeTorque(placements)

    val isRoundOver: Boolean get() = roundResult != null

    val placementBySlot: Map<Int, PlacedWeight>
        get() = placements.associateBy { it.slot }

    val isAiTurn: Boolean
        get() = mode == GameMode.AI && currentPlayer == Player.B && !isRoundOver

    val currentPlayerWeights: List<Int>
        get() = weightPool[currentPlayer] ?: emptyList()

    val selectedWeight: Int
        get() = selectedWeightByPlayer[currentPlayer] ?: INITIAL_WEIGHTS[0]

    val beamAngle: Float
        get() {
            val result = roundResult
            if (result is RoundResult.Tip) {
                return if (result.finalTorque > 0) 22f else -22f
            }
            val scaled = (torque.toFloat() / SAFE_TORQUE_LIMIT) * 14f
            return max(-16f, min(16f, scaled))
        }

    val statusText: String
        get() {
            val result = roundResult
            return when {
                result is RoundResult.Tip ->
                    "Round $roundNumber: Player ${result.loser} tipped the beam (${formatTorque(result.finalTorque)}). Player ${result.winner} wins."
                result is RoundResult.Stable ->
                    "Round $roundNumber: all slots filled at torque ${formatTorque(result.finalTorque)}. Draw round."
                else -> {
                    val label = if (currentPlayer == Player.A) "Player A"
                    else if (mode == GameMode.AI) "AI (Player B)" else "Player B"
                    "Round $roundNumber · $label to move · Torque ${formatTorque(torque)} (limit ±$SAFE_TORQUE_LIMIT)."
                }
            }
        }

    // --- Actions ---

    fun setGameMode(newMode: GameMode) {
        if (newMode == mode) return
        mode = newMode
        resetSession()
    }

    fun setGameDifficulty(newDifficulty: Difficulty) {
        difficulty = newDifficulty
    }

    fun selectWeight(weight: Int) {
        selectedWeightByPlayer = selectedWeightByPlayer.toMutableMap().apply {
            put(currentPlayer, weight)
        }
    }

    fun placeWeight(slot: Int, forcedWeight: Int? = null, isAiMove: Boolean = false) {
        if (isRoundOver) return
        if (placementBySlot.containsKey(slot)) return
        if (isAiTurn && !isAiMove) return

        val activeWeights = weightPool[currentPlayer] ?: return
        val weightToPlace = forcedWeight ?: selectedWeight
        val selectedIndex = activeWeights.indexOf(weightToPlace)
        if (selectedIndex < 0) return

        val placed = PlacedWeight(slot, weightToPlace, currentPlayer)
        val nextPlacements = placements + placed
        val nextTorque = computeTorque(nextPlacements)

        val nextActiveWeights = activeWeights.toMutableList().apply { removeAt(selectedIndex) }
        val nextPool = weightPool.toMutableMap().apply { put(currentPlayer, nextActiveWeights) }
        val nextSelected = selectedWeightByPlayer.toMutableMap().apply {
            put(currentPlayer, nextActiveWeights.firstOrNull() ?: INITIAL_WEIGHTS[0])
        }

        placements = nextPlacements
        weightPool = nextPool
        selectedWeightByPlayer = nextSelected
        lastPlacedSlot = slot
        animationCycle++

        if (abs(nextTorque) > SAFE_TORQUE_LIMIT) {
            val winner = opponent(currentPlayer)
            sessionWins = sessionWins.toMutableMap().apply {
                put(winner, (get(winner) ?: 0) + 1)
            }
            roundResult = RoundResult.Tip(winner, currentPlayer, nextTorque)
            return
        }

        if (nextPlacements.size == SLOT_POSITIONS.size) {
            drawRounds++
            roundResult = RoundResult.Stable(nextTorque)
            return
        }

        currentPlayer = opponent(currentPlayer)
    }

    fun startNextRound() {
        if (!isRoundOver) return

        val nextStarter = opponent(startingPlayer)
        roundNumber++
        startingPlayer = nextStarter
        currentPlayer = nextStarter
        placements = emptyList()
        weightPool = createInitialWeightPool()
        selectedWeightByPlayer = mapOf(
            Player.A to INITIAL_WEIGHTS[0],
            Player.B to INITIAL_WEIGHTS[0],
        )
        roundResult = null
        lastPlacedSlot = null
        animationCycle = 0
    }

    fun resetSession() {
        roundNumber = 1
        startingPlayer = Player.A
        currentPlayer = Player.A
        placements = emptyList()
        weightPool = createInitialWeightPool()
        selectedWeightByPlayer = mapOf(
            Player.A to INITIAL_WEIGHTS[0],
            Player.B to INITIAL_WEIGHTS[0],
        )
        roundResult = null
        lastPlacedSlot = null
        animationCycle = 0
        sessionWins = mapOf(Player.A to 0, Player.B to 0)
        drawRounds = 0
    }

    fun performAiMove() {
        if (!isAiTurn) return
        val move = pickAiMove(
            placements,
            weightPool[Player.B] ?: emptyList(),
            weightPool[Player.A] ?: emptyList(),
            difficulty,
        ) ?: return
        placeWeight(move.first, move.second, isAiMove = true)
    }
}
