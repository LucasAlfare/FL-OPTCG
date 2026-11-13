package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FclCondition

@Serializable
data class AllCondition(val all: List<FclCondition>) : FclCondition

@Serializable
data class AnyCondition(val any: List<FclCondition>) : FclCondition

@Serializable
data class NotCondition(val not: FclCondition) : FclCondition

@Serializable
sealed interface FclTestCondition : FclCondition

@Serializable
@SerialName("PROPERTY_COMPARE")
data class PropertyCompareTest(
  val subject: FclReference,
  val property: FclComparableProperty,
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
) : FclTestCondition

@Serializable
@SerialName("PLAYER_TURN")
data class PlayerTurnTest(val value: FclPlayerTurn) : FclTestCondition

@Serializable
@SerialName("DON_GIVEN_TO_SOURCE")
data class DonGivenToSourceTest(
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
) : FclTestCondition

@Serializable
@SerialName("DON_IN_FIELD")
data class DonInFieldTest(
  val player: FclReference,
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
) : FclTestCondition

@Serializable
@SerialName("AREA_COUNT")
data class AreaCountTest(
  val area: FclArea,
  val player: FclReference,
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
) : FclTestCondition

@Serializable
@SerialName("OBJECT_COUNT")
data class ObjectCountTest(
  @SerialName("object") val objectKind: FclObjectKind,
  val areas: List<FclAreaReference>,
  val player: FclReference,
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
  val filter: FclCondition? = null,
) : FclTestCondition

@Serializable
@SerialName("SUM_COST")
data class SumCostTest(
  val selection: SelectionId,
  val operator: FclComparisonOperator,
  val value: FclValue? = null,
  val minimum: Int? = null,
  val maximum: Int? = null,
) : FclTestCondition

@Serializable
@SerialName("EVENT_ACTIVATED_BY")
data class EventActivatedByTest(val player: FclReference) : FclTestCondition

@Serializable
@SerialName("COLOR_SET_COMPARE")
data class ColorSetCompareTest(
  val subject: FclReference,
  val relation: FclColorSetRelation,
  @SerialName("other_subject") val otherSubject: FclReference,
) : FclTestCondition

@Serializable
@SerialName("INSTANCE_IDENTITY_COMPARE")
data class InstanceIdentityCompareTest(
  val subject: FclReference,
  val relation: FclInstanceRelation,
  @SerialName("other_subject") val otherSubject: FclReference,
) : FclTestCondition