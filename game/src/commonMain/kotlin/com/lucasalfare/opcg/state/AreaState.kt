package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.AreaId
import com.lucasalfare.opcg.core.CardInstanceId
import com.lucasalfare.opcg.core.GameArea
import com.lucasalfare.opcg.core.PlayerId

class AreaState(
  val id: AreaId, val owner: PlayerId, val kind: GameArea, cardInstances: List<CardInstanceId> = emptyList()
) {
  val cardInstances: List<CardInstanceId> = cardInstances.toList()

  init {
    require(this.cardInstances.distinct().size == this.cardInstances.size) {
      "Area ${id.value} must not contain the same card instance more than once"
    }
  }
}