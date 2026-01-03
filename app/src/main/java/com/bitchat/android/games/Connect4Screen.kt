package com.bitchat.android.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Connect 4 game screen
 * @param game Current game state
 * @param myPiece Which piece the current user is playing (RED or YELLOW)
 * @param onMove Callback when user makes a move (column index)
 * @param onNewGame Callback to start a new game
 * @param onClose Callback to close the game screen
 */
@Composable
fun Connect4Screen(
    game: Connect4Game,
    myPiece: Piece,
    onMove: (Int) -> Unit,
    onNewGame: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMyTurn = game.currentPlayer == myPiece && !game.isGameOver
    val opponentPiece = myPiece.opposite

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Connect 4",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game status
        GameStatusText(
            game = game,
            myPiece = myPiece,
            isMyTurn = isMyTurn
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Game board
        Connect4Board(
            game = game,
            myPiece = myPiece,
            isMyTurn = isMyTurn,
            onMove = onMove,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // New game button (shown when game is over)
        if (game.isGameOver) {
            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New Game")
            }
        }
    }
}

@Composable
private fun GameStatusText(
    game: Connect4Game,
    myPiece: Piece,
    isMyTurn: Boolean
) {
    val statusText = when {
        game.winner != null -> {
            val winnerName = if (game.winner == myPiece) "You" else "Opponent"
            "$winnerName won!"
        }
        game.isGameOver -> "Tie game!"
        isMyTurn -> "Your turn"
        else -> "Opponent's turn"
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = when {
            game.winner == myPiece -> Color(0xFF4CAF50) // Green for win
            game.winner != null -> Color(0xFFF44336) // Red for loss
            else -> MaterialTheme.colorScheme.onBackground
        }
    )
}

@Composable
private fun Connect4Board(
    game: Connect4Game,
    myPiece: Piece,
    isMyTurn: Boolean,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Blue frame color
    val frameColor = Color(0xFF2196F3) // Blue

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Column headers (clickable when it's your turn)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(Connect4Game.COLUMNS) { col ->
                ColumnHeader(
                    column = col,
                    isMyTurn = isMyTurn && game.board[0][col] == Piece.EMPTY,
                    onClick = { onMove(col) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Board with blue border
        Box(
            modifier = Modifier
                .border(width = 4.dp, color = frameColor, shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1976D2).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(Connect4Game.ROWS) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(Connect4Game.COLUMNS) { col ->
                            GamePiece(
                                piece = game.board[row][col],
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(
    column: Int,
    isMyTurn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .then(
                if (isMyTurn) {
                    Modifier
                        .clickable(onClick = onClick)
                        .background(
                            Color(0xFF2196F3).copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isMyTurn) {
            Text(
                text = "↓",
                fontSize = 24.sp,
                color = Color(0xFF2196F3)
            )
        }
    }
}

@Composable
private fun GamePiece(
    piece: Piece,
    modifier: Modifier = Modifier
) {
    val color = when (piece) {
        Piece.RED -> Color(0xFFF44336) // Red
        Piece.YELLOW -> Color(0xFFFFEB3B) // Yellow
        Piece.EMPTY -> Color.White
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(width = 1.dp, color = Color.Gray.copy(alpha = 0.3f), shape = CircleShape)
    )
}

