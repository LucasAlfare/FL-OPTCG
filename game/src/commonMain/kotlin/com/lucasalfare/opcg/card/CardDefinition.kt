package com.lucasalfare.opcg.card

import com.lucasalfare.opcg.core.Attribute
import com.lucasalfare.opcg.core.CardId
import com.lucasalfare.opcg.core.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardDefinition(
  val number: String,
  val name: String,
  val category: CardCategory,
  val colors: List<Color>,
  val types: List<String> = emptyList(),
  val attributes: List<Attribute>? = null,
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null,
  @SerialName("default_printing_id")
  val defaultPrintingId: String? = null,
  @SerialName("printing_overrides")
  val printingOverrides: List<PrintingOverride> = emptyList(),
) {

  val id: CardId get() = CardId(number)

  fun characteristicsFor(printingId: String): CardCharacteristics {
    val base = CardCharacteristics(
      name = name,
      category = category,
      colors = colors,
      types = types,
      attributes = attributes,
      cost = cost,
      power = power,
      life = life,
      counter = counter,
    )
    if (printingId == defaultPrintingId) return base
    val override = printingOverrides.firstOrNull { it.printingId == printingId }
      ?: return base
    return base.copy(
      colors = override.colors ?: base.colors,
      types = override.types ?: base.types,
      attributes = override.attributes ?: base.attributes,
      cost = override.cost ?: base.cost,
      power = override.power ?: base.power,
      life = override.life ?: base.life,
      counter = override.counter ?: base.counter,
    )
  }
}

data class CardCharacteristics(
  val name: String,
  val category: CardCategory,
  val colors: List<Color>,
  val types: List<String>,
  val attributes: List<Attribute>?,
  val cost: Int?,
  val power: Int?,
  val life: Int?,
  val counter: Int?,
)

enum class CardCategory { LEADER, CHARACTER, EVENT, STAGE, DON }