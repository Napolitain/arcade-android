package com.napolitain.arcade.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.napolitain.arcade.R

enum class Difficulty {
    EASY,
    NORMAL,
    HARD,
}

@Composable
fun GameDifficultyToggle(
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { d ->
            FilterChip(
                selected = difficulty == d,
                onClick = { onDifficultyChange(d) },
                label = {
                    Text(
                        when (d) {
                            Difficulty.EASY -> stringResource(R.string.difficulty_easy)
                            Difficulty.NORMAL -> stringResource(R.string.difficulty_normal)
                            Difficulty.HARD -> stringResource(R.string.difficulty_hard)
                        },
                    )
                },
            )
        }
    }
}
