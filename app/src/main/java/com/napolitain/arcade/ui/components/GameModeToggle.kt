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

enum class GameMode {
    AI,
    LOCAL,
}

@Composable
fun GameModeToggle(
    mode: GameMode,
    onModeChange: (GameMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onModeChange(m) },
                label = {
                    Text(
                        when (m) {
                            GameMode.AI -> stringResource(R.string.mode_ai)
                            GameMode.LOCAL -> stringResource(R.string.mode_local)
                        },
                    )
                },
            )
        }
    }
}
