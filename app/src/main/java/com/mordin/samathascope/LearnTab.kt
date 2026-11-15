package com.mordin.samathascope

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Learn tab: metric deep dive and foundations.

private enum class LearnViewMode {
  METRICS,
  FOUNDATIONS,
}

@Composable
internal fun LearnTab() {
  val uriHandler = LocalUriHandler.current
  var viewMode by rememberSaveable { mutableStateOf(LearnViewMode.METRICS) }
  var selectedMetricName by rememberSaveable { mutableStateOf(PlotType.MEDITATION_PROXY.name) }

  val selectedMetric = PlotType.entries.firstOrNull { it.name == selectedMetricName } ?: PlotType.MEDITATION_PROXY
  val selectedEntry = MetricGlossary.entryFor(selectedMetric)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Panel {
      Text(stringResource(R.string.learn_mode_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactActionButton(
          label = stringResource(R.string.learn_mode_metrics),
          enabled = true,
          filled = viewMode == LearnViewMode.METRICS,
          onClick = { viewMode = LearnViewMode.METRICS },
        )
        CompactActionButton(
          label = stringResource(R.string.learn_mode_foundations),
          enabled = true,
          filled = viewMode == LearnViewMode.FOUNDATIONS,
          onClick = { viewMode = LearnViewMode.FOUNDATIONS },
        )
      }
    }

    if (viewMode == LearnViewMode.METRICS) {
      LearnCard(
        title = stringResource(R.string.learn_how_metrics_title),
        body = stringResource(R.string.learn_how_metrics_body),
      )

      LearnCard(
        title = stringResource(R.string.learn_eeg_assumptions_title),
        body = stringResource(R.string.learn_eeg_assumptions_body),
      )

      Panel {
        Text(stringResource(R.string.learn_select_metric_title), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MetricGlossary.learnEntries().forEach { entry ->
            CompactActionButton(
              label = entry.abbreviation,
              enabled = true,
              filled = selectedMetric == entry.type,
              onClick = { selectedMetricName = entry.type.name },
            )
          }
        }
      }

      Panel {
        Text(
          stringResource(R.string.learn_metric_details_title, stringResource(selectedEntry.plainName), selectedEntry.abbreviation),
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        MetricExplainer(selectedEntry)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(selectedEntry.longMeaning), style = MaterialTheme.typography.bodySmall)
        selectedEntry.interpretiveNote?.let { note ->
          Spacer(Modifier.height(8.dp))
          Text(
            stringResource(R.string.learn_interpretive_note_title),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
          )
          Text(stringResource(note), style = MaterialTheme.typography.bodySmall)
        }
      }
    } else {
      LearnCard(
        title = stringResource(R.string.learn_samatha_title),
        body = stringResource(R.string.learn_samatha_body),
      )
      LearnCard(
        title = stringResource(R.string.learn_score_title),
        body = stringResource(R.string.learn_score_body),
      )
      LearnCard(
        title = stringResource(R.string.learn_metric_difference_title),
        body = stringResource(R.string.learn_metric_difference_body),
      )
      LearnCard(
        title = stringResource(R.string.learn_calibration_title),
        body = stringResource(R.string.learn_calibration_body),
      )
      LearnCard(
        title = stringResource(R.string.learn_artifact_calibration_title),
        body = stringResource(R.string.learn_artifact_calibration_body),
      )
      LearnCard(
        title = stringResource(R.string.learn_caveat_title),
        body = stringResource(R.string.learn_caveat_body),
      )
    }

    Panel {
      Text(stringResource(R.string.learn_sources_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.learn_sources_intro), style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(8.dp))

      TextButton(onClick = { uriHandler.openUri("https://www.accesstoinsight.org/tipitaka/an/an11/an11.002.than.html") }) {
        Text(stringResource(R.string.learn_source_1))
      }
      TextButton(onClick = { uriHandler.openUri("https://suttacentral.net/an4.41/en/sujato") }) {
        Text(stringResource(R.string.learn_source_2))
      }
      TextButton(onClick = { uriHandler.openUri("https://www.dhammatalks.org/books/WithEachAndEveryBreath/Section0005.html") }) {
        Text(stringResource(R.string.learn_source_3))
      }
    }
  }
}

@Composable
private fun LearnCard(title: String, body: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(4.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(body, style = MaterialTheme.typography.bodySmall)
    }
  }
}
