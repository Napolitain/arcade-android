import { useEffect, useMemo, useState } from 'react'
import GameModeToggle, { type GameMode } from './GameModeToggle'
import GameShell from './GameShell'

type Mark = 'X' | 'O' | null

const WINNING_LINES: ReadonlyArray<readonly [number, number, number]> = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
]
const CORNER_INDICES: ReadonlyArray<number> = [0, 2, 6, 8]
const SIDE_INDICES: ReadonlyArray<number> = [1, 3, 5, 7]

function getWinner(board: Mark[]): Exclude<Mark, null> | null {
  for (const [a, b, c] of WINNING_LINES) {
    if (board[a] && board[a] === board[b] && board[a] === board[c]) {
      return board[a]
    }
  }

  return null
}

function getWinningMove(board: Mark[], mark: Exclude<Mark, null>): number | null {
  for (let index = 0; index < board.length; index += 1) {
    if (board[index]) {
      continue
    }

    const nextBoard = [...board]
    nextBoard[index] = mark

    if (getWinner(nextBoard) === mark) {
      return index
    }
  }

  return null
}

function getAiMove(board: Mark[]): number | null {
  const winningMove = getWinningMove(board, 'O')
  if (winningMove !== null) {
    return winningMove
  }

  const blockingMove = getWinningMove(board, 'X')
  if (blockingMove !== null) {
    return blockingMove
  }

  if (!board[4]) {
    return 4
  }

  const openCorner = CORNER_INDICES.find((index) => !board[index])
  if (typeof openCorner === 'number') {
    return openCorner
  }

  const openSide = SIDE_INDICES.find((index) => !board[index])
  return typeof openSide === 'number' ? openSide : null
}

function AnimatedMark({ mark }: { mark: Exclude<Mark, null> }) {
  if (mark === 'O') {
    return (
      <svg viewBox="0 0 100 100" className="h-12 w-12" fill="none" aria-hidden="true">
        <circle
          cx="50"
          cy="50"
          r="40"
          strokeWidth="10"
          strokeLinecap="round"
          className="mark-circle stroke-cyan-300"
        />
      </svg>
    )
  }

  return (
    <svg viewBox="0 0 100 100" className="h-12 w-12" fill="none" aria-hidden="true">
      <line
        x1="24"
        y1="24"
        x2="76"
        y2="76"
        strokeWidth="10"
        strokeLinecap="round"
        className="mark-line stroke-rose-300"
      />
      <line
        x1="76"
        y1="24"
        x2="24"
        y2="76"
        strokeWidth="10"
        strokeLinecap="round"
        className="mark-line mark-line-delay stroke-rose-300"
      />
    </svg>
  )
}

function TicTacToe() {
  const [mode, setMode] = useState<GameMode>('local')
  const [board, setBoard] = useState<Mark[]>(() => Array(9).fill(null))
  const [isXTurn, setIsXTurn] = useState(true)

  const winner = useMemo(() => getWinner(board), [board])
  const isDraw = board.every((value) => value !== null) && !winner
  const isAiTurn = mode === 'ai' && !isXTurn && !winner && !isDraw
  const statusText = winner
    ? `Winner: ${winner}`
    : isDraw
      ? "It's a draw!"
      : isAiTurn
        ? 'AI is thinking...'
        : `Turn: ${isXTurn ? 'X' : mode === 'ai' ? 'O (AI)' : 'O'}`

  useEffect(() => {
    if (!isAiTurn) {
      return
    }

    const aiMove = getAiMove(board)
    if (aiMove === null) {
      return
    }

    const timer = window.setTimeout(() => {
      const nextBoard = [...board]
      nextBoard[aiMove] = 'O'
      setIsXTurn(true)
      setBoard(nextBoard)
    }, 180)

    return () => window.clearTimeout(timer)
  }, [board, isAiTurn])

  const resetGame = () => {
    setBoard(Array(9).fill(null))
    setIsXTurn(true)
  }

  const handleModeChange = (nextMode: GameMode) => {
    if (nextMode === mode) {
      return
    }

    setMode(nextMode)
    resetGame()
  }

  const handleCellClick = (index: number) => {
    if (board[index] || winner || isDraw || isAiTurn) {
      return
    }

    const nextBoard = [...board]
    nextBoard[index] = isXTurn ? 'X' : 'O'
    setBoard(nextBoard)
    setIsXTurn((value) => !value)
  }

  const handleReset = () => {
    resetGame()
  }

  return (
    <GameShell status={statusText} onReset={handleReset}>
      <GameModeToggle mode={mode} onModeChange={handleModeChange} />
      <div className="grid grid-cols-3 gap-3">
        {board.map((mark, index) => (
          <button
            key={index}
            type="button"
            onClick={() => handleCellClick(index)}
            disabled={Boolean(mark) || Boolean(winner) || isDraw || isAiTurn}
            aria-label={`Cell ${index + 1}${mark ? `, ${mark}` : ''}`}
            className="touch-manipulation aspect-square rounded-xl border border-slate-600 bg-slate-800/80 transition hover:border-cyan-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 disabled:cursor-not-allowed disabled:opacity-70"
          >
            <span className="flex h-full items-center justify-center">{mark && <AnimatedMark mark={mark} />}</span>
          </button>
        ))}
      </div>
    </GameShell>
  )
}

export default TicTacToe
