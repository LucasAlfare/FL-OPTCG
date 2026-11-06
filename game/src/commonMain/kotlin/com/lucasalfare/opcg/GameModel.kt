@file:Suppress("UnusedPrivateMember")

package com.lucasalfare.opcg

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PlayerId(val value: String)

@Serializable
@JvmInline
value class CardDefinitionId(val value: String)

@Serializable
@JvmInline
value class CardInstanceId(val value: String)

@Serializable
@JvmInline
value class CardObjectId(val value: String)

@Serializable
@JvmInline
value class AbilityId(val value: String)

@Serializable
@JvmInline
value class EffectId(val value: String)

@Serializable
@JvmInline
value class GameId(val value: String)

@Serializable
@JvmInline
value class EventId(val value: String)

@Serializable
@JvmInline
value class ChoiceId(val value: String)

@Serializable
@JvmInline
value class VariableId(val value: String)

@Serializable
@JvmInline
value class ExecutionId(val value: String)

@Serializable
@JvmInline
value class ProgramId(val value: String)

@Serializable
@JvmInline
value class OptionId(val value: String)

@Serializable
enum class CardCategory { LEADER, CHARACTER, EVENT, STAGE, DON }

@Serializable
enum class CardColor { RED, GREEN, BLUE, PURPLE, BLACK, YELLOW }

@Serializable
enum class CardAttribute { SLASH, STRIKE, RANGED, SPECIAL, WISDOM, QUESTION_MARK }

@Serializable
enum class ZoneType { DECK, DON_DECK, HAND, TRASH, LEADER, CHARACTER, STAGE, COST, LIFE, ATTACHED_DON }

@Serializable
enum class CardOrientation { ACTIVE, RESTED, NOT_APPLICABLE }

@Serializable
enum class FaceState { FACE_UP, FACE_DOWN }

@Serializable
sealed class CardVisibility {
  @Serializable
  data object PUBLIC : CardVisibility()

  @Serializable
  data object OWNER_ONLY : CardVisibility()

  @Serializable
  data class PLAYERS(val playerIds: Set<PlayerId>) : CardVisibility()
}

@Serializable
enum class GamePhase { REFRESH, DRAW, DON, MAIN, END }

@Serializable
enum class BattleStep { ATTACK, BLOCK, COUNTER, DAMAGE, END }

@Serializable
enum class GameStatus { SETUP, IN_PROGRESS, FINISHED }

@Serializable
enum class GameSetupStep { INITIAL_EFFECTS, OPENING_HAND, MULLIGAN, LIFE, READY }

@Serializable
enum class PlayerRelation { SELF, OPPONENT, TURN_PLAYER, NON_TURN_PLAYER }

@Serializable
enum class KeywordEffect { RUSH, DOUBLE_ATTACK, BANISH, BLOCKER, TRIGGER, RUSH_CHARACTER, UNBLOCKABLE }

@Serializable
data class InitialEffectsProgress(val playerIndex: Int, val abilityIndex: Int)

@Serializable
enum class ActivationWindow { GAME_SETUP, MAIN_PHASE, BLOCK_STEP, COUNTER_STEP, TRIGGER_RESOLUTION, EVENT_RESOLUTION, RULE_PROCESSING }

@Serializable
enum class ActivationTiming {
  AT_GAME_START, AT_TURN_START, AT_MAIN_PHASE_START, ACTIVATE_MAIN, COUNTER_STEP, ON_PLAY, WHEN_ATTACKING, ON_BLOCK, ON_KO, ON_OPPONENT_ATTACK, ON_TRIGGER, END_OF_YOUR_TURN, END_OF_OPPONENT_TURN
}

@Serializable
enum class UsageLimit { UNLIMITED, ONCE_PER_TURN }

@Serializable
enum class NumericCharacteristic { POWER, BASE_POWER, COST, BASE_COST, LIFE, COUNTER, ATTACHED_DON_COUNT }

@Serializable
enum class NumericOperation { ADD, SUBTRACT, SET }

