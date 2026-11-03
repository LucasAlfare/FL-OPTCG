package com.lucasalfare.opcg

@JvmInline
value class PlayerId(val value: String)

@JvmInline
value class CardDefinitionId(val value: String)

@JvmInline
value class CardInstanceId(val value: String)

@JvmInline
value class CardObjectId(val value: String)

@JvmInline
value class AbilityId(val value: String)

@JvmInline
value class EffectId(val value: String)

@JvmInline
value class GameId(val value: String)

@JvmInline
value class EventId(val value: String)

@JvmInline
value class ChoiceId(val value: String)

@JvmInline
value class VariableId(val value: String)

@JvmInline
value class ExecutionId(val value: String)

@JvmInline
value class ProgramId(val value: String)

@JvmInline
value class OptionId(val value: String)

enum class CardCategory {
  LEADER, CHARACTER, EVENT, STAGE, DON,
}

enum class CardColor {
  RED, GREEN, BLUE, PURPLE, BLACK, YELLOW,
}

enum class CardAttribute {
  SLASH, STRIKE, RANGED, SPECIAL, WISDOM, QUESTION_MARK,
}

enum class ZoneType {
  DECK, DON_DECK, HAND, TRASH, LEADER, CHARACTER, STAGE, COST, LIFE, ATTACHED_DON,
}

enum class CardOrientation {
  ACTIVE, RESTED, NOT_APPLICABLE,
}

enum class FaceState {
  FACE_UP, FACE_DOWN,
}

sealed class CardVisibility {
  data object PUBLIC : CardVisibility()
  data object OWNER_ONLY : CardVisibility()
  data class PLAYERS(val playerIds: Set<PlayerId>) : CardVisibility()
}

enum class GamePhase {
  REFRESH, DRAW, DON, MAIN, END,
}

enum class BattleStep {
  ATTACK, BLOCK, COUNTER, DAMAGE, END,
}

enum class GameStatus {
  SETUP, IN_PROGRESS, FINISHED,
}

enum class GameSetupStep {
  INITIAL_EFFECTS, OPENING_HAND, MULLIGAN, LIFE, READY,
}

enum class PlayerRelation {
  SELF, OPPONENT, TURN_PLAYER, NON_TURN_PLAYER,
}

enum class KeywordEffect {
  RUSH, DOUBLE_ATTACK, BANISH, BLOCKER, TRIGGER, RUSH_CHARACTER, UNBLOCKABLE,
}

enum class ActivationWindow {
  GAME_SETUP, MAIN_PHASE, BLOCK_STEP, COUNTER_STEP, TRIGGER_RESOLUTION, EVENT_RESOLUTION, RULE_PROCESSING,
}

enum class ActivationTiming {
  AT_GAME_START, AT_TURN_START, AT_MAIN_PHASE_START, ACTIVATE_MAIN, COUNTER_STEP, ON_PLAY, WHEN_ATTACKING, ON_BLOCK, ON_KO, ON_OPPONENT_ATTACK, ON_TRIGGER, END_OF_YOUR_TURN, END_OF_OPPONENT_TURN,
}

enum class UsageLimit {
  UNLIMITED, ONCE_PER_TURN,
}

enum class NumericCharacteristic {
  POWER, BASE_POWER, COST, BASE_COST, LIFE, COUNTER,
}

enum class NumericOperation {
  ADD, SUBTRACT, SET,
}

enum class GameEndReason {
  LIFE_DEFEAT, DECK_OUT, CONCESSION, CARD_EFFECT, DRAW,
}

enum class BattleOutcome {
  ATTACKER_WON, ATTACKER_LOST, INTERRUPTED, NO_DAMAGE,
}

sealed class BattleTarget {
  data class PlayerTarget(val playerId: PlayerId) : BattleTarget()
  data class CardTarget(val cardInstanceId: CardInstanceId) : BattleTarget()
}

data class RandomState(
  val algorithmId: String,
  val seed: ULong,
  val state: ULong,
  val drawCount: Long,
)

enum class CardProperty {
  NAME, COLOR, TYPE, ATTRIBUTE, KEYWORDS,
}

sealed class PropertyValue {
  data class StringValue(val value: String) : PropertyValue()
  data class ColorSetValue(val value: Set<CardColor>) : PropertyValue()
  data class TypeSetValue(val value: Set<String>) : PropertyValue()
  data class AttributeSetValue(val value: Set<CardAttribute>) : PropertyValue()
  data class KeywordSetValue(val value: Set<KeywordEffect>) : PropertyValue()
}

sealed class ChoiceAnswer {
  data class OptionAnswer(val optionId: OptionId) : ChoiceAnswer()
  data class NumberAnswer(val value: Int) : ChoiceAnswer()
  data class CardSelectionAnswer(val cardIds: List<CardInstanceId>) : ChoiceAnswer()
  data class CardOrderAnswer(val cardIds: List<CardInstanceId>) : ChoiceAnswer()
  data class BooleanAnswer(val value: Boolean) : ChoiceAnswer()
}