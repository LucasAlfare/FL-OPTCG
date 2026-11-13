package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FclCountValue : FclValue

@Serializable
sealed interface FclCountSource : FclCountValue

@Serializable
data class CountAreaSource(val area: FclAreaReference) : FclCountSource

@Serializable
data class CountObjectsSource(
  @SerialName("object") val objectKind: FclObjectKind,
  val areas: List<FclArea>,
  val player: FclReference,
  val filter: FclCondition,
) : FclCountSource

@Serializable
data class CountSelectionSource(val selection: SelectionId) : FclCountSource

@Serializable
data class DonCountSource(val player: FclReference, val scope: FclDonScope) : FclCountSource

@Serializable
data class DonGivenSource(val subject: FclReference) : FclCountSource