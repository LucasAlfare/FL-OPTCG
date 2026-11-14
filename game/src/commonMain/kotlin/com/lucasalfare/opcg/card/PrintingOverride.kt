package com.lucasalfare.opcg.card

import com.lucasalfare.opcg.core.CardAttribute
import com.lucasalfare.opcg.core.CardColor
import com.lucasalfare.opcg.core.LogicalCardId
import com.lucasalfare.opcg.core.PrintingId

class PrintingOverride(
  val printingId: PrintingId,
  val logicalCardId: LogicalCardId,
  val colors: Set<CardColor>? = null,
  val types: Set<CardType>? = null,
  val attribute: CardAttribute? = null,
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null
) {
  init {
    require(colors == null || colors.isNotEmpty()) { "colors override must not be empty when present" }
    require(cost == null || cost >= 0) { "cost must not be negative" }
    require(power == null || power >= 0) { "power must not be negative" }
    require(life == null || life >= 0) { "life must not be negative" }
    require(counter == null || counter > 0) { "counter must be positive when present" }
  }

  fun requireCompatibleWith(definition: CardDefinition) {
    require(definition.id == logicalCardId) {
      "PrintingOverride ${printingId.value} belongs to ${logicalCardId.value}, not ${definition.id.value}"
    }

    if (attribute != null) {
      require(definition is LeaderDefinition || definition is CharacterDefinition) {
        "attribute override is not applicable to ${definition.category}"
      }
    }
    if (cost != null) {
      require(definition !is LeaderDefinition) { "cost override is not applicable to LEADER" }
    }
    if (power != null) {
      require(definition is LeaderDefinition || definition is CharacterDefinition) {
        "power override is not applicable to ${definition.category}"
      }
    }
    if (life != null) {
      require(definition is LeaderDefinition) { "life override is not applicable to ${definition.category}" }
    }
    if (counter != null) {
      require(definition is CharacterDefinition) { "counter override is not applicable to ${definition.category}" }
    }
  }
}