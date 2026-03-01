package com.napolitain.arcade.logic.rummy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.math.abs
import kotlin.random.Random

// ── Card types ──────────────────────────────────────────────────────────────

enum class Suit(val symbol: String) {
    SPADES("♠"), HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣");
    val isRed: Boolean get() = this == HEARTS || this == DIAMONDS
}

enum class Rank(val points: Int, val symbol: String, val order: Int) {
    ACE(1, "A", 1), TWO(2, "2", 2), THREE(3, "3", 3), FOUR(4, "4", 4),
    FIVE(5, "5", 5), SIX(6, "6", 6), SEVEN(7, "7", 7), EIGHT(8, "8", 8),
    NINE(9, "9", 9), TEN(10, "10", 10), JACK(10, "J", 11), QUEEN(10, "Q", 12),
    KING(10, "K", 13)
}

data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    val points: Int get() = rank.points
    override fun compareTo(other: Card): Int {
        val s = suit.ordinal.compareTo(other.suit.ordinal)
        return if (s != 0) s else rank.order.compareTo(other.rank.order)
    }
}

data class Meld(val cards: List<Card>, val isRun: Boolean)

enum class Phase {
    DRAW, DISCARD, OPPONENT_TURN, KNOCK_DECISION, ROUND_OVER, GAME_OVER
}

// ── Engine ──────────────────────────────────────────────────────────────────

class RummyEngine {

    companion object {
        private const val WINNING_SCORE = 100
        private const val GIN_BONUS = 25
        private const val UNDERCUT_BONUS = 25
        private const val KNOCK_THRESHOLD = 10

        fun createDeck(): MutableList<Card> {
            val deck = mutableListOf<Card>()
            for (suit in Suit.entries) for (rank in Rank.entries) deck.add(Card(rank, suit))
            return deck
        }

        /** Enumerate every valid meld (set or run) present in [hand]. */
        fun findAllMelds(hand: List<Card>): List<Meld> {
            val melds = mutableListOf<Meld>()

            // Sets: 3–4 cards of the same rank
            hand.groupBy { it.rank }.forEach { (_, cards) ->
                if (cards.size >= 3) {
                    for (i in cards.indices)
                        for (j in i + 1 until cards.size)
                            for (k in j + 1 until cards.size)
                                melds.add(Meld(listOf(cards[i], cards[j], cards[k]), isRun = false))
                    if (cards.size >= 4) melds.add(Meld(cards.toList(), isRun = false))
                }
            }

            // Runs: 3+ consecutive cards of the same suit
            hand.groupBy { it.suit }.forEach { (_, cards) ->
                val sorted = cards.sortedBy { it.rank.order }
                for (start in sorted.indices) {
                    val run = mutableListOf(sorted[start])
                    for (next in start + 1 until sorted.size) {
                        if (sorted[next].rank.order == run.last().rank.order + 1) {
                            run.add(sorted[next])
                            if (run.size >= 3) melds.add(Meld(run.toList(), isRun = true))
                        } else break
                    }
                }
            }
            return melds
        }

        /** Find the combination of non-overlapping melds that minimises deadwood. */
        fun findOptimalMelds(hand: List<Card>): Pair<List<Meld>, List<Card>> {
            val allMelds = findAllMelds(hand)
            var bestMelds = emptyList<Meld>()
            var bestDw = hand.sumOf { it.points }

            fun backtrack(idx: Int, used: Set<Card>, current: List<Meld>) {
                val dw = hand.filter { it !in used }.sumOf { it.points }
                if (dw < bestDw) { bestDw = dw; bestMelds = current.toList() }
                if (dw == 0) return
                for (i in idx until allMelds.size) {
                    if (allMelds[i].cards.none { it in used })
                        backtrack(i + 1, used + allMelds[i].cards.toSet(), current + allMelds[i])
                }
            }

            backtrack(0, emptySet(), emptyList())
            val meldedCards = bestMelds.flatMap { it.cards }.toSet()
            return bestMelds to hand.filter { it !in meldedCards }
        }
    }

