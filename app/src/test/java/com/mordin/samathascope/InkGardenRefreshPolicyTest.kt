package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InkGardenRefreshPolicyTest {

  @Test
  fun tracker_updatesOnlyOnMeaningfulImprovement() {
    val initial = InkGardenRefreshTracker(peakRichness = 0.70f, lastImprovementAtMs = 1_000L)

    val unchanged = updateInkGardenRefreshTracker(initial, richness = 0.708f, nowMs = 2_000L)
    val improved = updateInkGardenRefreshTracker(initial, richness = 0.73f, nowMs = 3_000L)

    assertThat(unchanged).isEqualTo(initial)
    assertThat(improved.peakRichness).isEqualTo(0.73f)
    assertThat(improved.lastImprovementAtMs).isEqualTo(3_000L)
  }

  @Test
  fun autoRefresh_requiresAutoModeEligibilityRichnessAndPlateau() {
    val tracker = InkGardenRefreshTracker(peakRichness = 0.78f, lastImprovementAtMs = 5_000L)

    assertThat(
      shouldAutoRefreshInkGarden(
        mode = InkGardenRefreshMode.MANUAL,
        eligible = true,
        richness = 0.82f,
        tracker = tracker,
        nowMs = 11_000L,
      )
    ).isFalse()

    assertThat(
      shouldAutoRefreshInkGarden(
        mode = InkGardenRefreshMode.AUTO,
        eligible = false,
        richness = 0.82f,
        tracker = tracker,
        nowMs = 11_000L,
      )
    ).isFalse()

    assertThat(
      shouldAutoRefreshInkGarden(
        mode = InkGardenRefreshMode.AUTO,
        eligible = true,
        richness = 0.64f,
        tracker = tracker,
        nowMs = 11_000L,
      )
    ).isFalse()

    assertThat(
      shouldAutoRefreshInkGarden(
        mode = InkGardenRefreshMode.AUTO,
        eligible = true,
        richness = 0.82f,
        tracker = tracker,
        nowMs = 9_000L,
      )
    ).isFalse()

    assertThat(
      shouldAutoRefreshInkGarden(
        mode = InkGardenRefreshMode.AUTO,
        eligible = true,
        richness = 0.82f,
        tracker = tracker,
        nowMs = 10_100L,
      )
    ).isTrue()
  }
}
