package com.mordin.samathascope

class ExponentialSmoother(
  private val alpha: Float,
) {
  private var currentValue: Float? = null

  fun reset() {
    currentValue = null
  }

  fun add(value: Float): Float {
    val previous = currentValue
    currentValue = if (previous == null) {
      value
    } else {
      (alpha * value) + ((1f - alpha) * previous)
    }
    return currentValue ?: value
  }

  fun current(): Float? = currentValue
}

class StateHoldSmoother(
  private val requiredWins: Int = 3,
  private val immediateThreshold: Float = 0.70f,
  initialState: StateLabel = StateLabel.UNCERTAIN,
) {
  private var displayedState = initialState
  private var pendingState = initialState
  private var pendingWins = 0

  fun reset(state: StateLabel = StateLabel.UNCERTAIN) {
    displayedState = state
    pendingState = state
    pendingWins = 0
  }

  fun update(candidate: StateLabel, confidence: Float): StateLabel {
    if (candidate == displayedState) {
      pendingState = candidate
      pendingWins = 0
      return displayedState
    }
    if (confidence >= immediateThreshold) {
      displayedState = candidate
      pendingState = candidate
      pendingWins = 0
      return displayedState
    }
    if (candidate == pendingState) {
      pendingWins++
    } else {
      pendingState = candidate
      pendingWins = 1
    }
    if (pendingWins >= requiredWins) {
      displayedState = candidate
      pendingWins = 0
    }
    return displayedState
  }
}
