package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.AbilityId

data class FlclEventActivation(
  val window: FlclEventCardWindow
)

data class FlclAbility(
  val id: AbilityId,
  val sourceText: String,
  val status: FlclTranslationStatus,
  val sourceRefs: List<FlclRuleRef> = emptyList(),
  val keywordEffects: List<FlclKeywordEffect> = emptyList(),
  val eventActivation: FlclEventActivation? = null,
  val additionalNames: List<String> = emptyList(),
  val effects: List<FlclEffect> = emptyList()
) {
  init {
    require(sourceText.isNotBlank()) { "ability source_text must not be blank" }
    require(keywordEffects.distinct().size == keywordEffects.size) {
      "ability keyword_effects must not repeat keywords"
    }
    require(additionalNames.none { it.isBlank() }) { "additional_names must not contain blank names" }
    require(additionalNames.distinct().size == additionalNames.size) {
      "additional_names must not repeat names"
    }
    require(effects.map { it.id }.distinct().size == effects.size) {
      "ability effect ids must be unique within the ability"
    }

    val representable =
      effects.isNotEmpty() || keywordEffects.isNotEmpty() || eventActivation != null || additionalNames.isNotEmpty()
    if (status == FlclTranslationStatus.SUPPORTED) {
      require(representable) {
        "SUPPORTED ability requires a representation in effects, keyword_effects, event_activation or additional_names"
      }
    } else {
      require(!representable) {
        "non-SUPPORTED ability must not carry effects, keywords, event_activation or additional_names"
      }
    }

    val keywordTrigger = FlclKeywordEffect.TRIGGER in keywordEffects
    val eventTrigger = eventActivation?.window == FlclEventCardWindow.TRIGGER
    val autoTrigger = effects.any { it is FlclAutoEffect && it.timing == FlclAutoTiming.TRIGGER }
    if (keywordTrigger) {
      require(eventTrigger != autoTrigger) {
        "TRIGGER keyword requires exactly one representation: event_activation TRIGGER or AUTO TRIGGER"
      }
    } else {
      require(!eventTrigger && !autoTrigger) {
        "a TRIGGER representation requires the TRIGGER keyword"
      }
    }
  }
}