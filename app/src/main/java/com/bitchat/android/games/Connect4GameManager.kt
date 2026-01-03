package com.bitchat.android.games

import android.util.Log
import kotlin.random.Random

/**
 * Manages Connect 4 games per peer
 */
class Connect4GameManager {
    companion object {
        private const val TAG = "Connect4GameManager"
        private const val MOVE_PREFIX = "connect4_move:"
        private const val INVITE_PREFIX = "connect4_invite:"
        private const val ACCEPT_PREFIX = "connect4_accept:"
        private const val START_PREFIX = "connect4_start:"
        private const val DECLINE_PREFIX = "connect4_decline:"
    }

    enum class GameSetupState {
        NONE,               // No game activity
        INVITE_SENT,        // We sent an invite, waiting for response
        INVITE_RECEIVED,    // We received an invite, need to respond
        ACCEPT_SENT,        // We accepted, waiting for start confirmation
        STARTED             // Game has started
    }

    data class GameSetup(
        var state: GameSetupState = GameSetupState.NONE,
        var myPreferredColor: Piece? = null,
        var myRandomNumber: Int? = null,
        var opponentPreferredColor: Piece? = null,
        var opponentRandomNumber: Int? = null
    )

    // Active games per peer ID
    private val activeGames = mutableMapOf<String, Connect4Game>()

    // Track which piece each player has (RED or YELLOW)
    private val playerPieces = mutableMapOf<String, Pair<Piece, Piece>>() // peerID -> (myPiece, opponentPiece)

    // Game setup state per peer
    private val gameSetups = mutableMapOf<String, GameSetup>()

    /**
     * Send a game invite with preferred color and random number
     */
    fun sendInvite(peerID: String, preferredColor: Piece): String {
        val randomNumber = Random.nextInt(1, 10000)
        val setup = GameSetup(
            state = GameSetupState.INVITE_SENT,
            myPreferredColor = preferredColor,
            myRandomNumber = randomNumber
        )
        gameSetups[peerID] = setup
        val message = "$INVITE_PREFIX${preferredColor.name}:$randomNumber"
        Log.d(TAG, "Sending invite to $peerID: color=${preferredColor.name}, random=$randomNumber")
        return message
    }

    /**
     * Handle received invite
     */
    fun handleInvite(peerID: String, opponentColor: Piece, opponentRandom: Int): String? {
        val setup = gameSetups.getOrPut(peerID) { GameSetup() }
        if (setup.state != GameSetupState.NONE && setup.state != GameSetupState.INVITE_RECEIVED) {
            Log.w(TAG, "Unexpected invite from $peerID in state ${setup.state}")
            return null
        }

        setup.state = GameSetupState.INVITE_RECEIVED
        setup.opponentPreferredColor = opponentColor
        setup.opponentRandomNumber = opponentRandom
        Log.d(TAG, "Received invite from $peerID: color=${opponentColor.name}, random=$opponentRandom")
        return null // Return null means we need user to choose color
    }

    /**
     * Accept invite with preferred color and random number
     */
    fun acceptInvite(peerID: String, preferredColor: Piece): String? {
        val setup = gameSetups[peerID] ?: return null
        if (setup.state != GameSetupState.INVITE_RECEIVED) {
            Log.w(TAG, "Cannot accept invite in state ${setup.state}")
            return null
        }

        // Ensure colors are different
        if (preferredColor == setup.opponentPreferredColor) {
            Log.w(TAG, "Color conflict: both chose ${preferredColor.name}")
            return null // Color conflict, need to retry
        }

        val randomNumber = Random.nextInt(1, 10000)
        setup.myPreferredColor = preferredColor
        setup.myRandomNumber = randomNumber
        setup.state = GameSetupState.ACCEPT_SENT

        val message = "$ACCEPT_PREFIX${preferredColor.name}:$randomNumber"
        Log.d(TAG, "Accepting invite from $peerID: color=${preferredColor.name}, random=$randomNumber")
        return message
    }

    /**
     * Handle accept message and determine final colors and starting player
     */
    fun handleAccept(peerID: String, opponentColor: Piece, opponentRandom: Int): String? {
        val setup = gameSetups[peerID] ?: return null
        if (setup.state != GameSetupState.INVITE_SENT) {
            Log.w(TAG, "Unexpected accept in state ${setup.state}")
            return null
        }

        setup.opponentPreferredColor = opponentColor
        setup.opponentRandomNumber = opponentRandom

        // Determine starting player based on random numbers (higher number starts)
        val myRandom = setup.myRandomNumber!!
        val iStartFirst = myRandom > opponentRandom

        // Determine final colors
        val myColor = setup.myPreferredColor!!
        val finalOpponentColor = if (opponentColor != myColor) {
            // Different colors: use preferences
            opponentColor
        } else {
            // Same color: starting player gets their chosen color, other gets opposite
            if (iStartFirst) {
                myColor.opposite  // Opponent gets opposite of my color
            } else {
                opponentColor  // Opponent gets their chosen color, I'll get opposite
            }
        }

        // If colors conflicted and opponent starts first, I get opposite of their color
        val finalMyColor = if (opponentColor == setup.myPreferredColor && !iStartFirst) {
            opponentColor.opposite
        } else {
            myColor
        }

        val startingPlayer = if (iStartFirst) finalMyColor else finalOpponentColor

        // Start the game
        val game = Connect4Game(currentPlayer = startingPlayer)
        activeGames[peerID] = game
        playerPieces[peerID] = Pair(finalMyColor, finalOpponentColor)
        setup.state = GameSetupState.STARTED

        val message = "$START_PREFIX${finalMyColor.name}:${finalOpponentColor.name}:${startingPlayer.name}"
        Log.d(TAG, "Starting game with $peerID: myColor=${finalMyColor.name}, opponentColor=${finalOpponentColor.name}, startingPlayer=${startingPlayer.name}")
        return message
    }