@Serializable
enum class GameEndReason { LIFE_DEFEAT, DECK_OUT, CONCESSION, CARD_EFFECT, DRAW }

@Serializable
enum class BattleOutcome { ATTACKER_WON, ATTACKER_LOST, INTERRUPTED, NO_DAMAGE }

@Serializable
sealed class BattleTarget {
  @Serializable
  data class PlayerTarget(val playerId: PlayerId) : BattleTarget()

  @Serializable
  data class CardTarget(val cardInstanceId: CardInstanceId) : BattleTarget()
}

@Serializable
data class RandomState(val algorithmId: String, val seed: ULong, val state: ULong, val drawCount: Long)

@Serializable
enum class CardProperty { NAME, COLOR, TYPE, ATTRIBUTE, KEYWORDS }

@Serializable
sealed class PropertyValue {
  @Serializable
  data class StringValue(val value: String) : PropertyValue()

  @Serializable
  data class ColorSetValue(val value: Set<CardColor>) : PropertyValue()

  @Serializable
  data class TypeSetValue(val value: Set<String>) : PropertyValue()

  @Serializable
  data class AttributeSetValue(val value: Set<CardAttribute>) : PropertyValue()

  @Serializable
  data class KeywordSetValue(val value: Set<KeywordEffect>) : PropertyValue()
}

@Serializable
sealed class ChoiceAnswer {
  @Serializable
  data class OptionAnswer(val optionId: OptionId) : ChoiceAnswer()

  @Serializable
  data class NumberAnswer(val value: Int) : ChoiceAnswer()

  @Serializable
  data class CardSelectionAnswer(val cardIds: List<CardInstanceId>) : ChoiceAnswer()

  @Serializable
  data class CardOrderAnswer(val cardIds: List<CardInstanceId>) : ChoiceAnswer()

  @Serializable
  data class BooleanAnswer(val value: Boolean) : ChoiceAnswer()
}

@Serializable
enum class GameEventType {
  GAME_CREATED, SETUP_STEP_CHANGED, GAME_STARTED, PROGRAM_FRAME_CHANGED, PENDING_CHOICE_CHANGED, TURN_STARTED, PHASE_CHANGED, TURN_ENDED, CARD_PLAYED, CARDS_DRAWN, CARD_MOVED, CARD_REVEALED, CARD_STATE_CHANGED, CARD_ACTIVATED, CARD_KNOCKED_OUT, DON_ADDED, DON_RESTED, DON_ATTACHED, DON_DETACHED, DON_RETURNED, ATTACK_DECLARED, BATTLE_STEP_CHANGED, BLOCK_ACTIVATED, COUNTER_ACTIVATED, BATTLE_ENDED, DAMAGE_PROCESSED, LIFE_CHANGED, EFFECT_ACTIVATED, EFFECT_RESOLVED, EFFECT_FAILED, MODIFIER_ADDED, MODIFIER_EXPIRED, REPLACEMENT_APPLIED, DECK_SHUFFLED, CHOICE_SUBMITTED, MULLIGAN_RESOLVED, PLAYER_WON, PLAYER_LOST, GAME_DRAWN, INITIAL_EFFECTS_PROGRESS_CHANGED
}

@Serializable
data class GameEventPattern(
  val eventTypes: Set<GameEventType>,
  val source: CardSelector? = null,
  val target: CardSelector? = null,
  val targetPlayer: PlayerRelation? = null,
  val player: PlayerRelation? = null,
)

@Serializable
sealed class EventVisibility {
  @Serializable
  data object PUBLIC : EventVisibility()

  @Serializable
  data class PRIVATE_TO_PLAYER(val playerId: PlayerId) : EventVisibility()

  @Serializable
  data class PRIVATE_TO_PLAYERS(val playerIds: Set<PlayerId>) : EventVisibility()

  @Serializable
  data object INTERNAL : EventVisibility()
}

@Serializable
data class GameEvent(
  val id: EventId,
  val sequence: Long,
  val type: GameEventType,
  val sourceCardId: CardInstanceId?,
  val payload: GameEventPayload,
  val visibility: EventVisibility,
)

