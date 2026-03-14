package com.mordin.samathascope

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePickerRow(
  selected: GameId,
  onSelect: (GameId) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    GameId.entries.forEach { gameId ->
      OutlinedButton(
        onClick = { onSelect(gameId) },
        modifier = Modifier.height(40.dp),
        border = BorderStroke(
          width = 2.dp,
          color = if (selected == gameId) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.outline
          },
        ),
      ) {
        Text(gameId.displayName())
      }
    }
  }
}
