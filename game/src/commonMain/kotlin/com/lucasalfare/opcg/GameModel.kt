@file:Suppress("UnusedPrivateMember")

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

enum class GameEventType {
  GAME_CREATED, SETUP_STEP_CHANGED, GAME_STARTED, PROGRAM_FRAME_CHANGED, PENDING_CHOICE_CHANGED, TURN_STARTED, PHASE_CHANGED, TURN_ENDED, CARD_PLAYED, CARDS_DRAWN, CARD_MOVED, CARD_REVEALED, CARD_STATE_CHANGED, CARD_ACTIVATED, CARD_KNOCKED_OUT, DON_ADDED, DON_RESTED, DON_ATTACHED, DON_DETACHED, DON_RETURNED, ATTACK_DECLARED, BLOCK_ACTIVATED, COUNTER_ACTIVATED, BATTLE_ENDED, DAMAGE_PROCESSED, LIFE_CHANGED, EFFECT_ACTIVATED, EFFECT_RESOLVED, EFFECT_FAILED, MODIFIER_ADDED, MODIFIER_EXPIRED, REPLACEMENT_APPLIED, DECK_SHUFFLED, CHOICE_SUBMITTED, MULLIGAN_RESOLVED, PLAYER_WON, PLAYER_LOST, GAME_DRAWN,
}

data class GameEventPattern(
  val eventTypes: Set<GameEventType>,
  val source: CardSelector? = null,
  val target: CardSelector? = null,
  val targetPlayer: PlayerRelation? = null,
  val player: PlayerRelation? = null,
)

sealed class EventVisibility {
  data object PUBLIC : EventVisibility()
  data class PRIVATE_TO_PLAYER(val playerId: PlayerId) : EventVisibility()
  data class PRIVATE_TO_PLAYERS(val playerIds: Set<PlayerId>) : EventVisibility()
  data object INTERNAL : EventVisibility()
}

data class GameEvent(
  val id: EventId,
  val sequence: Long,
  val type: GameEventType,
  val sourceCardId: CardInstanceId?,
  val payload: GameEventPayload,
  val visibility: EventVisibility,
)

sealed class GameEventPayload {
  data class GameCreatedPayload(
    val playerIds: List<PlayerId>,
    val firstPlayerId: PlayerId,
    val chooserPlayerId: PlayerId,
    val formatId: String,
  ) : GameEventPayload()

  data class SetupStepChangedPayload(
    val previousStep: GameSetupStep,
    val newStep: GameSetupStep,
  ) : GameEventPayload()

  data class GameStartedPayload(
    val firstPlayerId: PlayerId,
  ) : GameEventPayload()

  data class TurnStartedPayload(
    val turnNumber: Int,
    val playerId: PlayerId,
  ) : GameEventPayload()

  data class PhaseChangedPayload(
    val playerId: PlayerId,
    val previousPhase: GamePhase,
    val newPhase: GamePhase,
  ) : GameEventPayload()

  data class TurnEndedPayload(
    val turnNumber: Int,
    val playerId: PlayerId,
  ) : GameEventPayload()

  data class CardPlayedPayload(
    val cardId: CardInstanceId,
    val playerId: PlayerId,
    val fromZone: ZoneType,
    val toZone: ZoneType,
  ) : GameEventPayload()

