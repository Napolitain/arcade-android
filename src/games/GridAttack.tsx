import { useEffect, useMemo, useState } from 'react'
import GameShell from './GameShell'

type ShotResult = 'hit' | 'miss'
type Turn = 'player' | 'cpu'
type Winner = Turn | null
type ShotMap = Partial<Record<number, ShotResult>>

type ShipState = {
  id: string
  size: number
  cells: number[]
  hits: number
}

type BattleState = {
  playerWins: number
  playerShips: ShipState[]
  cpuShips: ShipState[]
  playerShots: ShotMap
  cpuShots: ShotMap
  turn: Turn
  winner: Winner
  lastEvent: string
}

type AttackOutcome = {
  nextShips: ShipState[]
  nextShots: ShotMap
  result: ShotResult
  sunkShipSize: number | null
  allShipsSunk: boolean
}

const GRID_SIZE = 6
const CELL_COUNT = GRID_SIZE * GRID_SIZE
const SHIP_SIZES = [3, 2, 2] as const
const ROW_LABELS = ['A', 'B', 'C', 'D', 'E', 'F'] as const
const CPU_DELAY_MS = 650

function getCellIndex(row: number, column: number) {
  return row * GRID_SIZE + column
}

function formatCell(index: number) {
  const row = Math.floor(index / GRID_SIZE)
  const column = (index % GRID_SIZE) + 1
  return `${ROW_LABELS[row]}${column}`
}

function createShipCells(size: number, row: number, column: number, horizontal: boolean) {
  return Array.from({ length: size }, (_, offset) =>
    getCellIndex(row + (horizontal ? 0 : offset), column + (horizontal ? offset : 0)),
  )
}

function createRandomFleet() {
  while (true) {
    const occupied = new Set<number>()
    const ships: ShipState[] = []
    let failedPlacement = false

    for (let shipIndex = 0; shipIndex < SHIP_SIZES.length; shipIndex += 1) {
      const shipSize = SHIP_SIZES[shipIndex]
      let placed = false

      for (let attempt = 0; attempt < 80; attempt += 1) {
        const horizontal = Math.random() < 0.5
        const maxRow = horizontal ? GRID_SIZE - 1 : GRID_SIZE - shipSize
        const maxColumn = horizontal ? GRID_SIZE - shipSize : GRID_SIZE - 1
        const row = Math.floor(Math.random() * (maxRow + 1))
        const column = Math.floor(Math.random() * (maxColumn + 1))
        const cells = createShipCells(shipSize, row, column, horizontal)

        if (cells.some((cell) => occupied.has(cell))) {
          continue
        }

        cells.forEach((cell) => occupied.add(cell))
        ships.push({
          id: `ship-${shipIndex + 1}`,
          size: shipSize,
          cells,
          hits: 0,
        })
        placed = true
        break
      }

      if (!placed) {
        failedPlacement = true
        break
      }
    }

    if (!failedPlacement) {
      return ships
    }
  }
}

function applyAttack(targetShips: ShipState[], existingShots: ShotMap, targetCell: number): AttackOutcome {
  const shipIndex = targetShips.findIndex((ship) => ship.cells.includes(targetCell))
  const result: ShotResult = shipIndex >= 0 ? 'hit' : 'miss'
  const nextShots: ShotMap = {
    ...existingShots,
    [targetCell]: result,
  }

  if (result === 'miss') {
    return {
      nextShips: targetShips,
      nextShots,
      result,
      sunkShipSize: null,
      allShipsSunk: false,
    }
  }

  const nextShips = targetShips.map((ship, index) =>
    index === shipIndex ? { ...ship, hits: ship.hits + 1 } : ship,
  )
  const updatedShip = nextShips[shipIndex]
  const sunkShipSize = updatedShip.hits >= updatedShip.size ? updatedShip.size : null
  const allShipsSunk = nextShips.every((ship) => ship.hits >= ship.size)

  return {
    nextShips,
    nextShots,
    result,
    sunkShipSize,
    allShipsSunk,
  }
}

function getRandomUntargetedCell(shots: ShotMap) {
  const available: number[] = []

  for (let index = 0; index < CELL_COUNT; index += 1) {
    if (!shots[index]) {
      available.push(index)
    }
  }

  if (available.length === 0) {
    return null
  }

  return available[Math.floor(Math.random() * available.length)]
}

function getShipCells(ships: ShipState[]) {
  return new Set(ships.flatMap((ship) => ship.cells))
}

function getSunkCells(ships: ShipState[]) {
  return new Set(
    ships.filter((ship) => ship.hits >= ship.size).flatMap((ship) => ship.cells),
  )
}

function createBattleState(playerWins = 0): BattleState {
  return {
    playerWins,
    playerShips: createRandomFleet(),
    cpuShips: createRandomFleet(),
    playerShots: {},
    cpuShots: {},
    turn: 'player',
    winner: null,
    lastEvent: 'Target enemy waters to start the battle.',
  }
}

