package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.EffectId

sealed interface FlclEffectBody

data class FlclOneShotBody(
  val resolution: FlclResolution
) : FlclEffectBody

data class FlclContinuousBody(
  val continuous: FlclContinuousEffect
) : FlclEffectBody

sealed interface FlclEffect {
  val id: EffectId
}

private fun mentionsEventActivatedBy(condition: FlclCondition?): Boolean = when (condition) {
  null -> false
  is FlclEventActivatedByTest -> true
  is FlclAllCondition -> condition.conditions.any { mentionsEventActivatedBy(it) }
  is FlclAnyCondition -> condition.conditions.any { mentionsEventActivatedBy(it) }
  is FlclNotCondition -> mentionsEventActivatedBy(condition.condition)
  else -> false
}

data class FlclAutoEffect(
  override val id: EffectId,
  val timing: FlclAutoTiming,
  val body: FlclEffectBody,
  val activationConditions: FlclCondition? = null,
  val activationCosts: List<FlclPayment> = emptyList(),
  val usageLimit: FlclUsageLimitSpec? = null
) : FlclEffect {
  init {
    val continuous = (body as? FlclContinuousBody)?.continuous
    if (continuous != null) {
      require(continuous.placement is FlclTemporaryPlacement) {
        "AUTO CONTINUOUS effect requires a duration, not a permanent placement"
      }
    }
    if (timing != FlclAutoTiming.AFTER_EVENT_CARD_RESOLUTION) {
      require(!mentionsEventActivatedBy(activationConditions)) {
        "EVENT_ACTIVATED_BY is only allowed in AUTO AFTER_EVENT_CARD_RESOLUTION activation_conditions"
      }
    }
  }
}

data class FlclActivateEffect(
  override val id: EffectId,
  val activationWindow: FlclActivateWindow,
  val body: FlclEffectBody,
  val activationConditions: FlclCondition? = null,
  val activationCosts: List<FlclPayment> = emptyList(),
  val usageLimit: FlclUsageLimitSpec? = null
) : FlclEffect {
  init {
    val continuous = (body as? FlclContinuousBody)?.continuous
    if (continuous != null) {
      require(continuous.placement is FlclTemporaryPlacement) {
        "ACTIVATE CONTINUOUS effect requires a duration, not a permanent placement"
      }
    }
    require(!mentionsEventActivatedBy(activationConditions)) {
      "EVENT_ACTIVATED_BY is not allowed in ACTIVATE activation_conditions"
    }
  }
}

data class FlclPermanentEffect(
  override val id: EffectId, val continuous: FlclContinuousEffect
) : FlclEffect {
  init {
    require(continuous.placement is FlclPermanentPlacement) {
      "PERMANENT effect requires a permanent placement with optional valid_when"
    }
  }
}

data class FlclReplacementAbilityEffect(
  override val id: EffectId, val replacement: FlclReplacementEffect
) : FlclEffect