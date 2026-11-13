package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FclRemainder(
  val id: SelectionId,
  @SerialName("look_step") val lookStep: SelectionId,
  val excluding: List<SelectionId> = emptyList(),
)