function CellIcon({
  shot,
  hasShip,
  showShip,
  isSunk,
}: {
  shot?: ShotResult
  hasShip: boolean
  showShip: boolean
  isSunk: boolean
}) {
  const backgroundFill =
    shot === 'hit'
      ? 'rgba(248, 113, 113, 0.22)'
      : shot === 'miss'
        ? 'rgba(56, 189, 248, 0.2)'
        : 'rgba(30, 41, 59, 0.7)'
  const borderColor = shot === 'hit' ? '#fda4af' : shot === 'miss' ? '#67e8f9' : '#334155'

  return (
    <svg viewBox="0 0 100 100" className="h-full w-full" fill="none" aria-hidden="true">
      <rect x="4" y="4" width="92" height="92" rx="20" fill={backgroundFill} stroke={borderColor} strokeWidth="6" />

      {showShip && hasShip && (
        <rect
          x="24"
          y="24"
          width="52"
          height="52"
          rx="14"
          fill="rgba(110, 231, 183, 0.35)"
          stroke="#6ee7b7"
          strokeWidth="5"
        />
      )}

      {shot === 'miss' && <circle cx="50" cy="50" r="11" fill="#67e8f9" />}

      {shot === 'hit' && (
        <>
          <path d="M30 30 70 70" stroke="#fecaca" strokeWidth="10" strokeLinecap="round" />
          <path d="M70 30 30 70" stroke="#fecaca" strokeWidth="10" strokeLinecap="round" />
        </>
      )}

      {isSunk && <circle cx="50" cy="50" r="42" stroke="#facc15" strokeWidth="6" strokeDasharray="8 6" />}
    </svg>
  )
}

function StatItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-700/70 bg-slate-900/50 px-3 py-2">
      <dt className="text-xs uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-slate-100">{value}</dd>
    </div>
  )
}

