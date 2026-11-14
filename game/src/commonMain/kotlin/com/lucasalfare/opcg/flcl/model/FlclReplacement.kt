package com.lucasalfare.opcg.flcl.model

@JvmInline
value class FlclRuleRef(val value: String) {
  init {
    require(value.isNotBlank()) { "FlclRuleRef must not be blank" }
  }
}

data class FlclReplacementTarget(
  val procedure: FlclReplacementProcedure, val ruleRef: FlclRuleRef, val subject: FlclObjectReference
)

data class FlclReplacementApplication(
  val chooser: FlclPlayerReference
)

data class FlclReplacementEffect(
  val target: FlclReplacementTarget,
  val point: FlclReplacementPoint,
  val replacedPart: FlclReplacedPart,
  val availableWhen: FlclCondition? = null,
  val duration: FlclDurationSpec? = null,
  val application: FlclReplacementApplication,
  val costs: List<FlclPayment> = emptyList(),
  val substituteSteps: List<FlclResolutionStep> = emptyList()
) {
  init {
    val compatible = when (target.procedure) {
      FlclReplacementProcedure.ACTIVATION_COST_PAYMENT -> setOf(FlclReplacedPart.ACTIVATION_COST_PAYMENT)
      FlclReplacementProcedure.LEADER_DAMAGE_PROCESSING -> setOf(
        FlclReplacedPart.LEADER_DAMAGE,
        FlclReplacedPart.LIFE_TO_HAND_DURING_DAMAGE
      )

      FlclReplacementProcedure.CHARACTER_KO_PROCESSING -> setOf(FlclReplacedPart.CHARACTER_KO)
      FlclReplacementProcedure.EFFECT_REMOVAL_FROM_FIELD -> setOf(FlclReplacedPart.FIELD_REMOVAL_BY_EFFECT)
      FlclReplacementProcedure.ORDINARY_CARD_MOVEMENT -> setOf(FlclReplacedPart.ORDINARY_CARD_MOVEMENT)
    }
    require(replacedPart in compatible) {
      "replaced_part $replacedPart is not compatible with procedure ${target.procedure}"
    }
  }
}