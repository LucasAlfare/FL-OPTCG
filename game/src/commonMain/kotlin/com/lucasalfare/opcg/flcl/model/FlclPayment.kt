package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.ActiveState
import com.lucasalfare.opcg.core.CardCategory
import com.lucasalfare.opcg.core.GameArea

data class FlclPaymentSelection(
  val objectKind: FlclSelectableObject,
  val areas: List<GameArea>,
  val quantity: FlclFiniteQuantity,
  val chooser: FlclPlayerReference,
  val information: FlclInformationMode,
  val filter: FlclFilter?
) {
  init {
    require(areas.isNotEmpty()) { "payment selection requires at least one area" }
    require(areas.distinct().size == areas.size) { "payment selection must not repeat areas" }
    require(
      information != FlclInformationMode.LOOKED_BY_CHOOSER && information != FlclInformationMode.REVEALED_TO_CHOOSER
    ) {
      "payment selection cannot use LOOK or REVEAL information, since payments have no information_step"
    }
  }
}

sealed interface FlclPayment {
  val payer: FlclPlayerReference
  val optional: Boolean
}

private val activeCondition: FlclCondition = FlclPropertyCompareTest(
  subject = FlclCandidateReference, property = FlclProperty.STATE, compare = FlclOperatorCompare(
    FlclComparisonOperator.EQ, FlclValueComparand(FlclEnumLiteral(ActiveState.ACTIVE))
  )
)

private val characterCondition: FlclCondition = FlclPropertyCompareTest(
  subject = FlclCandidateReference, property = FlclProperty.CATEGORY, compare = FlclOperatorCompare(
    FlclComparisonOperator.EQ, FlclValueComparand(FlclEnumLiteral(CardCategory.CHARACTER))
  )
)

private fun containsCondition(condition: FlclCondition?, target: FlclCondition): Boolean = when {
  condition == null -> false
  condition == target -> true
  condition is FlclAllCondition -> condition.conditions.any { containsCondition(it, target) }
  else -> false
}

private fun FlclPaymentSelection.filterContains(target: FlclCondition): Boolean =
  containsCondition(filter?.condition, target)

data class FlclRestDonPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.DON) { "REST_DON selects DON!! only" }
    require(selection.areas == listOf(GameArea.COST_AREA)) { "REST_DON uses only the payer's COST_AREA" }
    require(selection.filterContains(activeCondition)) { "REST_DON requires a STATE = ACTIVE filter" }
  }
}

data class FlclDonMinusPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.DON) { "DON_MINUS selects DON!! only" }
    require(
      selection.areas.all { it == GameArea.LEADER_AREA || it == GameArea.CHARACTER_AREA || it == GameArea.COST_AREA }) { "DON_MINUS may only target LEADER_AREA, CHARACTER_AREA and COST_AREA" }
  }
}

data class FlclRestCardSelectionPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.CARD) { "REST_CARD selection targets CARD only" }
    require(selection.quantity is FlclFixedQuantity) { "REST_CARD selection requires a FIXED quantity" }
    require(selection.filterContains(activeCondition)) { "REST_CARD selection requires a STATE = ACTIVE filter" }
  }
}

data class FlclRestCardSourcePayment(
  override val payer: FlclPlayerReference, val subject: FlclSourceReference, override val optional: Boolean = false
) : FlclPayment

data class FlclTrashHandCardPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.CARD) { "TRASH_HAND_CARD selects CARD only" }
    require(selection.areas == listOf(GameArea.HAND)) { "TRASH_HAND_CARD uses only the payer's HAND" }
    require(selection.quantity == FlclFixedQuantity(1)) { "TRASH_HAND_CARD requires FIXED 1" }
  }
}

data class FlclReturnCharacterToOwnerHandPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.CARD) { "RETURN_CHARACTER_TO_OWNER_HAND selects CARD only" }
    require(selection.areas == listOf(GameArea.CHARACTER_AREA)) {
      "RETURN_CHARACTER_TO_OWNER_HAND uses only the payer's CHARACTER_AREA"
    }
    require(selection.quantity == FlclFixedQuantity(1)) { "RETURN_CHARACTER_TO_OWNER_HAND requires FIXED 1" }
    require(selection.filterContains(characterCondition)) {
      "RETURN_CHARACTER_TO_OWNER_HAND requires a CATEGORY = CHARACTER filter"
    }
    require(selection.chooser == payer) { "RETURN_CHARACTER_TO_OWNER_HAND requires chooser = payer" }
    require(selection.information == FlclInformationMode.PUBLIC_INFORMATION) {
      "RETURN_CHARACTER_TO_OWNER_HAND requires PUBLIC_INFORMATION"
    }
  }
}

data class FlclAddLifeToHandPayment(
  override val payer: FlclPlayerReference, val quantity: FlclFiniteQuantity, override val optional: Boolean = false
) : FlclPayment

data class FlclPlaceHandCardBottomDeckPayment(
  override val payer: FlclPlayerReference, val selection: FlclPaymentSelection, override val optional: Boolean = false
) : FlclPayment {
  init {
    require(selection.objectKind == FlclSelectableObject.CARD) { "PLACE_HAND_CARD_BOTTOM_DECK selects CARD only" }
    require(selection.areas == listOf(GameArea.HAND)) { "PLACE_HAND_CARD_BOTTOM_DECK uses only the payer's HAND" }
    require(selection.quantity == FlclFixedQuantity(1)) { "PLACE_HAND_CARD_BOTTOM_DECK requires FIXED 1" }
    require(selection.chooser == payer) { "PLACE_HAND_CARD_BOTTOM_DECK requires chooser = payer" }
    require(selection.information == FlclInformationMode.OWNER_PRIVATE_INFORMATION) {
      "PLACE_HAND_CARD_BOTTOM_DECK requires OWNER_PRIVATE_INFORMATION"
    }
  }
}