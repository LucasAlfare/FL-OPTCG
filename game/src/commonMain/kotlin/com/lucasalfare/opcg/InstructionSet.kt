@file:Suppress("unused")

package com.lucasalfare.opcg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val SOURCE_CARD: VariableId = VariableId("SOURCE_CARD")

@Serializable
sealed interface Instruction

@Serializable
enum class ArithmeticOperator {
  ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX,
}

@Serializable
enum class ComparisonOperator {
  EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
}

@Serializable
sealed class ValueExpr {
  @Serializable
  data class NumberValue(val value: Int) : ValueExpr()

  @Serializable
  data class CountValue(val selector: CardSelector) : ValueExpr()

  @Serializable
  data class VariableValue(val variableId: VariableId) : ValueExpr()

  @Serializable
  data class ArithmeticValue(
    val operator: ArithmeticOperator,
    val left: ValueExpr,
    val right: ValueExpr,
  ) : ValueExpr()

  @Serializable
  data class CharacteristicValue(
    val selector: CardSelector,
    val characteristic: NumericCharacteristic,
  ) : ValueExpr()

  @Serializable
  data class CurrentCardValue(val characteristic: NumericCharacteristic) : ValueExpr()

  @Serializable
  data class CardCharacteristic(
    val cardReference: CardReference,
    val characteristic: NumericCharacteristic,
  ) : ValueExpr()
}

@Serializable
sealed class Condition {
  @Serializable
  data class Comparison(
    val left: ValueExpr,
    val operator: ComparisonOperator,
    val right: ValueExpr,
  ) : Condition()

  @Serializable
  data class AllConditions(val conditions: List<Condition>) : Condition()

  @Serializable
  data class AnyCondition(val conditions: List<Condition>) : Condition()

  @Serializable
  data class NotCondition(val condition: Condition) : Condition()

  @Serializable
  data class Exists(val selector: CardSelector) : Condition()

  @Serializable
  data class CardPropertyComparison(
    val leftCard: CardReference,
    val property: CardProperty,
    val operator: ComparisonOperator,
    val rightCard: CardReference,
  ) : Condition()
}

@Serializable
data class CardSelector(
  val owner: PlayerRelation? = null,
  val controller: PlayerRelation? = null,
  val zones: Set<ZoneType> = emptySet(),
  val categories: Set<CardCategory> = emptySet(),
  val colors: Set<CardColor> = emptySet(),
  val types: Set<String> = emptySet(),
  val attributes: Set<CardAttribute> = emptySet(),
  val keywords: Set<KeywordEffect> = emptySet(),
  val name: String? = null,
  val orientation: CardOrientation? = null,
  @SerialName("same_as_source") val sameAsSource: Boolean = false,
  @SerialName("exclude_source") val excludeSource: Boolean = false,
  val conditions: List<Condition> = emptyList(),
)

@Serializable
enum class Viewer {
  SOURCE_CONTROLLER, OWNER, OPPONENT, ALL_PLAYERS,
}

@Serializable
data class ZoneDestination(
  val playerId: PlayerId,
  val zone: ZoneType,
)

@Serializable
data class CardReference(
  val instanceId: CardInstanceId? = null,
  val variableId: VariableId? = null,
  val currentCard: Boolean = false,
) {
  init {
    require(listOf(instanceId != null, variableId != null, currentCard).count { it } == 1) {
      "CardReference requires exactly one of instanceId, variableId or currentCard"
    }
  }
}

@Serializable
data class DonSelector(
  val zones: Set<ZoneType> = emptySet(),
  val orientation: CardOrientation? = null,
  val source: VariableId? = null,
)

@Serializable
enum class Placement {
  TOP, BOTTOM, DEFAULT,
}

@Serializable
enum class LifePosition {
  TOP, BOTTOM, DEFAULT,
}

@Serializable
data class EffectContext(
  val gameId: GameId,
  val controllerId: PlayerId,
  val sourceCardId: CardInstanceId?,
  val abilityId: AbilityId?,
  val triggeringEvent: GameEvent?,
)

@Serializable
enum class PlayFromZone(val zone: ZoneType) {
  HAND(ZoneType.HAND), DECK(ZoneType.DECK), TRASH(ZoneType.TRASH), LIFE(ZoneType.LIFE),
}

@Serializable
enum class PlayOrientation {
  ACTIVE, RESTED,
}

@Serializable
data class PlayPayment(
  val restedDon: VariableId? = null,
  val additionalCosts: List<Cost> = emptyList(),
)

@Serializable
data class SelectCards(
  val selector: CardSelector,
  val playerId: PlayerId,
  val minimum: ValueExpr,
  val maximum: ValueExpr,
  val destination: VariableId,
) : Instruction

@Serializable
data class SelectFromVariable(
  val source: VariableId,
  val selector: CardSelector,
  val playerId: PlayerId,
  val minimum: ValueExpr,
  val maximum: ValueExpr,
  val destination: VariableId,
  val remainderDestination: VariableId? = null,
) : Instruction

@Serializable
@SerialName("RETURN_CARDS_TO_OWNER_HAND")
data class ReturnCardsToOwnerHand(
  val source: VariableId,
) : Instruction

@Serializable
data class CountCards(
  val selector: CardSelector,
  val destination: VariableId,
) : Instruction

@Serializable
data class LookAtTop(
  val playerId: PlayerId,
  val zone: ZoneType,
  val amount: ValueExpr,
  val destination: VariableId,
) : Instruction

@Serializable
data class RevealCards(
  val source: VariableId,
  val viewers: Set<Viewer>,
) : Instruction

