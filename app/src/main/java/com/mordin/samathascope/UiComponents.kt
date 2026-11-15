package com.mordin.samathascope

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

// Small building blocks shared by the tabs.

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(4.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      content = content,
    )
  }
}

@Composable
internal fun CompactActionButton(
  label: String,
  enabled: Boolean,
  filled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (filled) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(40.dp)) {
      Text(label)
    }
  } else {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(40.dp)) {
      Text(label)
    }
  }
}

@Composable
internal fun ValueText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    maxLines = 1,
  )
}

@Composable
internal fun LabeledCheckbox(
  checked: Boolean,
  label: String,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      modifier = modifier,
    )
    Text(
      label,
      style = MaterialTheme.typography.bodySmall,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
  }
}

@Composable
internal fun HudRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    ValueText(value)
  }
}

@Composable
internal fun SignedValueRow(label: String, value: Float) {
  HudRow(label = label, value = String.format(Locale.US, "%+.2f", value))
}

@Composable
internal fun MetricExplainer(
  entry: MetricGlossaryEntry,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Canvas(modifier = Modifier.width(12.dp).height(12.dp)) {
        drawCircle(metricColorFor(entry.type))
      }
      Text("${stringResource(entry.plainName)} (${entry.abbreviation})", fontWeight = FontWeight.SemiBold)
    }
    Text(stringResource(entry.shortMeaning), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.metric_drivers_line, stringResource(entry.drivers)), style = MaterialTheme.typography.bodySmall)
    Text(
      text = if (entry.canBeFeedbackSource) {
        stringResource(R.string.metric_source_eligible)
      } else {
        stringResource(R.string.metric_source_plot_only)
      },
      style = MaterialTheme.typography.bodySmall,
    )
    Text(stringResource(entry.displayFormula), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    entry.terms.forEach { term ->
      Text("${term.label}: ${stringResource(term.meaning)}", style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
internal fun MetricBar(label: String, value: Float, emphasise: Boolean = false) {
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        label,
        style = if (emphasise) {
          MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
          MaterialTheme.typography.bodySmall
        }
      )
      ValueText("${(value * 100).roundToInt()}%")
    }
    LinearProgressIndicator(
      progress = { value.coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth()
    )
  }
}
