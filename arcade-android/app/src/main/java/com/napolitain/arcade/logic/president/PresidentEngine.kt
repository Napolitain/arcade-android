package com.napolitain.arcade.logic.president

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.random.Random

enum class Suit(val symbol: String) {
    CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥"), SPADES("♠")
}

/** Rank values: 3=0, 4=1, …, K=10, A=11, 2=12 (highest). */
enum class Rank(val display: String, val value: Int) {
    THREE("3", 0), FOUR("4", 1), FIVE("5", 2), SIX("6", 3),
    SEVEN("7", 4), EIGHT("8", 5), NINE("9", 6), TEN("10", 7),
    JACK("J", 8), QUEEN("Q", 9), KING("K", 10), ACE("A", 11),
    TWO("2", 12);
}

data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    override fun compareTo(other: Card): Int = rank.value.compareTo(other.rank.value)
    val label: String get() = "${rank.display}${suit.symbol}"
}

enum class Title(val display: String) {
    PRESIDENT("President"),
    VICE_PRESIDENT("Vice President"),
    NEUTRAL("Neutral"),
    SCUM("Scum"),
    NONE("")
}

data class Player(
    val name: String,
    val hand: MutableList<Card> = mutableListOf(),
    var title: Title = Title.NONE,
    var finished: Boolean = false,
    var finishOrder: Int = -1,
    val isHuman: Boolean = false,
)

class PresidentEngine {

    companion object {
        private val TITLE_BY_ORDER = listOf(Title.PRESIDENT, Title.VICE_PRESIDENT, Title.NEUTRAL, Title.SCUM)

        private fun buildDeck(): List<Card> {
            val deck = mutableListOf<Card>()
            for (suit in Suit.entries) for (rank in Rank.entries) deck.add(Card(rank, suit))
            return deck
        }
    }

