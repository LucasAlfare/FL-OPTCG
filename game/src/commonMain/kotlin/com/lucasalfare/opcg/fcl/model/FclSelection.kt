package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FclSelection(
  val id: SelectionId,
  @SerialName("object") val objectKind: FclObjectKind,
  val areas: List<FclAreaReference>,
  val filter: FclCondition? = null,
  val quantity: FclQuantity,
  val chooser: FclReference? = null,
  val information: FclInformation,
  @SerialName("information_step") val informationStep: SelectionId? = null,
  @SerialName("order_chooser") val orderChooser: FclReference? = null,
)