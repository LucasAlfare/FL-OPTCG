package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.GameArea
import com.lucasalfare.opcg.core.PlayerId

class PlayerState(
  val id: PlayerId, areas: Map<GameArea, AreaState>
) {
  private val areasByKind: Map<GameArea, AreaState> = areas.toMap()

  init {
    require(areasByKind.keys == GameArea.entries.toSet()) {
      "Player ${id.value} must have exactly one area for each GameArea"
    }
    for ((kind, area) in areasByKind) {
      require(area.kind == kind) {
        "Area ${area.id.value} is registered as $kind but declares ${area.kind}"
      }
      require(area.owner == id) {
        "Area ${area.id.value} belongs to ${area.owner.value}, not ${id.value}"
      }
    }
  }

  fun area(kind: GameArea): AreaState = areasByKind.getValue(kind)
}