    // Observable state
    val players = mutableStateListOf<Player>()
    val currentPile = mutableStateListOf<Card>()
    var currentPlayerIndex by mutableIntStateOf(0)
        private set
    var pileCount by mutableIntStateOf(0)
        private set
    var pileRank by mutableStateOf<Rank?>(null)
        private set
    var roundNumber by mutableIntStateOf(1)
        private set
    var isRevolution by mutableStateOf(false)
        private set
    var roundOver by mutableStateOf(false)
        private set
    var passCount by mutableIntStateOf(0)
        private set
    var lastPlayedBy by mutableIntStateOf(-1)
        private set
    var lastAction by mutableStateOf("")
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)
    var gamePhase by mutableStateOf(GamePhase.PLAYING)
        private set

    enum class GamePhase { PLAYING, ROUND_END, EXCHANGING }

    // Cards played per rank (for hard AI card counting)
    private val playedCounts = IntArray(Rank.entries.size)

    val humanPlayableCards: Set<Int>
        get() {
            val human = players.getOrNull(0) ?: return emptySet()
            if (human.finished) return emptySet()
            if (currentPlayerIndex != 0) return emptySet()
            return getPlayableIndices(human)
        }

    init {
        setupGame()
    }

    // ── Public API ──────────────────────────────────────────────

    fun playCards(indices: List<Int>) {
        val player = players[currentPlayerIndex]
        if (player.finished || indices.isEmpty()) return

        val cards = indices.sortedDescending().map { player.hand[it] }
        // Validate: all same rank
        val rank = cards[0].rank
        if (!cards.all { it.rank == rank }) return
        // Validate count matches pile count (or pile is empty)
        if (pileCount != 0 && cards.size != pileCount) return
        // Validate rank beats current pile
        if (pileRank != null && !beats(rank, pileRank!!)) return

        // Remove from hand
        indices.sortedDescending().forEach { player.hand.removeAt(it) }
        // Track for card counting
        cards.forEach { playedCounts[it.rank.ordinal]++ }

        // Four-of-a-kind = revolution
        if (cards.size == 4) {
            isRevolution = !isRevolution
        }

        currentPile.clear()
        currentPile.addAll(cards)
        pileCount = cards.size
        pileRank = rank
        passCount = 0
        lastPlayedBy = currentPlayerIndex
        lastAction = "${player.name} played ${cards.joinToString { it.label }}"

        // Check if player finished
        if (player.hand.isEmpty()) {
            player.finished = true
            player.finishOrder = players.count { it.finished }
        }

        // Check round end
        if (players.count { it.finished } >= 3) {
            finishRound()
            return
        }

        advanceToNextPlayer()
    }

    fun pass() {
        val player = players[currentPlayerIndex]
        if (player.finished) {
            advanceToNextPlayer()
            return
        }
        passCount++
        lastAction = "${player.name} passed"

        // Count active (non-finished) players
        val activePlayers = players.count { !it.finished }
        if (passCount >= activePlayers - 1 && lastPlayedBy >= 0) {
            // All others passed, clear pile
            clearPile()
            currentPlayerIndex = lastPlayedBy
            // If that player finished, advance
            if (players[currentPlayerIndex].finished) {
                advanceToNextPlayer()
            }
            return
        }
        advanceToNextPlayer()
    }

    fun newRound() {
        roundNumber++
        isRevolution = false
        gamePhase = GamePhase.PLAYING
        playedCounts.fill(0)

        // Save titles
        val savedTitles = players.map { it.title }

        // Reset player state
        players.forEach {
            it.hand.clear()
            it.finished = false
            it.finishOrder = -1
        }

        dealCards()
        doCardExchange(savedTitles)
        sortAllHands()

        currentPile.clear()
        pileCount = 0
        pileRank = null
        passCount = 0
        lastPlayedBy = -1
        lastAction = ""

        // Find player with 3 of clubs
        currentPlayerIndex = findThreeOfClubsHolder()
    }

    fun reset() {
        roundNumber = 1
        isRevolution = false
        gamePhase = GamePhase.PLAYING
        playedCounts.fill(0)
        players.clear()
        setupGame()
    }

    // ── Internal ────────────────────────────────────────────────

    private fun setupGame() {
        players.addAll(
            listOf(
                Player("You", isHuman = true),
                Player("Alice"),
                Player("Bob"),
                Player("Carol"),
            ),
        )
        dealCards()
        sortAllHands()
        currentPile.clear()
        pileCount = 0
        pileRank = null
        passCount = 0
        lastPlayedBy = -1
        lastAction = ""
        roundOver = false
        gamePhase = GamePhase.PLAYING
        currentPlayerIndex = findThreeOfClubsHolder()
    }

    private fun dealCards() {
        val deck = buildDeck().shuffled(Random)
        for (i in deck.indices) {
            players[i % 4].hand.add(deck[i])
        }
    }

    private fun sortAllHands() {
        players.forEach { p ->
            p.hand.sortWith(compareBy { effectiveValue(it.rank) })
        }
    }

    private fun effectiveValue(rank: Rank): Int =
        if (isRevolution) (Rank.entries.size - 1 - rank.value) else rank.value

    private fun beats(attacker: Rank, defender: Rank): Boolean {
        val aVal = effectiveValue(attacker)
        val dVal = effectiveValue(defender)
        return aVal > dVal
    }

    private fun findThreeOfClubsHolder(): Int {
        val target = Card(Rank.THREE, Suit.CLUBS)
        for (i in players.indices) {
            if (players[i].hand.any { it.rank == target.rank && it.suit == target.suit }) return i
        }
        return 0
    }

    private fun clearPile() {
        currentPile.clear()
        pileCount = 0
        pileRank = null
        passCount = 0
    }

    private fun advanceToNextPlayer() {
        var next = (currentPlayerIndex + 1) % 4
        var safety = 0
        while (players[next].finished && safety < 4) {
            next = (next + 1) % 4
            safety++
        }
        currentPlayerIndex = next
    }

    private fun finishRound() {
        // Last remaining player is scum
        val unfinished = players.firstOrNull { !it.finished }
        if (unfinished != null) {
            unfinished.finished = true
            unfinished.finishOrder = 4
        }

        // Assign titles
        val ordered = players.sortedBy { it.finishOrder }
        ordered.forEachIndexed { i, p -> p.title = TITLE_BY_ORDER[i] }

        roundOver = true
        gamePhase = GamePhase.ROUND_END
        lastAction = "Round $roundNumber complete!"
        clearPile()
    }

    private fun doCardExchange(previousTitles: List<Title>) {
        // Find president and scum by previous title
        val presidentIdx = previousTitles.indexOfFirst { it == Title.PRESIDENT }
        val scumIdx = previousTitles.indexOfFirst { it == Title.SCUM }
        val vpIdx = previousTitles.indexOfFirst { it == Title.VICE_PRESIDENT }
        val neutralIdx = previousTitles.indexOfFirst { it == Title.NEUTRAL }

        if (presidentIdx < 0 || scumIdx < 0) return

        // Scum gives 2 best cards to President
        val scumHand = players[scumIdx].hand
        scumHand.sortWith(compareByDescending { effectiveValue(it.rank) })
        val best2 = scumHand.take(2).toList()
        scumHand.removeAll(best2.toSet())

        // President gives 2 worst cards to Scum
        val presHand = players[presidentIdx].hand
        presHand.sortWith(compareBy { effectiveValue(it.rank) })
        val worst2 = presHand.take(2).toList()
        presHand.removeAll(worst2.toSet())

        players[presidentIdx].hand.addAll(best2)
        players[scumIdx].hand.addAll(worst2)

        // VP and Neutral exchange 1 card
        if (vpIdx >= 0 && neutralIdx >= 0) {
            val neutralHand = players[neutralIdx].hand
            neutralHand.sortWith(compareByDescending { effectiveValue(it.rank) })
            val best1 = neutralHand.take(1).toList()
            neutralHand.removeAll(best1.toSet())

            val vpHand = players[vpIdx].hand
            vpHand.sortWith(compareBy { effectiveValue(it.rank) })
            val worst1 = vpHand.take(1).toList()
            vpHand.removeAll(worst1.toSet())

            players[vpIdx].hand.addAll(best1)
            players[neutralIdx].hand.addAll(worst1)
        }
    }

    private fun getPlayableIndices(player: Player): Set<Int> {
        val hand = player.hand
        val result = mutableSetOf<Int>()
        val requiredCount = if (pileCount == 0) null else pileCount
        val rankGroups = hand.withIndex().groupBy { it.value.rank }

        for ((rank, group) in rankGroups) {
            if (pileRank != null && !beats(rank, pileRank!!)) continue

            if (requiredCount == null) {
                // Can play any group size (1, 2, 3, or 4)
                group.forEach { result.add(it.index) }
            } else {
                if (group.size >= requiredCount) {
                    group.forEach { result.add(it.index) }
                }
            }
        }
        return result
    }

    // ── AI Logic ────────────────────────────────────────────────

    fun triggerAiMove() {
        val player = players[currentPlayerIndex]
        if (player.isHuman || player.finished) return

        val move = when (difficulty) {
            Difficulty.EASY -> getEasyAiMove(player)
            Difficulty.NORMAL -> getNormalAiMove(player)
            Difficulty.HARD -> getHardAiMove(player)
        }

        if (move == null) {
            pass()
        } else {
            playCards(move)
        }
    }

    /** Easy AI: play lowest valid combination. */
    private fun getEasyAiMove(player: Player): List<Int>? {
        val combos = getValidCombinations(player)
        return combos.minByOrNull { combo ->
            combo.sumOf { effectiveValue(player.hand[it].rank) }
        }
    }

    /** Normal AI: save 2s and pairs, play strategically. */
    private fun getNormalAiMove(player: Player): List<Int>? {
        val combos = getValidCombinations(player)
        if (combos.isEmpty()) return null

        // If leading (empty pile), play lowest single or pair
        if (pileRank == null) {
            return combos.minByOrNull { combo ->
                combo.sumOf { effectiveValue(player.hand[it].rank) }
            }
        }

        // Try to save 2s unless forced
        val nonTwoCombos = combos.filter { combo ->
            combo.none { player.hand[it].rank == Rank.TWO }
        }
        val pool = nonTwoCombos.ifEmpty { combos }

        // Play lowest valid
        return pool.minByOrNull { combo ->
            combo.sumOf { effectiveValue(player.hand[it].rank) }
        }
    }

    /** Hard AI: card counting, hold power cards, time bombs. */
    private fun getHardAiMove(player: Player): List<Int>? {
        val combos = getValidCombinations(player)
        if (combos.isEmpty()) return null

        // Check for four-of-a-kind (revolution bomb)
        val fourOfAKind = combos.firstOrNull { it.size == 4 }

        // If we're losing (many cards) and have a bomb, consider playing it
        if (fourOfAKind != null && player.hand.size > 8) {
            return fourOfAKind
        }

        // If leading, play lowest non-power card
        if (pileRank == null) {
            val lowCombos = combos.filter { combo ->
                combo.all {
                    val rank = player.hand[it].rank
                    rank != Rank.TWO && rank != Rank.ACE
                }
            }
            return (lowCombos.ifEmpty { combos }).minByOrNull { combo ->
                combo.sumOf { effectiveValue(player.hand[it].rank) }
            }
        }

        // Count remaining cards of each rank (card counting)
        val totalPerRank = 4
        val remainingPerRank = Rank.entries.map { rank ->
            totalPerRank - playedCounts[rank.ordinal] - player.hand.count { it.rank == rank }
        }

        // If few cards remain of the pile rank's neighbours, play conservatively
        // Save 2s and Aces unless necessary
        val saveCombos = combos.filter { combo ->
            combo.none {
                val r = player.hand[it].rank
                r == Rank.TWO || (r == Rank.ACE && player.hand.size > 4)
            }
        }
        val pool = saveCombos.ifEmpty { combos }

        // Play the lowest valid to conserve strong cards
        return pool.minByOrNull { combo ->
            combo.sumOf { effectiveValue(player.hand[it].rank) }
        }
    }

    /** Get all valid combinations the player can play. */
    private fun getValidCombinations(player: Player): List<List<Int>> {
        val hand = player.hand
        val rankGroups = hand.withIndex().groupBy { it.value.rank }
        val result = mutableListOf<List<Int>>()

        for ((rank, group) in rankGroups) {
            if (pileRank != null && !beats(rank, pileRank!!)) continue
            val indices = group.map { it.index }

            if (pileCount == 0) {
                // Can start with singles, pairs, triples, or quads
                for (size in 1..indices.size) {
                    // For singles, each card is a separate combo
                    if (size == 1) {
                        indices.forEach { result.add(listOf(it)) }
                    } else {
                        // Generate combinations of 'size' from indices
                        result.addAll(combinations(indices, size))
                    }
                }
            } else {
                if (indices.size >= pileCount) {
                    result.addAll(combinations(indices, pileCount))
                }
            }
        }
        return result
    }

    private fun combinations(list: List<Int>, k: Int): List<List<Int>> {
        if (k == 0) return listOf(emptyList())
        if (k > list.size) return emptyList()
        if (k == list.size) return listOf(list)
        val result = mutableListOf<List<Int>>()
        fun recurse(start: Int, current: List<Int>) {
            if (current.size == k) {
                result.add(current)
                return
            }
            for (i in start until list.size) {
                recurse(i + 1, current + list[i])
            }
        }
        recurse(0, emptyList())
        return result
    }
}
