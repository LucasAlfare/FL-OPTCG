package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.PlayerId

data class GameState(
  val players: Map<PlayerId, PlayerState>,
  val order: List<PlayerId>
) {
  fun player(id: PlayerId): PlayerState = players.getValue(id)
}