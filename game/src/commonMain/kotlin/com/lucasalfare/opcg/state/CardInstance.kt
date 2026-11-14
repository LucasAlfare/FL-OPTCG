package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.*

class CardInstance(
  val id: CardInstanceId,
  val logicalCardId: LogicalCardId,
  val printingId: PrintingId,
  val owner: PlayerId,
  val controller: PlayerId,
  val activeState: ActiveState,
  val face: CardFace,
  attachedDon: List<CardInstanceId> = emptyList()
) {
  val attachedDon: List<CardInstanceId> = attachedDon.toList()

  init {
    require(this.attachedDon.distinct().size == this.attachedDon.size) {
      "Card instance ${id.value} must not have the same DON!! instance attached more than once"
    }
    require(id !in this.attachedDon) {
      "Card instance ${id.value} must not attach itself"
    }
  }
}

class DonInstance(
  val id: CardInstanceId,
  val owner: PlayerId,
  val controller: PlayerId,
  val activeState: ActiveState,
  val face: CardFace
)