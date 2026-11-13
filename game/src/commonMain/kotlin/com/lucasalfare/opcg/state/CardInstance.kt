package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.card.CardDefinition
import com.lucasalfare.opcg.core.Face
import com.lucasalfare.opcg.core.InstanceId
import com.lucasalfare.opcg.core.Orientation
import com.lucasalfare.opcg.core.PlayerId

data class CardInstance(
  val id: InstanceId,
  val definition: CardDefinition,
  val printingId: String,
  val owner: PlayerId,
  val controller: PlayerId,
  val state: Orientation?,
  val face: Face,
  val attachedDon: Int,
)