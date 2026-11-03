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