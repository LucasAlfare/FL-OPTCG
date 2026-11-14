package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.GameArea

data class FlclAreaReference(
  val player: FlclPlayerReference, val name: GameArea
) : FlclReference