    /**
     * Handle start message (from opponent after we accepted)
     * Note: The colors in the message are from the initiator's perspective, so we need to swap them
     */
    fun handleStart(peerID: String, initiatorColor: Piece, recipientColor: Piece, startingPlayer: Piece) {
        val setup = gameSetups[peerID] ?: return
        if (setup.state != GameSetupState.ACCEPT_SENT) {
            Log.w(TAG, "Unexpected start in state ${setup.state}")
            return
        }

        // Swap colors because message is from initiator's perspective
        // initiatorColor is what initiator calls "myColor" (their color)
        // recipientColor is what initiator calls "opponentColor" (our color)
        val myColor = recipientColor  // From our perspective, the recipientColor is our color
        val opponentColor = initiatorColor  // From our perspective, the initiatorColor is opponent's color

        val game = Connect4Game(currentPlayer = startingPlayer)
        activeGames[peerID] = game
        playerPieces[peerID] = Pair(myColor, opponentColor)
        setup.state = GameSetupState.STARTED
        Log.d(TAG, "Game started with $peerID: myColor=${myColor.name}, opponentColor=${opponentColor.name}, startingPlayer=${startingPlayer.name}")
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
     * Get game setup state
     */
    fun getGameSetupState(peerID: String): GameSetupState {
        return gameSetups[peerID]?.state ?: GameSetupState.NONE
    }

    /**
     * Get opponent's preferred color from invite (for UI display)
     */
    fun getOpponentPreferredColor(peerID: String): Piece? {
        return gameSetups[peerID]?.opponentPreferredColor
    }

    /**
     * Check if a message is a game-related message
     */
    fun isGameMessage(content: String): Boolean {
        return content.startsWith(MOVE_PREFIX) ||
               content.startsWith(INVITE_PREFIX) ||
               content.startsWith(ACCEPT_PREFIX) ||
               content.startsWith(START_PREFIX) ||
               content.startsWith(DECLINE_PREFIX)
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
     * Parse invite message
     */
    fun parseInvite(content: String): Pair<Piece, Int>? {
        if (!content.startsWith(INVITE_PREFIX)) return null
        val rest = content.substring(INVITE_PREFIX.length)
        val parts = rest.split(":")
        if (parts.size != 2) return null
        val color = Piece.valueOf(parts[0])
        val random = parts[1].toIntOrNull() ?: return null
        return Pair(color, random)
    }

    /**
     * Parse accept message
     */
    fun parseAccept(content: String): Pair<Piece, Int>? {
        if (!content.startsWith(ACCEPT_PREFIX)) return null
        val rest = content.substring(ACCEPT_PREFIX.length)
        val parts = rest.split(":")
        if (parts.size != 2) return null
        val color = Piece.valueOf(parts[0])
        val random = parts[1].toIntOrNull() ?: return null
        return Pair(color, random)
    }

    /**
     * Parse start message
     */
    fun parseStart(content: String): Triple<Piece, Piece, Piece>? {
        if (!content.startsWith(START_PREFIX)) return null
        val rest = content.substring(START_PREFIX.length)
        val parts = rest.split(":")
        if (parts.size != 3) return null
        val myColor = Piece.valueOf(parts[0])
        val opponentColor = Piece.valueOf(parts[1])
        val startingPlayer = Piece.valueOf(parts[2])
        return Triple(myColor, opponentColor, startingPlayer)
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
     * Decline an invite and return the decline message to send
     */
    fun declineInvite(peerID: String): String {
        gameSetups.remove(peerID)
        return DECLINE_PREFIX
    }

    /**
     * Handle decline message (from recipient)
     */
    fun handleDecline(peerID: String) {
        gameSetups.remove(peerID)
        Log.d(TAG, "Game invite declined by $peerID")
    }

    /**
     * End a game (cleanup)
     */
    fun endGame(peerID: String) {
        activeGames.remove(peerID)
        playerPieces.remove(peerID)
        gameSetups.remove(peerID)
        Log.d(TAG, "Ended game with $peerID")
    }

    /**
     * Check if a game is active for a peer
     */
    fun hasActiveGame(peerID: String): Boolean {
        return activeGames.containsKey(peerID)
    }

    /**
     * Check if there's pending setup for a peer
     */
    fun hasPendingSetup(peerID: String): Boolean {
        val state = gameSetups[peerID]?.state
        return state == GameSetupState.INVITE_SENT ||
               state == GameSetupState.INVITE_RECEIVED ||
               state == GameSetupState.ACCEPT_SENT
    }
}
