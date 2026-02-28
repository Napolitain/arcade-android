import { useMemo, useState } from 'react'
import GameShell from './GameShell'

type Disc = 'B' | 'W' | null
type Player = Exclude<Disc, null>
type DiscAnimation = 'place' | 'flip' | null

const BOARD_SIZE = 8
const TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE
const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [-1, -1],
  [-1, 0],
  [-1, 1],
  [0, -1],
  [0, 1],
  [1, -1],
  [1, 0],
  [1, 1],
]

function getCellIndex(row: number, column: number) {
  return row * BOARD_SIZE + column
}

function isInsideBoard(row: number, column: number) {
  return row >= 0 && row < BOARD_SIZE && column >= 0 && column < BOARD_SIZE
}

function getOpponent(player: Player): Player {
  return player === 'B' ? 'W' : 'B'
}

function getPlayerLabel(player: Player) {
  return player === 'B' ? 'Black' : 'White'
}

function createInitialBoard(): Disc[] {
  const board: Disc[] = Array(TOTAL_CELLS).fill(null)
  const middle = BOARD_SIZE / 2

  board[getCellIndex(middle - 1, middle - 1)] = 'W'
  board[getCellIndex(middle - 1, middle)] = 'B'
  board[getCellIndex(middle, middle - 1)] = 'B'
  board[getCellIndex(middle, middle)] = 'W'

  return board
}

function getCapturedDiscs(board: Disc[], row: number, column: number, player: Player): number[] {
  if (board[getCellIndex(row, column)] !== null) {
    return []
  }

  const opponent = getOpponent(player)
  const captured: number[] = []

  for (const [rowStep, columnStep] of DIRECTIONS) {
    const line: number[] = []
    let nextRow = row + rowStep
    let nextColumn = column + columnStep

    while (isInsideBoard(nextRow, nextColumn)) {
      const index = getCellIndex(nextRow, nextColumn)
      const disc = board[index]

      if (disc === opponent) {
        line.push(index)
        nextRow += rowStep
        nextColumn += columnStep
        continue
      }

      if (disc === player && line.length > 0) {
        captured.push(...line)
      }

      break
    }
  }

  return captured
}

function getLegalMoves(board: Disc[], player: Player): number[] {
  const legalMoves: number[] = []

  for (let row = 0; row < BOARD_SIZE; row += 1) {
    for (let column = 0; column < BOARD_SIZE; column += 1) {
      const index = getCellIndex(row, column)

      if (board[index] !== null) {
        continue
      }

      if (getCapturedDiscs(board, row, column, player).length > 0) {
        legalMoves.push(index)
      }
    }
  }

  return legalMoves
}

function applyMove(board: Disc[], index: number, player: Player) {
  if (board[index] !== null) {
    return null
  }

  const row = Math.floor(index / BOARD_SIZE)
  const column = index % BOARD_SIZE
  const captured = getCapturedDiscs(board, row, column, player)

  if (captured.length === 0) {
    return null
  }

  const nextBoard = [...board]
  nextBoard[index] = player
  captured.forEach((capturedIndex) => {
    nextBoard[capturedIndex] = player
  })

  return { nextBoard, captured }
}

function countDiscs(board: Disc[], player: Player) {
  return board.reduce((total, disc) => (disc === player ? total + 1 : total), 0)
}

function ReversiDisc({ disc, animation }: { disc: Player; animation: DiscAnimation }) {
  const isBlack = disc === 'B'
  const animationClass =
    animation === 'place' ? 'motion-drop' : animation === 'flip' ? 'motion-tile-merge' : ''
  const style =
    animation === 'flip'
      ? {
          transition: 'transform 220ms ease',
          transform: 'rotateY(360deg)',
        }
      : undefined

  return (
    <svg viewBox="0 0 100 100" className={`h-full w-full ${animationClass}`} style={style} fill="none" aria-hidden="true">
      <circle
        cx="50"
        cy="50"
        r="42"
        fill={isBlack ? '#0f172a' : '#e2e8f0'}
        stroke={isBlack ? '#334155' : '#cbd5e1'}
        strokeWidth="8"
      />
      <circle
        cx="35"
        cy="34"
        r="9"
        fill={isBlack ? 'rgba(148, 163, 184, 0.45)' : 'rgba(255, 255, 255, 0.75)'}
      />
    </svg>
  )
}

