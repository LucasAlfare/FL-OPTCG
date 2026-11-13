package com.lucasalfare.opcg.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrintingOverride(
  @SerialName("printing_id") val printingId: String,
  val colors: List<CardColor>? = null,
  val types: List<String>? = null,
  val attributes: List<CardAttribute>? = null,
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null,
)