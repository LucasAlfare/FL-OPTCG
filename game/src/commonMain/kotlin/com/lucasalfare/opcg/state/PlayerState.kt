package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.Area
import com.lucasalfare.opcg.core.InstanceId
import com.lucasalfare.opcg.core.PlayerId

data class PlayerState(
  val id: PlayerId,
  val areas: Map<Area, AreaState>,
) {
  fun area(area: Area): AreaState =
    requireNotNull(areas[area]) { "Player ${id.value} has no area $area" }

  fun instance(id: InstanceId): CardInstance? =
    areas.values.asSequence()
      .flatMap { it.cards.asSequence() }
      .firstOrNull { it.id == id }
}