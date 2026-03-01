package com.napolitain.arcade.logic.sortorsplode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.math.atan2
import kotlin.math.sqrt
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
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val entryAngle: Float,
    val spawnTime: Long,
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

private fun numberItem(
    category: String,
    id: Int,
    x: Float,
    y: Float,
    vx: Float,
    vy: Float,
    angle: Float,
    time: Long,
): FallingItem {
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
        x = x,
        y = y,
        velocityX = vx,
        velocityY = vy,
        entryAngle = angle,
        spawnTime = time,
    )
}

/* ── engine ─────────────────────────────────────────────────────────── */

class SortOrSplodeEngine {

    companion object {
        const val MAX_LIVES = 3
        private const val LEVEL_UP_THRESHOLD = 10
        private const val MAX_ITEMS_ON_SCREEN = 5
        private const val BASE_DRIFT_SPEED = 0.00008f
        private const val SPEED_INCREMENT = 0.000012f
        private const val SPAWN_INTERVAL_BASE_MS = 2200L
        private const val SPAWN_INTERVAL_MIN_MS = 800L
        private const val ITEM_LIFETIME_BASE_MS = 12000L
        private const val ITEM_LIFETIME_MIN_MS = 5000L
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

    /** Set by the UI layer to exclude an item from physics while it is being dragged. */
    var draggedItemId: Int? = null

    private var nextId = 0
    private var correctCount = 0
    private var roundIndex = 0
    private var timeSinceSpawnMs = 0L
    private var gameTimeMs = 0L

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
        gameTimeMs = 0L
        timeSinceSpawnMs = SPAWN_INTERVAL_BASE_MS
        draggedItemId = null
        items.clear()
        explosions.clear()
        setupRound()
    }

    fun tick(deltaMs: Long) {
        if (gameOver) return

        gameTimeMs += deltaMs
        val speed = driftSpeed()

        // Advance explosions
        val expIter = explosions.listIterator()
        while (expIter.hasNext()) {
            val e = expIter.next()
            val next = e.copy(progress = e.progress + deltaMs / 600f)
            if (next.progress >= 1f) expIter.remove()
            else expIter.set(next)
        }

        // Advance items (skip the currently-dragged item)
        val expired = mutableListOf<FallingItem>()
        val iter = items.listIterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if (item.id == draggedItemId) continue

            val newX = item.x + item.velocityX * speed * deltaMs
            val newY = item.y + item.velocityY * speed * deltaMs
            val age = gameTimeMs - item.spawnTime

            if (newX < -0.15f || newX > 1.15f || newY < -0.15f || newY > 1.15f ||
                age > itemLifetime()
            ) {
                expired.add(item)
                iter.remove()
            } else {
                iter.set(item.copy(x = newX, y = newY))
            }
        }

        // Expired items explode
        for (item in expired) {
            loseLife(item.x.coerceIn(0f, 1f), item.y.coerceIn(0f, 1f))
        }

        // Spawn new items
        timeSinceSpawnMs += deltaMs
        val interval = spawnInterval()
        if (timeSinceSpawnMs >= interval && items.size < MAX_ITEMS_ON_SCREEN && !gameOver) {
            spawnItem()
            timeSinceSpawnMs = 0L
        }
    }

    /** Called by the UI when a dragged item is moved. Resets its lifetime timer. */
    fun updateItemPosition(itemId: Int, deltaX: Float, deltaY: Float) {
        val idx = items.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            val item = items[idx]
            items[idx] = item.copy(
                x = (item.x + deltaX).coerceIn(-0.1f, 1.1f),
                y = (item.y + deltaY).coerceIn(-0.1f, 1.1f),
                spawnTime = gameTimeMs,
            )
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
                x = item.x,
                y = item.y,
                isCorrect = true,
                text = "+$points",
            )
            checkLevelUp()
            true
        } else {
            combo = 0
            loseLife(item.x, item.y)
            false
        }
    }

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
        val spawn = randomEdgeSpawn()
        return when (currentRoundType) {
            RoundType.COLOR -> {
                val def = COLOR_ITEMS.first { it.first == category }
                FallingItem(
                    id = id,
                    category = category,
                    shape = listOf(ItemShape.CIRCLE, ItemShape.SQUARE, ItemShape.TRIANGLE, ItemShape.DIAMOND).random(),
                    color = def.second,
                    label = category.first().toString(),
                    x = spawn.x, y = spawn.y,
                    velocityX = spawn.vx, velocityY = spawn.vy,
                    entryAngle = spawn.angle,
                    spawnTime = gameTimeMs,
                )
            }
            RoundType.SHAPE -> {
                val def = SHAPE_ITEMS.first { it.first == category }
                FallingItem(
                    id = id,
                    category = category,
                    shape = def.third,
                    color = listOf(
                        Color(0xFFEF4444), Color(0xFF3B82F6),
                        Color(0xFF22C55E), Color(0xFFEAB308),
                    ).random(),
                    label = category.first().toString(),
                    x = spawn.x, y = spawn.y,
                    velocityX = spawn.vx, velocityY = spawn.vy,
                    entryAngle = spawn.angle,
                    spawnTime = gameTimeMs,
                )
            }
            RoundType.NUMBER -> {
                numberItem(
                    category, id,
                    spawn.x, spawn.y, spawn.vx, spawn.vy, spawn.angle,
                    gameTimeMs,
                )
            }
        }
    }

    private data class SpawnData(
        val x: Float, val y: Float,
        val vx: Float, val vy: Float,
        val angle: Float,
    )

    private fun randomEdgeSpawn(): SpawnData {
        // Target: a random point in the centre play-area (above bins)
        val targetX = 0.2f + Random.nextFloat() * 0.6f
        val targetY = 0.15f + Random.nextFloat() * 0.45f

        val edge = Random.nextInt(8)
        val (sx, sy) = when (edge) {
            0 -> -0.08f to (0.1f + Random.nextFloat() * 0.6f)   // left
            1 -> 1.08f to (0.1f + Random.nextFloat() * 0.6f)    // right
            2 -> (0.1f + Random.nextFloat() * 0.8f) to -0.08f   // top
            3 -> (0.1f + Random.nextFloat() * 0.8f) to -0.08f   // top (extra weight)
            4 -> -0.08f to -0.08f                                 // top-left corner
            5 -> 1.08f to -0.08f                                  // top-right corner
            6 -> -0.08f to 0.7f                                   // mid-left low
            7 -> 1.08f to 0.7f                                    // mid-right low
            else -> 0.5f to -0.08f
        }

        val dx = targetX - sx
        val dy = targetY - sy
        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val mag = sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.1f)
        val vx = dx / mag + (Random.nextFloat() - 0.5f) * 0.3f
        val vy = dy / mag + (Random.nextFloat() - 0.5f) * 0.3f

        return SpawnData(sx, sy, vx, vy, angle)
    }

    private fun driftSpeed(): Float {
        val base = BASE_DRIFT_SPEED + (level - 1) * SPEED_INCREMENT
        return when (difficulty) {
            Difficulty.EASY -> base * 0.65f
            Difficulty.NORMAL -> base
            Difficulty.HARD -> base * 1.45f
        }
    }

    private fun itemLifetime(): Long {
        val base = (ITEM_LIFETIME_BASE_MS - (level - 1) * 400L).coerceAtLeast(ITEM_LIFETIME_MIN_MS)
        return when (difficulty) {
            Difficulty.EASY -> (base * 1.4).toLong()
            Difficulty.NORMAL -> base
            Difficulty.HARD -> (base * 0.7).toLong()
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
