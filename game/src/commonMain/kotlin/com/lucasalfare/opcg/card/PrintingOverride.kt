package com.lucasalfare.opcg.card

import com.lucasalfare.opcg.core.Attribute
import com.lucasalfare.opcg.core.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrintingOverride(
  @SerialName("printing_id") val printingId: String,
  val colors: List<Color>? = null,
  val types: List<String>? = null,
  val attributes: List<Attribute>? = null,
  val cost: Int? = null,
  val power: Int? = null,
  val life: Int? = null,
  val counter: Int? = null,
)