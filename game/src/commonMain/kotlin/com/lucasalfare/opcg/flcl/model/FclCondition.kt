package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.GameArea

sealed interface FlclCondition

data class FlclAllCondition(
  val conditions: List<FlclCondition>
) : FlclCondition {
  init {
    require(conditions.isNotEmpty()) { "all requires at least one condition" }
  }
}

data class FlclAnyCondition(
  val conditions: List<FlclCondition>
) : FlclCondition {
  init {
    require(conditions.isNotEmpty()) { "any requires at least one condition" }
  }
}

data class FlclNotCondition(
  val condition: FlclCondition
) : FlclCondition

sealed interface FlclTest : FlclCondition

sealed interface FlclCompare<out T>

data class FlclOperatorCompare<out T>(
  val operator: FlclComparisonOperator, val value: T
) : FlclCompare<T> {
  init {
    require(operator != FlclComparisonOperator.INCLUSIVE_RANGE) {
      "INCLUSIVE_RANGE requires minimum and maximum, not value"
    }
  }
}

data class FlclRangeCompare<out T>(
  val minimum: T, val maximum: T
) : FlclCompare<T>

sealed interface FlclComparand

data class FlclValueComparand(
  val value: FlclValue
) : FlclComparand

data class FlclPlayerComparand(
  val player: FlclPlayerReference
) : FlclComparand

data class FlclOtherSubject(
  val reference: FlclObjectReference
) {
  init {
    require(reference !is FlclCandidateReference) {
      "CANDIDATE must not be used as other_subject"
    }
  }
}

enum class FlclTurnPlayerValue {
  YOUR_TURN, OPPONENT_TURN
}

data class FlclPropertyCompareTest(
  val subject: FlclObjectReference, val property: FlclProperty, val compare: FlclCompare<FlclComparand>
) : FlclTest

data class FlclPlayerTurnTest(
  val value: FlclTurnPlayerValue
) : FlclTest

data class FlclDonGivenToSourceTest(
  val compare: FlclCompare<FlclValue>
) : FlclTest

data class FlclDonInFieldTest(
  val player: FlclPlayerReference, val compare: FlclCompare<FlclValue>
) : FlclTest

data class FlclAreaCountTest(
  val area: GameArea, val player: FlclPlayerReference, val compare: FlclCompare<FlclValue>
) : FlclTest

data class FlclObjectCountTest(
  val counted: FlclCountedObject,
  val areas: List<GameArea>,
  val player: FlclPlayerReference,
  val filter: FlclFilter?,
  val compare: FlclCompare<FlclValue>
) : FlclTest {
  init {
    require(areas.isNotEmpty()) { "OBJECT_COUNT requires at least one area" }
    require(areas.distinct().size == areas.size) { "OBJECT_COUNT must not repeat areas" }
  }
}

data class FlclSumCostTest(
  val selection: FlclSelectionId, val compare: FlclCompare<FlclValue>
) : FlclTest

data class FlclEventActivatedByTest(
  val player: FlclPlayerReference
) : FlclTest

data class FlclColorSetCompareTest(
  val subject: FlclObjectReference, val relation: FlclColorRelation, val otherSubject: FlclOtherSubject
) : FlclTest

data class FlclInstanceIdentityCompareTest(
  val subject: FlclObjectReference, val relation: FlclInstanceRelation, val otherSubject: FlclOtherSubject
) : FlclTest

data class FlclFilter(
  val condition: FlclCondition
) {
  init {
    require(condition.isFilterCompatible()) {
      "filter may only use all, any, not, PROPERTY_COMPARE, COLOR_SET_COMPARE and INSTANCE_IDENTITY_COMPARE"
    }
  }
}

private fun FlclCondition.isFilterCompatible(): Boolean = when (this) {
  is FlclAllCondition -> conditions.all { it.isFilterCompatible() }
  is FlclAnyCondition -> conditions.all { it.isFilterCompatible() }
  is FlclNotCondition -> condition.isFilterCompatible()
  is FlclPropertyCompareTest, is FlclColorSetCompareTest, is FlclInstanceIdentityCompareTest -> true
  else -> false
}