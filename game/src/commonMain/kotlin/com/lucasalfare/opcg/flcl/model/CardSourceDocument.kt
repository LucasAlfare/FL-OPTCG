package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.AbilityId

private fun FlclTranslationStatus.isBlocked(): Boolean =
  this == FlclTranslationStatus.BLOCKED_NORMATIVE || this == FlclTranslationStatus.BLOCKED_CAPABILITY || this == FlclTranslationStatus.INVALID_SOURCE

private fun severity(status: FlclTranslationStatus): Int = when (status) {
  FlclTranslationStatus.SUPPORTED -> 0
  FlclTranslationStatus.BLOCKED_CAPABILITY -> 1
  FlclTranslationStatus.BLOCKED_NORMATIVE -> 2
  FlclTranslationStatus.INVALID_SOURCE -> 3
  FlclTranslationStatus.NON_GAME_TEXT -> -1
}

data class CardSourceDocument(
  val format: String,
  val languageVersion: FlclVersion,
  val card: CardSourceCard,
  val source: CardSource,
  val translation: CardSourceTranslation,
  val editorial: EditorialMetadata? = null,
  val abilities: List<FlclAbility> = emptyList()
) {
  init {
    require(format == FLCL_FORMAT) { "format must be $FLCL_FORMAT, got '$format'" }
    require(languageVersion <= FlclVersion.TARGET) {
      "language_version $languageVersion is newer than the supported target ${FlclVersion.TARGET}"
    }

    val artIds = editorial?.printings?.map { it.artId }.orEmpty()
    val overrideIds = card.printingOverrides.map { it.printingId }
    if (card.printingOverrides.isNotEmpty()) {
      val defaultId = card.defaultPrintingId
      require(defaultId != null && defaultId in artIds) {
        "default_printing_id must match exactly one editorial printing art_id"
      }
      require(overrideIds.all { it in artIds }) {
        "every printing override must match exactly one editorial printing art_id"
      }
    } else if (card.defaultPrintingId != null) {
      require(card.defaultPrintingId in artIds) {
        "default_printing_id must match exactly one editorial printing art_id"
      }
    }

    val referenceIds = source.references.map { it.id }.toSet()
    require(abilities.map { it.id }.distinct().size == abilities.size) {
      "ability ids must be unique in the document"
    }

    abilities.forEach { ability ->
      require(source.currentText.contains(ability.sourceText)) {
        "ability ${ability.id.value} source_text must be an exact excerpt of source.current_text"
      }
      ability.sourceRefs.forEach { ref ->
        require(ref in referenceIds) { "source_refs ${ref.value} does not match any source reference" }
      }
      ability.effects.filterIsInstance<FlclReplacementAbilityEffect>().forEach { effect ->
        val ruleRef = effect.replacement.target.ruleRef
        require(ruleRef in referenceIds) {
          "replacement rule_ref ${ruleRef.value} does not match any source reference"
        }
      }
    }

    translation.blockers.forEach { blocker ->
      blocker.references.forEach { ref ->
        require(ref in referenceIds) { "blocker reference ${ref.value} does not match any source reference" }
      }
      require(source.currentText.contains(blocker.sourceExcerpt)) {
        "blocker source_excerpt must be an exact excerpt of source.current_text"
      }
      val abilityId: AbilityId? = blocker.abilityId
      if (abilityId != null) {
        val ability = abilities.firstOrNull { it.id == abilityId }
        require(ability != null) { "blocker ability_id ${abilityId.value} does not match any ability" }
        require(ability.status.isBlocked()) {
          "blocker ability_id ${abilityId.value} must point to a blocked ability"
        }
        val effectId = blocker.effectId
        if (effectId != null) {
          require(ability.effects.any { it.id == effectId }) {
            "blocker effect_id ${effectId.value} does not match any effect of ability ${abilityId.value}"
          }
        }
      }
    }

    abilities.filter { it.status.isBlocked() }.forEach { ability ->
      require(translation.blockers.any {
        it.abilityId == ability.id && FlclTranslationStatus.valueOf(it.code.name) == ability.status
      }) {
        "blocked ability ${ability.id.value} requires a matching blocker"
      }
    }

    val gameplay = abilities.filter { it.status != FlclTranslationStatus.NON_GAME_TEXT }
    val cardBlockerStatuses = translation.blockers.filter { it.scope == FlclBlockerScope.CARD }
      .map { FlclTranslationStatus.valueOf(it.code.name) }
    val relevant = gameplay.map { it.status } + cardBlockerStatuses
    val expected = if (relevant.isEmpty()) {
      FlclTranslationStatus.NON_GAME_TEXT
    } else {
      relevant.maxByOrNull { severity(it) }!!
    }
    require(translation.status == expected) {
      "translation status ${translation.status} does not match aggregated status $expected"
    }
  }
}