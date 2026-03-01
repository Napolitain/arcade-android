package com.napolitain.arcade.logic.gridattack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.napolitain.arcade.ui.components.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/* ── constants ─────────────────────────────────────────────────────── */

const val GRID_SIZE = 6
const val CELL_COUNT = GRID_SIZE * GRID_SIZE
val SHIP_SIZES = intArrayOf(3, 2, 2)
private val ROW_LABELS = charArrayOf('A', 'B', 'C', 'D', 'E', 'F')
private const val CPU_DELAY_MS = 1100L

/* ── value types ───────────────────────────────────────────────────── */

enum class ShotResult { HIT, MISS }
enum class Turn { PLAYER, CPU }

data class ShipState(
    val id: String,
    val size: Int,
    val cells: List<Int>,
    val hits: Int,
) {
    val isSunk: Boolean get() = hits >= size
}

data class AttackOutcome(
    val nextShips: List<ShipState>,
    val nextShots: Map<Int, ShotResult>,
    val result: ShotResult,
    val sunkShipSize: Int?,
    val allShipsSunk: Boolean,
)

/* ── pure helpers ──────────────────────────────────────────────────── */

private fun cellIndex(row: Int, col: Int): Int = row * GRID_SIZE + col

fun formatCell(index: Int): String {
    val row = index / GRID_SIZE
    val col = index % GRID_SIZE + 1
    return "${ROW_LABELS[row]}$col"
}

private fun createShipCells(size: Int, row: Int, col: Int, horizontal: Boolean): List<Int> =
    List(size) { offset ->
        cellIndex(
            row + if (horizontal) 0 else offset,
            col + if (horizontal) offset else 0,
        )
    }

fun createRandomFleet(): List<ShipState> {
    while (true) {
        val occupied = mutableSetOf<Int>()
        val ships = mutableListOf<ShipState>()
        var failed = false

        for ((shipIndex, shipSize) in SHIP_SIZES.withIndex()) {
            var placed = false
            for (attempt in 0 until 80) {
                val horizontal = Random.nextBoolean()
                val maxRow = if (horizontal) GRID_SIZE - 1 else GRID_SIZE - shipSize
                val maxCol = if (horizontal) GRID_SIZE - shipSize else GRID_SIZE - 1
                val row = Random.nextInt(maxRow + 1)
                val col = Random.nextInt(maxCol + 1)
                val cells = createShipCells(shipSize, row, col, horizontal)

                if (cells.any { it in occupied }) continue

                occupied.addAll(cells)
                ships += ShipState(
                    id = "ship-${shipIndex + 1}",
                    size = shipSize,
                    cells = cells,
                    hits = 0,
                )
                placed = true
                break
            }
            if (!placed) { failed = true; break }
        }

        if (!failed) return ships
    }
}

fun applyAttack(
    targetShips: List<ShipState>,
    existingShots: Map<Int, ShotResult>,
    targetCell: Int,
): AttackOutcome {
    val shipIndex = targetShips.indexOfFirst { targetCell in it.cells }
    val result = if (shipIndex >= 0) ShotResult.HIT else ShotResult.MISS
    val nextShots = existingShots + (targetCell to result)

    if (result == ShotResult.MISS) {
        return AttackOutcome(targetShips, nextShots, result, null, false)
    }

    val nextShips = targetShips.mapIndexed { i, ship ->
        if (i == shipIndex) ship.copy(hits = ship.hits + 1) else ship
    }
    val updated = nextShips[shipIndex]
    val sunkSize = if (updated.isSunk) updated.size else null
    val allSunk = nextShips.all { it.isSunk }

    return AttackOutcome(nextShips, nextShots, result, sunkSize, allSunk)
}

fun getShipCells(ships: List<ShipState>): Set<Int> =
    ships.flatMapTo(mutableSetOf()) { it.cells }

fun getSunkCells(ships: List<ShipState>): Set<Int> =
    ships.filter { it.isSunk }.flatMapTo(mutableSetOf()) { it.cells }

/* ── AI targeting ──────────────────────────────────────────────────── */

