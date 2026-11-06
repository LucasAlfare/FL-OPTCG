package com.lucasalfare.opcg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Program(
  val id: ProgramId,
  val instructions: List<Instruction>,
)

@Serializable
data class Sequence(
  val instructions: List<Instruction>,
) : Instruction

@Serializable
data class Conditional(
  val condition: Condition,
  val ifTrue: List<Instruction>,
  val ifFalse: List<Instruction>,
) : Instruction

@Serializable
data class Optional(
  val instructions: List<Instruction>,
) : Instruction

@Serializable
data class ForEach(
  val source: VariableId,
  val item: VariableId,
  val instructions: List<Instruction>,
) : Instruction

@Serializable
data class RepeatWhile(
  val condition: Condition,
  val instructions: List<Instruction>,
) : Instruction

@Serializable
sealed class Cost {
  @Serializable
  data object NoCost : Cost()

  @Serializable
  data class CombinedCost(val costs: List<Cost>) : Cost()

  @Serializable
  data object RestSourceCost : Cost()

  @Serializable
  data class RestDonCost(val amount: ValueExpr) : Cost()

  @Serializable
  data class ReturnDonCost(val amount: ValueExpr) : Cost()

  @Serializable
  data class TrashCardsCost(val selector: CardSelector, val amount: ValueExpr) : Cost()
}

@Serializable
sealed class Duration {
  @Serializable
  data object DuringBattle : Duration()

  @Serializable
  data object UntilEndOfTurn : Duration()

  @Serializable
  data object UntilEndOfPhase : Duration()

  @Serializable
  data object UntilEndOfNextOpponentTurn : Duration()

  @Serializable
  data class UntilEvent(val pattern: GameEventPattern) : Duration()

  @Serializable
  data object WhileSourceValid : Duration()

  @Serializable
  data class WhileCondition(val condition: Condition) : Duration()

  @Serializable
  data object Permanent : Duration()
}

@Serializable
enum class RuleAction {
  PLAY_CARD, ACTIVATE_ABILITY, DECLARE_ATTACK, ACTIVATE_BLOCKER, USE_COUNTER, ATTACH_DON, ATTACK_ACTIVE_CHARACTER, BE_KNOCKED_OUT,
}

@Serializable
enum class KnockOutCause {
  BATTLE, EFFECT, ANY,
}

val ACTIONS_REQUIRING_PERMISSION_TARGET: Set<RuleAction> = setOf(RuleAction.ATTACK_ACTIVE_CHARACTER)

@Serializable
data class RuleRestriction(
  val action: RuleAction,
  val subject: CardSelector? = null,
  val target: CardSelector? = null,
  val maximumUses: ValueExpr? = null,
  val condition: Condition? = null,
  val cause: KnockOutCause? = null,
  val source: CardSelector? = null,
)

@Serializable
data class RulePermission(
  val action: RuleAction,
  val subject: CardSelector? = null,
  val condition: Condition? = null,
  val target: CardSelector? = null,
)

@Serializable
enum class AbilityType {
  AUTO, ACTIVATED, PERMANENT, REPLACEMENT,
}

@Serializable
sealed class EffectScope {
  @Serializable
  data object All : EffectScope()

  @Serializable
  data class Ability(val abilityId: AbilityId) : EffectScope()

  @Serializable
  data class Card(val cardInstanceId: CardInstanceId) : EffectScope()

  @Serializable
  data object CurrentBattle : EffectScope()
}

@Serializable
enum class KeywordOperation {
  ADD, REMOVE,
}

@Serializable
data class AbilitySelector(
  val cardSelector: CardSelector? = null,
  val abilityId: AbilityId? = null,
  val abilityType: AbilityType? = null,
  val timing: ActivationTiming? = null,
)

@Serializable
enum class DeckRuleProperty {
  MAIN_DECK_SIZE, DON_DECK_SIZE, MAXIMUM_COPIES, ALLOWED_COLORS,
}

@Serializable
data class DeckRuleModifier(
  val property: DeckRuleProperty,
  val numericOperation: NumericOperation? = null,
  val numericValue: ValueExpr? = null,
  val allowedColors: Set<CardColor>? = null,
  val cardNumber: String? = null,
)

@Serializable
sealed class Modifier {
  @Serializable
  data class CharacteristicModifier(
    val characteristic: NumericCharacteristic,
    val operation: NumericOperation,
    val value: ValueExpr,
  ) : Modifier()

  @Serializable
  data class PropertyModifier(
    val property: CardProperty,
    val value: PropertyValue,
  ) : Modifier()

  @Serializable
  data class KeywordModifier(
    val keyword: KeywordEffect,
    val operation: KeywordOperation,
  ) : Modifier()

  @Serializable
  data class ControlModifier(
    val controllerId: PlayerId,
  ) : Modifier()

  @Serializable
  data class DamagePreventionModifier(
    val playerId: PlayerId,
    val amount: ValueExpr,
  ) : Modifier()

  @Serializable
  data class PermissionModifier(
    val permission: RulePermission,
  ) : Modifier()

  @Serializable
  data class RestrictionModifier(
    val restriction: RuleRestriction,
  ) : Modifier()

  @Serializable
  data class EffectNegationModifier(
    val scope: EffectScope,
  ) : Modifier()

  @Serializable
  data class ActivationCostModifier(
    val selector: AbilitySelector,
    val cost: Cost,
  ) : Modifier()

  @Serializable
  data class DeckRuleEffectModifier(
    val modifier: DeckRuleModifier,
  ) : Modifier()

  @Serializable
  data class ReplacementModifier(
    val definition: ReplacementDefinition,
  ) : Modifier()

  @Serializable
  @SerialName("NAME_ALIAS")
  data class NameAliasModifier(
    val name: String,
  ) : Modifier()

  @Serializable
  data class AbilityModifier(
    val ability: AbilityDefinition,
    val add: Boolean,
  ) : Modifier()
}

@Serializable
data class ActiveEffect(
  val id: EffectId,
  val sourceCardId: CardInstanceId?,
  val sourceAbilityId: AbilityId?,
  val controllerId: PlayerId,
  val targetCardIds: List<CardInstanceId>,
  val targetPlayerId: PlayerId?,
  val modifier: Modifier,
  val duration: Duration,
  val timestampSequence: Long,
  val nextOpponentTurn: NextOpponentTurnMarker? = null,
)

@Serializable
data class NextOpponentTurnMarker(
  val opponentId: PlayerId,
  val expiresOnTurnNumber: Int,
)

@Serializable
data class ReplacementDefinition(
  val id: EffectId,
  val pattern: GameEventPattern,
  val condition: Condition?,
  val program: Program,
  val priority: Int,
  val duration: Duration,
)

@Serializable
sealed interface AbilityDefinition {
  val id: AbilityId
}

@Serializable
data class AutoAbilityDefinition(
  override val id: AbilityId,
  val trigger: GameEventPattern,
  val condition: Condition?,
  val program: Program,
  val usageLimit: UsageLimit,
) : AbilityDefinition

@Serializable
data class ActivatedAbilityDefinition(
  override val id: AbilityId,
  val timing: ActivationTiming,
  val cost: Cost,
  val condition: Condition?,
  val program: Program,
  val usageLimit: UsageLimit,
) : AbilityDefinition

@Serializable
data class PermanentAbilityDefinition(
  override val id: AbilityId,
  val condition: Condition?,
  val selector: CardSelector? = null,
  val modifier: Modifier,
) : AbilityDefinition

@Serializable
data class ReplacementAbilityDefinition(
  override val id: AbilityId,
  val replacement: ReplacementDefinition,
) : AbilityDefinition