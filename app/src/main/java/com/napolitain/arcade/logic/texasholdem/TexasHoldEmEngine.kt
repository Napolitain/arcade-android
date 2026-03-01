package com.napolitain.arcade.logic.texasholdem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.random.Random

// ── Data types ──────────────────────────────────────────────────

enum class Suit(val symbol: String, val isRed: Boolean) {
    HEARTS("♥", true), DIAMONDS("♦", true),
    CLUBS("♣", false), SPADES("♠", false),
}

enum class Rank(val display: String, val value: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9),
    TEN("10", 10), JACK("J", 11), QUEEN("Q", 12), KING("K", 13), ACE("A", 14),
}

data class Card(val rank: Rank, val suit: Suit) {
    override fun toString() = "${rank.display}${suit.symbol}"
}

enum class Phase { PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN }

enum class HandRank(val display: String) {
    HIGH_CARD("High Card"),
    ONE_PAIR("One Pair"),
    TWO_PAIR("Two Pair"),
    THREE_OF_A_KIND("Three of a Kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full House"),
    FOUR_OF_A_KIND("Four of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),
    ROYAL_FLUSH("Royal Flush"),
}

data class HandEvaluation(
    val handRank: HandRank,
    val tiebreakers: List<Int>,
) : Comparable<HandEvaluation> {
    override fun compareTo(other: HandEvaluation): Int {
        val cmp = handRank.ordinal.compareTo(other.handRank.ordinal)
        if (cmp != 0) return cmp
        for (i in tiebreakers.indices) {
            if (i >= other.tiebreakers.size) return 1
            val tc = tiebreakers[i].compareTo(other.tiebreakers[i])
            if (tc != 0) return tc
        }
        return 0
    }
}

enum class PlayerAction { FOLD, CHECK, CALL, RAISE }

data class Player(
    val name: String,
    val isHuman: Boolean,
    var chips: Int = 1000,
    val hand: MutableList<Card> = mutableListOf(),
    var folded: Boolean = false,
    var currentBet: Int = 0,
    var isAllIn: Boolean = false,
    var isDealer: Boolean = false,
)

// ── Engine ──────────────────────────────────────────────────────

class TexasHoldEmEngine {

    companion object {
        private const val SMALL_BLIND = 10
        private const val BIG_BLIND = 20
        private const val STARTING_CHIPS = 1000

        private fun buildDeck(): MutableList<Card> {
            val deck = mutableListOf<Card>()
            for (s in Suit.entries) for (r in Rank.entries) deck.add(Card(r, s))
            deck.shuffle()
            return deck
        }
    }

    // ── Observable state ────────────────────────────────────────

    val players = mutableStateListOf<Player>()
    val communityCards = mutableStateListOf<Card>()
    var pot by mutableIntStateOf(0)
        private set
    var currentBet by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(Phase.PRE_FLOP)
        private set
    var activePlayerIndex by mutableIntStateOf(0)
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)
    var message by mutableStateOf("")
        private set
    var handOver by mutableStateOf(false)
        private set
    var isHumanTurn by mutableStateOf(false)
        private set
    var waitingForAi by mutableStateOf(false)
        private set
    var showdownResults by mutableStateOf<List<Pair<Player, HandEvaluation?>>>(emptyList())
        private set

    val humanActions: List<PlayerAction>
        get() {
            if (!isHumanTurn || handOver) return emptyList()
            val human = players.firstOrNull { it.isHuman } ?: return emptyList()
            if (human.folded || human.isAllIn) return emptyList()
            val actions = mutableListOf(PlayerAction.FOLD)
            val toCall = currentBet - human.currentBet
            if (toCall <= 0) {
                actions.add(PlayerAction.CHECK)
            } else {
                actions.add(PlayerAction.CALL)
            }
            if (human.chips > toCall) {
                actions.add(PlayerAction.RAISE)
            }
            return actions
        }

    private var deck = mutableListOf<Card>()
    private var dealerIndex = 0
    private var lastRaiserIndex = -1
    private var bettingStartIndex = 0
    private var playersActedThisRound = 0

