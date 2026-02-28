package com.napolitain.arcade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.ui.games.BalanceGame
import com.napolitain.arcade.ui.games.CheckersGame
import com.napolitain.arcade.ui.games.ConnectFourGame
import com.napolitain.arcade.ui.games.DotsAndBoxesGame
import com.napolitain.arcade.ui.games.Game2048
import com.napolitain.arcade.ui.games.GridAttackGame
import com.napolitain.arcade.ui.games.ReversiGame
import com.napolitain.arcade.ui.games.SnakeGame
import com.napolitain.arcade.ui.games.TakeoverGame
import com.napolitain.arcade.ui.games.TicTacToeGame
import com.napolitain.arcade.ui.games.WordBalloonGame

data class GameDef(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val content: @Composable () -> Unit,
)

private val GAMES = listOf(
    GameDef("tic-tac-toe", R.string.game_tictactoe, R.string.desc_tictactoe) { TicTacToeGame() },
    GameDef("connect-four", R.string.game_connectfour, R.string.desc_connectfour) { ConnectFourGame() },
    GameDef("game-2048", R.string.game_2048, R.string.desc_2048) { Game2048() },
    GameDef("snake", R.string.game_snake, R.string.desc_snake) { SnakeGame() },
    GameDef("reversi", R.string.game_reversi, R.string.desc_reversi) { ReversiGame() },
    GameDef("dots-and-boxes", R.string.game_dotsandboxes, R.string.desc_dotsandboxes) { DotsAndBoxesGame() },
    GameDef("word-balloon", R.string.game_wordballoon, R.string.desc_wordballoon) { WordBalloonGame() },
    GameDef("grid-attack", R.string.game_gridattack, R.string.desc_gridattack) { GridAttackGame() },
    GameDef("checkers", R.string.game_checkers, R.string.desc_checkers) { CheckersGame() },
    GameDef("balance", R.string.game_balance, R.string.desc_balance) { BalanceGame() },
    GameDef("takeover", R.string.game_takeover, R.string.desc_takeover) { TakeoverGame() },
)

@Composable
fun ArcadeHomeScreen(modifier: Modifier = Modifier) {
    var activeGameId by rememberSaveable { mutableStateOf("tic-tac-toe") }
    val activeGame = GAMES.first { it.id == activeGameId }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(GAMES, key = { it.id }) { game ->
                    val isSelected = game.id == activeGameId
                    Card(
                        onClick = { activeGameId = game.id },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            stringResource(game.nameRes),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).weight(1f),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(activeGame.nameRes), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(activeGame.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    activeGame.content()
                }
            }
        }
    }
}
