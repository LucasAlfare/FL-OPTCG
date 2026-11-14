package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.CardInstanceId
import com.lucasalfare.opcg.core.PlayerId

data class GameContext(
  val turnPlayer: PlayerId? = null,
  val activator: PlayerId? = null,
  val source: CardInstanceId? = null,
  val attacker: CardInstanceId? = null,
  val target: CardInstanceId? = null
) {
  init {
    require((attacker == null) == (target == null)) {
      "attacker and target must be provided together"
    }
  }
}