    init {
        players.addAll(
            listOf(
                Player("You", isHuman = true, chips = STARTING_CHIPS),
                Player("Alice", isHuman = false, chips = STARTING_CHIPS),
                Player("Bob", isHuman = false, chips = STARTING_CHIPS),
                Player("Carol", isHuman = false, chips = STARTING_CHIPS),
            ),
        )
        startNewHand()
    }

    // ── Public API ──────────────────────────────────────────────

    fun fold() {
        if (!isHumanTurn || handOver) return
        val human = players.first { it.isHuman }
        human.folded = true
        refreshPlayers()
        message = "You fold."
        advanceAction()
    }

    fun check() {
        if (!isHumanTurn || handOver) return
        val human = players.first { it.isHuman }
        val toCall = currentBet - human.currentBet
        if (toCall > 0) return // can't check
        message = "You check."
        advanceAction()
    }

    fun call() {
        if (!isHumanTurn || handOver) return
        val human = players.first { it.isHuman }
        val toCall = (currentBet - human.currentBet).coerceAtMost(human.chips)
        if (toCall <= 0) return
        human.chips -= toCall
        human.currentBet += toCall
        pot += toCall
        if (human.chips == 0) human.isAllIn = true
        refreshPlayers()
        message = "You call $toCall."
        advanceAction()
    }

    fun raise(amount: Int) {
        if (!isHumanTurn || handOver) return
        val human = players.first { it.isHuman }
        val toCall = currentBet - human.currentBet
        val totalCost = toCall + amount
        val actual = totalCost.coerceAtMost(human.chips)
        human.chips -= actual
        human.currentBet += actual
        pot += actual
        if (human.chips == 0) human.isAllIn = true
        if (human.currentBet > currentBet) {
            currentBet = human.currentBet
            lastRaiserIndex = players.indexOf(human)
            playersActedThisRound = 0
        }
        refreshPlayers()
        message = "You raise to ${human.currentBet}."
        advanceAction()
    }

    fun newHand() {
        startNewHand()
    }

    fun reset() {
        for (p in players) {
            p.chips = STARTING_CHIPS
            p.hand.clear()
            p.folded = false
            p.currentBet = 0
            p.isAllIn = false
            p.isDealer = false
        }
        dealerIndex = 0
        refreshPlayers()
        startNewHand()
    }

    /** Call from LaunchedEffect to process AI turns. Returns true if an AI acted. */
    fun processAiTurn(): Boolean {
        if (handOver || isHumanTurn) return false
        val player = players.getOrNull(activePlayerIndex) ?: return false
        if (player.isHuman || player.folded || player.isAllIn) return false

        waitingForAi = true
        performAiAction(player)
        waitingForAi = false
        advanceAction()
        return true
    }

    // ── Hand setup ──────────────────────────────────────────────

    private fun startNewHand() {
        // Remove eliminated players
        val alive = players.filter { it.chips > 0 || it.isHuman }
        if (alive.size < 2) {
            message = if (players.first { it.isHuman }.chips > 0) "You win the game!" else "Game over!"
            handOver = true
            isHumanTurn = false
            return
        }

        deck = buildDeck()
        communityCards.clear()
        pot = 0
        currentBet = 0
        phase = Phase.PRE_FLOP
        handOver = false
        showdownResults = emptyList()
        message = ""

        for (p in players) {
            p.hand.clear()
            p.folded = p.chips <= 0
            p.currentBet = 0
            p.isAllIn = false
            p.isDealer = false
        }

        // Rotate dealer
        dealerIndex = nextActive(dealerIndex)
        players[dealerIndex].isDealer = true

        // Deal 2 hole cards to each active player
        for (round in 0..1) {
            for (p in players) {
                if (!p.folded) p.hand.add(deck.removeFirst())
            }
        }

        // Post blinds
        val sbIndex = nextActive(dealerIndex)
        val bbIndex = nextActive(sbIndex)
        postBlind(players[sbIndex], SMALL_BLIND)
        postBlind(players[bbIndex], BIG_BLIND)
        currentBet = BIG_BLIND

        // First to act is after big blind
        activePlayerIndex = nextActive(bbIndex)
        bettingStartIndex = activePlayerIndex
        lastRaiserIndex = bbIndex
        playersActedThisRound = 0

        refreshPlayers()
        updateTurnState()
    }

