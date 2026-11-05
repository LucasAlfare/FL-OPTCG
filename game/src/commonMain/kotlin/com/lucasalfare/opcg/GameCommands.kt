package com.lucasalfare.opcg

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameCommand {
  val playerId: PlayerId
}

@Serializable
data class PlayCardCommand(
  override val playerId: PlayerId,
  val cardId: CardInstanceId,
) : GameCommand

@Serializable
data class ActivateAbilityCommand(
  override val playerId: PlayerId,
  val sourceCardId: CardInstanceId,
  val abilityId: AbilityId,
) : GameCommand

@Serializable
data class GiveDonCommand(
  override val playerId: PlayerId,
  val donId: CardInstanceId,
  val targetId: CardInstanceId,
) : GameCommand

@Serializable
data class DeclareAttackCommand(
  override val playerId: PlayerId,
  val attackerId: CardInstanceId,
  val targetId: BattleTarget,
) : GameCommand

@Serializable
data class ActivateBlockerCommand(
  override val playerId: PlayerId,
  val blockerId: CardInstanceId,
) : GameCommand

@Serializable
data class UseCharacterCounterCommand(
  override val playerId: PlayerId,
  val cardId: CardInstanceId,
  val targetId: CardInstanceId,
) : GameCommand

@Serializable
data class UseEventCounterCommand(
  override val playerId: PlayerId,
  val cardId: CardInstanceId,
) : GameCommand

@Serializable
data class EndMainPhaseCommand(
  override val playerId: PlayerId,
) : GameCommand

@Serializable
data class SubmitChoiceCommand(
  override val playerId: PlayerId,
  val choiceId: ChoiceId,
  val answer: ChoiceAnswer,
) : GameCommand

@Serializable
data class ResolveMulliganCommand(
  override val playerId: PlayerId,
  val redraw: Boolean,
) : GameCommand

@Serializable
data class ConcedeCommand(
  override val playerId: PlayerId,
) : GameCommand

@Serializable
data class PassBattleStepCommand(
  override val playerId: PlayerId,
) : GameCommand

data class DeckList(
  val leader: CardDefinitionId,
  val cards: List<CardDefinitionId>,
  val donDeck: List<CardDefinitionId>,
)

data class PlayerDeck(
  val playerId: PlayerId,
  val deckList: DeckList,
)

data class GameFormat(
  val id: String,
  val leaderCount: Int,
  val mainDeckSize: Int,
  val donDeckSize: Int,
  val maximumCopies: Int,
  val enforceLeaderColors: Boolean,
  val bannedCardNumbers: Set<String>,
  val restrictedCardNumbers: Map<String, Int>,
) {
  init {
    require(id.isNotBlank()) { "GameFormat.id must not be blank" }
    require(leaderCount >= 1) { "GameFormat.leaderCount must be >= 1, got $leaderCount" }
    require(mainDeckSize >= 1) { "GameFormat.mainDeckSize must be >= 1, got $mainDeckSize" }
    require(donDeckSize >= 0) { "GameFormat.donDeckSize must be >= 0, got $donDeckSize" }
    require(maximumCopies >= 1) { "GameFormat.maximumCopies must be >= 1, got $maximumCopies" }
  }
}

data class DeckViolation(
  val code: String,
  val message: String,
  val cardNumber: String?,
  val expected: Int?,
  val actual: Int?,
)

data class DeckValidationResult(
  val valid: Boolean,
  val violations: List<DeckViolation>,
)

data class GameSetup(
  val gameId: GameId,
  val players: List<PlayerDeck>,
  val format: GameFormat,
  val firstPlayerId: PlayerId,
  val chooserPlayerId: PlayerId,
  val randomState: RandomState,
)