package com.lucasalfare.opcg.card

import com.lucasalfare.opcg.core.CardAttribute
import com.lucasalfare.opcg.core.CardCategory
import com.lucasalfare.opcg.core.CardColor
import com.lucasalfare.opcg.core.LogicalCardId

@JvmInline
value class OfficialCardNumber(val value: String) {
  init {
    require(value.isNotBlank()) { "OfficialCardNumber must not be blank" }
  }
}

@JvmInline
value class CardType(val value: String) {
  init {
    require(value.isNotBlank()) { "CardType must not be blank" }
  }
}

sealed class CardDefinition(
  val id: LogicalCardId,
  val officialNumber: OfficialCardNumber,
  val originalName: String,
  val colors: Set<CardColor>,
  val types: Set<CardType>
) {
  abstract val category: CardCategory

  init {
    require(originalName.isNotBlank()) { "originalName must not be blank" }
    require(colors.isNotEmpty()) { "A logical card must have at least one color" }
  }
}

class LeaderDefinition(
  id: LogicalCardId,
  officialNumber: OfficialCardNumber,
  originalName: String,
  colors: Set<CardColor>,
  types: Set<CardType>,
  val attribute: CardAttribute,
  val power: Int,
  val life: Int
) : CardDefinition(id, officialNumber, originalName, colors, types) {

  override val category: CardCategory = CardCategory.LEADER

  init {
    require(power >= 0) { "power must not be negative" }
    require(life >= 0) { "life must not be negative" }
  }
}

class CharacterDefinition(
  id: LogicalCardId,
  officialNumber: OfficialCardNumber,
  originalName: String,
  colors: Set<CardColor>,
  types: Set<CardType>,
  val attribute: CardAttribute,
  val cost: Int,
  val power: Int,
  val counter: Int?
) : CardDefinition(id, officialNumber, originalName, colors, types) {

  override val category: CardCategory = CardCategory.CHARACTER

  init {
    require(cost >= 0) { "cost must not be negative" }
    require(power >= 0) { "power must not be negative" }
    require(counter == null || counter > 0) { "counter must be positive when present" }
  }
}

class EventDefinition(
  id: LogicalCardId,
  officialNumber: OfficialCardNumber,
  originalName: String,
  colors: Set<CardColor>,
  types: Set<CardType>,
  val cost: Int
) : CardDefinition(id, officialNumber, originalName, colors, types) {

  override val category: CardCategory = CardCategory.EVENT

  init {
    require(cost >= 0) { "cost must not be negative" }
  }
}

class StageDefinition(
  id: LogicalCardId,
  officialNumber: OfficialCardNumber,
  originalName: String,
  colors: Set<CardColor>,
  types: Set<CardType>,
  val cost: Int
) : CardDefinition(id, officialNumber, originalName, colors, types) {

  override val category: CardCategory = CardCategory.STAGE

  init {
    require(cost >= 0) { "cost must not be negative" }
  }
}