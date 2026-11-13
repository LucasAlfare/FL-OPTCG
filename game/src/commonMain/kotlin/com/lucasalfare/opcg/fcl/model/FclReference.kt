package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SelectionId(val value: String)

@Serializable
sealed interface FclReference

@Serializable
@SerialName("SOURCE")
object SourceReference : FclReference

@Serializable
@SerialName("EVENT_OBJECT")
object EventObjectReference : FclReference

@Serializable
@SerialName("PLAYER")
data class PlayerReference(val role: FclPlayerRole) : FclReference

@Serializable
@SerialName("SELECTED_OBJECT")
data class SelectedObjectReference(val selection: SelectionId) : FclReference

@Serializable
@SerialName("SELECTED_SET")
data class SelectedSetReference(val selection: SelectionId) : FclReference

@Serializable
@SerialName("TEXT_DESIGNATED_OBJECT")
data class TextDesignatedObjectReference(val designation: String) : FclReference

@Serializable
@SerialName("ATTACKER")
object AttackerReference : FclReference

@Serializable
@SerialName("ATTACK_TARGET")
object AttackTargetReference : FclReference

@Serializable
@SerialName("BATTLE")
object BattleReference : FclReference

@Serializable
@SerialName("TOP_CARD")
data class TopCardReference(val player: FclReference, val area: FclArea) : FclReference

@Serializable
@SerialName("CANDIDATE")
object CandidateReference : FclReference