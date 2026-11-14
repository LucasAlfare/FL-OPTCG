package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.ActiveState
import com.lucasalfare.opcg.core.CardAttribute
import com.lucasalfare.opcg.core.CardFace

sealed interface FlclTarget

data class FlclSubjectTarget(
  val subject: FlclObjectReference
) : FlclTarget

data class FlclSelectionTarget(
  val selection: FlclSelectionId
) : FlclTarget

enum class FlclGameOutcome {
  WIN, LOSE
}

private val arithmeticOperators = setOf(FlclModificationOperator.ADD, FlclModificationOperator.SUBTRACT)
private val characteristicOperators = setOf(
  FlclModificationOperator.SET, FlclModificationOperator.ADD, FlclModificationOperator.REMOVE
)

sealed interface FlclOperation

data class FlclDrawOperation(
  val player: FlclPlayerReference, val quantity: FlclFiniteQuantity
) : FlclOperation

data class FlclTrashHandOperation(
  val player: FlclPlayerReference, val selection: FlclSelectionId
) : FlclOperation

data class FlclTrashDeckOperation(
  val player: FlclPlayerReference, val selection: FlclSelectionId
) : FlclOperation

data class FlclKoCharacterOperation(
  val target: FlclTarget
) : FlclOperation

data class FlclMoveCardOperation(
  val target: FlclTarget,
  val sourceArea: FlclAreaReference,
  val destinationArea: FlclAreaReference,
  val face: CardFace? = null,
  val state: ActiveState? = null,
  val position: FlclPosition? = null,
  val orderChooser: FlclPlayerReference? = null
) : FlclOperation

data class FlclRestOperation(
  val target: FlclTarget
) : FlclOperation

data class FlclSetActiveOperation(
  val target: FlclTarget
) : FlclOperation

data class FlclGiveDonOperation(
  val recipient: FlclTarget, val quantity: FlclFiniteQuantity, val sourceState: ActiveState
) : FlclOperation

data class FlclAddDonOperation(
  val player: FlclPlayerReference, val quantity: FlclFiniteQuantity, val arrivalState: ActiveState
) : FlclOperation

data class FlclDonMinusOperation(
  val player: FlclPlayerReference, val quantity: FlclFiniteQuantity
) : FlclOperation

data class FlclPlayCardOperation(
  val target: FlclTarget,
  val sourceArea: FlclAreaReference,
  val player: FlclPlayerReference,
  val state: ActiveState? = null
) : FlclOperation

data class FlclRevealOperation(
  val source: FlclRevealSource, val recipients: List<FlclPlayerReference>? = null
) : FlclOperation {
  init {
    if (recipients != null) {
      require(recipients.isNotEmpty()) { "REVEAL recipients, when present, must not be empty" }
      require(recipients.distinct().size == recipients.size) { "REVEAL recipients must not repeat players" }
    }
  }
}

data class FlclLookOperation(
  val area: FlclAreaReference, val player: FlclPlayerReference, val quantity: FlclFiniteQuantity
) : FlclOperation {
  init {
    require(area.player == player) { "LOOK area must belong to the LOOK player" }
  }
}

data class FlclShuffleDeckOperation(
  val player: FlclPlayerReference
) : FlclOperation

data class FlclModifyPowerOperation(
  val target: FlclTarget, val operator: FlclModificationOperator, val value: FlclValue, val duration: FlclDurationSpec
) : FlclOperation {
  init {
    require(operator in arithmeticOperators) { "MODIFY_POWER accepts only ADD or SUBTRACT" }
  }
}

data class FlclSetBasePowerOperation(
  val target: FlclTarget, val value: FlclValue, val duration: FlclDurationSpec
) : FlclOperation

data class FlclModifyCostOperation(
  val target: FlclTarget, val operator: FlclModificationOperator, val value: FlclValue, val duration: FlclDurationSpec
) : FlclOperation {
  init {
    require(operator in arithmeticOperators) { "MODIFY_COST accepts only ADD or SUBTRACT" }
  }
}

data class FlclSetBaseCostOperation(
  val target: FlclTarget, val value: FlclValue, val duration: FlclDurationSpec
) : FlclOperation

sealed interface FlclCharacteristicChange

data class FlclNameSetChange(
  val value: String
) : FlclCharacteristicChange

data class FlclTypeChange(
  val operator: FlclModificationOperator, val value: String
) : FlclCharacteristicChange {
  init {
    require(operator in characteristicOperators) { "TYPE accepts only SET, ADD or REMOVE" }
  }
}

data class FlclAttributeChange(
  val operator: FlclModificationOperator, val value: CardAttribute
) : FlclCharacteristicChange {
  init {
    require(operator in characteristicOperators) { "ATTRIBUTE accepts only SET, ADD or REMOVE" }
  }
}

data class FlclModifyCharacteristicOperation(
  val target: FlclTarget, val change: FlclCharacteristicChange, val duration: FlclDurationSpec
) : FlclOperation

data class FlclGrantKeywordEffectOperation(
  val target: FlclTarget, val keyword: FlclKeywordEffect, val duration: FlclDurationSpec
) : FlclOperation

data class FlclDealLeaderDamageOperation(
  val player: FlclPlayerReference, val quantity: FlclFiniteQuantity
) : FlclOperation

data class FlclMoveLifeCardOperation(
  val target: FlclTarget,
  val sourceArea: FlclAreaReference,
  val destinationArea: FlclAreaReference,
  val position: FlclPosition? = null,
  val face: CardFace? = null,
  val orderChooser: FlclPlayerReference? = null
) : FlclOperation

data class FlclPositionCardOperation(
  val target: FlclTarget,
  val destinationArea: FlclAreaReference,
  val position: FlclPosition,
  val orderChooser: FlclPlayerReference? = null
) : FlclOperation

data class FlclReturnCardOperation(
  val target: FlclTarget, val destinationArea: FlclAreaReference, val position: FlclPosition? = null
) : FlclOperation

data class FlclEndGameOperation(
  val player: FlclPlayerReference, val outcome: FlclGameOutcome
) : FlclOperation

data class FlclPreventAttackOperation(
  val target: FlclTarget, val duration: FlclDurationSpec
) : FlclOperation