function GridAttack() {
  const [battleState, setBattleState] = useState<BattleState>(() => createBattleState())

  useEffect(() => {
    if (battleState.turn !== 'cpu' || battleState.winner) {
      return
    }

    const cpuTurnTimer = window.setTimeout(() => {
      setBattleState((currentState) => {
        if (currentState.turn !== 'cpu' || currentState.winner) {
          return currentState
        }

        const targetCell = getRandomUntargetedCell(currentState.cpuShots)

        if (targetCell === null) {
          return { ...currentState, turn: 'player', lastEvent: 'CPU has no valid targets left.' }
        }

        const outcome = applyAttack(currentState.playerShips, currentState.cpuShots, targetCell)
        const coordinate = formatCell(targetCell)
        let lastEvent =
          outcome.result === 'hit' ? `CPU hit your ship at ${coordinate}.` : `CPU missed at ${coordinate}.`

        if (outcome.sunkShipSize) {
          lastEvent = `CPU sunk your ${outcome.sunkShipSize}-cell ship at ${coordinate}.`
        }

        if (outcome.allShipsSunk) {
          return {
            ...currentState,
            playerShips: outcome.nextShips,
            cpuShots: outcome.nextShots,
            winner: 'cpu',
            lastEvent: 'CPU destroyed your fleet.',
          }
        }

        return {
          ...currentState,
          playerShips: outcome.nextShips,
          cpuShots: outcome.nextShots,
          turn: 'player',
          lastEvent,
        }
      })
    }, CPU_DELAY_MS)

    return () => window.clearTimeout(cpuTurnTimer)
  }, [battleState.turn, battleState.winner])

  const playerShipCells = useMemo(() => getShipCells(battleState.playerShips), [battleState.playerShips])
  const playerSunkCells = useMemo(() => getSunkCells(battleState.playerShips), [battleState.playerShips])
  const enemyShipCells = useMemo(() => getShipCells(battleState.cpuShips), [battleState.cpuShips])
  const enemySunkCells = useMemo(() => getSunkCells(battleState.cpuShips), [battleState.cpuShips])

  const playerHits = Object.values(battleState.playerShots).filter((result) => result === 'hit').length
  const playerMisses = Object.values(battleState.playerShots).filter((result) => result === 'miss').length
  const cpuHits = Object.values(battleState.cpuShots).filter((result) => result === 'hit').length
  const cpuMisses = Object.values(battleState.cpuShots).filter((result) => result === 'miss').length
  const playerShipsSunk = battleState.playerShips.filter((ship) => ship.hits >= ship.size).length
  const enemyShipsSunk = battleState.cpuShips.filter((ship) => ship.hits >= ship.size).length

  const canTargetEnemy = battleState.turn === 'player' && !battleState.winner
  const revealEnemyShips = Boolean(battleState.winner)
  const enemyBoardInstructionsId = 'grid-attack-enemy-board-instructions'

  const statusText = battleState.winner
    ? battleState.winner === 'player'
      ? `Victory! ${battleState.lastEvent}`
      : `Defeat. ${battleState.lastEvent}`
    : battleState.turn === 'player'
      ? `Your turn. ${battleState.lastEvent}`
      : `CPU turn. ${battleState.lastEvent}`

  const handleEnemyCellAttack = (cellIndex: number) => {
    setBattleState((currentState) => {
      if (currentState.turn !== 'player' || currentState.winner || currentState.playerShots[cellIndex]) {
        return currentState
      }

      const outcome = applyAttack(currentState.cpuShips, currentState.playerShots, cellIndex)
      const coordinate = formatCell(cellIndex)
      let lastEvent = outcome.result === 'hit' ? `Direct hit at ${coordinate}.` : `Shot missed at ${coordinate}.`

      if (outcome.sunkShipSize) {
        lastEvent = `You sunk an enemy ${outcome.sunkShipSize}-cell ship at ${coordinate}.`
      }

      if (outcome.allShipsSunk) {
        return {
          ...currentState,
          playerWins: currentState.playerWins + 1,
          cpuShips: outcome.nextShips,
          playerShots: outcome.nextShots,
          winner: 'player',
          lastEvent: 'You destroyed the entire enemy fleet.',
        }
      }

      return {
        ...currentState,
        cpuShips: outcome.nextShips,
        playerShots: outcome.nextShots,
        turn: 'cpu',
        lastEvent,
      }
    })
  }

  const handleReset = () => {
    setBattleState((currentState) => createBattleState(currentState.playerWins))
  }

  return (
    <GameShell
      status={statusText}
      onReset={handleReset}
      scoreLabel="Session wins"
      scoreValue={battleState.playerWins}
    >
      <p id={enemyBoardInstructionsId} className="sr-only">
        Attack the enemy board by selecting an untried cell. Hits, misses, and sunk ships are shown on the grid.
      </p>

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="rounded-xl border border-slate-700/70 bg-slate-900/45 p-3">
          <h3 className="text-sm font-semibold text-cyan-200">Your fleet</h3>
          <p className="mt-1 text-xs text-slate-300">Green markers are your ships. Red marks are enemy hits.</p>
          <div className="mx-auto mt-3 grid w-full max-w-[22rem] grid-cols-6 gap-1.5">
            {Array.from({ length: CELL_COUNT }, (_, index) => (
              <div key={`player-${index}`} className="aspect-square">
                <CellIcon
                  shot={battleState.cpuShots[index]}
                  hasShip={playerShipCells.has(index)}
                  showShip
                  isSunk={playerSunkCells.has(index)}
                />
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-xl border border-slate-700/70 bg-slate-900/45 p-3">
          <h3 className="text-sm font-semibold text-rose-200">Enemy waters</h3>
          <p className="mt-1 text-xs text-slate-300">Tap or press Enter to fire. Yellow rings mark sunk enemy ships.</p>
          <div
            role="grid"
            aria-label="Enemy board"
            aria-describedby={enemyBoardInstructionsId}
            className="mx-auto mt-3 grid w-full max-w-[22rem] grid-cols-6 gap-1.5"
          >
            {Array.from({ length: CELL_COUNT }, (_, index) => {
              const row = Math.floor(index / GRID_SIZE)
              const column = index % GRID_SIZE
              const shot = battleState.playerShots[index]
              const hasShip = enemyShipCells.has(index)
              const isDisabled = !canTargetEnemy || Boolean(shot)
              const shotText = shot === 'hit' ? 'hit' : shot === 'miss' ? 'miss' : 'untargeted'

              return (
                <button
                  key={`enemy-${index}`}
                  type="button"
                  onClick={() => handleEnemyCellAttack(index)}
                  disabled={isDisabled}
                  aria-label={`Enemy cell ${ROW_LABELS[row]}${column + 1}, ${shotText}${!shot && canTargetEnemy ? ', ready to fire' : ''}`}
                  aria-describedby={enemyBoardInstructionsId}
                  className="motion-control-press touch-manipulation aspect-square rounded-lg border border-slate-700 bg-slate-950/70 p-0.5 transition hover:border-cyan-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 disabled:cursor-not-allowed disabled:opacity-80"
                >
                  <CellIcon
                    shot={shot}
                    hasShip={hasShip}
                    showShip={revealEnemyShips}
                    isSunk={enemySunkCells.has(index)}
                  />
                </button>
              )
            })}
          </div>
        </section>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
        <StatItem label="Your hits / misses" value={`${playerHits} / ${playerMisses}`} />
        <StatItem label="CPU hits / misses" value={`${cpuHits} / ${cpuMisses}`} />
        <StatItem label="Enemy ships sunk" value={`${enemyShipsSunk} / ${SHIP_SIZES.length}`} />
        <StatItem label="Your ships sunk" value={`${playerShipsSunk} / ${SHIP_SIZES.length}`} />
        <StatItem label="Enemy cells left" value={`${CELL_COUNT - Object.keys(battleState.playerShots).length}`} />
        <StatItem label="Your cells untouched" value={`${CELL_COUNT - Object.keys(battleState.cpuShots).length}`} />
      </dl>
    </GameShell>
  )
}

export default GridAttack
