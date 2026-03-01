package com.napolitain.arcade.logic.wordballoon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class RoundStatus { PLAYING, WON, LOST }

data class WordEntry(val category: String, val word: String)

class WordBalloonEngine {

    companion object {
        val WORD_GROUPS: Map<String, List<String>> = mapOf(
            "Animals" to listOf("PANTHER", "DOLPHIN", "GIRAFFE", "KOALA"),
            "Space" to listOf("GALAXY", "COMET", "NEBULA", "ASTRONAUT"),
            "Food" to listOf("PANCAKE", "NOODLES", "AVOCADO", "BISCUIT"),
        )

        val WORD_BANK: List<WordEntry> = WORD_GROUPS.flatMap { (category, words) ->
            words.map { WordEntry(category, it) }
        }

        val CATEGORY_NAMES: String = WORD_GROUPS.keys.joinToString(", ")
        val ALPHABET: List<Char> = ('A'..'Z').toList()
        const val MAX_WRONG_GUESSES = 6

        private fun pickRandomWord(previousWord: String? = null): WordEntry {
            if (WORD_BANK.size == 1) return WORD_BANK[0]
            var candidate = WORD_BANK[Random.nextInt(WORD_BANK.size)]
            while (candidate.word == previousWord) {
                candidate = WORD_BANK[Random.nextInt(WORD_BANK.size)]
            }
            return candidate
        }
    }

    var targetWord by mutableStateOf(pickRandomWord())
        private set
    val guessedLetters = mutableStateListOf<Char>()
    var wrongGuesses by mutableIntStateOf(0)
        private set
    var roundStatus by mutableStateOf(RoundStatus.PLAYING)
        private set
    var wins by mutableIntStateOf(0)
        private set

    // ── derived state ───────────────────────────────────────────

    val uniqueLetters: Set<Char>
        get() = targetWord.word.toSet()

    val misses: List<Char>
        get() = guessedLetters.filter { it !in targetWord.word }

    val maskedWord: List<Char>
        get() = targetWord.word.map { letter ->
            if (roundStatus == RoundStatus.LOST || letter in guessedLetters) letter else '•'
        }

    val mistakesLeft: Int
        get() = MAX_WRONG_GUESSES - wrongGuesses

    val statusText: String
        get() = when (roundStatus) {
            RoundStatus.WON -> "Great flight! You solved \"${targetWord.word}\"."
            RoundStatus.LOST -> "Balloon popped! The word was \"${targetWord.word}\"."
            RoundStatus.PLAYING -> "Category: ${targetWord.category} • $mistakesLeft mistakes left."
        }

    // ── public API ──────────────────────────────────────────────

    fun submitGuess(inputLetter: Char) {
        if (roundStatus != RoundStatus.PLAYING) return

        val letter = inputLetter.uppercaseChar()
        if (letter !in 'A'..'Z' || letter in guessedLetters) return

        guessedLetters.add(letter)

        if (letter in targetWord.word) {
            val solved = uniqueLetters.all { it in guessedLetters }
            if (solved) {
                roundStatus = RoundStatus.WON
                wins++
            }
            return
        }

        wrongGuesses++
        if (wrongGuesses >= MAX_WRONG_GUESSES) {
            roundStatus = RoundStatus.LOST
        }
    }

    fun reset() {
        targetWord = pickRandomWord(targetWord.word)
        guessedLetters.clear()
        wrongGuesses = 0
        roundStatus = RoundStatus.PLAYING
    }
}
