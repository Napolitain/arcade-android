package com.napolitain.arcade.logic.blackjack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Suit(val symbol: String) {
    SPADES("♠"), HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣");

    val isRed: Boolean get() = this == HEARTS || this == DIAMONDS
}

enum class Rank(val display: String, val baseValue: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9),
    TEN("10", 10), JACK("J", 10), QUEEN("Q", 10), KING("K", 10),
    ACE("A", 11);
}

data class Card(val rank: Rank, val suit: Suit) {
    val display: String get() = "${rank.display}${suit.symbol}"
}

enum class GamePhase { BETTING, PLAYER_TURN, DEALER_TURN, RESULT }

enum class GameResult { PLAYER_BLACKJACK, PLAYER_WIN, DEALER_WIN, PUSH, PLAYER_BUST, DEALER_BUST }

class BlackjackEngine {

    private var deck = mutableListOf<Card>()

    val playerHand = mutableStateListOf<Card>()
    val dealerHand = mutableStateListOf<Card>()

    var playerTotal by mutableIntStateOf(0)
        private set
    var dealerTotal by mutableIntStateOf(0)
        private set
    var chips by mutableIntStateOf(1000)
        private set
    var currentBet by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(GamePhase.BETTING)
        private set
    var result by mutableStateOf<GameResult?>(null)
        private set
    var dealerCardHidden by mutableStateOf(true)
        private set

    // Tracks the number of cards dealt so the UI can animate new arrivals.
    var dealSequence by mutableIntStateOf(0)
        private set

    init {
        buildAndShuffleDeck()
    }

    // ── Public API ──────────────────────────────────────────────

    fun placeBet(amount: Int) {
        if (phase != GamePhase.BETTING) return
        if (amount > chips) return
        currentBet = amount
        chips -= amount
        deal()
    }

    fun hit() {
        if (phase != GamePhase.PLAYER_TURN) return
        dealCardTo(playerHand)
        playerTotal = bestTotal(playerHand)
        if (playerTotal > 21) {
            phase = GamePhase.RESULT
            result = GameResult.PLAYER_BUST
            dealerCardHidden = false
            dealerTotal = bestTotal(dealerHand)
        }
    }

    fun stand() {
        if (phase != GamePhase.PLAYER_TURN) return
        dealerCardHidden = false
        phase = GamePhase.DEALER_TURN
        playDealer()
    }

    fun doubleDown() {
        if (phase != GamePhase.PLAYER_TURN) return
        if (chips < currentBet) return
        chips -= currentBet
        currentBet *= 2
        dealCardTo(playerHand)
        playerTotal = bestTotal(playerHand)
        if (playerTotal > 21) {
            phase = GamePhase.RESULT
            result = GameResult.PLAYER_BUST
            dealerCardHidden = false
            dealerTotal = bestTotal(dealerHand)
        } else {
            stand()
        }
    }

    fun newHand() {
        if (phase != GamePhase.RESULT) return
        if (chips <= 0) {
            reset()
            return
        }
        playerHand.clear()
        dealerHand.clear()
        currentBet = 0
        result = null
        dealerCardHidden = true
        dealSequence = 0
        phase = GamePhase.BETTING
        if (deck.size < 15) buildAndShuffleDeck()
    }

    fun reset() {
        playerHand.clear()
        dealerHand.clear()
        chips = 1000
        currentBet = 0
        result = null
        dealerCardHidden = true
        dealSequence = 0
        phase = GamePhase.BETTING
        buildAndShuffleDeck()
    }

    // ── Internals ───────────────────────────────────────────────

    private fun buildAndShuffleDeck() {
        deck.clear()
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                deck.add(Card(rank, suit))
            }
        }
        deck.shuffle()
    }

    private fun dealCardTo(hand: MutableList<Card>) {
        if (deck.isEmpty()) buildAndShuffleDeck()
        hand.add(deck.removeFirst())
        dealSequence++
    }

    private fun deal() {
        dealCardTo(playerHand)
        dealCardTo(dealerHand)
        dealCardTo(playerHand)
        dealCardTo(dealerHand)
        playerTotal = bestTotal(playerHand)
        dealerTotal = bestTotal(listOf(dealerHand.first())) // visible card only

        // Check natural blackjack
        if (playerTotal == 21) {
            dealerCardHidden = false
            dealerTotal = bestTotal(dealerHand)
            phase = GamePhase.RESULT
            result = if (dealerTotal == 21) {
                chips += currentBet // push returns bet
                GameResult.PUSH
            } else {
                chips += currentBet + (currentBet * 3) / 2 // blackjack pays 3:2
                GameResult.PLAYER_BLACKJACK
            }
        } else {
            phase = GamePhase.PLAYER_TURN
        }
    }

    private fun playDealer() {
        dealerTotal = bestTotal(dealerHand)
        while (dealerTotal < 17) {
            dealCardTo(dealerHand)
            dealerTotal = bestTotal(dealerHand)
        }
        resolveResult()
    }

    private fun resolveResult() {
        phase = GamePhase.RESULT
        result = when {
            dealerTotal > 21 -> {
                chips += currentBet * 2
                GameResult.DEALER_BUST
            }
            playerTotal > dealerTotal -> {
                chips += currentBet * 2
                GameResult.PLAYER_WIN
            }
            playerTotal == dealerTotal -> {
                chips += currentBet
                GameResult.PUSH
            }
            else -> GameResult.DEALER_WIN
        }
    }

    companion object {
        fun bestTotal(hand: List<Card>): Int {
            var total = 0
            var aces = 0
            for (card in hand) {
                total += card.rank.baseValue
                if (card.rank == Rank.ACE) aces++
            }
            while (total > 21 && aces > 0) {
                total -= 10
                aces--
            }
            return total
        }
    }
}
