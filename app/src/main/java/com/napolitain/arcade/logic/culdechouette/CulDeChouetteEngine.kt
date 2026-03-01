package com.napolitain.arcade.logic.culdechouette

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.random.Random

data class Player(val name: String, val isHuman: Boolean, var score: Int = 0)

enum class GamePhase {
    WAITING_TO_ROLL,
    SHOWING_CHOUETTES,
    SHOWING_CUL,
    REACTION_CHALLENGE,
    SHOWING_RESULT,
    AI_TURN,
    GAME_OVER
}

enum class ComboType {
    CUL_DE_CHOUETTE, CHOUETTE_VELUTE, VELUTE, CHOUETTE, SUITE, NEANT
}

private const val WINNING_SCORE = 343
private const val REACTION_TIMEOUT_MS = 3000L

class CulDeChouetteEngine {

    private var _difficulty = mutableStateOf(Difficulty.NORMAL)
    var difficulty: Difficulty
        get() = _difficulty.value
        set(value) {
            _difficulty.value = value
            reset()
        }

    val players = mutableStateListOf<Player>()

    private val _currentPlayerIndex = mutableIntStateOf(0)
    var currentPlayerIndex: Int
        get() = _currentPlayerIndex.intValue
        private set(value) { _currentPlayerIndex.intValue = value }

    private val _phase = mutableStateOf(GamePhase.WAITING_TO_ROLL)
    var phase: GamePhase
        get() = _phase.value
        private set(value) { _phase.value = value }

    private val _dice = mutableStateOf(intArrayOf(0, 0, 0))
    var dice: IntArray
        get() = _dice.value
        private set(value) { _dice.value = value }

    private val _currentCombo = mutableStateOf<ComboType?>(null)
    var currentCombo: ComboType?
        get() = _currentCombo.value
        private set(value) { _currentCombo.value = value }

    private val _currentPoints = mutableIntStateOf(0)
    var currentPoints: Int
        get() = _currentPoints.intValue
        private set(value) { _currentPoints.intValue = value }

    private val _comboText = mutableStateOf("")
    var comboText: String
        get() = _comboText.value
        private set(value) { _comboText.value = value }

    private val _reactionTimeMs = mutableStateOf(0L)
    var reactionTimeMs: Long
        get() = _reactionTimeMs.value
        private set(value) { _reactionTimeMs.value = value }

    private val _reactionWinnerIndex = mutableStateOf<Int?>(null)
    var reactionWinnerIndex: Int?
        get() = _reactionWinnerIndex.value
        private set(value) { _reactionWinnerIndex.value = value }

    private val _gameOver = mutableStateOf(false)
    var gameOver: Boolean
        get() = _gameOver.value
        private set(value) { _gameOver.value = value }

    private val _winnerIndex = mutableStateOf<Int?>(null)
    var winnerIndex: Int?
        get() = _winnerIndex.value
        private set(value) { _winnerIndex.value = value }

    private var reactionStartTime: Long = 0L

    init {
        reset()
    }

