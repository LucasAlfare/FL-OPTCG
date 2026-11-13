package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.PlayerId

class GameContext(
  val state: GameState,
  val currentPlayer: PlayerId,
  var event: GameEvent? = null,
  var battle: BattleInfo? = null
) {
  val attacker: CardInstance? get() = battle?.attacker
  val target: CardInstance? get() = battle?.target
}

data class BattleInfo(
  val attacker: CardInstance,
  val target: CardInstance
)