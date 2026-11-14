package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.GameArea

@JvmInline
value class FlclStepId(val value: String) {
  init {
    require(value.isNotBlank()) { "FlclStepId must not be blank" }
  }
}

enum class FlclSelectableObject {
  CARD, DON
}

sealed interface FlclInformationStepRef

data class FlclLookStepRef(
  val step: FlclStepId
) : FlclInformationStepRef

data class FlclRevealStepRef(
  val step: FlclStepId
) : FlclInformationStepRef

data class FlclSelection(
  val id: FlclSelectionId,
  val objectKind: FlclSelectableObject,
  val areas: List<GameArea>,
  val quantity: FlclQuantity,
  val information: FlclInformationMode,
  val filter: FlclFilter?,
  val chooser: FlclPlayerReference?,
  val informationStep: FlclInformationStepRef?,
  val orderChooser: FlclPlayerReference?
) {
  init {
    require(areas.isNotEmpty()) { "SELECT requires at least one area" }
    require(areas.distinct().size == areas.size) { "SELECT must not repeat areas" }

    val automatic = quantity is FlclAllQuantity
    require(automatic == (chooser == null)) {
      "chooser is required for every quantity except ALL, and forbidden for ALL"
    }

    when (information) {
      FlclInformationMode.LOOKED_BY_CHOOSER -> require(informationStep is FlclLookStepRef) { "LOOKED_BY_CHOOSER requires a LOOK information_step" }
      FlclInformationMode.REVEALED_TO_CHOOSER -> require(informationStep is FlclRevealStepRef) { "REVEALED_TO_CHOOSER requires a REVEAL information_step" }
      else -> require(informationStep == null) { "information_step is only allowed for LOOKED_BY_CHOOSER and REVEALED_TO_CHOOSER" }
    }

    if (information == FlclInformationMode.OWNER_PRIVATE_INFORMATION) {
      require(chooser?.role == FlclPlayerRole.OWNER) {
        "OWNER_PRIVATE_INFORMATION requires a chooser that owns the secret area"
      }
    }
  }
}

data class FlclLook(
  val id: FlclStepId, val area: FlclAreaReference, val count: FlclFiniteQuantity
)

sealed interface FlclRevealSource

data class FlclRevealSubject(
  val subject: FlclObjectReference
) : FlclRevealSource

data class FlclRevealSelection(
  val selection: FlclSelectionId
) : FlclRevealSource

data class FlclRevealLook(
  val look: FlclLookStepRef
) : FlclRevealSource

data class FlclReveal(
  val id: FlclStepId, val source: FlclRevealSource, val recipients: List<FlclPlayerReference>?
) {
  init {
    if (recipients != null) {
      require(recipients.isNotEmpty()) { "REVEAL recipients, when present, must not be empty" }
      require(recipients.distinct().size == recipients.size) { "REVEAL recipients must not repeat players" }
    }
  }
}