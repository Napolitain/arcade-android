import { useMemo, useState } from 'react'
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

function getCellIndex(row: number, column: number) {
  return row * COLUMNS + column
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
  const [board, setBoard] = useState<Disc[]>(() => Array(ROWS * COLUMNS).fill(null))
  const [isRedTurn, setIsRedTurn] = useState(true)
  const [lastDropIndex, setLastDropIndex] = useState<number | null>(null)

  const winner = useMemo(() => getWinner(board), [board])
  const isDraw = board.every((value) => value !== null) && !winner
  const statusText = winner
    ? `Winner: ${winner === 'R' ? 'Red' : 'Yellow'}`
    : isDraw
      ? "It's a draw!"
      : `Turn: ${isRedTurn ? 'Red' : 'Yellow'}`

  const isColumnFull = (column: number) => board[getCellIndex(0, column)] !== null

  const handleDrop = (column: number) => {
    if (winner || isDraw || isColumnFull(column)) {
      return
    }

    let targetRow = -1

    for (let row = ROWS - 1; row >= 0; row -= 1) {
      if (!board[getCellIndex(row, column)]) {
        targetRow = row
        break
      }
    }

    if (targetRow < 0) {
      return
    }

    const nextBoard = [...board]
    const targetIndex = getCellIndex(targetRow, column)
    nextBoard[targetIndex] = isRedTurn ? 'R' : 'Y'
    setBoard(nextBoard)
    setLastDropIndex(targetIndex)
    setIsRedTurn((value) => !value)
  }

  const handleReset = () => {
    setBoard(Array(ROWS * COLUMNS).fill(null))
    setIsRedTurn(true)
    setLastDropIndex(null)
  }

  const boardInstructionsId = 'connect-four-board-instructions'

  return (
    <GameShell status={statusText} onReset={handleReset}>
      <p id={boardInstructionsId} className="sr-only">
        Select any slot in a column to drop your disc into the lowest available row of that column.
      </p>
      <div
        className="grid grid-cols-7 gap-2 rounded-xl border border-slate-700/70 bg-indigo-950/50 p-3"
        role="grid"
        aria-label="Connect Four board"
        aria-describedby={boardInstructionsId}
      >
        {board.map((disc, index) => {
          const column = index % COLUMNS
          const row = Math.floor(index / COLUMNS)
          const columnFull = isColumnFull(column)
          const isDisabled = Boolean(winner) || isDraw || columnFull
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
              className="motion-control-press aspect-square rounded-full border border-slate-800 bg-slate-700/80 p-1 transition hover:border-cyan-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 disabled:cursor-not-allowed disabled:border-slate-700/70 disabled:bg-slate-800/40 disabled:opacity-70"
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
