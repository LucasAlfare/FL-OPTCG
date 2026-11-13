package com.lucasalfare.opcg.catalog

import com.lucasalfare.opcg.card.CardDefinition
import com.lucasalfare.opcg.core.CardId

class CardCatalog internal constructor(
  private val definitionsById: Map<CardId, CardDefinition>,
  private val definitionsByNumber: Map<String, CardDefinition>,
) {

  fun findById(id: CardId): CardDefinition? = definitionsById[id]

  fun findByOfficialNumber(number: String): CardDefinition? = definitionsByNumber[number]

  fun getById(id: CardId): CardDefinition =
    findById(id) ?: throw NoSuchElementException("Card definition not found: $id")

  fun getByOfficialNumber(number: String): CardDefinition =
    findByOfficialNumber(number) ?: throw NoSuchElementException("Card definition not found: $number")
}