@Serializable
sealed class GameEventPayload {
  @Serializable
  data class GameCreatedPayload(
    val playerIds: List<PlayerId>, val firstPlayerId: PlayerId,
    val chooserPlayerId: PlayerId, val formatId: String,
  ) : GameEventPayload()

  @Serializable
  data class SetupStepChangedPayload(val previousStep: GameSetupStep, val newStep: GameSetupStep) : GameEventPayload()

  @Serializable
  data class GameStartedPayload(val firstPlayerId: PlayerId) : GameEventPayload()

  @Serializable
  data class TurnStartedPayload(val turnNumber: Int, val playerId: PlayerId) : GameEventPayload()

  @Serializable
  data class PhaseChangedPayload(val playerId: PlayerId, val previousPhase: GamePhase, val newPhase: GamePhase) :
    GameEventPayload()

  @Serializable
  data class TurnEndedPayload(val turnNumber: Int, val playerId: PlayerId) : GameEventPayload()

  @Serializable
  data class CardPlayedPayload(
    val cardId: CardInstanceId, val playerId: PlayerId, val fromZone: ZoneType, val toZone: ZoneType
  ) : GameEventPayload()

  @Serializable
  data class CardsDrawnPayload(val playerId: PlayerId, val cardIds: List<CardInstanceId>) : GameEventPayload()

  @Serializable
  data class CardMovedPayload(
    val cardId: CardInstanceId, val fromPlayerId: PlayerId, val fromZone: ZoneType,
    val toPlayerId: PlayerId, val toZone: ZoneType, val destinationIndex: Int?,
    val newObjectId: CardObjectId?, val orientation: CardOrientation,
    val faceState: FaceState, val visibility: CardVisibility,
  ) : GameEventPayload()

  @Serializable
  data class CardRevealedPayload(val cardIds: List<CardInstanceId>, val visibility: CardVisibility) : GameEventPayload()

  @Serializable
  data class CardStateChangedPayload(
    val cardId: CardInstanceId, val orientation: CardOrientation, val faceState: FaceState
  ) : GameEventPayload()

  @Serializable
  data class CardActivatedPayload(val cardId: CardInstanceId, val playerId: PlayerId) : GameEventPayload()

  @Serializable
  data class CardKnockedOutPayload(
    val cardId: CardInstanceId, val ownerId: PlayerId,
    val sourceCardId: CardInstanceId?, val effectId: EffectId?,
  ) : GameEventPayload()

  @Serializable
  data class DonAddedPayload(val playerId: PlayerId, val donCardIds: List<CardInstanceId>) : GameEventPayload()

  @Serializable
  data class DonRestedPayload(val playerId: PlayerId, val donCardIds: List<CardInstanceId>) : GameEventPayload()

  @Serializable
  data class DonAttachedPayload(val hostCardId: CardInstanceId, val donCardIds: List<CardInstanceId>) :
    GameEventPayload()

  @Serializable
  data class DonDetachedPayload(
    val hostCardId: CardInstanceId, val donCardIds: List<CardInstanceId>,
    val destinationPlayerId: PlayerId, val destinationZone: ZoneType, val destinationIndex: Int?,
  ) : GameEventPayload()

  @Serializable
  data class DonReturnedPayload(val playerId: PlayerId, val donCardIds: List<CardInstanceId>) : GameEventPayload()

  @Serializable
  data class AttackDeclaredPayload(val attackerId: CardInstanceId, val target: BattleTarget) : GameEventPayload()

  @Serializable
  data class BattleStepChangedPayload(val previousStep: BattleStep, val newStep: BattleStep) : GameEventPayload()

  @Serializable
  data class BlockActivatedPayload(
    val blockerId: CardInstanceId, val previousTarget: BattleTarget, val newTarget: BattleTarget,
  ) : GameEventPayload()

  @Serializable
  data class CounterActivatedPayload(
    val playerId: PlayerId, val cardId: CardInstanceId,
    val targetCardId: CardInstanceId, val powerAdded: Int,
  ) : GameEventPayload()

