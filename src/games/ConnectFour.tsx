import { useEffect, useMemo, useState } from 'react'
import GameModeToggle, { type GameMode } from './GameModeToggle'
import GameShell from './GameShell'

type Disc = 'R' | 'Y' | null

const ROWS = 6
const COLUMNS = 7
const WIN_LENGTH = 4
const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [1, 0],
  [0, 1],
  [1, 1],
  [1, -1],
]
const CENTER_PRIORITY_COLUMNS: ReadonlyArray<number> = [3, 2, 4, 1, 5, 0, 6]

function getCellIndex(row: number, column: number) {
  return row * COLUMNS + column
}

function getDropRow(board: Disc[], column: number) {
  for (let row = ROWS - 1; row >= 0; row -= 1) {
    if (!board[getCellIndex(row, column)]) {
      return row
    }
  }

  return -1
}

function simulateDrop(board: Disc[], column: number, disc: Exclude<Disc, null>) {
  const targetRow = getDropRow(board, column)
  if (targetRow < 0) {
    return null
  }

  const targetIndex = getCellIndex(targetRow, column)
  const nextBoard = [...board]
  nextBoard[targetIndex] = disc
  return { nextBoard, targetIndex }
}

function getWinner(board: Disc[]): Exclude<Disc, null> | null {
  for (let row = 0; row < ROWS; row += 1) {
    for (let column = 0; column < COLUMNS; column += 1) {
      const currentDisc = board[getCellIndex(row, column)]

      if (!currentDisc) {
        continue
      }

      for (const [rowStep, columnStep] of DIRECTIONS) {
        let matches = 1

        while (matches < WIN_LENGTH) {
          const nextRow = row + rowStep * matches
          const nextColumn = column + columnStep * matches

          if (nextRow < 0 || nextRow >= ROWS || nextColumn < 0 || nextColumn >= COLUMNS) {
            break
          }

          if (board[getCellIndex(nextRow, nextColumn)] !== currentDisc) {
            break
          }

          matches += 1
        }

        if (matches === WIN_LENGTH) {
          return currentDisc
        }
      }
    }
  }

  return null
}

function getWinningColumn(board: Disc[], disc: Exclude<Disc, null>): number | null {
  for (let column = 0; column < COLUMNS; column += 1) {
    const simulatedDrop = simulateDrop(board, column, disc)
    if (!simulatedDrop) {
      continue
    }

    if (getWinner(simulatedDrop.nextBoard) === disc) {
      return column
    }
  }

  return null
}

function getAiColumn(board: Disc[]): number | null {
  const winningColumn = getWinningColumn(board, 'Y')
  if (winningColumn !== null) {
    return winningColumn
  }

  const blockingColumn = getWinningColumn(board, 'R')
  if (blockingColumn !== null) {
    return blockingColumn
  }

  const preferredColumn = CENTER_PRIORITY_COLUMNS.find((column) => getDropRow(board, column) >= 0)
  return typeof preferredColumn === 'number' ? preferredColumn : null
}

function ConnectFourDisc({ disc, animate }: { disc: Exclude<Disc, null>; animate: boolean }) {
  const stroke = disc === 'R' ? '#fecdd3' : '#fef3c7'
  const highlight = disc === 'R' ? 'rgba(255, 228, 230, 0.7)' : 'rgba(254, 243, 199, 0.72)'

  return (
    <svg
      viewBox="0 0 100 100"
      className={`h-full w-full ${animate ? 'motion-drop' : ''}`}
      fill="none"
      aria-hidden="true"
    >
      <circle
        cx="50"
        cy="50"
        r="42"
        fill={disc === 'R' ? '#fb7185' : '#fcd34d'}
        stroke={stroke}
        strokeWidth="8"
      />
      <circle cx="38" cy="34" r="8" fill={highlight} />
    </svg>
  )
}

