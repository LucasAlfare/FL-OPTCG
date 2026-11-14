package com.lucasalfare.opcg.flcl.model

data class FlclRemainder(
  val id: FlclSelectionId, val lookStep: FlclLookStepRef, val excluding: List<FlclSelectionId> = emptyList()
) {
  init {
    require(excluding.distinct().size == excluding.size) {
      "REMAINDER excluding must not repeat selections"
    }
  }
}