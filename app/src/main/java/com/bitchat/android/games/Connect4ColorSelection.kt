package com.bitchat.android.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Color selection dialog for Connect 4
 */
@Composable
fun Connect4ColorSelection(
    opponentColor: Piece? = null,
    onColorSelected: (Piece) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (opponentColor != null) {
        "Opponent chose ${if (opponentColor == Piece.RED) "Red" else "Yellow"}. Choose your color:"
    } else {
        "Choose your color:"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Red option
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onColorSelected(Piece.RED)
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF44336))
                        .border(
                            width = 3.dp,
                            color = if (opponentColor == Piece.RED) Color.Gray else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Empty - just a colored circle
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Red",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (opponentColor == Piece.RED) {
                    Text(
                        text = "(Opponent)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Yellow option
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onColorSelected(Piece.YELLOW)
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFEB3B))
                        .border(
                            width = 3.dp,
                            color = if (opponentColor == Piece.YELLOW) Color.Gray else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Empty - just a colored circle
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Yellow",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (opponentColor == Piece.YELLOW) {
                    Text(
                        text = "(Opponent)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

