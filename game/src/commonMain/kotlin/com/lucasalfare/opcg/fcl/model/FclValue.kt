package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FclValue

@Serializable
sealed interface FclLiteral

@JvmInline
@Serializable
value class IntegerLiteral(val value: Int) : FclLiteral

@JvmInline
@Serializable
value class TextLiteral(val value: String) : FclLiteral

@JvmInline
@Serializable
value class BooleanLiteral(val value: Boolean) : FclLiteral

@Serializable
data class LiteralValue(val literal: FclLiteral) : FclValue

@Serializable
data class PropertyValue(val subject: FclReference, val name: FclComparableProperty) : FclValue

@Serializable
data class SumCostValue(val selection: SelectionId) : FclValue

@Serializable
data class CountGroupsValue(
  val count: FclCountSource,
  @SerialName("group_size") val groupSize: Int,
) : FclValue, FclCountValue

@Serializable
data class ScaleValue(val count: FclCountValue, val multiplier: Int) : FclValue