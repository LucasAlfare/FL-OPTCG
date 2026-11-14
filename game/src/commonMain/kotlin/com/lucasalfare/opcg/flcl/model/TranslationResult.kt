package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.AbilityId
import com.lucasalfare.opcg.core.EffectId

data class TranslationBlocker(
  val code: FlclBlockerCode,
  val scope: FlclBlockerScope,
  val abilityId: AbilityId? = null,
  val effectId: EffectId? = null,
  val sourceExcerpt: String,
  val reason: String,
  val references: List<FlclRuleRef> = emptyList()
) {
  init {
    when (scope) {
      FlclBlockerScope.CARD -> require(abilityId == null && effectId == null) {
        "CARD blocker must not declare ability_id or effect_id"
      }

      FlclBlockerScope.ABILITY -> require(abilityId != null && effectId == null) {
        "ABILITY blocker requires ability_id and must not declare effect_id"
      }

      FlclBlockerScope.EFFECT -> require(abilityId != null && effectId != null) {
        "EFFECT blocker requires both ability_id and effect_id"
      }
    }
    require(sourceExcerpt.isNotBlank()) { "blocker source_excerpt must not be blank" }
    require(reason.isNotBlank()) { "blocker reason must not be blank" }
    require(references.distinct().size == references.size) { "blocker references must not repeat" }
  }
}

data class CardSourceTranslation(
  val status: FlclTranslationStatus, val notes: String? = null, val blockers: List<TranslationBlocker> = emptyList()
) {
  init {
    if (notes != null) require(notes.isNotBlank()) { "translation notes must not be blank when present" }
    when (status) {
      FlclTranslationStatus.SUPPORTED, FlclTranslationStatus.NON_GAME_TEXT -> require(blockers.isEmpty()) { "$status must not declare blockers" }
      else -> require(blockers.isNotEmpty()) { "$status requires at least one blocker" }
    }
  }
}