    private fun postBlind(player: Player, amount: Int) {
        val actual = amount.coerceAtMost(player.chips)
        player.chips -= actual
        player.currentBet = actual
        pot += actual
        if (player.chips == 0) player.isAllIn = true
    }

    // ── Betting flow ────────────────────────────────────────────

    private fun advanceAction() {
        playersActedThisRound++

        // Check if hand is over (all but one folded)
        val activePlayers = players.filter { !it.folded }
        if (activePlayers.size == 1) {
            val winner = activePlayers[0]
            winner.chips += pot
            pot = 0
            message = "${winner.name} wins the pot!"
            handOver = true
            isHumanTurn = false
            refreshPlayers()
            return
        }

        // Find next player who can act
        var nextIdx = nextActive(activePlayerIndex)
        var loopCount = 0
        while (loopCount < players.size) {
            val p = players[nextIdx]
            if (!p.folded && !p.isAllIn) break
            nextIdx = nextActive(nextIdx)
            loopCount++
        }

        // Check if betting round is complete
        val canAct = players.filter { !it.folded && !it.isAllIn }
        val allMatched = canAct.all { it.currentBet == currentBet }
        val roundComplete = allMatched && playersActedThisRound >= canAct.size

        if (roundComplete || canAct.isEmpty()) {
            advancePhase()
        } else {
            activePlayerIndex = nextIdx
            updateTurnState()
        }
    }

    private fun advancePhase() {
        // Reset bets for new round
        for (p in players) p.currentBet = 0
        currentBet = 0
        playersActedThisRound = 0
        lastRaiserIndex = -1

        when (phase) {
            Phase.PRE_FLOP -> {
                phase = Phase.FLOP
                repeat(3) { communityCards.add(deck.removeFirst()) }
            }
            Phase.FLOP -> {
                phase = Phase.TURN
                communityCards.add(deck.removeFirst())
            }
            Phase.TURN -> {
                phase = Phase.RIVER
                communityCards.add(deck.removeFirst())
            }
            Phase.RIVER -> {
                phase = Phase.SHOWDOWN
                resolveShowdown()
                return
            }
            Phase.SHOWDOWN -> return
        }

        // First to act after flop is first active after dealer
        activePlayerIndex = nextActive(dealerIndex)
        bettingStartIndex = activePlayerIndex

        // If only one (or zero) players can act, skip to next phase
        val canAct = players.filter { !it.folded && !it.isAllIn }
        if (canAct.size <= 1) {
            advancePhase()
        } else {
            updateTurnState()
        }
    }

    private fun resolveShowdown() {
        val activePlayers = players.filter { !it.folded }
        val results = activePlayers.map { p ->
            val allCards = p.hand + communityCards
            p to evaluateBestHand(allCards)
        }.sortedByDescending { it.second }

        showdownResults = results

        val winner = results.first().first
        winner.chips += pot
        pot = 0
        message = "${winner.name} wins with ${results.first().second.handRank.display}!"
        handOver = true
        isHumanTurn = false
        refreshPlayers()
    }

    private fun updateTurnState() {
        val p = players.getOrNull(activePlayerIndex)
        isHumanTurn = p?.isHuman == true && !p.folded && !p.isAllIn && !handOver
        if (!isHumanTurn && !handOver) {
            waitingForAi = true
        }
    }

    private fun nextActive(from: Int): Int {
        var idx = (from + 1) % players.size
        var loopCount = 0
        while (players[idx].folded && players[idx].chips <= 0 && loopCount < players.size) {
            idx = (idx + 1) % players.size
            loopCount++
        }
        return idx
    }

    private fun refreshPlayers() {
        // Trigger recomposition by replacing list contents
        val snapshot = players.toList()
        players.clear()
        players.addAll(snapshot)
    }

    // ── AI logic ────────────────────────────────────────────────

    private fun performAiAction(player: Player) {
        when (difficulty) {
            Difficulty.EASY -> aiEasy(player)
            Difficulty.NORMAL -> aiNormal(player)
            Difficulty.HARD -> aiHard(player)
        }
    }

