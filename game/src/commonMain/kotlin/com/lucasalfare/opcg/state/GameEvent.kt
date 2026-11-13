package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.PlayerId

data class GameEvent(
  val type: String,
  val source: CardInstance? = null,
  val player: PlayerId? = null,
  val data: Map<String, Any> = emptyMap()
)