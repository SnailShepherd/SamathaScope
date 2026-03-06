package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RollingAverageTest {

  @Test
  fun add_usesMovingWindowOfFixedCapacity() {
    val avg = RollingAverage(capacity = 16)

    var last = 0f
    for (v in 1..20) {
      last = avg.add(v.toFloat())
    }

    // Average of [5..20] = 12.5
    assertThat(last).isWithin(1e-6f).of(12.5f)
    assertThat(avg.size()).isEqualTo(16)
  }

  @Test
  fun reset_clearsState() {
    val avg = RollingAverage(capacity = 4)
    avg.add(1f)
    avg.add(3f)

    avg.reset()

    assertThat(avg.size()).isEqualTo(0)
    assertThat(avg.average()).isEqualTo(0f)
  }
}

