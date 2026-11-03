package com.lucasalfare.opcg

data class Program(
  val id: ProgramId,
  val instructions: List<Instruction>,
)

data class Sequence(
  val instructions: List<Instruction>,
) : Instruction

data class Conditional(
  val condition: Condition,
  val ifTrue: List<Instruction>,
  val ifFalse: List<Instruction>,
) : Instruction

data class Optional(
  val instructions: List<Instruction>,
) : Instruction

data class ForEach(
  val source: VariableId,
  val item: VariableId,
  val instructions: List<Instruction>,
) : Instruction

data class RepeatWhile(
  val condition: Condition,
  val instructions: List<Instruction>,
) : Instruction

sealed class Cost {
  data object NoCost : Cost()
  data class CombinedCost(val costs: List<Cost>) : Cost()
  data object RestSourceCost : Cost()
  data class RestDonCost(val amount: ValueExpr) : Cost()
  data class ReturnDonCost(val amount: ValueExpr) : Cost()
  data class TrashCardsCost(val selector: CardSelector, val amount: ValueExpr) : Cost()
}

sealed class Duration {
  data object DuringBattle : Duration()
  data object UntilEndOfTurn : Duration()
  data object UntilEndOfPhase : Duration()
  data class UntilEvent(val pattern: GameEventPattern) : Duration()
  data object WhileSourceValid : Duration()
  data class WhileCondition(val condition: Condition) : Duration()
  data object Permanent : Duration()
}

enum class RuleAction {
  PLAY_CARD, ACTIVATE_ABILITY, DECLARE_ATTACK, ACTIVATE_BLOCKER, USE_COUNTER, ATTACH_DON,
}

data class RuleRestriction(
  val action: RuleAction,
  val subject: CardSelector? = null,
  val maximumUses: ValueExpr? = null,
  val condition: Condition? = null,
)

data class RulePermission(
  val action: RuleAction,
  val subject: CardSelector? = null,
  val condition: Condition? = null,
)

enum class AbilityType {
  AUTO, ACTIVATED, PERMANENT, REPLACEMENT,
}

sealed class EffectScope {
  data object All : EffectScope()
  data class Ability(val abilityId: AbilityId) : EffectScope()
  data class Card(val cardInstanceId: CardInstanceId) : EffectScope()
  data object CurrentBattle : EffectScope()
}

enum class KeywordOperation {
  ADD, REMOVE,
}

data class AbilitySelector(
  val cardSelector: CardSelector? = null,
  val abilityId: AbilityId? = null,
  val abilityType: AbilityType? = null,
  val timing: ActivationTiming? = null,
)

enum class DeckRuleProperty {
  MAIN_DECK_SIZE, DON_DECK_SIZE, MAXIMUM_COPIES, ALLOWED_COLORS,
}

data class DeckRuleModifier(
  val property: DeckRuleProperty,
  val numericOperation: NumericOperation? = null,
  val numericValue: ValueExpr? = null,
  val allowedColors: Set<CardColor>? = null,
  val cardNumber: String? = null,
)

sealed class Modifier {
  data class CharacteristicModifier(
    val characteristic: NumericCharacteristic,
    val operation: NumericOperation,
    val value: ValueExpr,
  ) : Modifier()

  data class PropertyModifier(
    val property: CardProperty,
    val value: PropertyValue,
  ) : Modifier()

  data class KeywordModifier(
    val keyword: KeywordEffect,
    val operation: KeywordOperation,
  ) : Modifier()

  data class ControlModifier(
    val controllerId: PlayerId,
  ) : Modifier()

  data class DamagePreventionModifier(
    val playerId: PlayerId,
    val amount: ValueExpr,
  ) : Modifier()

  data class PermissionModifier(
    val permission: RulePermission,
  ) : Modifier()

  data class RestrictionModifier(
    val restriction: RuleRestriction,
  ) : Modifier()

  data class EffectNegationModifier(
    val scope: EffectScope,
  ) : Modifier()

  data class ActivationCostModifier(
    val selector: AbilitySelector,
    val cost: Cost,
  ) : Modifier()

  data class DeckRuleEffectModifier(
    val modifier: DeckRuleModifier,
  ) : Modifier()

  data class ReplacementModifier(
    val definition: ReplacementDefinition,
  ) : Modifier()
}

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
)

data class ReplacementDefinition(
  val id: EffectId,
  val pattern: GameEventPattern,
  val condition: Condition?,
  val program: Program,
  val priority: Int,
  val duration: Duration,
)