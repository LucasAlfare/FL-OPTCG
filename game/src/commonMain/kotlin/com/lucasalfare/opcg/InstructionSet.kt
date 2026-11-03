package com.lucasalfare.opcg

sealed interface Instruction

enum class ArithmeticOperator {
  ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX,
}

enum class ComparisonOperator {
  EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
}

sealed class ValueExpr {
  data class NumberValue(val value: Int) : ValueExpr()
  data class CountValue(val selector: CardSelector) : ValueExpr()
  data class VariableValue(val variableId: VariableId) : ValueExpr()
  data class ArithmeticValue(
    val operator: ArithmeticOperator,
    val left: ValueExpr,
    val right: ValueExpr,
  ) : ValueExpr()

  data class CharacteristicValue(
    val selector: CardSelector,
    val characteristic: NumericCharacteristic,
  ) : ValueExpr()

  data class CurrentCardValue(val characteristic: NumericCharacteristic) : ValueExpr()
}

sealed class Condition {
  data class Comparison(
    val left: ValueExpr,
    val operator: ComparisonOperator,
    val right: ValueExpr,
  ) : Condition()

  data class AllConditions(val conditions: List<Condition>) : Condition()
  data class AnyCondition(val conditions: List<Condition>) : Condition()
  data class NotCondition(val condition: Condition) : Condition()
  data class Exists(val selector: CardSelector) : Condition()
}

data class CardSelector(
  val owner: PlayerRelation? = null,
  val controller: PlayerRelation? = null,
  val zones: Set<ZoneType> = emptySet(),
  val categories: Set<CardCategory> = emptySet(),
  val colors: Set<CardColor> = emptySet(),
  val types: Set<String> = emptySet(),
  val attributes: Set<CardAttribute> = emptySet(),
  val name: String? = null,
  val conditions: List<Condition> = emptyList(),
)

enum class Viewer {
  SOURCE_CONTROLLER, OWNER, OPPONENT, ALL_PLAYERS,
}

data class ZoneDestination(
  val playerId: PlayerId,
  val zone: ZoneType,
)

data class CardReference(
  val instanceId: CardInstanceId? = null,
  val variableId: VariableId? = null,
) {
  init {
    require((instanceId != null) xor (variableId != null)) {
      "CardReference requires exactly one of instanceId or variableId"
    }
  }
}

data class DonSelector(
  val zones: Set<ZoneType> = emptySet(),
  val orientation: CardOrientation? = null,
)

enum class Placement {
  TOP, BOTTOM, DEFAULT,
}

enum class LifePosition {
  TOP, BOTTOM, DEFAULT,
}

data class EffectContext(
  val gameId: GameId,
  val controllerId: PlayerId,
  val sourceCardId: CardInstanceId?,
  val abilityId: AbilityId?,
  val triggeringEvent: GameEvent?,
)

data class PlayPayment(
  val restedDonIds: List<CardInstanceId>,
  val additionalCosts: List<Cost>,
)

data class SelectCards(
  val selector: CardSelector,
  val playerId: PlayerId,
  val minimum: ValueExpr,
  val maximum: ValueExpr,
  val destination: VariableId,
) : Instruction

data class CountCards(
  val selector: CardSelector,
  val destination: VariableId,
) : Instruction

data class LookAtTop(
  val playerId: PlayerId,
  val zone: ZoneType,
  val amount: ValueExpr,
  val destination: VariableId,
) : Instruction

data class RevealCards(
  val source: VariableId,
  val viewers: Set<Viewer>,
) : Instruction

data class ChooseOption(
  val playerId: PlayerId,
  val options: List<OptionId>,
  val destination: VariableId,
) : Instruction

data class ChooseNumber(
  val playerId: PlayerId,
  val minimum: ValueExpr,
  val maximum: ValueExpr,
  val destination: VariableId,
) : Instruction

data class OrderCards(
  val source: VariableId,
  val playerId: PlayerId,
  val destination: VariableId,
) : Instruction

data class CheckCondition(
  val condition: Condition,
  val destination: VariableId,
) : Instruction

data class Draw(
  val playerId: PlayerId,
  val amount: ValueExpr,
) : Instruction

data class MoveCards(
  val source: VariableId,
  val destination: ZoneDestination,
  val placement: Placement,
) : Instruction

data class TrashFromHand(
  val playerId: PlayerId,
  val selector: CardSelector,
  val amount: ValueExpr,
) : Instruction

data class KnockOut(
  val source: VariableId,
  val effectId: EffectId?,
) : Instruction

data class PlayCard(
  val cardId: CardInstanceId,
  val playerId: PlayerId,
  val payment: PlayPayment,
) : Instruction

data class ShuffleZone(
  val playerId: PlayerId,
  val zone: ZoneType,
) : Instruction

data class DealDamage(
  val playerId: PlayerId,
  val amount: ValueExpr,
  val effectId: EffectId?,
) : Instruction

data class SetLife(
  val playerId: PlayerId,
  val source: VariableId,
  val position: LifePosition,
) : Instruction

data class PreventDamage(
  val playerId: PlayerId,
  val amount: ValueExpr,
  val duration: Duration,
) : Instruction

data class AddDon(
  val playerId: PlayerId,
  val amount: ValueExpr,
) : Instruction

data class RestDon(
  val playerId: PlayerId,
  val selector: DonSelector,
  val amount: ValueExpr,
) : Instruction

data class SetDonActive(
  val source: VariableId,
) : Instruction

data class AttachDon(
  val cardId: CardInstanceId,
  val source: VariableId,
) : Instruction

data class ReturnDon(
  val playerId: PlayerId,
  val selector: DonSelector,
  val amount: ValueExpr,
) : Instruction

data class DetachDon(
  val cardId: CardInstanceId,
  val amount: ValueExpr,
  val destination: ZoneDestination,
) : Instruction

data class RestCards(
  val source: VariableId,
) : Instruction

data class SetCardState(
  val source: VariableId,
  val orientation: CardOrientation,
) : Instruction

data class ChangeControl(
  val source: VariableId,
  val playerId: PlayerId,
  val duration: Duration,
) : Instruction

data class ModifyCharacteristic(
  val source: VariableId,
  val characteristic: NumericCharacteristic,
  val operation: NumericOperation,
  val value: ValueExpr,
  val duration: Duration,
) : Instruction

data class SetBaseCharacteristic(
  val source: VariableId,
  val characteristic: NumericCharacteristic,
  val value: ValueExpr,
  val duration: Duration,
) : Instruction

data class SetPowerToZero(
  val source: VariableId,
  val duration: Duration,
) : Instruction

data class SetCardProperty(
  val source: VariableId,
  val property: CardProperty,
  val value: PropertyValue,
  val duration: Duration,
) : Instruction

data class CopyCardProperties(
  val source: VariableId,
  val reference: CardReference,
  val properties: Set<CardProperty>,
  val duration: Duration,
) : Instruction

data class GrantKeyword(
  val source: VariableId,
  val keyword: KeywordEffect,
  val duration: Duration,
) : Instruction

data class RemoveKeyword(
  val source: VariableId,
  val keyword: KeywordEffect,
  val duration: Duration,
) : Instruction

data class GrantAbility(
  val source: VariableId,
  val ability: AbilityDefinition,
  val duration: Duration,
) : Instruction

data class CopyAbilities(
  val source: VariableId,
  val reference: CardReference,
  val duration: Duration,
) : Instruction

data class NegateEffects(
  val source: VariableId,
  val scope: EffectScope,
  val duration: Duration,
) : Instruction