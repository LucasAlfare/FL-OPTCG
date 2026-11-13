package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.InstanceId
import com.lucasalfare.opcg.core.PlayerId

data class GameState(
  val players: List<PlayerState>,
  val turnPlayer: PlayerId,
) {
  init {
    require(players.any { it.id == turnPlayer }) { "Turn player ${turnPlayer.value} is not part of the game" }
  }

  fun player(id: PlayerId): PlayerState =
    requireNotNull(players.firstOrNull { it.id == id }) { "Unknown player ${id.value}" }

  fun playersInTurnOrder(): List<PlayerState> =
    listOf(player(turnPlayer)) + players.filter { it.id != turnPlayer }

  fun instance(id: InstanceId): CardInstance? =
    players.firstNotNullOfOrNull { it.instance(id) }
}