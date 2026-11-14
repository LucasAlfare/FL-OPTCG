package com.lucasalfare.opcg.flcl.model

data class FlclUsageLimitSpec(
  val kind: FlclUsageLimit
) {
  init {
    require(kind == FlclUsageLimit.ONCE_PER_TURN) {
      "usage_limit kind must be ONCE_PER_TURN, got $kind"
    }
  }
}