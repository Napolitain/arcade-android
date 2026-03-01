package com.napolitain.arcade.logic.sortorsplode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.random.Random

/* ── value types ───────────────────────────────────────────────────── */

enum class RoundType { COLOR, SHAPE, NUMBER }

enum class ItemShape { CIRCLE, SQUARE, TRIANGLE, DIAMOND }

data class FallingItem(
    val id: Int,
    val category: String,
    val shape: ItemShape,
    val color: Color,
    val label: String,
    val xSlot: Int,
    val yProgress: Float = 0f,
)

data class Bin(
    val label: String,
    val category: String,
    val index: Int,
)

data class Explosion(
    val id: Int,
    val x: Float,
    val y: Float,
    val progress: Float = 0f,
    val isCorrect: Boolean = false,
    val pointsText: String = "",
)

/* ── round definitions ─────────────────────────────────────────────── */

private val COLOR_ITEMS = listOf(
    Triple("Red", Color(0xFFEF4444), ItemShape.CIRCLE),
    Triple("Blue", Color(0xFF3B82F6), ItemShape.SQUARE),
    Triple("Green", Color(0xFF22C55E), ItemShape.TRIANGLE),
    Triple("Yellow", Color(0xFFEAB308), ItemShape.DIAMOND),
)

private val SHAPE_ITEMS = listOf(
    Triple("Circle", Color(0xFF8B5CF6), ItemShape.CIRCLE),
    Triple("Square", Color(0xFFF97316), ItemShape.SQUARE),
    Triple("Triangle", Color(0xFF06B6D4), ItemShape.TRIANGLE),
    Triple("Diamond", Color(0xFFEC4899), ItemShape.DIAMOND),
)

private fun numberItem(category: String, id: Int): FallingItem {
    val value = if (category == "Odd") {
        Random.nextInt(0, 50) * 2 + 1
    } else {
        Random.nextInt(1, 50) * 2
    }
    val color = if (category == "Odd") Color(0xFFF59E0B) else Color(0xFF14B8A6)
    val shape = if (category == "Odd") ItemShape.DIAMOND else ItemShape.CIRCLE
    return FallingItem(
        id = id,
        category = category,
        shape = shape,
        color = color,
        label = value.toString(),
        xSlot = 0,
    )
}

/* ── engine ─────────────────────────────────────────────────────────── */

class SortOrSplodeEngine {

    companion object {
        const val MAX_LIVES = 3
        private const val LEVEL_UP_THRESHOLD = 10
        private const val MAX_ITEMS_ON_SCREEN = 5
        private const val BASE_FALL_SPEED = 0.00025f
        private const val SPEED_INCREMENT = 0.000035f
        private const val SPAWN_INTERVAL_BASE_MS = 2200L
        private const val SPAWN_INTERVAL_MIN_MS = 800L
    }

    /* ── observable state ──────────────────────────────────────────── */

    private var _difficulty = mutableStateOf(Difficulty.NORMAL)
    var difficulty: Difficulty
        get() = _difficulty.value
        set(value) {
            _difficulty.value = value
            reset()
        }

    var score by mutableIntStateOf(0)
        private set

    var combo by mutableIntStateOf(0)
        private set

    var lives by mutableIntStateOf(MAX_LIVES)
        private set

    var level by mutableIntStateOf(1)
        private set

    var gameOver by mutableStateOf(false)
        private set

    var currentRoundType by mutableStateOf(RoundType.COLOR)
        private set

    val items = mutableStateListOf<FallingItem>()

    val bins = mutableStateListOf<Bin>()

    val explosions = mutableStateListOf<Explosion>()

    private var nextId = 0
    private var correctCount = 0
    private var roundIndex = 0
    private var timeSinceSpawnMs = 0L

    private val roundCycle: List<RoundType>
        get() = when (difficulty) {
            Difficulty.EASY -> listOf(RoundType.COLOR, RoundType.NUMBER)
            Difficulty.NORMAL -> listOf(RoundType.COLOR, RoundType.SHAPE, RoundType.NUMBER)
            Difficulty.HARD -> listOf(RoundType.COLOR, RoundType.SHAPE, RoundType.NUMBER)
        }