private fun untargetedCells(shots: Map<Int, ShotResult>): List<Int> =
    (0 until CELL_COUNT).filter { it !in shots }

private fun orthogonalNeighbors(index: Int): List<Int> {
    val row = index / GRID_SIZE
    val col = index % GRID_SIZE
    return buildList {
        if (row > 0) add(cellIndex(row - 1, col))
        if (row < GRID_SIZE - 1) add(cellIndex(row + 1, col))
        if (col > 0) add(cellIndex(row, col - 1))
        if (col < GRID_SIZE - 1) add(cellIndex(row, col + 1))
    }
}

private fun targetNeighbors(shots: Map<Int, ShotResult>, hitCells: List<Int>): List<Int> {
    val candidates = mutableSetOf<Int>()
    for (cell in hitCells) {
        for (n in orthogonalNeighbors(cell)) {
            if (n !in shots) candidates += n
        }
    }
    return candidates.toList()
}

private fun hardFocusTargets(shots: Map<Int, ShotResult>, hitCells: List<Int>): List<Int> {
    val focused = mutableSetOf<Int>()
    val rowGroups = mutableMapOf<Int, MutableList<Int>>()
    val colGroups = mutableMapOf<Int, MutableList<Int>>()

    for (cell in hitCells) {
        val row = cell / GRID_SIZE
        val col = cell % GRID_SIZE
        rowGroups.getOrPut(row) { mutableListOf() }.add(col)
        colGroups.getOrPut(col) { mutableListOf() }.add(row)
    }

    for ((row, cols) in rowGroups) {
        if (cols.size < 2) continue
        val sorted = cols.sorted()
        val left = sorted.first() - 1
        val right = sorted.last() + 1
        if (left >= 0) {
            val idx = cellIndex(row, left)
            if (idx !in shots) focused += idx
        }
        if (right < GRID_SIZE) {
            val idx = cellIndex(row, right)
            if (idx !in shots) focused += idx
        }
    }

    for ((col, rows) in colGroups) {
        if (rows.size < 2) continue
        val sorted = rows.sorted()
        val top = sorted.first() - 1
        val bottom = sorted.last() + 1
        if (top >= 0) {
            val idx = cellIndex(top, col)
            if (idx !in shots) focused += idx
        }
        if (bottom < GRID_SIZE) {
            val idx = cellIndex(bottom, col)
            if (idx !in shots) focused += idx
        }
    }

    return focused.toList()
}

fun pickCpuTargetCell(
    shots: Map<Int, ShotResult>,
    targetShips: List<ShipState>,
    difficulty: Difficulty,
): Int? {
    if (difficulty == Difficulty.EASY) {
        val available = untargetedCells(shots)
        return available.randomOrNull()
    }

    val available = untargetedCells(shots)
    if (available.isEmpty()) return null

    val sunkSet = getSunkCells(targetShips)
    val activeHits = (0 until CELL_COUNT).filter { shots[it] == ShotResult.HIT && it !in sunkSet }

    val neighbors = targetNeighbors(shots, activeHits)

    if (difficulty == Difficulty.NORMAL) {
        return if (neighbors.isNotEmpty()) neighbors.random() else available.random()
    }

    // HARD
    val focus = hardFocusTargets(shots, activeHits)
    if (focus.isNotEmpty()) return focus.random()

    if (neighbors.isNotEmpty()) {
        return neighbors.sortedByDescending { n ->
            orthogonalNeighbors(n).count { it !in shots }
        }.first()
    }

    val center = (GRID_SIZE - 1) / 2.0
    val parity = available.filter { cell ->
        val r = cell / GRID_SIZE
        val c = cell % GRID_SIZE
        (r + c) % 2 == 0
    }
    val hunt = parity.ifEmpty { available }
    return hunt.sortedBy { cell ->
        val r = cell / GRID_SIZE
        val c = cell % GRID_SIZE
        abs(r - center) + abs(c - center)
    }.first()
}

/* ── observable engine ─────────────────────────────────────────────── */

class GridAttackEngine {

