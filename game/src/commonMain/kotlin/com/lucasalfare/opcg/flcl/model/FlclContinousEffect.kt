package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.ActiveState
import com.lucasalfare.opcg.core.GameArea
import com.lucasalfare.opcg.core.PlayerId

private fun <T> requireDistinctNonEmpty(items: List<T>, name: String) {
  require(items.isNotEmpty()) { "$name must not be empty" }
  require(items.distinct().size == items.size) { "$name must not repeat entries" }
}

data class FlclScope(
  val areas: List<GameArea>, val player: FlclPlayerReference? = null, val filter: FlclFilter? = null
) {
  init {
    requireDistinctNonEmpty(areas, "scope areas")
  }
}

data class FlclTargetScope(
  val areas: List<GameArea>, val states: List<ActiveState>? = null, val filter: FlclFilter? = null
) {
  init {
    requireDistinctNonEmpty(areas, "target_scope areas")
    if (states != null) {
      requireDistinctNonEmpty(states, "target_scope states")
    }
  }
}

sealed interface FlclEffectTarget

data class FlclSubjectEffectTarget(
  val subject: FlclObjectReference
) : FlclEffectTarget

data class FlclScopeEffectTarget(
  val scope: FlclScope
) : FlclEffectTarget

sealed interface FlclContinuousPlacement

data class FlclTemporaryPlacement(
  val duration: FlclDurationSpec
) : FlclContinuousPlacement

data class FlclPermanentPlacement(
  val validWhen: FlclCondition? = null
) : FlclContinuousPlacement

sealed interface FlclContinuousSpec

data class FlclModifyPowerEffect(
  val target: FlclEffectTarget, val operator: FlclModificationOperator, val value: FlclValue
) : FlclContinuousSpec {
  init {
    require(operator == FlclModificationOperator.ADD || operator == FlclModificationOperator.SUBTRACT) {
      "MODIFY_POWER accepts only ADD or SUBTRACT"
    }
  }
}

data class FlclModifyCostEffect(
  val target: FlclEffectTarget, val operator: FlclModificationOperator, val value: FlclValue
) : FlclContinuousSpec {
  init {
    require(operator == FlclModificationOperator.ADD || operator == FlclModificationOperator.SUBTRACT) {
      "MODIFY_COST accepts only ADD or SUBTRACT"
    }
  }
}

data class FlclSetBasePowerEffect(
  val target: FlclEffectTarget, val value: FlclValue
) : FlclContinuousSpec

data class FlclSetBaseCostEffect(
  val target: FlclEffectTarget, val value: FlclValue
) : FlclContinuousSpec

data class FlclModifyCharacteristicEffect(
  val target: FlclEffectTarget, val change: FlclCharacteristicChange
) : FlclContinuousSpec

data class FlclGrantKeywordContinuousEffect(
  val target: FlclEffectTarget, val keyword: FlclKeywordEffect
) : FlclContinuousSpec

data class FlclRemoveKeywordContinuousEffect(
  val target: FlclEffectTarget, val keyword: FlclKeywordEffect
) : FlclContinuousSpec

data class FlclGrantAttackTargetPermissionEffect(
  val target: FlclEffectTarget, val targetScope: FlclTargetScope
) : FlclContinuousSpec

data class FlclRestrictAttackTargetsEffect(
  val subject: FlclSourceReference, val attackingPlayer: FlclPlayerReference, val allowedTargets: FlclTargetScope
) : FlclContinuousSpec

data class FlclPreventAttackEffect(
  val target: FlclEffectTarget
) : FlclContinuousSpec {
  init {
    val scope = (target as? FlclScopeEffectTarget)?.scope
    if (scope != null) {
      require(scope.areas.all { it == GameArea.LEADER_AREA || it == GameArea.CHARACTER_AREA }) {
        "PREVENT_ATTACK scope may only use LEADER_AREA and CHARACTER_AREA"
      }
    }
  }
}

data class FlclPreventBattleKoEffect(
  val target: FlclEffectTarget, val attackerCondition: FlclCondition? = null
) : FlclContinuousSpec

data class FlclRestrictKeywordEffectActivationEffect(
  val target: FlclEffectTarget, val keyword: FlclKeywordEffect, val activatingPlayer: PlayerId
) : FlclContinuousSpec {
  init {
    require(keyword == FlclKeywordEffect.BLOCKER) {
      "RESTRICT_KEYWORD_EFFECT_ACTIVATION accepts only BLOCKER in FLCL 1.10.0"
    }
    val scope = (target as? FlclScopeEffectTarget)?.scope
    if (scope != null) {
      require(scope.areas.all { it == GameArea.CHARACTER_AREA }) {
        "RESTRICT_KEYWORD_EFFECT_ACTIVATION scope may only use CHARACTER_AREA"
      }
    }
  }
}

data class FlclContinuousEffect(
  val spec: FlclContinuousSpec, val placement: FlclContinuousPlacement
)