  @Serializable
  data class BattleEndedPayload(
    val attackerId: CardInstanceId, val finalTarget: BattleTarget, val outcome: BattleOutcome,
  ) : GameEventPayload()

  @Serializable
  data class DamageProcessedPayload(
    val playerId: PlayerId, val amount: Int,
    val sourceCardId: CardInstanceId?, val effectId: EffectId?,
  ) : GameEventPayload()

  @Serializable
  data class LifeChangedPayload(
    val playerId: PlayerId, val addedCardIds: List<CardInstanceId>,
    val removedCardIds: List<CardInstanceId>, val destinationZone: ZoneType?,
  ) : GameEventPayload()

  @Serializable
  data class EffectActivatedPayload(
    val effectId: EffectId, val sourceCardId: CardInstanceId?, val abilityId: AbilityId?
  ) : GameEventPayload()

  @Serializable
  data class EffectResolvedPayload(val effectId: EffectId) : GameEventPayload()

  @Serializable
  data class EffectFailedPayload(val effectId: EffectId, val errorCode: String) : GameEventPayload()

  @Serializable
  data class ModifierAddedPayload(val effect: ActiveEffect) : GameEventPayload()

  @Serializable
  data class ModifierExpiredPayload(val effectId: EffectId) : GameEventPayload()

  @Serializable
  data class ReplacementAppliedPayload(val replacementId: EffectId, val replacedEventId: EventId) : GameEventPayload()

  @Serializable
  data class DeckShuffledPayload(
    val playerId: PlayerId, val zone: ZoneType,
    val resultingOrder: List<CardInstanceId>, val randomStateAfter: RandomState,
  ) : GameEventPayload()

  @Serializable
  data class ChoiceSubmittedPayload(val playerId: PlayerId, val choiceId: ChoiceId, val answer: ChoiceAnswer) :
    GameEventPayload()

  @Serializable
  data class MulliganResolvedPayload(
    val playerId: PlayerId, val redrawn: Boolean, val returnedCardIds: List<CardInstanceId>
  ) : GameEventPayload()

  @Serializable
  data class PlayerWonPayload(val playerId: PlayerId, val reason: GameEndReason) : GameEventPayload()

  @Serializable
  data class PlayerLostPayload(val playerId: PlayerId, val reason: GameEndReason) : GameEventPayload()

  @Serializable
  data class GameDrawnPayload(val playerIds: Set<PlayerId>, val reason: GameEndReason) : GameEventPayload()

  @Serializable
  data class InitialEffectsProgressPayload(val progress: InitialEffectsProgress) : GameEventPayload()

  @Serializable
  data class ProgramFrameChangedPayload(val executionId: ExecutionId, val frame: ProgramFrame?) : GameEventPayload()

  @Serializable
  data class PendingChoiceChangedPayload(val choiceId: ChoiceId, val choice: PendingChoice?) : GameEventPayload()
}

data class CardDefinition(
  val id: CardDefinitionId, val number: String, val name: String,
  val category: CardCategory, val colors: Set<CardColor>, val types: Set<String>,
  val attributes: Set<CardAttribute>, val baseCost: Int?, val basePower: Int?,
  val life: Int?, val counter: Int?, val keywords: Set<KeywordEffect>,
  val abilities: List<AbilityDefinition>,
)

@Serializable
data class CardInstance(
  val id: CardInstanceId,
  val definitionId: CardDefinitionId,
  val ownerId: PlayerId,
  val controllerId: PlayerId,
  val objectId: CardObjectId,
  val orientation: CardOrientation,
  val faceState: FaceState,
  val visibility: CardVisibility,
  val enteredPlayTurn: Int? = null,
)

data class EffectiveCharacteristics(
  val name: String, val colors: Set<CardColor>, val types: Set<String>,
  val attributes: Set<CardAttribute>, val cost: Int?, val power: Int?,
  val life: Int?, val counter: Int?, val keywords: Set<KeywordEffect>,
  val attachedDonCount: Int,
  val aliases: Set<String> = emptySet(),
) {
  fun answersToName(name: String): Boolean = this.name == name || name in aliases
}

