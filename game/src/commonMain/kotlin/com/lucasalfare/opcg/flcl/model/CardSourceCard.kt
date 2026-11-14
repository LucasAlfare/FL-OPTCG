package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.card.CardType
import com.lucasalfare.opcg.card.OfficialCardNumber
import com.lucasalfare.opcg.core.CardAttribute
import com.lucasalfare.opcg.core.CardCategory
import com.lucasalfare.opcg.core.CardColor

data class CardSourcePrintingOverride(
  val printingId: String,
  val colors: List<CardColor>? = null,
  val types: List<CardType>? = null,
  val attributes: List<CardAttribute>? = null,
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null
) {
  init {
    require(printingId.isNotBlank()) { "printing_id must not be blank" }
    if (colors != null) {
      require(colors.isNotEmpty() && colors.distinct().size == colors.size) {
        "printing override colors must be non-empty and without repetition"
      }
    }
    if (types != null) {
      require(types.distinct().size == types.size) { "printing override types must not repeat" }
    }
    if (attributes != null) {
      require(attributes.size == 1) { "printing override attributes must contain exactly one attribute" }
    }
    require(cost == null || cost >= 0) { "cost must not be negative" }
    require(power == null || power >= 0) { "power must not be negative" }
    require(life == null || life >= 0) { "life must not be negative" }
    require(counter == null || counter > 0) { "counter must be positive when present" }
  }
}

private fun CardSourcePrintingOverride.requireApplicableTo(category: CardCategory) {
  val isLeader = category == CardCategory.LEADER
  val isCharacter = category == CardCategory.CHARACTER
  if (attributes != null) {
    require(isLeader || isCharacter) { "attributes override is not applicable to $category" }
  }
  if (cost != null) {
    require(!isLeader) { "cost override is not applicable to LEADER" }
  }
  if (power != null) {
    require(isLeader || isCharacter) { "power override is not applicable to $category" }
  }
  if (life != null) {
    require(isLeader) { "life override is not applicable to $category" }
  }
  if (counter != null) {
    require(isCharacter) { "counter override is not applicable to $category" }
  }
}

data class CardSourceCard(
  val number: OfficialCardNumber,
  val name: String,
  val category: CardCategory,
  val colors: List<CardColor>,
  val types: List<CardType> = emptyList(),
  val attributes: List<CardAttribute> = emptyList(),
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null,
  val defaultPrintingId: String? = null,
  val printingOverrides: List<CardSourcePrintingOverride> = emptyList()
) {
  init {
    require(name.isNotBlank()) { "card name must not be blank" }
    require(colors.isNotEmpty()) { "card must have at least one color" }
    require(colors.distinct().size == colors.size) { "card colors must not repeat" }
    require(types.distinct().size == types.size) { "card types must not repeat" }
    require(attributes.distinct().size == attributes.size) { "card attributes must not repeat" }
    require(cost == null || cost >= 0) { "cost must not be negative" }
    require(power == null || power >= 0) { "power must not be negative" }
    require(life == null || life >= 0) { "life must not be negative" }
    require(counter == null || counter > 0) { "counter must be positive when present" }

    when (category) {
      CardCategory.LEADER -> {
        require(attributes.size == 1) { "LEADER requires exactly one attribute" }
        require(power != null && life != null) { "LEADER requires power and life" }
        require(cost == null && counter == null) { "LEADER must not declare cost or counter" }
      }

      CardCategory.CHARACTER -> {
        require(attributes.size == 1) { "CHARACTER requires exactly one attribute" }
        require(cost != null && power != null) { "CHARACTER requires cost and power" }
        require(life == null) { "CHARACTER must not declare life" }
      }

      CardCategory.EVENT, CardCategory.STAGE -> {
        require(attributes.isEmpty()) { "$category must not declare attributes" }
        require(cost != null) { "$category requires cost" }
        require(power == null && life == null && counter == null) {
          "$category must not declare power, life or counter"
        }
      }
    }

    if (defaultPrintingId != null) {
      require(defaultPrintingId.isNotBlank()) { "default_printing_id must not be blank" }
    }
    if (printingOverrides.isNotEmpty()) {
      require(defaultPrintingId != null) { "default_printing_id is required when printing overrides exist" }
    }
    require(printingOverrides.map { it.printingId }.distinct().size == printingOverrides.size) {
      "each printing may have at most one override"
    }
    require(defaultPrintingId == null || printingOverrides.none { it.printingId == defaultPrintingId }) {
      "the default printing must not have an override"
    }
    printingOverrides.forEach { it.requireApplicableTo(category) }
  }
}