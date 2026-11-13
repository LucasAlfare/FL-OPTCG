package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.PlayerId

sealed interface GameEvent {

  data class OpponentCharacterKo(
    val eventObject: CardInstance,
    val observer: PlayerId,
  ) : GameEvent

  data class EventCardResolved(
    val activatedBy: PlayerId,
  ) : GameEvent
}