    fun reset() {
        players.clear()
        players.add(Player("You", isHuman = true))
        val aiCount = when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.NORMAL -> 2
            Difficulty.HARD -> 3
        }
        for (i in 1..aiCount) {
            players.add(Player("AI $i", isHuman = false))
        }
        currentPlayerIndex = 0
        phase = GamePhase.WAITING_TO_ROLL
        dice = intArrayOf(0, 0, 0)
        currentCombo = null
        currentPoints = 0
        comboText = ""
        reactionTimeMs = 0L
        reactionWinnerIndex = null
        gameOver = false
        winnerIndex = null
        reactionStartTime = 0L
    }

    fun rollChouettes() {
        if (phase != GamePhase.WAITING_TO_ROLL) return
        val d1 = rollDie()
        val d2 = rollDie()
        dice = intArrayOf(d1, d2, 0)
        phase = GamePhase.SHOWING_CHOUETTES
    }

    fun rollCul() {
        if (phase != GamePhase.SHOWING_CHOUETTES) return
        val d3 = rollDie()
        dice = intArrayOf(dice[0], dice[1], d3)
        evaluateCombo()
        if (currentCombo == ComboType.SUITE || currentCombo == ComboType.CHOUETTE_VELUTE) {
            reactionStartTime = System.currentTimeMillis()
            phase = GamePhase.REACTION_CHALLENGE
        } else {
            applyPoints(currentPlayerIndex)
            phase = GamePhase.SHOWING_CUL
        }
    }

    fun reactToChallenge() {
        if (phase != GamePhase.REACTION_CHALLENGE) return
        val elapsed = System.currentTimeMillis() - reactionStartTime
        reactionTimeMs = elapsed

        if (elapsed > REACTION_TIMEOUT_MS) {
            // Human too slow, AI wins the challenge
            handleReactionResult(humanWon = false)
        } else {
            // Compare with best AI reaction time
            val bestAiTime = generateAiReactionTime()
            if (elapsed <= bestAiTime) {
                handleReactionResult(humanWon = true)
            } else {
                handleReactionResult(humanWon = false)
            }
        }
        phase = GamePhase.SHOWING_CUL
    }

    fun advancePhase() {
        when (phase) {
            GamePhase.SHOWING_CUL -> {
                phase = GamePhase.SHOWING_RESULT
            }
            GamePhase.SHOWING_RESULT -> {
                if (checkGameOver()) return
                moveToNextPlayer()
                val current = players[currentPlayerIndex]
                if (current.isHuman) {
                    phase = GamePhase.WAITING_TO_ROLL
                } else {
                    phase = GamePhase.AI_TURN
                }
            }
            GamePhase.AI_TURN -> {
                // After AI turn display, advance to next
                if (checkGameOver()) return
                moveToNextPlayer()
                val current = players[currentPlayerIndex]
                if (current.isHuman) {
                    phase = GamePhase.WAITING_TO_ROLL
                } else {
                    phase = GamePhase.AI_TURN
                }
            }
            else -> {}
        }
    }

    /** AI rolls automatically. Returns points scored. */
    fun processAiTurn(): Int {
        if (phase != GamePhase.AI_TURN) return 0
        val d1 = rollDie()
        val d2 = rollDie()
        val d3 = rollDie()
        dice = intArrayOf(d1, d2, d3)
        evaluateCombo()

        if (currentCombo == ComboType.SUITE || currentCombo == ComboType.CHOUETTE_VELUTE) {
            // AI always wins reaction on its own turn (closest AI reacts)
            reactionWinnerIndex = currentPlayerIndex
            if (currentCombo == ComboType.SUITE) {
                // Suite: last to react loses 10 points. Pick a random other player to penalize.
                val loserIndex = pickSuiteLoser(currentPlayerIndex)
                players[loserIndex] = players[loserIndex].copy(
                    score = players[loserIndex].score - 10
                )
                comboText = "Suite ${formatDice()}! ${players[loserIndex].name} loses 10 pts!"
                currentPoints = 0
            } else {
                // Chouette-Velute: AI current player gets the points
                applyPoints(currentPlayerIndex)
            }
        } else {
            applyPoints(currentPlayerIndex)
        }
        return currentPoints
    }

    // --- Internal Logic ---

    internal fun evaluateCombo() {
        val sorted = dice.sorted()
        val a = sorted[0]
        val b = sorted[1]
        val c = sorted[2]

        val allSame = a == b && b == c
        val hasPair = a == b || b == c || a == c
        val isVelute = (a + b == c)
        val isSuite = (c - b == 1 && b - a == 1)

        when {
            allSame -> {
                currentCombo = ComboType.CUL_DE_CHOUETTE
                currentPoints = 40 + 10 * a
                comboText = "Cul de Chouette de $a!"
            }
            hasPair && isVelute -> {
                currentCombo = ComboType.CHOUETTE_VELUTE
                currentPoints = 2 * c * c
                comboText = "Chouette-Velute de $c!"
            }
            isVelute -> {
                currentCombo = ComboType.VELUTE
                currentPoints = 2 * c * c
                comboText = "Velute de $c!"
            }
            isSuite -> {
                currentCombo = ComboType.SUITE
                currentPoints = 0
                comboText = "Suite ${a}-${b}-${c}! Grelotte ça picote!"
            }
            hasPair -> {
                val pairValue = when {
                    a == b -> a
                    b == c -> b
                    else -> a // a == c
                }
                currentCombo = ComboType.CHOUETTE
                currentPoints = pairValue * pairValue
                comboText = "Chouette de $pairValue!"
            }
            else -> {
                currentCombo = ComboType.NEANT
                currentPoints = 0
                comboText = "Néant!"
            }
        }
    }

    private fun applyPoints(playerIndex: Int) {
        if (currentPoints > 0) {
            players[playerIndex] = players[playerIndex].copy(
                score = players[playerIndex].score + currentPoints
            )
        }
    }

    private fun handleReactionResult(humanWon: Boolean) {
        when (currentCombo) {
            ComboType.SUITE -> {
                if (humanWon) {
                    // Human reacted first → pick an AI to lose 10 points
                    val loserIndex = pickSuiteLoser(0) // 0 = human index
                    players[loserIndex] = players[loserIndex].copy(
                        score = players[loserIndex].score - 10
                    )
                    reactionWinnerIndex = 0
                    comboText = "Suite ${formatDice()}! ${players[loserIndex].name} loses 10 pts!"
                } else {
                    // Human was slowest → human loses 10 points
                    players[0] = players[0].copy(score = players[0].score - 10)
                    reactionWinnerIndex = pickRandomAiIndex()
                    comboText = "Suite ${formatDice()}! You lose 10 pts!"
                }
                currentPoints = 0
            }
            ComboType.CHOUETTE_VELUTE -> {
                if (humanWon) {
                    reactionWinnerIndex = 0
                    applyPoints(0)
                    comboText = "${comboText} You grab it!"
                } else {
                    val aiWinner = pickRandomAiIndex()
                    reactionWinnerIndex = aiWinner
                    applyPoints(aiWinner)
                    comboText = "${comboText} ${players[aiWinner].name} grabs it!"
                }
            }
            else -> {}
        }
    }

    private fun generateAiReactionTime(): Long {
        val (min, max) = when (difficulty) {
            Difficulty.EASY -> 800L to 1500L
            Difficulty.NORMAL -> 400L to 900L
            Difficulty.HARD -> 200L to 500L
        }
        return Random.nextLong(min, max + 1)
    }

    private fun pickSuiteLoser(excludeIndex: Int): Int {
        val candidates = players.indices.filter { it != excludeIndex }
        return candidates.random()
    }

    private fun pickRandomAiIndex(): Int {
        val aiIndices = players.indices.filter { !players[it].isHuman }
        return aiIndices.random()
    }

    private fun moveToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
    }

    private fun checkGameOver(): Boolean {
        val winner = players.indices.firstOrNull { players[it].score >= WINNING_SCORE }
        if (winner != null) {
            winnerIndex = winner
            gameOver = true
            phase = GamePhase.GAME_OVER
            return true
        }
        return false
    }

    private fun rollDie(): Int = Random.nextInt(1, 7)

    private fun formatDice(): String {
        val sorted = dice.sorted()
        return "${sorted[0]}-${sorted[1]}-${sorted[2]}"
    }
}
