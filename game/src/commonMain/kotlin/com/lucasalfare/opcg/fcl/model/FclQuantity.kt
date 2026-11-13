package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface FclQuantity

@Serializable
data class FixedQuantity(val value: FclValue) : FclQuantity

@Serializable
data class UpToQuantity(val maximum: FclValue) : FclQuantity

@Serializable
data class DynamicQuantity(val value: FclValue) : FclQuantity

@Serializable
object AllQuantity : FclQuantity