package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.GameArea

sealed interface FlclValue

sealed interface FlclLiteral : FlclValue

data class FlclIntegerLiteral(val value: Int) : FlclLiteral

data class FlclStringLiteral(val value: String) : FlclLiteral

data class FlclBooleanLiteral(val value: Boolean) : FlclLiteral

data class FlclEnumLiteral(val value: Enum<*>) : FlclLiteral

data class FlclPropertyValue(
  val subject: FlclObjectReference, val name: FlclProperty
) : FlclValue

data class FlclCountAreaValue(
  val area: FlclAreaReference
) : FlclValue, FlclCountSource

data class FlclCountObjectsValue(
  val counted: FlclCountedObject, val areas: List<GameArea>, val player: FlclPlayerReference
) : FlclValue, FlclCountSource {
  init {
    require(areas.isNotEmpty()) { "count_objects requires at least one area" }
    require(areas.distinct().size == areas.size) { "count_objects must not repeat areas" }
  }
}

data class FlclCountSelectionValue(
  val selection: FlclSelectionId
) : FlclValue, FlclCountSource

data class FlclDonCountValue(
  val player: FlclPlayerReference, val scope: FlclDonScope
) : FlclValue, FlclCountSource

data class FlclDonGivenValue(
  val subject: FlclObjectReference
) : FlclValue, FlclCountSource

data class FlclSumCostValue(
  val selection: FlclSelectionId
) : FlclValue

data class FlclCountGroupsValue(
  val count: FlclCountSource, val groupSize: Int
) : FlclValue, FlclScalableCount {
  init {
    require(groupSize > 0) { "count_groups.group_size must be a positive integer, got $groupSize" }
  }
}

data class FlclScaleValue(
  val count: FlclScalableCount, val multiplier: Int
) : FlclValue {
  init {
    require(multiplier > 0) { "scale.multiplier must be a positive integer, got $multiplier" }
  }
}
