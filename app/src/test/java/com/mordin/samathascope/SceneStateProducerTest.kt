package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import com.mordin.samathascope.scene.SceneSignalInputs
import com.mordin.samathascope.scene.SceneStateProducer
import org.junit.Test

class SceneStateProducerTest {

  @Test
  fun producer_emitsBoundedStateAtFixedCadence() {
    val producer = SceneStateProducer()
    producer.setPlayback(active = true, frozen = false)
    producer.updateInputs(
      SceneSignalInputs(
        settledness = 0.82f,
        alertness = 0.74f,
        control = 0.68f,
        qualityConfidence = 0.92f,
        artefactScore = 0.18f,
        effortfulFocus = 0.60f,
        mindWandering = 0.18f,
        displayedDrowsyScore = 0.12f,
        timestampMs = 1_000L,
      )
    )

    producer.tick(1_000L)
    producer.tick(1_050L)
    val state = producer.state.value

    assertThat(state.calmness).isAtLeast(0f)
    assertThat(state.calmness).isAtMost(1f)
    assertThat(state.focus).isAtLeast(0f)
    assertThat(state.focus).isAtMost(1f)
    assertThat(state.stability).isAtLeast(0f)
    assertThat(state.stability).isAtMost(1f)
    assertThat(state.artefact).isAtLeast(0f)
    assertThat(state.artefact).isAtMost(1f)
    assertThat(state.intensity).isAtLeast(0f)
    assertThat(state.intensity).isAtMost(1f)
    assertThat(state.drift).isAtLeast(-1f)
    assertThat(state.drift).isAtMost(1f)
  }

  @Test
  fun producer_freezesProgressWhenPlaybackIsFrozen() {
    val producer = SceneStateProducer()
    producer.setPlayback(active = true, frozen = false)
    producer.updateInputs(
      SceneSignalInputs(
        settledness = 0.60f,
        alertness = 0.66f,
        control = 0.58f,
        qualityConfidence = 0.86f,
        artefactScore = 0.10f,
        effortfulFocus = 0.42f,
        mindWandering = 0.22f,
        displayedDrowsyScore = 0.18f,
        timestampMs = 1_000L,
      )
    )
    producer.tick(1_000L)
    producer.tick(1_200L)
    val beforeFreeze = producer.state.value.progress

    producer.setPlayback(active = true, frozen = true)
    producer.tick(2_000L)

    assertThat(producer.state.value.progress).isEqualTo(beforeFreeze)
  }

  @Test
  fun restartScene_resetsAccumulatedProgress() {
    val producer = SceneStateProducer()
    producer.setPlayback(active = true, frozen = false)
    producer.tick(1_000L)
    producer.tick(121_000L)
    assertThat(producer.state.value.progress).isGreaterThan(0f)

    producer.restartScene(122_000L)

    assertThat(producer.state.value.progress).isEqualTo(0f)
  }
}
