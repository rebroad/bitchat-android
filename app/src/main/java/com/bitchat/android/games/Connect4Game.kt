package com.bitchat.android.games

/**
 * Connect 4 game logic
 * Board is 7 columns (0-6) x 6 rows (0-5)
 * Pieces: RED (player 1), YELLOW (player 2), EMPTY
 */
enum class Piece {
    EMPTY,
    RED,
    YELLOW;

    val opposite: Piece
        get() = when (this) {
            RED -> YELLOW
            YELLOW -> RED
            EMPTY -> EMPTY
        }
}

data class Connect4Move(val column: Int, val player: Piece)

data class Connect4Game(
    val board: Array<Array<Piece>> = Array(6) { Array(7) { Piece.EMPTY } },
    val currentPlayer: Piece = Piece.RED,
    val moves: List<Connect4Move> = emptyList(),
    val winner: Piece? = null,
    val isGameOver: Boolean = false
) {
    companion object {
        const val COLUMNS = 7
        const val ROWS = 6
    }

    /**
     * Make a move in the specified column
     * Returns the new game state, or null if the move is invalid
     */
    fun makeMove(column: Int): Connect4Game? {
        if (isGameOver || column < 0 || column >= COLUMNS) {
            return null
        }

        // Find the lowest empty row in this column
        val row = (ROWS - 1 downTo 0).firstOrNull { board[it][column] == Piece.EMPTY }
            ?: return null // Column is full

        // Create new board with the move
        val newBoard = board.mapIndexed { r, rowArray ->
            if (r == row) {
                rowArray.mapIndexed { c, piece ->
                    if (c == column) currentPlayer else piece
                }.toTypedArray()
            } else {
                rowArray.copyOf()
            }
        }.toTypedArray()

        val move = Connect4Move(column, currentPlayer)
        val newMoves = moves + move

        // Check for win
        val newWinner = checkWinner(newBoard, row, column, currentPlayer)
        val newIsGameOver = newWinner != null || isBoardFull(newBoard)

        return Connect4Game(
            board = newBoard,
            currentPlayer = currentPlayer.opposite,
            moves = newMoves,
            winner = newWinner,
            isGameOver = newIsGameOver
        )
    }

    /**
     * Check if the board is full (tie game)
     */
    private fun isBoardFull(board: Array<Array<Piece>>): Boolean {
        return board[0].all { it != Piece.EMPTY }
    }

    /**
     * Check if the last move resulted in a win
     */
    private fun checkWinner(board: Array<Array<Piece>>, row: Int, col: Int, piece: Piece): Piece? {
        // Check horizontal
        var count = 1
        // Left
        var c = col - 1
        while (c >= 0 && board[row][c] == piece) {
            count++
            c--
        }
        // Right
        c = col + 1
        while (c < COLUMNS && board[row][c] == piece) {
            count++
            c++
        }
        if (count >= 4) return piece

        // Check vertical
        count = 1
        // Down (only need to check down since pieces fall)
        var r = row + 1
        while (r < ROWS && board[r][col] == piece) {
            count++
            r++
        }
        if (count >= 4) return piece

        // Check diagonal (bottom-left to top-right)
        count = 1
        r = row - 1
        c = col - 1
        while (r >= 0 && c >= 0 && board[r][c] == piece) {
            count++
            r--
            c--
        }
        r = row + 1
        c = col + 1
        while (r < ROWS && c < COLUMNS && board[r][c] == piece) {
            count++
            r++
            c++
        }
        if (count >= 4) return piece

        // Check diagonal (top-left to bottom-right)
        count = 1
        r = row - 1
        c = col + 1
        while (r >= 0 && c < COLUMNS && board[r][c] == piece) {
            count++
            r--
            c++
        }
        r = row + 1
        c = col - 1
        while (r < ROWS && c >= 0 && board[r][c] == piece) {
            count++
            r++
            c--
        }
        if (count >= 4) return piece

        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Connect4Game

        if (!board.contentDeepEquals(other.board)) return false
        if (currentPlayer != other.currentPlayer) return false
        if (moves != other.moves) return false
        if (winner != other.winner) return false
        if (isGameOver != other.isGameOver) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + moves.hashCode()
        result = 31 * result + (winner?.hashCode() ?: 0)
        result = 31 * result + isGameOver.hashCode()
        return result
    }
}