    private fun aiEasy(player: Player) {
        val r = Random.nextFloat()
        val toCall = currentBet - player.currentBet
        when {
            r < 0.2f -> {
                // Fold
                player.folded = true
                message = "${player.name} folds."
            }
            r < 0.7f || toCall == 0 -> {
                // Call / Check
                if (toCall > 0) {
                    val actual = toCall.coerceAtMost(player.chips)
                    player.chips -= actual
                    player.currentBet += actual
                    pot += actual
                    if (player.chips == 0) player.isAllIn = true
                    message = "${player.name} calls $actual."
                } else {
                    message = "${player.name} checks."
                }
            }
            else -> {
                // Random raise
                val raiseAmt = BIG_BLIND * (1..3).random()
                aiRaise(player, raiseAmt)
            }
        }
        refreshPlayers()
    }

    private fun aiNormal(player: Player) {
        val strength = evaluateHandStrength(player)
        val toCall = currentBet - player.currentBet
        when {
            strength < 0.25f && toCall > 0 -> {
                player.folded = true
                message = "${player.name} folds."
            }
            strength > 0.7f -> {
                val raiseAmt = (BIG_BLIND * (1 + (strength * 3).toInt())).coerceAtMost(player.chips)
                if (raiseAmt > 0 && player.chips > toCall) {
                    aiRaise(player, raiseAmt)
                } else {
                    aiCall(player)
                }
            }
            toCall == 0 -> {
                message = "${player.name} checks."
            }
            strength > 0.4f || toCall <= BIG_BLIND -> {
                aiCall(player)
            }
            else -> {
                player.folded = true
                message = "${player.name} folds."
            }
        }
        refreshPlayers()
    }

    private fun aiHard(player: Player) {
        val strength = evaluateHandStrength(player)
        val toCall = currentBet - player.currentBet
        val potOdds = if (pot + toCall > 0) toCall.toFloat() / (pot + toCall) else 0f
        val position = getPositionValue(player)
        val bluff = Random.nextFloat() < 0.15f

        when {
            // Strong hand or bluff: raise
            strength > 0.65f || (bluff && player.chips > BIG_BLIND * 3) -> {
                val multiplier = if (bluff) 2 else (2 + (strength * 4).toInt())
                val raiseAmt = (BIG_BLIND * multiplier).coerceAtMost(player.chips - toCall).coerceAtLeast(BIG_BLIND)
                if (player.chips > toCall + raiseAmt) {
                    aiRaise(player, raiseAmt)
                } else {
                    aiCall(player)
                }
            }
            // Good position and decent hand: call
            strength > 0.4f && (potOdds < strength + position * 0.1f || toCall == 0) -> {
                if (toCall > 0) aiCall(player) else { message = "${player.name} checks." }
            }
            // Pot odds favor calling
            toCall > 0 && potOdds < strength -> {
                aiCall(player)
            }
            // Free check
            toCall == 0 -> {
                message = "${player.name} checks."
            }
            else -> {
                player.folded = true
                message = "${player.name} folds."
            }
        }
        refreshPlayers()
    }

    private fun aiCall(player: Player) {
        val toCall = (currentBet - player.currentBet).coerceAtMost(player.chips)
        player.chips -= toCall
        player.currentBet += toCall
        pot += toCall
        if (player.chips == 0) player.isAllIn = true
        message = "${player.name} calls $toCall."
    }

    private fun aiRaise(player: Player, amount: Int) {
        val toCall = currentBet - player.currentBet
        val totalCost = (toCall + amount).coerceAtMost(player.chips)
        player.chips -= totalCost
        player.currentBet += totalCost
        pot += totalCost
        if (player.chips == 0) player.isAllIn = true
        if (player.currentBet > currentBet) {
            currentBet = player.currentBet
            lastRaiserIndex = players.indexOf(player)
            playersActedThisRound = 0
        }
        message = "${player.name} raises to ${player.currentBet}."
    }

