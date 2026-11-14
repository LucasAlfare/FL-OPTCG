package com.lucasalfare.opcg.flcl.model

sealed interface FlclQuantity

sealed interface FlclFiniteQuantity : FlclQuantity

data class FlclFixedQuantity(
  val value: Int
) : FlclFiniteQuantity {
  init {
    require(value > 0) { "FIXED quantity requires a positive value, got $value" }
  }
}

data class FlclUpToQuantity(
  val maximum: Int, val zeroAuthorized: Boolean
) : FlclFiniteQuantity {
  init {
    require(maximum >= 0) { "UP_TO maximum must not be negative, got $maximum" }
    require(maximum > 0 || zeroAuthorized) {
      "UP_TO maximum of zero requires explicit authorization in the text or rule"
    }
  }
}

data class FlclDynamicQuantity(
  val value: FlclValue
) : FlclFiniteQuantity {
  init {
    require(value !is FlclLiteral) { "DYNAMIC quantity requires a dynamic Value, not a literal" }
  }
}

object FlclAllQuantity : FlclQuantity