  data class CardsDrawnPayload(
    val playerId: PlayerId,
    val cardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class CardMovedPayload(
    val cardId: CardInstanceId,
    val fromPlayerId: PlayerId,
    val fromZone: ZoneType,
    val toPlayerId: PlayerId,
    val toZone: ZoneType,
    val destinationIndex: Int?,
    val newObjectId: CardObjectId?,
    val orientation: CardOrientation,
    val faceState: FaceState,
    val visibility: CardVisibility,
  ) : GameEventPayload()

  data class CardRevealedPayload(
    val cardIds: List<CardInstanceId>,
    val visibility: CardVisibility,
  ) : GameEventPayload()

  data class CardStateChangedPayload(
    val cardId: CardInstanceId,
    val orientation: CardOrientation,
    val faceState: FaceState,
  ) : GameEventPayload()

  data class CardActivatedPayload(
    val cardId: CardInstanceId,
    val playerId: PlayerId,
  ) : GameEventPayload()

  data class CardKnockedOutPayload(
    val cardId: CardInstanceId,
    val ownerId: PlayerId,
    val sourceCardId: CardInstanceId?,
    val effectId: EffectId?,
  ) : GameEventPayload()

  data class DonAddedPayload(
    val playerId: PlayerId,
    val donCardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class DonRestedPayload(
    val playerId: PlayerId,
    val donCardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class DonAttachedPayload(
    val hostCardId: CardInstanceId,
    val donCardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class DonDetachedPayload(
    val hostCardId: CardInstanceId,
    val donCardIds: List<CardInstanceId>,
    val destinationPlayerId: PlayerId,
    val destinationZone: ZoneType,
    val destinationIndex: Int?,
  ) : GameEventPayload()

  data class DonReturnedPayload(
    val playerId: PlayerId,
    val donCardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class AttackDeclaredPayload(
    val attackerId: CardInstanceId,
    val target: BattleTarget,
  ) : GameEventPayload()

  data class BlockActivatedPayload(
    val blockerId: CardInstanceId,
    val previousTarget: BattleTarget,
    val newTarget: BattleTarget,
  ) : GameEventPayload()

  data class CounterActivatedPayload(
    val playerId: PlayerId,
    val cardId: CardInstanceId,
    val targetCardId: CardInstanceId,
    val powerAdded: Int,
  ) : GameEventPayload()

  data class BattleEndedPayload(
    val attackerId: CardInstanceId,
    val finalTarget: BattleTarget,
    val outcome: BattleOutcome,
  ) : GameEventPayload()

  data class DamageProcessedPayload(
    val playerId: PlayerId,
    val amount: Int,
    val sourceCardId: CardInstanceId?,
    val effectId: EffectId?,
  ) : GameEventPayload()

  data class LifeChangedPayload(
    val playerId: PlayerId,
    val addedCardIds: List<CardInstanceId>,
    val removedCardIds: List<CardInstanceId>,
    val destinationZone: ZoneType?,
  ) : GameEventPayload()

  data class EffectActivatedPayload(
    val effectId: EffectId,
    val sourceCardId: CardInstanceId?,
    val abilityId: AbilityId?,
  ) : GameEventPayload()

  data class EffectResolvedPayload(
    val effectId: EffectId,
  ) : GameEventPayload()

  data class EffectFailedPayload(
    val effectId: EffectId,
    val errorCode: String,
  ) : GameEventPayload()

  data class ModifierAddedPayload(
    val effect: ActiveEffect,
  ) : GameEventPayload()

  data class ModifierExpiredPayload(
    val effectId: EffectId,
  ) : GameEventPayload()

  data class ReplacementAppliedPayload(
    val replacementId: EffectId,
    val replacedEventId: EventId,
  ) : GameEventPayload()

  data class DeckShuffledPayload(
    val playerId: PlayerId,
    val zone: ZoneType,
    val resultingOrder: List<CardInstanceId>,
    val randomStateAfter: RandomState,
  ) : GameEventPayload()

  data class ChoiceSubmittedPayload(
    val playerId: PlayerId,
    val choiceId: ChoiceId,
    val answer: ChoiceAnswer,
  ) : GameEventPayload()

  data class MulliganResolvedPayload(
    val playerId: PlayerId,
    val redrawn: Boolean,
    val returnedCardIds: List<CardInstanceId>,
  ) : GameEventPayload()

  data class PlayerWonPayload(
    val playerId: PlayerId,
    val reason: GameEndReason,
  ) : GameEventPayload()

  data class PlayerLostPayload(
    val playerId: PlayerId,
    val reason: GameEndReason,
  ) : GameEventPayload()

  data class GameDrawnPayload(
    val playerIds: Set<PlayerId>,
    val reason: GameEndReason,
  ) : GameEventPayload()
}

data class CardDefinition(
  val id: CardDefinitionId,
  val number: String,
  val name: String,
  val category: CardCategory,
  val colors: Set<CardColor>,
  val types: Set<String>,
  val attributes: Set<CardAttribute>,
  val baseCost: Int?,
  val basePower: Int?,
  val life: Int?,
  val counter: Int?,
  val keywords: Set<KeywordEffect>,
  val abilities: List<AbilityDefinition>,
)

data class CardInstance(
  val id: CardInstanceId,
  val definitionId: CardDefinitionId,
  val ownerId: PlayerId,
  val controllerId: PlayerId,
  val objectId: CardObjectId,
  val orientation: CardOrientation,
  val faceState: FaceState,
  val visibility: CardVisibility,
)

data class EffectiveCharacteristics(
  val name: String,
  val colors: Set<CardColor>,
  val types: Set<String>,
  val attributes: Set<CardAttribute>,
  val cost: Int?,
  val power: Int?,
  val life: Int?,
  val counter: Int?,
  val keywords: Set<KeywordEffect>,
)

interface CardCatalog {
  val version: String
  val hash: String
  fun get(id: CardDefinitionId): CardDefinition?
}

data class ZoneState(
  val cards: List<CardInstanceId>,
)

data class PlayerState(
  val id: PlayerId,
  val deck: ZoneState,
  val donDeck: ZoneState,
  val hand: ZoneState,
  val trash: ZoneState,
  val leader: ZoneState,
  val characters: ZoneState,
  val stage: ZoneState,
  val cost: ZoneState,
  val life: ZoneState,
)

data class DonAttachment(
  val hostCardId: CardInstanceId,
  val donCardIds: List<CardInstanceId>,
)

data class TurnState(
  val number: Int,
  val playerId: PlayerId,
  val phase: GamePhase,
)

data class BattleState(
  val attackerId: CardInstanceId,
  val originalTarget: BattleTarget,
  val currentTarget: BattleTarget,
  val step: BattleStep,
  val blockerUsed: Boolean,
  val counterPower: Int,
)

data class GameResult(
  val winnerId: PlayerId?,
  val loserIds: Set<PlayerId>,
  val reason: GameEndReason,
)

data class GameManifest(
  val rulesVersion: String,
  val cardCatalogVersion: String,
  val cardCatalogHash: String,
  val instructionSetVersion: String,
  val vmVersion: String,
  val randomAlgorithm: String,
  val gameFormatId: String,
)

data class GameState(
  val id: GameId,
  val status: GameStatus,
  val players: Map<PlayerId, PlayerState>,
  val playerOrder: List<PlayerId>,
  val firstPlayerId: PlayerId,
  val chooserPlayerId: PlayerId,
  val cards: Map<CardInstanceId, CardInstance>,
  val donAttachments: List<DonAttachment>,
  val turn: TurnState?,
  val battle: BattleState?,
  val activeEffects: List<ActiveEffect>,
  val randomState: RandomState,
  val lastEventSequence: Long,
  val result: GameResult?,
)