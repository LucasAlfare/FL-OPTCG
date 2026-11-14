package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.*

data class CardSnapshot(
  val instanceId: CardInstanceId,
  val logicalCardId: LogicalCardId,
  val printingId: PrintingId,
  val owner: PlayerId,
  val controller: PlayerId,
  val activeState: ActiveState,
  val face: CardFace,
  val attachedDon: List<CardInstanceId>,
  val originArea: GameArea
) {
  init {
    require(attachedDon.distinct().size == attachedDon.size) {
      "Snapshot of ${instanceId.value} must not repeat attached DON!! instances"
    }
  }

  companion object {
    fun of(card: CardInstance, originArea: GameArea): CardSnapshot = CardSnapshot(
      instanceId = card.id,
      logicalCardId = card.logicalCardId,
      printingId = card.printingId,
      owner = card.owner,
      controller = card.controller,
      activeState = card.activeState,
      face = card.face,
      attachedDon = card.attachedDon.toList(),
      originArea = originArea
    )
  }
}

sealed class GameEvent(
  val id: EventId, val context: GameContext
)

class CharacterKnockedOutEvent(
  id: EventId, context: GameContext, val knockedOut: CardSnapshot
) : GameEvent(id, context) {
  init {
    require(knockedOut.originArea == GameArea.CHARACTER_AREA) {
      "Character K.O. event requires an origin in CHARACTER_AREA, not ${knockedOut.originArea}"
    }
  }
}