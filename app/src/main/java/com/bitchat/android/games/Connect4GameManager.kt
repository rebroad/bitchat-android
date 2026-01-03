package com.bitchat.android.games

import android.util.Log

/**
 * Manages Connect 4 games per peer
 */
class Connect4GameManager {
    companion object {
        private const val TAG = "Connect4GameManager"
        private const val MOVE_PREFIX = "connect4_move:"
    }

    // Active games per peer ID
    private val activeGames = mutableMapOf<String, Connect4Game>()

    // Track which piece each player has (RED or YELLOW)
    private val playerPieces = mutableMapOf<String, Pair<Piece, Piece>>() // peerID -> (myPiece, opponentPiece)

    /**
     * Start a new game with a peer
     * @param peerID The peer to play with
     * @param iAmRed Whether I (the local player) am playing as RED
     */
    fun startGame(peerID: String, iAmRed: Boolean = true): Connect4Game {
        val myPiece = if (iAmRed) Piece.RED else Piece.YELLOW
        val opponentPiece = myPiece.opposite
        playerPieces[peerID] = Pair(myPiece, opponentPiece)
        val game = Connect4Game()
        activeGames[peerID] = game
        Log.d(TAG, "Started new Connect 4 game with $peerID (I am $myPiece)")
        return game
    }

    /**
     * Get the current game state for a peer, or null if no game is active
     */
    fun getGame(peerID: String): Connect4Game? {
        return activeGames[peerID]
    }

    /**
     * Get which piece I am playing as
     */
    fun getMyPiece(peerID: String): Piece? {
        return playerPieces[peerID]?.first
    }

    /**
     * Check if a message is a Connect 4 move
     */
    fun isGameMove(content: String): Boolean {
        return content.startsWith(MOVE_PREFIX)
    }

    /**
     * Parse a move from a message content
     * @return Column index (0-6), or null if invalid
     */
    fun parseMove(content: String): Int? {
        if (!content.startsWith(MOVE_PREFIX)) return null
        val columnStr = content.substring(MOVE_PREFIX.length).trim()
        return columnStr.toIntOrNull()?.takeIf { it in 0..6 }
    }

    /**
     * Format a move for sending as a message
     */
    fun formatMove(column: Int): String {
        return "$MOVE_PREFIX$column"
    }

    /**
     * Apply a move from the opponent
     * @param peerID The peer who made the move
     * @param column The column index (0-6)
     * @return The updated game state, or null if the move was invalid
     */
    fun applyOpponentMove(peerID: String, column: Int): Connect4Game? {
        val game = activeGames[peerID] ?: return null
        val (myPiece, opponentPiece) = playerPieces[peerID] ?: return null

        // The opponent's move should use the current player (which should be opponentPiece)
        if (game.currentPlayer != opponentPiece) {
            Log.w(TAG, "Move out of turn from $peerID")
            return null
        }

        val newGame = game.makeMove(column) ?: return null
        activeGames[peerID] = newGame
        Log.d(TAG, "Applied move from $peerID: column $column")
        return newGame
    }

    /**
     * Apply my move locally (before sending to opponent)
     * @param peerID The peer I'm playing with
     * @param column The column index (0-6)
     * @return The updated game state, or null if the move was invalid
     */
    fun applyMyMove(peerID: String, column: Int): Connect4Game? {
        val game = activeGames[peerID] ?: return null
        val (myPiece, _) = playerPieces[peerID] ?: return null

        if (game.currentPlayer != myPiece) {
            Log.w(TAG, "Not my turn")
            return null
        }

        val newGame = game.makeMove(column) ?: return null
        activeGames[peerID] = newGame
        Log.d(TAG, "Applied my move: column $column")
        return newGame
    }

    /**
     * End a game (cleanup)
     */
    fun endGame(peerID: String) {
        activeGames.remove(peerID)
        playerPieces.remove(peerID)
        Log.d(TAG, "Ended game with $peerID")
    }

    /**
     * Check if a game is active for a peer
     */
    fun hasActiveGame(peerID: String): Boolean {
        return activeGames.containsKey(peerID)
    }
}