    private fun evaluateHandStrength(player: Player): Float {
        val allCards = player.hand + communityCards
        if (allCards.size < 2) return 0.5f
        // Pre-flop: simple hole card strength
        if (communityCards.isEmpty()) {
            val c1 = player.hand[0]
            val c2 = player.hand[1]
            var score = 0f
            // Pair bonus
            if (c1.rank == c2.rank) score += 0.5f + c1.rank.value / 28f
            // High card value
            score += (c1.rank.value + c2.rank.value) / 28f * 0.4f
            // Suited bonus
            if (c1.suit == c2.suit) score += 0.1f
            // Connected bonus
            val gap = kotlin.math.abs(c1.rank.value - c2.rank.value)
            if (gap <= 2) score += 0.05f
            return score.coerceIn(0f, 1f)
        }
        // Post-flop: evaluate actual hand
        val eval = evaluateBestHand(allCards)
        return when (eval.handRank) {
            HandRank.HIGH_CARD -> 0.1f + eval.tiebreakers.first() / 140f
            HandRank.ONE_PAIR -> 0.35f + eval.tiebreakers.first() / 140f
            HandRank.TWO_PAIR -> 0.55f + eval.tiebreakers.first() / 140f
            HandRank.THREE_OF_A_KIND -> 0.65f
            HandRank.STRAIGHT -> 0.7f
            HandRank.FLUSH -> 0.75f
            HandRank.FULL_HOUSE -> 0.85f
            HandRank.FOUR_OF_A_KIND -> 0.92f
            HandRank.STRAIGHT_FLUSH -> 0.96f
            HandRank.ROYAL_FLUSH -> 1.0f
        }
    }

    private fun getPositionValue(player: Player): Float {
        val idx = players.indexOf(player)
        val dist = (idx - dealerIndex + players.size) % players.size
        return dist.toFloat() / players.size // later position = higher value
    }

    // ── Hand evaluation ─────────────────────────────────────────

    /** Evaluate the best 5-card hand from available cards (5, 6, or 7 cards). */
    fun evaluateBestHand(cards: List<Card>): HandEvaluation {
        if (cards.size <= 5) return evaluate5(cards)
        var best: HandEvaluation? = null
        val combos = combinations(cards, 5)
        for (combo in combos) {
            val eval = evaluate5(combo)
            if (best == null || eval > best) best = eval
        }
        return best ?: HandEvaluation(HandRank.HIGH_CARD, listOf(0))
    }

    private fun evaluate5(cards: List<Card>): HandEvaluation {
        val sorted = cards.sortedByDescending { it.rank.value }
        val ranks = sorted.map { it.rank.value }
        val suits = sorted.map { it.suit }

        val isFlush = suits.distinct().size == 1
        val isWheel = ranks == listOf(14, 5, 4, 3, 2) // A-2-3-4-5
        val isStraight = isWheel || (ranks.distinct().size == 5 && ranks.first() - ranks.last() == 4)

        val groups = ranks.groupBy { it }.mapValues { it.value.size }
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val groupSizes = groups.map { it.value }
        val groupRanks = groups.map { it.key }

        return when {
            isFlush && isStraight && !isWheel && ranks.first() == 14 ->
                HandEvaluation(HandRank.ROYAL_FLUSH, listOf(14))
            isFlush && isStraight ->
                HandEvaluation(HandRank.STRAIGHT_FLUSH, listOf(if (isWheel) 5 else ranks.first()))
            groupSizes == listOf(4, 1) ->
                HandEvaluation(HandRank.FOUR_OF_A_KIND, groupRanks)
            groupSizes == listOf(3, 2) ->
                HandEvaluation(HandRank.FULL_HOUSE, groupRanks)
            isFlush ->
                HandEvaluation(HandRank.FLUSH, ranks)
            isStraight ->
                HandEvaluation(HandRank.STRAIGHT, listOf(if (isWheel) 5 else ranks.first()))
            groupSizes.first() == 3 ->
                HandEvaluation(HandRank.THREE_OF_A_KIND, groupRanks)
            groupSizes == listOf(2, 2, 1) ->
                HandEvaluation(HandRank.TWO_PAIR, groupRanks)
            groupSizes.first() == 2 ->
                HandEvaluation(HandRank.ONE_PAIR, groupRanks)
            else ->
                HandEvaluation(HandRank.HIGH_CARD, ranks)
        }
    }

    private fun <T> combinations(list: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (list.isEmpty()) return emptyList()
        val head = list.first()
        val tail = list.drop(1)
        val withHead = combinations(tail, k - 1).map { listOf(head) + it }
        val withoutHead = combinations(tail, k)
        return withHead + withoutHead
    }
}