    var playerShips by mutableStateOf(createRandomFleet())
        private set
    var cpuShips by mutableStateOf(createRandomFleet())
        private set
    var playerShots by mutableStateOf<Map<Int, ShotResult>>(emptyMap())
        private set
    var cpuShots by mutableStateOf<Map<Int, ShotResult>>(emptyMap())
        private set
    var turn by mutableStateOf(Turn.PLAYER)
        private set
    var winner by mutableStateOf<Turn?>(null)
        private set
    var lastEvent by mutableStateOf("Target enemy waters to start the battle.")
        private set
    var playerWins by mutableIntStateOf(0)
        private set
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    /* derived */

    val playerShipCells: Set<Int> get() = getShipCells(playerShips)
    val playerSunkCells: Set<Int> get() = getSunkCells(playerShips)
    val enemyShipCells: Set<Int> get() = getShipCells(cpuShips)
    val enemySunkCells: Set<Int> get() = getSunkCells(cpuShips)

    val playerHits: Int get() = playerShots.values.count { it == ShotResult.HIT }
    val playerMisses: Int get() = playerShots.values.count { it == ShotResult.MISS }
    val cpuHits: Int get() = cpuShots.values.count { it == ShotResult.HIT }
    val cpuMisses: Int get() = cpuShots.values.count { it == ShotResult.MISS }
    val playerShipsSunk: Int get() = playerShips.count { it.isSunk }
    val enemyShipsSunk: Int get() = cpuShips.count { it.isSunk }

    val canTargetEnemy: Boolean get() = turn == Turn.PLAYER && winner == null

    val statusText: String
        get() = when {
            winner == Turn.PLAYER -> "Victory! $lastEvent"
            winner == Turn.CPU -> "Defeat. $lastEvent"
            turn == Turn.PLAYER -> "Your turn. $lastEvent"
            else -> "CPU turn. $lastEvent"
        }

    val cpuDelayMs: Long get() = CPU_DELAY_MS

    /* actions */

    fun attackEnemy(cellIndex: Int) {
        if (turn != Turn.PLAYER || winner != null || cellIndex in playerShots) return

        val outcome = applyAttack(cpuShips, playerShots, cellIndex)
        val coord = formatCell(cellIndex)
        var event = if (outcome.result == ShotResult.HIT) "Direct hit at $coord." else "Shot missed at $coord."
        if (outcome.sunkShipSize != null) {
            event = "You sunk an enemy ${outcome.sunkShipSize}-cell ship at $coord."
        }

        cpuShips = outcome.nextShips
        playerShots = outcome.nextShots

        if (outcome.allShipsSunk) {
            playerWins++
            winner = Turn.PLAYER
            lastEvent = "You destroyed the entire enemy fleet."
        } else {
            turn = Turn.CPU
            lastEvent = event
        }
    }

    fun executeCpuTurn() {
        if (turn != Turn.CPU || winner != null) return

        val targetCell = pickCpuTargetCell(cpuShots, playerShips, difficulty)
        if (targetCell == null) {
            turn = Turn.PLAYER
            lastEvent = "CPU has no valid targets left."
            return
        }

        val outcome = applyAttack(playerShips, cpuShots, targetCell)
        val coord = formatCell(targetCell)
        var event = if (outcome.result == ShotResult.HIT) "CPU hit your ship at $coord." else "CPU missed at $coord."
        if (outcome.sunkShipSize != null) {
            event = "CPU sunk your ${outcome.sunkShipSize}-cell ship at $coord."
        }

        playerShips = outcome.nextShips
        cpuShots = outcome.nextShots

        if (outcome.allShipsSunk) {
            winner = Turn.CPU
            lastEvent = "CPU destroyed your fleet."
        } else {
            turn = Turn.PLAYER
            lastEvent = event
        }
    }

    fun reset() {
        playerShips = createRandomFleet()
        cpuShips = createRandomFleet()
        playerShots = emptyMap()
        cpuShots = emptyMap()
        turn = Turn.PLAYER
        winner = null
        lastEvent = "Target enemy waters to start the battle."
    }
}
