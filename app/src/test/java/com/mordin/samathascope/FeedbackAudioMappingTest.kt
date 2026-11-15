package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedbackAudioMappingTest {

  @Test
  fun betterFeedback_isQuieterByDefault() {
    assertThat(feedbackBaseDb(1f, invertReward = false, gamma = 1.6f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-30f)
    assertThat(feedbackBaseDb(0f, invertReward = false, gamma = 1.6f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-3f)
  }

  @Test
  fun invertReward_makesBetterFeedbackLouder() {
    assertThat(feedbackBaseDb(1f, invertReward = true, gamma = 1.6f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-3f)
    assertThat(feedbackBaseDb(0f, invertReward = true, gamma = 1.6f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-30f)
  }

  @Test
  fun gamma_shapesTheMiddleOfTheCurve() {
    val linear = feedbackBaseDb(0.5f, invertReward = false, gamma = 1f, gMinDb = -30, gMaxDb = -10)
    val curved = feedbackBaseDb(0.5f, invertReward = false, gamma = 2f, gMinDb = -30, gMaxDb = -10)

    assertThat(linear).isWithin(1e-4f).of(-20f)
    assertThat(curved).isWithin(1e-4f).of(-25f) // 0.5^2 = 0.25 of the way up.
  }

  @Test
  fun outOfRangeFeedback_isClamped() {
    assertThat(feedbackBaseDb(1.7f, invertReward = false, gamma = 1f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-30f)
    assertThat(feedbackBaseDb(-0.4f, invertReward = false, gamma = 1f, gMinDb = -30, gMaxDb = -3)).isEqualTo(-3f)
  }
}
