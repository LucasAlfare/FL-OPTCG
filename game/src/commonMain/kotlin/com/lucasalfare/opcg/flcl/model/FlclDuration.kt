package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.PlayerId

@JvmInline
value class FlclBattleId(val value: String) {
  init {
    require(value.isNotBlank()) { "FlclBattleId must not be blank" }
  }
}

sealed interface FlclDurationSpec

object FlclThisTurnDuration : FlclDurationSpec

object FlclUntilEndOfTurnDuration : FlclDurationSpec

object FlclUntilEndOfEndPhaseDuration : FlclDurationSpec

data class FlclThisBattleDuration(
  val battle: FlclBattleId
) : FlclDurationSpec

data class FlclUntilStartOfNextTurnDuration(
  val player: PlayerId
) : FlclDurationSpec

data class FlclUntilEndOfNextTurnDuration(
  val player: PlayerId
) : FlclDurationSpec

data class FlclWhileValidDuration(
  val condition: FlclCondition
) : FlclDurationSpec

data class FlclPermanentDuration(
  val authorizedByRule: Boolean
) : FlclDurationSpec {
  init {
    require(authorizedByRule) {
      "PERMANENT duration requires an explicit text or rule establishing absence of common temporal expiration"
    }
  }
}