package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("AREA")
data class FclAreaReference(val player: FclReference, val name: FclArea) : FclReference