    val binCount: Int
        get() = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.NORMAL -> 3
            Difficulty.HARD -> 4
        }

    val statusText: String
        get() = when {
            gameOver -> "Game Over"
            else -> "Level $level"
        }

    init {
        reset()
    }

    /* ── public API ────────────────────────────────────────────────── */

    fun reset() {
        score = 0
        combo = 0
        lives = MAX_LIVES
        level = 1
        gameOver = false
        correctCount = 0
        roundIndex = 0
        nextId = 0
        timeSinceSpawnMs = SPAWN_INTERVAL_BASE_MS
        items.clear()
        explosions.clear()
        setupRound()
    }

    fun tick(deltaMs: Long) {
        if (gameOver) return

        val speed = fallSpeed()

        // Advance explosions
        val expIter = explosions.listIterator()
        while (expIter.hasNext()) {
            val e = expIter.next()
            val next = e.copy(progress = e.progress + deltaMs / 600f)
            if (next.progress >= 1f) expIter.remove()
            else expIter.set(next)
        }

        // Advance items
        val expired = mutableListOf<FallingItem>()
        val iter = items.listIterator()
        while (iter.hasNext()) {
            val item = iter.next()
            val newY = item.yProgress + speed * deltaMs
            if (newY >= 1f) {
                expired.add(item)
                iter.remove()
            } else {
                iter.set(item.copy(yProgress = newY))
            }
        }

        // Expired items explode (lose lives)
        for (item in expired) {
            loseLife(item.xSlot.toFloat() / binCount.coerceAtLeast(1), 0.95f)
        }

        // Spawn new items
        timeSinceSpawnMs += deltaMs
        val interval = spawnInterval()
        if (timeSinceSpawnMs >= interval && items.size < MAX_ITEMS_ON_SCREEN && !gameOver) {
            spawnItem()
            timeSinceSpawnMs = 0L
        }
    }

    fun sortItem(itemId: Int, binIndex: Int): Boolean {
        if (gameOver) return false
        val idx = items.indexOfFirst { it.id == itemId }
        if (idx < 0) return false
        val item = items[idx]
        val bin = bins.getOrNull(binIndex) ?: return false
        items.removeAt(idx)

        return if (item.category == bin.category) {
            combo++
            val points = 10 * combo
            score += points
            correctCount++
            addExplosion(
                x = (binIndex.toFloat() + 0.5f) / binCount,
                y = item.yProgress,
                isCorrect = true,
                text = "+$points",
            )
            checkLevelUp()
            true
        } else {
            combo = 0
            loseLife(
                (binIndex.toFloat() + 0.5f) / binCount,
                item.yProgress,
            )
            false
        }
    }

    fun getLowestItem(): FallingItem? =
        items.maxByOrNull { it.yProgress }

    /* ── internals ─────────────────────────────────────────────────── */

    private fun setupRound() {
        val cycle = roundCycle
        currentRoundType = cycle[roundIndex % cycle.size]
        bins.clear()
        val categories = categoriesForRound()
        categories.forEachIndexed { i, cat ->
            bins.add(Bin(label = cat, category = cat, index = i))
        }
    }

    private fun categoriesForRound(): List<String> = when (currentRoundType) {
        RoundType.COLOR -> COLOR_ITEMS.map { it.first }.take(binCount)
        RoundType.SHAPE -> SHAPE_ITEMS.map { it.first }.take(binCount)
        RoundType.NUMBER -> listOf("Odd", "Even").take(binCount)
    }

    fun spawnItem() {
        if (gameOver) return
        val categories = categoriesForRound()
        val cat = categories.random()
        val item = createItem(cat)
        items.add(item)
    }

    private fun createItem(category: String): FallingItem {
        val id = nextId++
        val slot = Random.nextInt(0, binCount)
        return when (currentRoundType) {
            RoundType.COLOR -> {
                val def = COLOR_ITEMS.first { it.first == category }
                FallingItem(
                    id = id,
                    category = category,
                    shape = listOf(ItemShape.CIRCLE, ItemShape.SQUARE, ItemShape.TRIANGLE, ItemShape.DIAMOND).random(),
                    color = def.second,
                    label = category.first().toString(),
                    xSlot = slot,
                )
            }
            RoundType.SHAPE -> {
                val def = SHAPE_ITEMS.first { it.first == category }
                FallingItem(
                    id = id,
                    category = category,
                    shape = def.third,
                    color = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFEAB308)).random(),
                    label = category.first().toString(),
                    xSlot = slot,
                )
            }
            RoundType.NUMBER -> {
                numberItem(category, id).copy(xSlot = slot)
            }
        }
    }

    private fun fallSpeed(): Float {
        val base = BASE_FALL_SPEED + (level - 1) * SPEED_INCREMENT
        return when (difficulty) {
            Difficulty.EASY -> base * 0.65f
            Difficulty.NORMAL -> base
            Difficulty.HARD -> base * 1.45f
        }
    }

    private fun spawnInterval(): Long {
        val base = (SPAWN_INTERVAL_BASE_MS - (level - 1) * 120L).coerceAtLeast(SPAWN_INTERVAL_MIN_MS)
        return when (difficulty) {
            Difficulty.EASY -> (base * 1.4).toLong()
            Difficulty.NORMAL -> base
            Difficulty.HARD -> (base * 0.7).toLong()
        }
    }

    private fun checkLevelUp() {
        if (correctCount >= level * LEVEL_UP_THRESHOLD) {
            level++
            // Advance round type every 2 levels
            if (level % 2 == 1) {
                roundIndex++
                setupRound()
            }
        }
    }

    private fun loseLife(x: Float, y: Float) {
        combo = 0
        lives--
        addExplosion(x, y, isCorrect = false, text = "💥")
        if (lives <= 0) {
            lives = 0
            gameOver = true
        }
    }

    private fun addExplosion(x: Float, y: Float, isCorrect: Boolean, text: String) {
        explosions.add(
            Explosion(
                id = nextId++,
                x = x,
                y = y,
                isCorrect = isCorrect,
                pointsText = text,
            ),
        )
    }
}
