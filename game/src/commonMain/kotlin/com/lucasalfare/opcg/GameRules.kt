package com.lucasalfare.opcg

data class RuleViolation(
  val code: String,
  val message: String,
)

data class ValidationResult(
  val valid: Boolean,
  val violations: List<RuleViolation>,
)

data class ProgramRequest(
  val executionId: ExecutionId,
  val program: Program,
  val context: EffectContext,
)

data class GameTransition(
  val events: List<GameEvent>,
  val programsToRun: List<ProgramRequest>,
  val choiceToResume: PlayerChoice? = null,
)

class RulesEngine(
  private val cardCatalog: CardCatalog,
) : InstructionExecutor, ProgramEvaluator {

  private val turnController = TurnController()
  private val mainPhaseController = MainPhaseController()
  private val battleResolver = BattleResolver()
  private val damageProcessor = DamageProcessor()
  private val costProcessor = CostProcessor()
  private val effectScheduler = EffectScheduler()
  private val continuousEffectProcessor = ContinuousEffectProcessor()
  private val replacementProcessor = ReplacementProcessor()
  private val ruleProcessor = RuleProcessor()
  private val informationManager = InformationManager()
  private val loopProcessor = LoopProcessor()
  private val deckValidator = DeckValidator(cardCatalog)
  private val gameFactory = GameFactory(cardCatalog)

  fun validate(state: GameState, command: GameCommand): ValidationResult {
    throw UnsupportedOperationException("Not implemented")
  }

  fun process(state: GameState, command: GameCommand): GameTransition {
    throw UnsupportedOperationException("Not implemented")
  }

  fun processEvents(state: GameState, events: List<GameEvent>): GameTransition {
    throw UnsupportedOperationException("Not implemented")
  }

  fun processAutomaticRules(state: GameState): GameTransition {
    throw UnsupportedOperationException("Not implemented")
  }

  fun createInitialState(setup: GameSetup): GameCreationResult {
    return gameFactory.create(setup)
  }

  override fun execute(
    instruction: Instruction,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun evaluateValue(
    expression: ValueExpr,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<StoredValue> {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun evaluateCondition(
    condition: Condition,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<Boolean> {
    throw UnsupportedOperationException("Not implemented")
  }
}

internal class DeckValidator(
  private val cardCatalog: CardCatalog,
) {

  fun validate(deck: DeckList, format: GameFormat): DeckValidationResult {
    val violations = mutableListOf<DeckViolation>()

    val leaderDef = cardCatalog.get(deck.leader)
    if (leaderDef == null) {
      violations += DeckViolation(
        code = "UNKNOWN_CARD",
        message = "Leader definition not found in catalog",
        cardNumber = null,
        expected = null,
        actual = null,
      )
    } else {
      if (leaderDef.category != CardCategory.LEADER) {
        violations += DeckViolation(
          code = "INVALID_LEADER_CATEGORY",
          message = "Leader must have LEADER category",
          cardNumber = leaderDef.number,
          expected = null,
          actual = null,
        )
      }
      if (format.bannedCardNumbers.contains(leaderDef.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "Leader card is banned",
          cardNumber = leaderDef.number,
          expected = null,
          actual = null,
        )
      }
    }

    val mainSize = deck.cards.size
    if (mainSize != format.mainDeckSize) {
      violations += DeckViolation(
        code = "MAIN_DECK_SIZE",
        message = "Main deck must contain exactly ${format.mainDeckSize} cards",
        cardNumber = null,
        expected = format.mainDeckSize,
        actual = mainSize,
      )
    }

    val donSize = deck.donDeck.size
    if (donSize != format.donDeckSize) {
      violations += DeckViolation(
        code = "DON_DECK_SIZE",
        message = "DON!! deck must contain exactly ${format.donDeckSize} cards",
        cardNumber = null,
        expected = format.donDeckSize,
        actual = donSize,
      )
    }

    val allowedMainCategories = setOf(CardCategory.CHARACTER, CardCategory.EVENT, CardCategory.STAGE)
    val mainNumberCounts = mutableMapOf<String, Int>()
    val mainColors = mutableSetOf<CardColor>()

    for (defId in deck.cards) {
      val def = cardCatalog.get(defId)
      if (def == null) {
        violations += DeckViolation(
          code = "UNKNOWN_CARD",
          message = "Card definition not found in catalog",
          cardNumber = null,
          expected = null,
          actual = null,
        )
        continue
      }
      if (def.category !in allowedMainCategories) {
        violations += DeckViolation(
          code = "INVALID_MAIN_CATEGORY",
          message = "Main deck may only contain Character, Event or Stage",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
      mainColors += def.colors
      mainNumberCounts[def.number] = (mainNumberCounts[def.number] ?: 0) + 1
      if (format.bannedCardNumbers.contains(def.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "Card is banned",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
    }

    for ((number, count) in mainNumberCounts) {
      val restrictedLimit = format.restrictedCardNumbers[number]
      val maxAllowed = restrictedLimit ?: format.maximumCopies
      if (count > maxAllowed) {
        violations += DeckViolation(
          code = if (restrictedLimit != null) "RESTRICTED_COPIES" else "MAXIMUM_COPIES",
          message = "Too many copies of card $number",
          cardNumber = number,
          expected = maxAllowed,
          actual = count,
        )
      }
    }

    if (format.enforceLeaderColors && leaderDef != null) {
      val excess = mainColors - leaderDef.colors
      if (excess.isNotEmpty()) {
        violations += DeckViolation(
          code = "LEADER_COLOR_MISMATCH",
          message = "Main deck colors must be contained in leader colors",
          cardNumber = leaderDef.number,
          expected = null,
          actual = null,
        )
      }
    }

    val donNumberCounts = mutableMapOf<String, Int>()
    for (defId in deck.donDeck) {
      val def = cardCatalog.get(defId)
      if (def == null) {
        violations += DeckViolation(
          code = "UNKNOWN_CARD",
          message = "DON!! definition not found in catalog",
          cardNumber = null,
          expected = null,
          actual = null,
        )
        continue
      }
      if (def.category != CardCategory.DON) {
        violations += DeckViolation(
          code = "INVALID_DON_CATEGORY",
          message = "DON!! deck may only contain DON!! cards",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
      donNumberCounts[def.number] = (donNumberCounts[def.number] ?: 0) + 1
      if (format.bannedCardNumbers.contains(def.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "DON!! card is banned",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
    }

    for ((number, count) in donNumberCounts) {
      val restrictedLimit = format.restrictedCardNumbers[number]
      val maxAllowed = restrictedLimit ?: format.maximumCopies
      if (count > maxAllowed) {
        violations += DeckViolation(
          code = if (restrictedLimit != null) "RESTRICTED_COPIES" else "MAXIMUM_COPIES",
          message = "Too many copies of DON!! card $number",
          cardNumber = number,
          expected = maxAllowed,
          actual = count,
        )
      }
    }

    return DeckValidationResult(valid = violations.isEmpty(), violations = violations)
  }
}

internal class GameFactory(
  private val cardCatalog: CardCatalog,
) {

  private val deckValidator = DeckValidator(cardCatalog)

  fun create(setup: GameSetup): GameCreationResult {
    require(setup.players.size == 2) { "Exactly two players are required" }
    val playerIds = setup.players.map { it.playerId }
    require(playerIds.distinct().size == 2) { "Players must be distinct" }
    require(setup.firstPlayerId in playerIds) { "firstPlayerId must belong to a player" }
    require(setup.chooserPlayerId in playerIds) { "chooserPlayerId must belong to a player" }

    for (playerDeck in setup.players) {
      val result = deckValidator.validate(playerDeck.deckList, setup.format)
      if (!result.valid) {
        val detail = result.violations.joinToString("; ") { it.message }
        throw IllegalArgumentException("Invalid deck for ${playerDeck.playerId.value}: $detail")
      }
    }

    val gameId = setup.gameId
    val cards = mutableMapOf<CardInstanceId, CardInstance>()
    val players = mutableMapOf<PlayerId, PlayerState>()
    var sequenceCounter = 0L
    var randomState = setup.randomState
    val events = mutableListOf<GameEvent>()

    sequenceCounter += 1
    events += GameEvent(
      id = EventId("${gameId.value}:$sequenceCounter"),
      sequence = sequenceCounter,
      type = GameEventType.GAME_CREATED,
      sourceCardId = null,
      payload = GameEventPayload.GameCreatedPayload(
        playerIds = playerIds,
        firstPlayerId = setup.firstPlayerId,
        chooserPlayerId = setup.chooserPlayerId,
        formatId = setup.format.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    for (playerDeck in setup.players) {
      val playerId = playerDeck.playerId
      val deckList = playerDeck.deckList

      val leaderInstance = createInstance(
        gameId = gameId,
        playerId = playerId,
        area = "leader",
        position = 0,
        definitionId = deckList.leader,
        orientation = CardOrientation.ACTIVE,
        faceState = FaceState.FACE_UP,
        visibility = CardVisibility.PUBLIC,
      )
      cards[leaderInstance.id] = leaderInstance

      val mainInstances = deckList.cards.mapIndexed { index, defId ->
        createInstance(
          gameId = gameId,
          playerId = playerId,
          area = "deck",
          position = index,
          definitionId = defId,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ).also { cards[it.id] = it }
      }

      val donInstances = deckList.donDeck.mapIndexed { index, defId ->
        createInstance(
          gameId = gameId,
          playerId = playerId,
          area = "don_deck",
          position = index,
          definitionId = defId,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ).also { cards[it.id] = it }
      }

      val mainOrder = mainInstances.map { it.id }.toMutableList()
      val (shuffledMain, randomAfterMain) = fisherYates(mainOrder, randomState)
      randomState = randomAfterMain
      sequenceCounter += 1
      events += GameEvent(
        id = EventId("${gameId.value}:$sequenceCounter"),
        sequence = sequenceCounter,
        type = GameEventType.DECK_SHUFFLED,
        sourceCardId = null,
        payload = GameEventPayload.DeckShuffledPayload(
          playerId = playerId,
          zone = ZoneType.DECK,
          resultingOrder = shuffledMain,
          randomStateAfter = randomState,
        ),
        visibility = EventVisibility.PUBLIC,
      )

      val donOrder = donInstances.map { it.id }.toMutableList()
      val (shuffledDon, randomAfterDon) = fisherYates(donOrder, randomState)
      randomState = randomAfterDon
      sequenceCounter += 1
      events += GameEvent(
        id = EventId("${gameId.value}:$sequenceCounter"),
        sequence = sequenceCounter,
        type = GameEventType.DECK_SHUFFLED,
        sourceCardId = null,
        payload = GameEventPayload.DeckShuffledPayload(
          playerId = playerId,
          zone = ZoneType.DON_DECK,
          resultingOrder = shuffledDon,
          randomStateAfter = randomState,
        ),
        visibility = EventVisibility.PUBLIC,
      )

      players[playerId] = PlayerState(
        id = playerId,
        deck = ZoneState(mainInstances.map { it.id }),
        donDeck = ZoneState(donInstances.map { it.id }),
        hand = ZoneState(emptyList()),
        trash = ZoneState(emptyList()),
        leader = ZoneState(listOf(leaderInstance.id)),
        characters = ZoneState(emptyList()),
        stage = ZoneState(emptyList()),
        cost = ZoneState(emptyList()),
        life = ZoneState(emptyList()),
      )
    }

    val initialState = GameState(
      id = gameId,
      status = GameStatus.SETUP,
      players = players,
      playerOrder = playerIds,
      firstPlayerId = setup.firstPlayerId,
      chooserPlayerId = setup.chooserPlayerId,
      cards = cards,
      donAttachments = emptyList(),
      turn = null,
      battle = null,
      activeEffects = emptyList(),
      activePrograms = emptyList(),
      pendingChoices = emptyList(),
      setup = GameSetupState(
        step = GameSetupStep.INITIAL_EFFECTS,
        pendingMulliganPlayers = emptySet(),
        completedMulligans = emptySet(),
      ),
      randomState = setup.randomState,
      lastEventSequence = 0,
      result = null,
    )

    return GameCreationResult(initialState = initialState, initialEvents = events)
  }

  private fun createInstance(
    gameId: GameId,
    playerId: PlayerId,
    area: String,
    position: Int,
    definitionId: CardDefinitionId,
    orientation: CardOrientation,
    faceState: FaceState,
    visibility: CardVisibility,
  ): CardInstance {
    val key = "${gameId.value}:${playerId.value}:$area:$position"
    return CardInstance(
      id = CardInstanceId(key),
      definitionId = definitionId,
      ownerId = playerId,
      controllerId = playerId,
      objectId = CardObjectId(key),
      orientation = orientation,
      faceState = faceState,
      visibility = visibility,
    )
  }

  private fun fisherYates(
    list: MutableList<CardInstanceId>,
    start: RandomState,
  ): Pair<List<CardInstanceId>, RandomState> {
    var state = start
    for (i in list.lastIndex downTo 1) {
      val (j, next) = nextBounded(state, i + 1)
      state = next
      val tmp = list[i]
      list[i] = list[j]
      list[j] = tmp
    }
    return list.toList() to state
  }

  private fun nextBounded(state: RandomState, bound: Int): Pair<Int, RandomState> {
    require(bound > 0)
    if (bound == 1) {
      val (_, next) = nextULong(state)
      return 0 to next
    }
    val limit = (ULong.MAX_VALUE / bound.toULong()) * bound.toULong()
    var current = state
    while (true) {
      val (value, next) = nextULong(current)
      current = next
      if (value < limit) {
        return (value % bound.toULong()).toInt() to current
      }
    }
  }

  private fun nextULong(state: RandomState): Pair<ULong, RandomState> {
    val newState = state.state + 0x9E3779B97F4A7C15uL
    var z = newState
    z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
    z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
    val result = z xor (z shr 31)
    val next = state.copy(state = newState, drawCount = state.drawCount + 1)
    return result to next
  }
}

internal class TurnController

internal class MainPhaseController

internal class BattleResolver

internal class DamageProcessor

internal class CostProcessor

internal class EffectScheduler

internal class ContinuousEffectProcessor

internal class ReplacementProcessor

internal class RuleProcessor

internal class InformationManager

internal class LoopProcessor