interface CardCatalog {
  val version: String
  val hash: String
  fun get(id: CardDefinitionId): CardDefinition?
}

@Serializable
data class ZoneState(val cards: List<CardInstanceId>)

@Serializable
data class PlayerState(
  val id: PlayerId, val deck: ZoneState, val donDeck: ZoneState,
  val hand: ZoneState, val trash: ZoneState, val leader: ZoneState,
  val characters: ZoneState, val stage: ZoneState, val cost: ZoneState, val life: ZoneState,
)

@Serializable
data class DonAttachment(val hostCardId: CardInstanceId, val donCardIds: List<CardInstanceId>)

@Serializable
data class TurnState(val number: Int, val playerId: PlayerId, val phase: GamePhase)

@Serializable
data class BattleState(
  val attackerId: CardInstanceId,
  val originalTarget: BattleTarget,
  val currentTarget: BattleTarget,
  val step: BattleStep,
  val blockerUsed: Boolean,
  val counterPower: Int,
)

@Serializable
data class GameResult(val winnerId: PlayerId?, val loserIds: Set<PlayerId>, val reason: GameEndReason)

@Serializable
data class GameManifest(
  val rulesVersion: String, val cardCatalogVersion: String, val cardCatalogHash: String,
  val instructionSetVersion: String, val vmVersion: String, val randomAlgorithm: String,
  val gameFormatId: String,
) {
  fun differencesFrom(other: GameManifest): List<String> {
    val diffs = mutableListOf<String>()
    if (rulesVersion != other.rulesVersion) {
      diffs += "rulesVersion (stored=$rulesVersion, runtime=${other.rulesVersion})"
    }
    if (cardCatalogVersion != other.cardCatalogVersion) {
      diffs += "cardCatalogVersion (stored=$cardCatalogVersion, runtime=${other.cardCatalogVersion})"
    }
    if (cardCatalogHash != other.cardCatalogHash) {
      diffs += "cardCatalogHash (stored=$cardCatalogHash, runtime=${other.cardCatalogHash})"
    }
    if (instructionSetVersion != other.instructionSetVersion) {
      diffs += "instructionSetVersion (stored=$instructionSetVersion, runtime=${other.instructionSetVersion})"
    }
    if (vmVersion != other.vmVersion) {
      diffs += "vmVersion (stored=$vmVersion, runtime=${other.vmVersion})"
    }
    if (randomAlgorithm != other.randomAlgorithm) {
      diffs += "randomAlgorithm (stored=$randomAlgorithm, runtime=${other.randomAlgorithm})"
    }
    if (gameFormatId != other.gameFormatId) {
      diffs += "gameFormatId (stored=$gameFormatId, runtime=${other.gameFormatId})"
    }
    return diffs
  }
}

@Serializable
data class GameSetupState(
  val step: GameSetupStep, val pendingMulliganPlayers: Set<PlayerId>,
  val completedMulligans: Set<PlayerId>, val initialEffectsProgress: InitialEffectsProgress? = null,
)

data class GameCreationResult(val initialState: GameState, val initialEvents: List<GameEvent>)

@Serializable
data class AbilityUsageKey(val cardObjectId: CardObjectId, val abilityId: AbilityId, val turnNumber: Int)

@Serializable
data class GameState(
  val id: GameId, val status: GameStatus,
  val players: Map<PlayerId, PlayerState>, val playerOrder: List<PlayerId>,
  val firstPlayerId: PlayerId, val chooserPlayerId: PlayerId,
  val cards: Map<CardInstanceId, CardInstance>, val donAttachments: List<DonAttachment>,
  val turn: TurnState?, val battle: BattleState?,
  val activeEffects: List<ActiveEffect>,
  val activePrograms: List<ProgramFrame> = emptyList(),
  val pendingChoices: List<PendingChoice> = emptyList(),
  val setup: GameSetupState? = null,
  val abilityUsage: Map<AbilityUsageKey, Int> = emptyMap(),
  val randomState: RandomState, val lastEventSequence: Long, val result: GameResult?,
)