function Reversi() {
  const [board, setBoard] = useState<Disc[]>(() => createInitialBoard())
  const [currentPlayer, setCurrentPlayer] = useState<Player>('B')
  const [isGameOver, setIsGameOver] = useState(false)
  const [passMessage, setPassMessage] = useState<string | null>(null)
  const [lastPlacedIndex, setLastPlacedIndex] = useState<number | null>(null)
  const [lastFlippedIndices, setLastFlippedIndices] = useState<number[]>([])
  const [animationCycle, setAnimationCycle] = useState(0)

  const blackCount = useMemo(() => countDiscs(board, 'B'), [board])
  const whiteCount = useMemo(() => countDiscs(board, 'W'), [board])
  const legalMoves = useMemo(() => new Set(getLegalMoves(board, currentPlayer)), [board, currentPlayer])
  const lastFlippedLookup = useMemo(() => new Set(lastFlippedIndices), [lastFlippedIndices])

  const winner = useMemo(() => {
    if (!isGameOver || blackCount === whiteCount) {
      return null
    }

    return blackCount > whiteCount ? 'B' : 'W'
  }, [blackCount, whiteCount, isGameOver])

  const statusText = isGameOver
    ? winner
      ? `Game over! ${getPlayerLabel(winner)} wins.`
      : "Game over! It's a tie."
    : passMessage ??
      `Turn: ${getPlayerLabel(currentPlayer)} (${legalMoves.size} legal move${legalMoves.size === 1 ? '' : 's'})`

  const boardInstructionsId = 'reversi-board-instructions'

  const handleCellClick = (index: number) => {
    if (isGameOver) {
      return
    }

    const move = applyMove(board, index, currentPlayer)

    if (!move) {
      return
    }

    const opponent = getOpponent(currentPlayer)
    const opponentLegalMoves = getLegalMoves(move.nextBoard, opponent)
    const currentLegalMoves = getLegalMoves(move.nextBoard, currentPlayer)

    setBoard(move.nextBoard)
    setLastPlacedIndex(index)
    setLastFlippedIndices(move.captured)
    setAnimationCycle((value) => value + 1)

    if (opponentLegalMoves.length > 0) {
      setCurrentPlayer(opponent)
      setPassMessage(null)
      setIsGameOver(false)
      return
    }

    if (currentLegalMoves.length > 0) {
      setCurrentPlayer(currentPlayer)
      setPassMessage(`${getPlayerLabel(opponent)} has no legal moves. ${getPlayerLabel(currentPlayer)} plays again.`)
      setIsGameOver(false)
      return
    }

    setPassMessage(null)
    setIsGameOver(true)
  }

  const handleReset = () => {
    setBoard(createInitialBoard())
    setCurrentPlayer('B')
    setIsGameOver(false)
    setPassMessage(null)
    setLastPlacedIndex(null)
    setLastFlippedIndices([])
    setAnimationCycle(0)
  }

  return (
    <GameShell status={statusText} onReset={handleReset}>
      <p id={boardInstructionsId} className="sr-only">
        Place discs on highlighted cells to capture lines of your opponent&apos;s discs.
      </p>

      <div className="mb-3 grid grid-cols-2 gap-2 text-sm">
        <div className="flex items-center justify-between rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2">
          <span className="inline-flex items-center gap-2">
            <span className="h-3 w-3 rounded-full bg-slate-950 ring-1 ring-slate-500" aria-hidden="true" />
            Black
          </span>
          <span className="font-semibold text-cyan-100">{blackCount}</span>
        </div>
        <div className="flex items-center justify-between rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2">
          <span className="inline-flex items-center gap-2">
            <span className="h-3 w-3 rounded-full bg-slate-100 ring-1 ring-slate-300" aria-hidden="true" />
            White
          </span>
          <span className="font-semibold text-cyan-100">{whiteCount}</span>
        </div>
      </div>

      <div
        className="touch-manipulation grid grid-cols-8 gap-1 rounded-xl border border-slate-700/70 bg-emerald-950/40 p-2 sm:gap-1.5 sm:p-3"
        role="grid"
        aria-label="Reversi board"
        aria-describedby={boardInstructionsId}
      >
        {board.map((disc, index) => {
          const row = Math.floor(index / BOARD_SIZE)
          const column = index % BOARD_SIZE
          const isLegalMove = legalMoves.has(index) && !isGameOver
          const isFlipped = lastFlippedLookup.has(index)
          const animation: DiscAnimation = index === lastPlacedIndex ? 'place' : isFlipped ? 'flip' : null
          const occupancyLabel =
            disc === 'B' ? 'occupied by Black' : disc === 'W' ? 'occupied by White' : 'empty'

          return (
            <button
              key={`${index}-${animation ? animationCycle : 'steady'}`}
              type="button"
              onClick={() => handleCellClick(index)}
              disabled={!isLegalMove}
              aria-label={`Row ${row + 1}, column ${column + 1}, ${occupancyLabel}${isLegalMove ? ', legal move' : ''}`}
              aria-describedby={boardInstructionsId}
              className={`motion-control-press touch-manipulation relative aspect-square rounded-md border p-0.5 transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 disabled:cursor-not-allowed ${
                isLegalMove
                  ? 'border-cyan-400/80 bg-emerald-700/40 hover:border-cyan-300 hover:bg-emerald-700/55'
                  : 'border-emerald-900/70 bg-emerald-900/45'
              }`}
            >
              <span className="flex h-full w-full items-center justify-center">
                {disc ? (
                  <ReversiDisc disc={disc} animation={animation} />
                ) : (
                  isLegalMove && <span className="h-2.5 w-2.5 rounded-full bg-cyan-300/80" aria-hidden="true" />
                )}
              </span>
            </button>
          )
        })}
      </div>
    </GameShell>
  )
}

export default Reversi
