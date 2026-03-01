package com.napolitain.arcade.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R
import com.napolitain.arcade.ui.games.BalanceGame
import com.napolitain.arcade.ui.games.BlackjackGame
import com.napolitain.arcade.ui.games.CheckersGame
import com.napolitain.arcade.ui.games.ChessGame
import com.napolitain.arcade.ui.games.ConnectFourGame
import com.napolitain.arcade.ui.games.DotsAndBoxesGame
import com.napolitain.arcade.ui.games.Game2048
import com.napolitain.arcade.ui.games.GridAttackGame
import com.napolitain.arcade.ui.games.ReversiGame
import com.napolitain.arcade.ui.games.SnakeGame
import com.napolitain.arcade.ui.games.TakeoverGame
import com.napolitain.arcade.ui.games.TicTacToeGame
import com.napolitain.arcade.ui.games.PresidentGame
import com.napolitain.arcade.ui.games.TexasHoldEmGame
import com.napolitain.arcade.ui.games.RummyGame
import com.napolitain.arcade.ui.games.SortOrSplodeGame
import com.napolitain.arcade.ui.games.WordBalloonGame

data class GameDef(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val accentColor: Color,
    val emoji: String,
    val content: @Composable () -> Unit,
)

private val GAMES = listOf(
    GameDef("tic-tac-toe", R.string.game_tictactoe, R.string.desc_tictactoe, Color(0xFF06B6D4), "✕○") { TicTacToeGame() },
    GameDef("connect-four", R.string.game_connectfour, R.string.desc_connectfour, Color(0xFFF59E0B), "🔴") { ConnectFourGame() },
    GameDef("game-2048", R.string.game_2048, R.string.desc_2048, Color(0xFFF97316), "🔢") { Game2048() },
    GameDef("snake", R.string.game_snake, R.string.desc_snake, Color(0xFF22C55E), "🐍") { SnakeGame() },
    GameDef("reversi", R.string.game_reversi, R.string.desc_reversi, Color(0xFF10B981), "⚫") { ReversiGame() },
    GameDef("dots-and-boxes", R.string.game_dotsandboxes, R.string.desc_dotsandboxes, Color(0xFF8B5CF6), "⬜") { DotsAndBoxesGame() },
    GameDef("word-balloon", R.string.game_wordballoon, R.string.desc_wordballoon, Color(0xFFEC4899), "🎈") { WordBalloonGame() },
    GameDef("grid-attack", R.string.game_gridattack, R.string.desc_gridattack, Color(0xFF3B82F6), "💣") { GridAttackGame() },
    GameDef("checkers", R.string.game_checkers, R.string.desc_checkers, Color(0xFFEF4444), "♛") { CheckersGame() },
    GameDef("chess", R.string.game_chess, R.string.desc_chess, Color(0xFF78909C), "♚") { ChessGame() },
    GameDef("balance", R.string.game_balance, R.string.desc_balance, Color(0xFF14B8A6), "⚖") { BalanceGame() },
    GameDef("takeover", R.string.game_takeover, R.string.desc_takeover, Color(0xFF6366F1), "🏴") { TakeoverGame() },
    GameDef("texas-holdem", R.string.game_texasholdem, R.string.desc_texasholdem, Color(0xFF059669), "🃏") { TexasHoldEmGame() },
    GameDef("blackjack", R.string.game_blackjack, R.string.desc_blackjack, Color(0xFF2E7D32), "🃏") { BlackjackGame() },
    GameDef("president", R.string.game_president, R.string.desc_president, Color(0xFFEAB308), "👑") { PresidentGame() },
    GameDef("rummy", R.string.game_rummy, R.string.desc_rummy, Color(0xFFD97706), "🃏") { RummyGame() },
    GameDef("sort-or-splode", R.string.game_sortorsplode, R.string.desc_sortorsplode, Color(0xFFE11D48), "💥") { SortOrSplodeGame() },
)

// Simple back-arrow icon built with vector paths (no Material Icons dependency)
private val BackArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
    ) {
        moveTo(19f, 12f)
        horizontalLineTo(5f)
        moveTo(12f, 5f)
        lineTo(5f, 12f)
        lineTo(12f, 19f)
    }.build()
}

@Composable
fun ArcadeHomeScreen(modifier: Modifier = Modifier) {
    var activeGameId by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedContent(
        targetState = activeGameId,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != null) {
                // Entering a game: scale up + fade in
                (scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn(animationSpec = tween(250)))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.95f))
            } else {
                // Returning to picker: fade in
                (fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.95f))
                    .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.92f))
            }
        },
        label = "screen-transition",
    ) { gameId ->
        if (gameId == null) {
            GamePickerScreen(onGameSelected = { activeGameId = it })
        } else {
            val game = GAMES.first { it.id == gameId }
            GamePlayScreen(game = game, onBack = { activeGameId = null })
        }
    }
}

@Composable
private fun GamePickerScreen(onGameSelected: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
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
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(GAMES, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onGameSelected(game.id) })
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameDef, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accent gradient header with emoji
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                game.accentColor.copy(alpha = 0.7f),
                                game.accentColor.copy(alpha = 0.3f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(game.emoji, style = MaterialTheme.typography.headlineMedium)
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(game.nameRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(game.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GamePlayScreen(game: GameDef, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Minimal top bar: back button + game title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                game.accentColor.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                ) {
                    Icon(
                        imageVector = BackArrowIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(game.nameRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            // Game content fills remaining space
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                game.content()
            }
        }
    }
}
