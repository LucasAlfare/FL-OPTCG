package com.lucasalfare.opcg.flcl.model

sealed interface FlclScalableCount

sealed interface FlclCountSource : FlclScalableCount

enum class FlclDonScope {
  FIELD, COST_AREA, DON_DECK
}

enum class FlclCountedObject {
  CARD
}