@Serializable
data class ChooseOption(
  val playerId: PlayerId,
  val options: List<OptionId>,
  val destination: VariableId,
) : Instruction

@Serializable
data class ChooseNumber(
  val playerId: PlayerId,
  val minimum: ValueExpr,
  val maximum: ValueExpr,
  val destination: VariableId,
) : Instruction

@Serializable
data class OrderCards(
  val source: VariableId,
  val playerId: PlayerId,
  val destination: VariableId,
) : Instruction

@Serializable
data class CheckCondition(
  val condition: Condition,
  val destination: VariableId,
) : Instruction

@Serializable
data class Draw(
  val playerId: PlayerId,
  val amount: ValueExpr,
) : Instruction

@Serializable
data class MoveCards(
  val source: VariableId,
  val destination: ZoneDestination,
  val placement: Placement,
) : Instruction

@Serializable
data class TrashFromHand(
  val playerId: PlayerId,
  val selector: CardSelector,
  val amount: ValueExpr,
) : Instruction

@Serializable
data class KnockOut(
  val source: VariableId,
  val effectId: EffectId?,
) : Instruction

@Serializable
data class PlayCard(
  val card: CardReference,
  val fromZone: PlayFromZone,
  val controller: PlayerRelation,
  val payment: PlayPayment = PlayPayment(),
  val orientation: PlayOrientation? = null,
) : Instruction

@Serializable
data class ShuffleZone(
  val playerId: PlayerId,
  val zone: ZoneType,
) : Instruction

@Serializable
data class DealDamage(
  val playerId: PlayerId,
  val amount: ValueExpr,
  val effectId: EffectId?,
) : Instruction

@Serializable
data class SetLife(
  val playerId: PlayerId,
  val source: VariableId,
  val position: LifePosition,
) : Instruction

@Serializable
data class PreventDamage(
  val playerId: PlayerId,
  val amount: ValueExpr,
  val duration: Duration,
) : Instruction

@Serializable
data class AddDon(
  val playerId: PlayerId,
  val amount: ValueExpr,
  val destination: VariableId? = null,
) : Instruction

@Serializable
data class RestDon(
  val playerId: PlayerId,
  val selector: DonSelector,
  val amount: ValueExpr,
) : Instruction

@Serializable
data class SetDonActive(
  val source: VariableId,
) : Instruction

@Serializable
data class AttachDon(
  val cardId: CardInstanceId,
  val source: VariableId,
) : Instruction

@Serializable
data class ReturnDon(
  val playerId: PlayerId,
  val selector: DonSelector,
  val amount: ValueExpr,
) : Instruction

@Serializable
data class DetachDon(
  val cardId: CardInstanceId,
  val amount: ValueExpr,
  val destination: ZoneDestination,
) : Instruction

@Serializable
data class RestCards(
  val source: VariableId,
) : Instruction

@Serializable
data class SetCardState(
  val source: VariableId,
  val orientation: CardOrientation,
) : Instruction

@Serializable
data class ChangeControl(
  val source: VariableId,
  val playerId: PlayerId,
  val duration: Duration,
) : Instruction

@Serializable
data class ModifyCharacteristic(
  val source: VariableId,
  val characteristic: NumericCharacteristic,
  val operation: NumericOperation,
  val value: ValueExpr,
  val duration: Duration,
) : Instruction

@Serializable
data class SetBaseCharacteristic(
  val source: VariableId,
  val characteristic: NumericCharacteristic,
  val value: ValueExpr,
  val duration: Duration,
) : Instruction

@Serializable
data class SetPowerToZero(
  val source: VariableId,
  val duration: Duration,
) : Instruction

@Serializable
data class SetCardProperty(
  val source: VariableId,
  val property: CardProperty,
  val value: PropertyValue,
  val duration: Duration,
) : Instruction

@Serializable
data class CopyCardProperties(
  val source: VariableId,
  val reference: CardReference,
  val properties: Set<CardProperty>,
  val duration: Duration,
) : Instruction

@Serializable
data class GrantKeyword(
  val source: VariableId,
  val keyword: KeywordEffect,
  val duration: Duration,
) : Instruction

@Serializable
data class RemoveKeyword(
  val source: VariableId,
  val keyword: KeywordEffect,
  val duration: Duration,
) : Instruction

@Serializable
data class GrantAbility(
  val source: VariableId,
  val ability: AbilityDefinition,
  val duration: Duration,
) : Instruction

@Serializable
data class CopyAbilities(
  val source: VariableId,
  val reference: CardReference,
  val duration: Duration,
) : Instruction

@Serializable
data class NegateEffects(
  val source: VariableId,
  val scope: EffectScope,
  val duration: Duration,
) : Instruction

@Serializable
data class ApplyRestriction(
  val source: VariableId,
  val restriction: RuleRestriction,
  val duration: Duration,
) : Instruction

@Serializable
data class GrantPermission(
  val source: VariableId,
  val permission: RulePermission,
  val duration: Duration,
) : Instruction

@Serializable
data class AddActivationCost(
  val selector: AbilitySelector,
  val cost: Cost,
  val duration: Duration,
) : Instruction

@Serializable
data class ModifyDeckRule(
  val modifier: DeckRuleModifier,
  val duration: Duration,
) : Instruction

@Serializable
data class RegisterReplacement(
  val definition: ReplacementDefinition,
) : Instruction

@Serializable
data class ActivateAbility(
  val selector: AbilitySelector,
  val timing: ActivationTiming?,
) : Instruction

@Serializable
data class SetGameResult(
  val result: GameResult,
) : Instruction