    // ── Observable state ────────────────────────────────────────────────────

    val playerHand = mutableStateListOf<Card>()
    private val _opponentHand = mutableListOf<Card>()

    var opponentCardCount by mutableStateOf(0); private set
    var stockCount by mutableStateOf(0); private set
    var discardTop by mutableStateOf<Card?>(null); private set
    var playerMelds by mutableStateOf<List<Meld>>(emptyList()); private set
    var playerDeadwood by mutableStateOf(0); private set
    var playerScore by mutableStateOf(0); private set
    var opponentScore by mutableStateOf(0); private set
    var phase by mutableStateOf(Phase.DRAW); private set
    var canKnock by mutableStateOf(false); private set
    var isGin by mutableStateOf(false); private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)
    var roundMessage by mutableStateOf(""); private set
    var opponentMelds by mutableStateOf<List<Meld>>(emptyList()); private set
    var opponentDeadwoodCards by mutableStateOf<List<Card>>(emptyList()); private set
    var selectedCardIndex by mutableStateOf(-1); private set
    val scores: Pair<Int, Int> get() = playerScore to opponentScore

    // Internal
    private var stock = mutableListOf<Card>()
    private var discardPile = mutableListOf<Card>()
    private val aiPickedFromDiscard = mutableSetOf<Card>()
    private val aiDiscarded = mutableSetOf<Card>()

    init { newRound() }

    // ── Public API ──────────────────────────────────────────────────────────

    fun drawFromStock() {
        if (phase != Phase.DRAW || stock.isEmpty()) return
        playerHand.add(stock.removeFirst())
        updateStockCount()
        sortAndUpdatePlayer()
        phase = Phase.DISCARD
    }

    fun drawFromDiscard() {
        if (phase != Phase.DRAW || discardPile.isEmpty()) return
        playerHand.add(discardPile.removeLast())
        updateDiscardTop()
        sortAndUpdatePlayer()
        phase = Phase.DISCARD
    }

    fun selectCard(index: Int) {
        if (phase == Phase.DISCARD) {
            selectedCardIndex = if (selectedCardIndex == index) -1 else index
        }
    }

    fun discard(index: Int) {
        if (phase != Phase.DISCARD || index !in playerHand.indices) return
        discardPile.add(playerHand.removeAt(index))
        updateDiscardTop()
        updatePlayerMelds()
        selectedCardIndex = -1

        if (stock.isEmpty()) {
            roundMessage = "Stock exhausted — round is a draw."
            phase = Phase.ROUND_OVER
            return
        }
        phase = if (canKnock || isGin) Phase.KNOCK_DECISION else Phase.OPPONENT_TURN
    }

    fun knock() {
        if (phase != Phase.KNOCK_DECISION || (!canKnock && !isGin)) return
        resolveKnock(playerKnocks = true)
    }

    fun pass() {
        if (phase != Phase.KNOCK_DECISION) return
        phase = Phase.OPPONENT_TURN
    }

    fun triggerAiTurn() {
        if (phase != Phase.OPPONENT_TURN) return
        performAiTurn()
    }

    fun newRound() {
        stock = createDeck().also { it.shuffle() }
        discardPile.clear()
        playerHand.clear()
        _opponentHand.clear()
        repeat(10) { playerHand.add(stock.removeFirst()); _opponentHand.add(stock.removeFirst()) }
        discardPile.add(stock.removeFirst())
        sortPlayerHand()
        aiPickedFromDiscard.clear(); aiDiscarded.clear()
        updateStockCount(); updateDiscardTop(); updatePlayerMelds(); updateOpponentCount()
        opponentMelds = emptyList(); opponentDeadwoodCards = emptyList()
        roundMessage = ""; selectedCardIndex = -1
        phase = Phase.DRAW
    }

    fun reset() {
        playerScore = 0; opponentScore = 0
        newRound()
    }

    // ── AI ──────────────────────────────────────────────────────────────────

    private fun performAiTurn() {
        aiDraw()
        aiDiscard()
        updateOpponentCount()

        val (_, deadwood) = findOptimalMelds(_opponentHand)
        val aiDw = deadwood.sumOf { it.points }
        if (aiDw == 0 || (aiDw <= KNOCK_THRESHOLD && shouldAiKnock(aiDw))) {
            resolveKnock(playerKnocks = false); return
        }
        if (stock.isEmpty()) {
            roundMessage = "Stock exhausted — round is a draw."
            phase = Phase.ROUND_OVER; return
        }
        phase = Phase.DRAW
    }

    private fun aiDraw() {
        val top = discardPile.lastOrNull()
        val drawDiscard = top != null && difficulty != Difficulty.EASY &&
            wouldReduceDeadwood(_opponentHand, top)
        if (drawDiscard && top != null) {
            _opponentHand.add(discardPile.removeLast())
            aiPickedFromDiscard.add(top)
            updateDiscardTop()
        } else if (stock.isNotEmpty()) {
            _opponentHand.add(stock.removeFirst())
        }
        updateStockCount()
    }

    private fun aiDiscard() {
        if (_opponentHand.isEmpty()) return
        val idx = when (difficulty) {
            Difficulty.EASY -> Random.nextInt(_opponentHand.size)
            Difficulty.NORMAL -> findBestDiscard(_opponentHand)
            Difficulty.HARD -> findSmartDiscard(_opponentHand)
        }
        val card = _opponentHand.removeAt(idx)
        aiDiscarded.add(card)
        discardPile.add(card)
        updateDiscardTop()
    }

    private fun wouldReduceDeadwood(hand: List<Card>, card: Card): Boolean {
        val currentDw = findOptimalMelds(hand).second.sumOf { it.points }
        val extended = hand + card
        return extended.indices.any { i ->
            val test = extended.toMutableList().apply { removeAt(i) }
            findOptimalMelds(test).second.sumOf { it.points } < currentDw
        }
    }

    private fun findBestDiscard(hand: List<Card>): Int {
        var best = 0; var bestDw = Int.MAX_VALUE
        for (i in hand.indices) {
            val test = hand.toMutableList().apply { removeAt(i) }
            val dw = findOptimalMelds(test).second.sumOf { it.points }
            if (dw < bestDw) { bestDw = dw; best = i }
        }
        return best
    }

    private fun findSmartDiscard(hand: List<Card>): Int {
        var best = 0; var bestScore = Int.MIN_VALUE
        for (i in hand.indices) {
            val card = hand[i]
            val test = hand.toMutableList().apply { removeAt(i) }
            val dw = findOptimalMelds(test).second.sumOf { it.points }
            var penalty = 0
            if (aiPickedFromDiscard.any { it.rank == card.rank }) penalty += 20
            if (aiPickedFromDiscard.any { it.suit == card.suit && abs(it.rank.order - card.rank.order) <= 2 }) penalty += 15
            var bonus = 0
            if (aiDiscarded.any { it.rank == card.rank }) bonus += 10
            val score = -dw - penalty + bonus
            if (score > bestScore) { bestScore = score; best = i }
        }
        return best
    }

    private fun shouldAiKnock(dw: Int): Boolean = when (difficulty) {
        Difficulty.EASY -> true
        Difficulty.NORMAL -> dw <= 5 || (dw <= KNOCK_THRESHOLD && Random.nextFloat() < 0.5f)
        Difficulty.HARD -> dw <= 3 || (dw <= 7 && Random.nextFloat() < 0.6f)
    }

    // ── Knock resolution ────────────────────────────────────────────────────

    private fun resolveKnock(playerKnocks: Boolean) {
        val knockerHand = if (playerKnocks) playerHand.toList() else _opponentHand.toList()
        val defenderHand = if (playerKnocks) _opponentHand.toList() else playerHand.toList()

        val (kMelds, kDeadwood) = findOptimalMelds(knockerHand)
        val kDw = kDeadwood.sumOf { it.points }
        val gin = kDw == 0

        val (dMelds, dDeadwoodRaw) = findOptimalMelds(defenderHand)
        val finalDefDw = if (gin) dDeadwoodRaw else computeLayoffs(dDeadwoodRaw, kMelds)
        val dDw = finalDefDw.sumOf { it.points }

        // Reveal opponent info
        if (playerKnocks) {
            opponentMelds = dMelds; opponentDeadwoodCards = finalDefDw
        } else {
            opponentMelds = kMelds; opponentDeadwoodCards = kDeadwood
        }

        // Score
        when {
            gin -> {
                val pts = GIN_BONUS + dDw
                if (playerKnocks) { playerScore += pts; roundMessage = "GIN! You score $pts points." }
                else { opponentScore += pts; roundMessage = "Opponent GIN! They score $pts points." }
            }
            dDw <= kDw -> { // undercut
                val pts = UNDERCUT_BONUS + (kDw - dDw)
                if (playerKnocks) { opponentScore += pts; roundMessage = "UNDERCUT! Opponent scores $pts." }
                else { playerScore += pts; roundMessage = "UNDERCUT! You score $pts points." }
            }
            else -> {
                val pts = dDw - kDw
                if (playerKnocks) { playerScore += pts; roundMessage = "You knocked — score $pts points." }
                else { opponentScore += pts; roundMessage = "Opponent knocked — scores $pts points." }
            }
        }

        phase = if (playerScore >= WINNING_SCORE || opponentScore >= WINNING_SCORE) {
            val w = if (playerScore >= opponentScore) "You" else "Opponent"
            roundMessage += " $w won the game!"
            Phase.GAME_OVER
        } else Phase.ROUND_OVER
    }

    /**
     * After a knock (non-gin), the defender may lay off deadwood cards on the
     * knocker's melds.  Melds are extended in-place so chained layoffs work
     * (e.g. laying off 4♣ extends run 5-6-7♣ so 3♣ can follow).
     */
    private fun computeLayoffs(deadwood: List<Card>, knockerMelds: List<Meld>): List<Card> {
        val remaining = deadwood.toMutableList()
        val extended = knockerMelds.map { it.cards.toMutableList() to it.isRun }
        var changed = true
        while (changed) {
            changed = false
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val card = iter.next()
                for ((meldCards, isRun) in extended) {
                    if (canLayOff(card, meldCards, isRun)) {
                        meldCards.add(card); iter.remove(); changed = true; break
                    }
                }
            }
        }
        return remaining
    }

    private fun canLayOff(card: Card, meldCards: List<Card>, isRun: Boolean): Boolean {
        if (isRun) {
            val sorted = meldCards.sortedBy { it.rank.order }
            return card.suit == sorted.first().suit &&
                (card.rank.order == sorted.first().rank.order - 1 ||
                    card.rank.order == sorted.last().rank.order + 1)
        }
        return meldCards.size < 4 && card.rank == meldCards.first().rank &&
            meldCards.none { it.suit == card.suit }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun sortPlayerHand() {
        val sorted = playerHand.sortedWith(compareBy<Card> { it.suit.ordinal }.thenBy { it.rank.order })
        playerHand.clear(); playerHand.addAll(sorted)
    }

    private fun sortAndUpdatePlayer() { sortPlayerHand(); updatePlayerMelds() }
    private fun updateStockCount() { stockCount = stock.size }
    private fun updateDiscardTop() { discardTop = discardPile.lastOrNull() }
    private fun updateOpponentCount() { opponentCardCount = _opponentHand.size }

    private fun updatePlayerMelds() {
        val (melds, dw) = findOptimalMelds(playerHand.toList())
        playerMelds = melds
        playerDeadwood = dw.sumOf { it.points }
        canKnock = playerHand.size == 10 && playerDeadwood <= KNOCK_THRESHOLD
        isGin = playerHand.size == 10 && playerDeadwood == 0
    }
}