function ConnectFour() {
  const [mode, setMode] = useState<GameMode>('local')
  const [board, setBoard] = useState<Disc[]>(() => Array(ROWS * COLUMNS).fill(null))
  const [isRedTurn, setIsRedTurn] = useState(true)
  const [lastDropIndex, setLastDropIndex] = useState<number | null>(null)

  const winner = useMemo(() => getWinner(board), [board])
  const isDraw = board.every((value) => value !== null) && !winner
  const isAiTurn = mode === 'ai' && !isRedTurn && !winner && !isDraw
  const statusText = winner
    ? `Winner: ${winner === 'R' ? 'Red' : 'Yellow'}`
    : isDraw
      ? "It's a draw!"
      : isAiTurn
        ? 'AI is thinking...'
        : `Turn: ${isRedTurn ? 'Red' : mode === 'ai' ? 'Yellow (AI)' : 'Yellow'}`

  useEffect(() => {
    if (!isAiTurn) {
      return
    }

    const aiColumn = getAiColumn(board)
    if (aiColumn === null) {
      return
    }

    const timer = window.setTimeout(() => {
      const simulatedDrop = simulateDrop(board, aiColumn, 'Y')
      if (!simulatedDrop) {
        return
      }

      setIsRedTurn(true)
      setBoard(simulatedDrop.nextBoard)
      setLastDropIndex(simulatedDrop.targetIndex)
    }, 180)

    return () => window.clearTimeout(timer)
  }, [board, isAiTurn])

  const resetGame = () => {
    setBoard(Array(ROWS * COLUMNS).fill(null))
    setIsRedTurn(true)
    setLastDropIndex(null)
  }

  const handleModeChange = (nextMode: GameMode) => {
    if (nextMode === mode) {
      return
    }

    setMode(nextMode)
    resetGame()
  }

  const isColumnFull = (column: number) => getDropRow(board, column) < 0

  const handleDrop = (column: number) => {
    if (winner || isDraw || isAiTurn || isColumnFull(column)) {
      return
    }

    const simulatedDrop = simulateDrop(board, column, isRedTurn ? 'R' : 'Y')
    if (!simulatedDrop) {
      return
    }

    setBoard(simulatedDrop.nextBoard)
    setLastDropIndex(simulatedDrop.targetIndex)
    setIsRedTurn((value) => !value)
  }

  const handleReset = () => {
    resetGame()
  }

  const boardInstructionsId = 'connect-four-board-instructions'

  return (
    <GameShell status={statusText} onReset={handleReset}>
      <GameModeToggle mode={mode} onModeChange={handleModeChange} />
      <p id={boardInstructionsId} className="sr-only">
        Select any slot in a column to drop your disc into the lowest available row of that column.
      </p>
      <div
        className="touch-manipulation grid grid-cols-7 gap-2 rounded-xl border border-slate-700/70 bg-indigo-950/50 p-3"
        role="grid"
        aria-label="Connect Four board"
        aria-describedby={boardInstructionsId}
      >
        {board.map((disc, index) => {
          const column = index % COLUMNS
          const row = Math.floor(index / COLUMNS)
          const columnFull = isColumnFull(column)
          const isDisabled = Boolean(winner) || isDraw || columnFull || isAiTurn
          const occupancyLabel =
            disc === 'R' ? 'occupied by Red' : disc === 'Y' ? 'occupied by Yellow' : 'empty'

          return (
            <button
              key={index}
              type="button"
              onClick={() => handleDrop(column)}
              disabled={isDisabled}
              aria-label={`Column ${column + 1}, row ${row + 1}, ${occupancyLabel}. ${
                columnFull ? 'Column is full.' : `Drop disc in column ${column + 1}.`
              }`}
              aria-describedby={boardInstructionsId}
              className="motion-control-press touch-manipulation aspect-square rounded-full border border-slate-800 bg-slate-700/80 p-1 transition hover:border-cyan-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 disabled:cursor-not-allowed disabled:border-slate-700/70 disabled:bg-slate-800/40 disabled:opacity-70"
            >
              {disc && <ConnectFourDisc disc={disc} animate={index === lastDropIndex} />}
            </button>
          )
        })}
      </div>
    </GameShell>
  )
}

export default ConnectFour
