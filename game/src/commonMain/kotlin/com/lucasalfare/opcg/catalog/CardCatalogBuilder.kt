package com.lucasalfare.opcg.catalog

import com.lucasalfare.opcg.card.CardDefinition
import com.lucasalfare.opcg.core.CardId

class CardCatalogBuilder {

  private val definitions = mutableListOf<CardDefinition>()

  fun add(definition: CardDefinition): CardCatalogBuilder = apply {
    definitions += definition
  }

  fun build(): CardCatalog {
    val byId = linkedMapOf<CardId, CardDefinition>()
    val byNumber = linkedMapOf<String, CardDefinition>()

    definitions.forEach { definition ->
      require(byId.put(definition.id, definition) == null) {
        "Duplicate card id: ${definition.id}"
      }
      require(byNumber.put(definition.number, definition) == null) {
        "Duplicate official number: ${definition.number}"
      }
    }

    return CardCatalog(byId.toMap(), byNumber.toMap())
  }
}