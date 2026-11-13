package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.InstanceId

data class BattleId(val value: Int)

data class BattleContext(
  val id: BattleId,
  val attacker: InstanceId,
  val attackTarget: InstanceId,
)

data class GameContext(
  val event: GameEvent?,
  val battle: BattleContext?,
)