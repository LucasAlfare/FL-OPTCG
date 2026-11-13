package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.Area
import com.lucasalfare.opcg.core.PlayerId

data class AreaState(
  val area: Area,
  val player: PlayerId,
  val cards: List<CardInstance>,
)