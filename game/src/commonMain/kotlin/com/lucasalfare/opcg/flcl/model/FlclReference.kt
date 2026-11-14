package com.lucasalfare.opcg.flcl.model

import com.lucasalfare.opcg.core.GameArea

@JvmInline
value class FlclSelectionId(val value: String) {
  init {
    require(value.isNotBlank()) { "FlclSelectionId must not be blank" }
  }
}

@JvmInline
value class FlclDesignationId(val value: String) {
  init {
    require(value.isNotBlank()) { "FlclDesignationId must not be blank" }
  }
}

sealed interface FlclReference

sealed interface FlclObjectReference : FlclReference

object FlclSourceReference : FlclObjectReference

object FlclEventObjectReference : FlclObjectReference

object FlclAttackerReference : FlclObjectReference

object FlclAttackTargetReference : FlclObjectReference

object FlclCandidateReference : FlclObjectReference

data class FlclSelectedObjectReference(
  val selection: FlclSelectionId
) : FlclObjectReference

data class FlclTextDesignatedObjectReference(
  val designation: FlclDesignationId
) : FlclObjectReference

data class FlclTopCardReference(
  val player: FlclPlayerReference,
  val area: GameArea
) : FlclObjectReference

data class FlclSelectedSetReference(
  val selection: FlclSelectionId
) : FlclReference

object FlclBattleReference : FlclReference

data class FlclPlayerReference(
  val role: FlclPlayerRole
) : FlclReference