package com.lucasalfare.opcg.rules

import com.lucasalfare.opcg.*
import kotlinx.serialization.Serializable

@Serializable
data class RuleViolation(
  val code: String,
  val message: String,
)

data class ValidationResult(
  val valid: Boolean,
  val violations: List<RuleViolation>,
)

@Serializable
data class ProgramRequest(
  val executionId: ExecutionId,
  val program: Program,
  val context: EffectContext,
  val initialVariables: Map<VariableId, StoredValue> = emptyMap(),
)

data class GameTransition(
  val events: List<GameEvent>,
  val programsToRun: List<ProgramRequest>,
  val choiceToResume: PlayerChoice? = null,
)

internal class DeckValidator(
  private val cardCatalog: CardCatalog,
) {

  private val programValidator = CardProgramValidator()

  fun validate(deck: DeckList, format: GameFormat): DeckValidationResult = validate(deck, format, emptyList())

  fun validate(
    deck: DeckList,
    format: GameFormat,
    modifiers: List<DeckRuleModifier>,
  ): DeckValidationResult {
    val effectiveFormat = applyModifiers(format, modifiers)
    val violations = mutableListOf<DeckViolation>()

    if (effectiveFormat.leaderCount != 1) {
      violations += DeckViolation(
        code = "UNSUPPORTED_LEADER_COUNT",
        message = "Engine currently supports exactly one leader per deck, but format ${effectiveFormat.id} declares ${effectiveFormat.leaderCount}",
        cardNumber = null,
        expected = 1,
        actual = effectiveFormat.leaderCount,
      )
    }

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
      if (effectiveFormat.bannedCardNumbers.contains(leaderDef.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "Leader card is banned",
          cardNumber = leaderDef.number,
          expected = null,
          actual = null,
        )
      }
      violations += validatePrograms(leaderDef)
    }

    val mainSize = deck.cards.size
    if (mainSize != effectiveFormat.mainDeckSize) {
      violations += DeckViolation(
        code = "MAIN_DECK_SIZE",
        message = "Main deck must contain exactly ${effectiveFormat.mainDeckSize} cards",
        cardNumber = null,
        expected = effectiveFormat.mainDeckSize,
        actual = mainSize,
      )
    }

    val donSize = deck.donDeck.size
    if (donSize != effectiveFormat.donDeckSize) {
      violations += DeckViolation(
        code = "DON_DECK_SIZE",
        message = "DON!! deck must contain exactly ${effectiveFormat.donDeckSize} cards",
        cardNumber = null,
        expected = effectiveFormat.donDeckSize,
        actual = donSize,
      )
    }

    val allowedMainCategories = setOf(CardCategory.CHARACTER, CardCategory.EVENT, CardCategory.STAGE)
    val mainNumberCounts = mutableMapOf<String, Int>()
    val mainColors = mutableSetOf<CardColor>()
    val validatedMainDefs = mutableSetOf<CardDefinitionId>()

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
      if (effectiveFormat.bannedCardNumbers.contains(def.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "Card is banned",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
      if (validatedMainDefs.add(defId)) {
        violations += validatePrograms(def)
      }
    }

    for ((number, count) in mainNumberCounts) {
      val restrictedLimit = effectiveFormat.restrictedCardNumbers[number]
      val maxAllowed = restrictedLimit ?: effectiveFormat.maximumCopies
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

    if (effectiveFormat.enforceLeaderColors && leaderDef != null) {
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
    val validatedDonDefs = mutableSetOf<CardDefinitionId>()

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
      if (effectiveFormat.bannedCardNumbers.contains(def.number)) {
        violations += DeckViolation(
          code = "BANNED_CARD",
          message = "DON!! card is banned",
          cardNumber = def.number,
          expected = null,
          actual = null,
        )
      }
      if (validatedDonDefs.add(defId)) {
        violations += validatePrograms(def)
      }
    }

    for ((number, count) in donNumberCounts) {
      val restrictedLimit = effectiveFormat.restrictedCardNumbers[number]
      val maxAllowed = restrictedLimit ?: effectiveFormat.maximumCopies
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

  private fun validatePrograms(def: CardDefinition): List<DeckViolation> {
    val issues = programValidator.validateDefinition(def).issues
    if (issues.isEmpty()) return emptyList()
    return issues.map { issue ->
      DeckViolation(
        code = "INVALID_PROGRAM_${issue.code}",
        message = "Card ${def.number} program invalid at ${
          if (issue.path.isEmpty()) "root" else issue.path.joinToString(".")
        }: ${issue.message}",
        cardNumber = def.number,
        expected = null,
        actual = null,
      )
    }
  }

  private fun applyModifiers(format: GameFormat, modifiers: List<DeckRuleModifier>): GameFormat {
    if (modifiers.isEmpty()) return format
    var current = format
    val restricted = current.restrictedCardNumbers.toMutableMap()
    for (modifier in modifiers) {
      current = when (modifier.property) {
        DeckRuleProperty.MAIN_DECK_SIZE -> {
          val value = applyNumeric(modifier, current.mainDeckSize)
          current.copy(mainDeckSize = value)
        }

        DeckRuleProperty.DON_DECK_SIZE -> {
          val value = applyNumeric(modifier, current.donDeckSize)
          current.copy(donDeckSize = value)
        }

        DeckRuleProperty.MAXIMUM_COPIES -> {
          val value = applyNumeric(modifier, current.maximumCopies)
          val targetCard = modifier.cardNumber
          if (targetCard != null) {
            restricted[targetCard] = value
            current.copy(restrictedCardNumbers = restricted.toMap())
          } else {
            current.copy(maximumCopies = value)
          }
        }

        DeckRuleProperty.ALLOWED_COLORS -> {
          if (modifier.allowedColors != null) {
            current.copy(enforceLeaderColors = false)
          } else current
        }
      }
    }
    return current
  }

  private fun applyNumeric(modifier: DeckRuleModifier, base: Int): Int {
    val operation = modifier.numericOperation ?: return base
    val value = (modifier.numericValue as? ValueExpr.NumberValue)?.value ?: return base
    return when (operation) {
      NumericOperation.ADD -> base + value
      NumericOperation.SUBTRACT -> base - value
      NumericOperation.SET -> value
    }
  }
}

internal class GameFactory(
  private val cardCatalog: CardCatalog,
) {

  private val deckValidator = DeckValidator(cardCatalog)

  fun create(setup: GameSetup): GameCreationResult = create(setup, emptyList())

  fun create(setup: GameSetup, deckModifiers: List<DeckRuleModifier>): GameCreationResult {
    require(setup.players.size == 2) { "Exactly two players are required" }
    val playerIds = setup.players.map { it.playerId }
    require(playerIds.distinct().size == 2) { "Players must be distinct" }
    require(setup.firstPlayerId in playerIds) { "firstPlayerId must belong to a player" }
    require(setup.chooserPlayerId in playerIds) { "chooserPlayerId must belong to a player" }

    for (playerDeck in setup.players) {
      val result = deckValidator.validate(playerDeck.deckList, setup.format, deckModifiers)
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

internal object RandomSupport {
  fun fisherYates(
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

  fun nextBounded(state: RandomState, bound: Int): Pair<Int, RandomState> {
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

  fun nextULong(state: RandomState): Pair<ULong, RandomState> {
    val newState = state.state + 0x9E3779B97F4A7C15uL
    var z = newState
    z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
    z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
    val result = z xor (z shr 31)
    val next = state.copy(state = newState, drawCount = state.drawCount + 1)
    return result to next
  }
}

internal class BattleResolver(
  private val cardCatalog: CardCatalog,
) {
  private val costResolver = CostResolver()

  fun validateDeclareAttack(
    state: GameState,
    command: DeclareAttackCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val violations = mutableListOf<RuleViolation>()
    val restrictions = continuous.isActionRestricted(state, RuleAction.DECLARE_ATTACK, command.attackerId)
    if (restrictions.isNotEmpty()) {
      return ValidationResult(false, restrictions)
    }
    val turn = state.turn
    if (turn == null || turn.phase != GamePhase.MAIN || turn.playerId != command.playerId) {
      violations += RuleViolation("NOT_MAIN_PHASE", "Attack only during your Main Phase")
    }
    if (state.battle != null) {
      violations += RuleViolation("BATTLE_IN_PROGRESS", "A battle is already in progress")
    }
    if (turn != null && turn.number == 1 && command.playerId == state.firstPlayerId) {
      violations += RuleViolation("FIRST_TURN", "First player cannot attack on the first turn")
    }

    val attacker = state.cards[command.attackerId] ?: return ValidationResult(
      false, violations + RuleViolation("UNKNOWN_ATTACKER", "Attacker not found")
    )
    val attackerController = continuous.effectiveController(state, command.attackerId) ?: attacker.controllerId
    if (attackerController != command.playerId) {
      violations += RuleViolation("NOT_CONTROLLER", "Attacker is not controlled by the player")
    }
    if (attacker.orientation != CardOrientation.ACTIVE) {
      violations += RuleViolation("ATTACKER_RESTED", "Attacker must be active")
    }

    val player = state.players[command.playerId]
    val inLeader = player != null && command.attackerId in player.leader.cards
    val inCharacter = player != null && command.attackerId in player.characters.cards
    if (!inLeader && !inCharacter) {
      violations += RuleViolation("INVALID_ATTACKER_ZONE", "Attacker must be Leader or Character")
    }

    var targetIsOpponentLeader = false

    when (val target = command.targetId) {
      is BattleTarget.PlayerTarget -> {
        targetIsOpponentLeader = true
        violations += RuleViolation("INVALID_TARGET", "Direct player target is not used; target Leader or Character")
      }

      is BattleTarget.CardTarget -> {
        val targetCard = state.cards[target.cardInstanceId]
        if (targetCard == null) {
          violations += RuleViolation("UNKNOWN_TARGET", "Target card not found")
        } else {
          val targetController = continuous.effectiveController(state, target.cardInstanceId) ?: targetCard.controllerId
          if (targetController == command.playerId) {
            violations += RuleViolation("FRIENDLY_TARGET", "Cannot attack own cards")
          }
          val opponent = state.players[targetController]
          val isOppLeader = opponent != null && target.cardInstanceId in opponent.leader.cards
          val isOppCharacter = opponent != null && target.cardInstanceId in opponent.characters.cards
          targetIsOpponentLeader = isOppLeader
          if (!isOppLeader && !isOppCharacter) {
            violations += RuleViolation("INVALID_TARGET_ZONE", "Target must be opponent Leader or Character")
          }
          if (isOppCharacter && targetCard.orientation != CardOrientation.RESTED) {
            val permitted = canAttackActiveCharacter(
              state, command, target.cardInstanceId, targetCard, targetController, continuous,
            )
            if (!permitted) {
              violations += RuleViolation("TARGET_NOT_RESTED", "Character targets must be rested")
            }
          }
        }
      }
    }

    if (inCharacter && turn != null && attacker.enteredPlayTurn == turn.number) {
      val keywords = continuous.effectiveCharacteristics(state, command.attackerId)?.keywords.orEmpty()
      val hasRush = KeywordEffect.RUSH in keywords
      val hasRushCharacter = KeywordEffect.RUSH_CHARACTER in keywords
      when {
        hasRush -> Unit
        hasRushCharacter -> if (targetIsOpponentLeader) {
          violations += RuleViolation(
            "RUSH_CHARACTER_NO_LEADER",
            "Character with Rush: Character cannot attack the opponent's Leader on the turn it entered the field",
          )
        }

        else -> violations += RuleViolation(
          "RUSH_REQUIRED",
          "Character cannot attack on the turn it entered the field without Rush",
        )
      }
    }

    return ValidationResult(violations.isEmpty(), violations)
  }

  private fun canAttackActiveCharacter(
    state: GameState,
    command: DeclareAttackCommand,
    targetId: CardInstanceId,
    targetCard: CardInstance,
    targetController: PlayerId,
    continuous: ContinuousEffectProcessor,
  ): Boolean {
    if (targetController == command.playerId) return false
    if (targetCard.orientation != CardOrientation.ACTIVE) return false
    if (continuous.isActionRestricted(state, RuleAction.ATTACK_ACTIVE_CHARACTER, command.attackerId).isNotEmpty()) {
      return false
    }
    return continuous.hasPermissionFor(state, RuleAction.ATTACK_ACTIVE_CHARACTER, command.attackerId, targetId)
  }

  fun validatePassBattleStep(
    state: GameState,
    command: PassBattleStepCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val battle =
      state.battle ?: return ValidationResult(false, listOf(RuleViolation("NO_BATTLE", "No battle in progress")))
    val expected = expectedBattleActor(state, battle, continuous)
    if (expected != null && command.playerId != expected) {
      return ValidationResult(
        false,
        listOf(RuleViolation("WRONG_ACTOR", "Player cannot pass in this battle window")),
      )
    }
    return ValidationResult(true, emptyList())
  }

  private fun expectedBattleActor(
    state: GameState,
    battle: BattleState,
    continuous: ContinuousEffectProcessor,
  ): PlayerId? {
    val attacker = state.cards[battle.attackerId]?.let { card ->
      continuous.effectiveController(state, battle.attackerId) ?: card.controllerId
    }
    val defender = when (val target = battle.currentTarget) {
      is BattleTarget.CardTarget -> state.cards[target.cardInstanceId]?.let { card ->
        continuous.effectiveController(state, target.cardInstanceId) ?: card.controllerId
      }

      is BattleTarget.PlayerTarget -> target.playerId
    }
    return when (battle.step) {
      BattleStep.ATTACK -> attacker
      BattleStep.BLOCK -> defender
      BattleStep.COUNTER -> defender
      BattleStep.DAMAGE -> attacker
      BattleStep.END -> attacker
    }
  }

  fun processDeclareAttack(
    state: GameState,
    command: DeclareAttackCommand,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_STATE_CHANGED,
      sourceCardId = command.attackerId,
      payload = GameEventPayload.CardStateChangedPayload(
        command.attackerId,
        CardOrientation.RESTED,
        state.cards[command.attackerId]?.faceState ?: FaceState.FACE_UP,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    val declared = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.ATTACK_DECLARED,
      sourceCardId = command.attackerId,
      payload = GameEventPayload.AttackDeclaredPayload(command.attackerId, command.targetId),
      visibility = EventVisibility.PUBLIC,
    )
    events += declared

    val auto = evaluator.processEvents(
      state.copy(
        battle = BattleState(
          attackerId = command.attackerId,
          originalTarget = command.targetId,
          currentTarget = command.targetId,
          step = BattleStep.ATTACK,
          blockerUsed = false,
          counterPower = 0,
        ),
      ),
      listOf(declared),
    )
    return GameTransition(events + auto.events, auto.programsToRun)
  }

  fun validateActivateBlocker(
    state: GameState,
    command: ActivateBlockerCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val restrictions = continuous.isActionRestricted(state, RuleAction.ACTIVATE_BLOCKER, command.blockerId)
    if (restrictions.isNotEmpty()) {
      return ValidationResult(false, restrictions)
    }
    val violations = mutableListOf<RuleViolation>()
    val battle = state.battle
    if (battle == null || battle.step != BattleStep.BLOCK) {
      violations += RuleViolation("NOT_BLOCK_STEP", "Blocker only during Block step")
    }
    if (battle?.blockerUsed == true) {
      violations += RuleViolation("BLOCKER_USED", "A Blocker was already used this battle")
    }
    val blocker = state.cards[command.blockerId]
    if (blocker == null) {
      return ValidationResult(false, violations + RuleViolation("UNKNOWN_BLOCKER", "Blocker not found"))
    }
    val controller = continuous.effectiveController(state, command.blockerId) ?: blocker.controllerId
    if (controller != command.playerId) {
      violations += RuleViolation("NOT_CONTROLLER", "Player does not control the Blocker")
    }
    if (blocker.orientation != CardOrientation.ACTIVE) {
      violations += RuleViolation("BLOCKER_RESTED", "Blocker must be active")
    }
    val player = state.players[command.playerId]
    if (player == null || command.blockerId !in player.characters.cards) {
      violations += RuleViolation("INVALID_BLOCKER_ZONE", "Blocker must be a Character")
    }
    val keywords = continuous.effectiveCharacteristics(state, command.blockerId)?.keywords.orEmpty()
    if (KeywordEffect.BLOCKER !in keywords) {
      violations += RuleViolation("NO_BLOCKER_KEYWORD", "Character does not have Blocker")
    }
    if (battle != null) {
      val attackerKeywords = continuous.effectiveCharacteristics(state, battle.attackerId)?.keywords.orEmpty()
      if (KeywordEffect.UNBLOCKABLE in attackerKeywords) {
        violations += RuleViolation("UNBLOCKABLE", "Attacker is Unblockable")
      }
      val attackerController = state.cards[battle.attackerId]?.let {
        continuous.effectiveController(state, battle.attackerId) ?: it.controllerId
      }
      if (attackerController == command.playerId) {
        violations += RuleViolation("ATTACKING_PLAYER", "Attacking player cannot block")
      }
    }
    return ValidationResult(violations.isEmpty(), violations)
  }

  fun processActivateBlocker(
    state: GameState,
    command: ActivateBlockerCommand,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    val battle = state.battle ?: return GameTransition(emptyList(), emptyList())
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_STATE_CHANGED,
      sourceCardId = command.blockerId,
      payload = GameEventPayload.CardStateChangedPayload(
        command.blockerId,
        CardOrientation.RESTED,
        state.cards[command.blockerId]?.faceState ?: FaceState.FACE_UP,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    val newTarget = BattleTarget.CardTarget(command.blockerId)
    seq += 1
    val blocked = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.BLOCK_ACTIVATED,
      sourceCardId = command.blockerId,
      payload = GameEventPayload.BlockActivatedPayload(
        blockerId = command.blockerId,
        previousTarget = battle.currentTarget,
        newTarget = newTarget,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    events += blocked

    val auto = evaluator.processEvents(state, listOf(blocked))
    return GameTransition(events + auto.events, auto.programsToRun)
  }

  fun validateCharacterCounter(
    state: GameState,
    command: UseCharacterCounterCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val restrictions = continuous.isActionRestricted(state, RuleAction.USE_COUNTER, command.cardId)
    if (restrictions.isNotEmpty()) {
      return ValidationResult(false, restrictions)
    }
    val violations = mutableListOf<RuleViolation>()
    val battle = state.battle
    if (battle == null || battle.step != BattleStep.COUNTER) {
      violations += RuleViolation("NOT_COUNTER_STEP", "Counter only during Counter step")
    }
    val card = state.cards[command.cardId]
    if (card == null) {
      return ValidationResult(false, violations + RuleViolation("UNKNOWN_CARD", "Counter card not found"))
    }
    if (card.ownerId != command.playerId) {
      violations += RuleViolation("NOT_OWNER", "Player does not own the card")
    }
    val player = state.players[command.playerId]
    if (player == null || command.cardId !in player.hand.cards) {
      violations += RuleViolation("NOT_IN_HAND", "Counter card must be in hand")
    }
    val def = cardCatalog.get(card.definitionId)
    if (def == null || def.category != CardCategory.CHARACTER) {
      violations += RuleViolation("NOT_CHARACTER", "Character Counter requires a Character card")
    }
    val counterValue = continuous.effectiveCharacteristics(state, command.cardId)?.counter ?: def?.counter
    if (counterValue == null || counterValue <= 0) {
      violations += RuleViolation("NO_COUNTER", "Card has no Counter value")
    }
    if (battle != null) {
      val legalTargets = counterTargets(state, battle)
      if (command.targetId !in legalTargets) {
        violations += RuleViolation("INVALID_COUNTER_TARGET", "Counter target is not legal")
      }
      val attackerController = state.cards[battle.attackerId]?.let {
        continuous.effectiveController(state, battle.attackerId) ?: it.controllerId
      }
      if (attackerController == command.playerId) {
        violations += RuleViolation("ATTACKING_PLAYER", "Attacking player cannot use Counter")
      }
    }
    return ValidationResult(violations.isEmpty(), violations)
  }

  fun processCharacterCounter(
    state: GameState,
    command: UseCharacterCounterCommand,
    continuous: ContinuousEffectProcessor,
  ): GameTransition {
    state.battle ?: return GameTransition(emptyList(), emptyList())
    val def =
      cardCatalog.get(state.cards[command.cardId]?.definitionId ?: return GameTransition(emptyList(), emptyList()))
    val power = continuous.effectiveCharacteristics(state, command.cardId)?.counter ?: def?.counter ?: 0
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_MOVED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CardMovedPayload(
        cardId = command.cardId,
        fromPlayerId = command.playerId,
        fromZone = ZoneType.HAND,
        toPlayerId = command.playerId,
        toZone = ZoneType.TRASH,
        destinationIndex = null,
        newObjectId = null,
        orientation = CardOrientation.NOT_APPLICABLE,
        faceState = FaceState.FACE_UP,
        visibility = CardVisibility.PUBLIC,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.COUNTER_ACTIVATED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CounterActivatedPayload(
        playerId = command.playerId,
        cardId = command.cardId,
        targetCardId = command.targetId,
        powerAdded = power,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return GameTransition(events, emptyList())
  }

  fun validateEventCounter(
    state: GameState,
    command: UseEventCounterCommand,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): ValidationResult {
    val restrictions = continuous.isActionRestricted(state, RuleAction.USE_COUNTER, command.cardId)
    if (restrictions.isNotEmpty()) {
      return ValidationResult(false, restrictions)
    }
    val violations = mutableListOf<RuleViolation>()
    val battle = state.battle
    if (battle == null || battle.step != BattleStep.COUNTER) {
      violations += RuleViolation("NOT_COUNTER_STEP", "Event Counter only during Counter step")
    }
    val card = state.cards[command.cardId]
    if (card == null) {
      return ValidationResult(false, violations + RuleViolation("UNKNOWN_CARD", "Event not found"))
    }
    if (card.ownerId != command.playerId) {
      violations += RuleViolation("NOT_OWNER", "Player does not own the Event")
    }
    val player = state.players[command.playerId]
    if (player == null || command.cardId !in player.hand.cards) {
      violations += RuleViolation("NOT_IN_HAND", "Event must be in hand")
    }
    val def = cardCatalog.get(card.definitionId)
    if (def == null || def.category != CardCategory.EVENT) {
      violations += RuleViolation("NOT_EVENT", "Card is not an Event")
    }
    val counterAbility = def?.abilities?.filterIsInstance<ActivatedAbilityDefinition>()
      ?.firstOrNull { it.timing == ActivationTiming.COUNTER_STEP }
    if (counterAbility == null) {
      violations += RuleViolation("NO_COUNTER_ABILITY", "Event has no Counter Step ability")
    } else if (costResolver.preview(state, command.playerId, counterAbility.cost, command.cardId, evaluator) == null) {
      violations += RuleViolation("COST_UNPAID", "Unable to pay Counter cost")
    }
    if (battle != null) {
      val attackerController = state.cards[battle.attackerId]?.let {
        continuous.effectiveController(state, battle.attackerId) ?: it.controllerId
      }
      if (attackerController == command.playerId) {
        violations += RuleViolation("ATTACKING_PLAYER", "Attacking player cannot use Event Counter")
      }
    }
    return ValidationResult(violations.isEmpty(), violations)
  }

  fun processEventCounter(
    state: GameState,
    command: UseEventCounterCommand,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    val card = state.cards[command.cardId] ?: return GameTransition(emptyList(), emptyList())
    val def = cardCatalog.get(card.definitionId) ?: return GameTransition(emptyList(), emptyList())
    val ability = def.abilities.filterIsInstance<ActivatedAbilityDefinition>()
      .firstOrNull { it.timing == ActivationTiming.COUNTER_STEP } ?: return GameTransition(emptyList(), emptyList())

    val payments =
      costResolver.preview(state, command.playerId, ability.cost, command.cardId, evaluator) ?: return GameTransition(
        emptyList(), emptyList()
      )
    val events = costResolver.events(state, command.playerId, payments).toMutableList()
    var seq = state.lastEventSequence + events.size
    val programs = mutableListOf<ProgramRequest>()

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_REVEALED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CardRevealedPayload(listOf(command.cardId), CardVisibility.PUBLIC),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_MOVED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CardMovedPayload(
        cardId = command.cardId,
        fromPlayerId = command.playerId,
        fromZone = ZoneType.HAND,
        toPlayerId = command.playerId,
        toZone = ZoneType.TRASH,
        destinationIndex = null,
        newObjectId = null,
        orientation = CardOrientation.NOT_APPLICABLE,
        faceState = FaceState.FACE_UP,
        visibility = CardVisibility.PUBLIC,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_ACTIVATED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CardActivatedPayload(command.cardId, command.playerId),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    val counterEvent = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.COUNTER_ACTIVATED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.CounterActivatedPayload(
        playerId = command.playerId,
        cardId = command.cardId,
        targetCardId = state.battle?.attackerId ?: command.cardId,
        powerAdded = 0,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    events += counterEvent

    seq += 1
    val activation = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = command.cardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = EffectId("${state.id.value}:counter:$seq"),
        sourceCardId = command.cardId,
        abilityId = ability.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    events += activation
    programs += ProgramRequest(
      executionId = ExecutionId(activation.id.value),
      program = ability.program,
      context = EffectContext(
        gameId = state.id,
        controllerId = command.playerId,
        sourceCardId = command.cardId,
        abilityId = ability.id,
        triggeringEvent = counterEvent,
      ),
    )

    return GameTransition(events, programs)
  }

  fun advanceStep(
    state: GameState,
    continuous: ContinuousEffectProcessor,
    damageProcessor: DamageProcessor,
    evaluator: RulesEngine,
    replacementProcessor: ReplacementProcessor,
  ): GameTransition {
    val battle = state.battle ?: return GameTransition(emptyList(), emptyList())
    if (!participantsPresent(state, battle)) {
      return endBattle(state, battle, BattleOutcome.INTERRUPTED)
    }
    return when (battle.step) {
      BattleStep.ATTACK -> moveToStep(state, BattleStep.BLOCK)
      BattleStep.BLOCK -> moveToStep(state, BattleStep.COUNTER)
      BattleStep.COUNTER -> moveToStep(state, BattleStep.DAMAGE)
      BattleStep.DAMAGE -> damageProcessor.processDamage(state, battle, continuous, evaluator, replacementProcessor)
      BattleStep.END -> endBattle(state, battle, BattleOutcome.NO_DAMAGE)
    }
  }

  fun checkInterrupted(state: GameState): GameTransition {
    val battle = state.battle ?: return GameTransition(emptyList(), emptyList())
    if (!participantsPresent(state, battle)) {
      return endBattle(state, battle, BattleOutcome.INTERRUPTED)
    }
    return GameTransition(emptyList(), emptyList())
  }

  private fun participantsPresent(state: GameState, battle: BattleState): Boolean {
    if (state.cards[battle.attackerId] == null) return false
    if (!inLeaderOrCharacter(state, battle.attackerId)) return false
    return when (val target = battle.currentTarget) {
      is BattleTarget.CardTarget -> {
        state.cards[target.cardInstanceId] != null && inLeaderOrCharacter(state, target.cardInstanceId)
      }

      is BattleTarget.PlayerTarget -> state.players.containsKey(target.playerId)
    }
  }

  private fun inLeaderOrCharacter(state: GameState, cardId: CardInstanceId): Boolean {
    for (player in state.players.values) {
      if (cardId in player.leader.cards || cardId in player.characters.cards) return true
    }
    return false
  }

  private fun counterTargets(state: GameState, battle: BattleState): Set<CardInstanceId> {
    val targets = mutableSetOf(battle.attackerId)
    when (val t = battle.currentTarget) {
      is BattleTarget.CardTarget -> targets += t.cardInstanceId
      is BattleTarget.PlayerTarget -> Unit
    }
    return targets
  }

  private fun moveToStep(state: GameState, step: BattleStep): GameTransition {
    val battle = state.battle ?: return GameTransition(emptyList(), emptyList())
    if (battle.step == step) return GameTransition(emptyList(), emptyList())
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.BATTLE_STEP_CHANGED,
      sourceCardId = battle.attackerId,
      payload = GameEventPayload.BattleStepChangedPayload(
        previousStep = battle.step,
        newStep = step,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return GameTransition(listOf(event), emptyList())
  }

  private fun endBattle(state: GameState, battle: BattleState, outcome: BattleOutcome): GameTransition {
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.BATTLE_ENDED,
      sourceCardId = battle.attackerId,
      payload = GameEventPayload.BattleEndedPayload(
        attackerId = battle.attackerId,
        finalTarget = battle.currentTarget,
        outcome = outcome,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return GameTransition(listOf(event), emptyList())
  }
}

internal class EffectScheduler(
  private val cardCatalog: CardCatalog,
  private val continuous: ContinuousEffectProcessor,
  private val evaluator: RulesEngine,
) {
  private val costResolver = CostResolver()

  private val ignoredTriggerTypes = setOf(
    GameEventType.PROGRAM_FRAME_CHANGED,
    GameEventType.PENDING_CHOICE_CHANGED,
    GameEventType.MODIFIER_ADDED,
    GameEventType.MODIFIER_EXPIRED,
  )

  fun validateActivate(
    state: GameState,
    command: ActivateAbilityCommand,
    evaluator: RulesEngine,
  ): ValidationResult {
    val result = resolveActivatedAbility(
      state, command.playerId, command.sourceCardId, command.abilityId, null, evaluator, forManual = true,
    )
    return when (result) {
      is AbilityResolveResult.Ok -> ValidationResult(true, emptyList())
      is AbilityResolveResult.Invalid -> ValidationResult(false, result.violations)
    }
  }

  fun processActivate(
    state: GameState,
    command: ActivateAbilityCommand,
    evaluator: RulesEngine,
  ): GameTransition {
    val resolved = resolveActivatedAbility(
      state, command.playerId, command.sourceCardId, command.abilityId, null, evaluator, forManual = true,
    )
    if (resolved is AbilityResolveResult.Invalid) {
      return GameTransition(emptyList(), emptyList())
    }
    resolved as AbilityResolveResult.Ok
    return activateAbility(state, resolved, evaluator)
  }

  fun executeActivateInstruction(
    state: GameState,
    instruction: ActivateAbility,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val matches = findActivatedAbilities(
      state, instruction.selector, instruction.timing, context.controllerId, evaluator,
    )
    if (matches.isEmpty()) {
      return InstructionExecutionResult.Completed(null, emptyList())
    }
    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    for ((cardId, ability) in matches) {
      val card = state.cards[cardId] ?: continue
      val controller = evaluator.effectiveController(state, cardId) ?: card.controllerId
      val resolved = resolveActivatedAbility(
        state, controller, cardId, ability.id, instruction.timing, evaluator, forManual = false,
      )
      if (resolved is AbilityResolveResult.Invalid) continue
      resolved as AbilityResolveResult.Ok
      val transition = activateAbility(state, resolved, evaluator)
      events += transition.events
      programs += transition.programsToRun
    }
    return InstructionExecutionResult.Completed(null, events, programs)
  }

  fun processEvents(
    state: GameState,
    events: List<GameEvent>,
    evaluator: RulesEngine,
  ): GameTransition {
    val triggerEvents = events.filter { event ->
      event.type !in ignoredTriggerTypes && event.visibility != EventVisibility.INTERNAL
    }
    if (triggerEvents.isEmpty()) {
      return GameTransition(emptyList(), emptyList())
    }

    val outEvents = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    val consumedKeys = mutableMapOf<AbilityUsageKey, Int>()
    val scheduled = mutableListOf<ScheduledAuto>()

    for (event in triggerEvents) {
      scheduled += collectAutoAbilities(state, event, evaluator, consumedKeys)
    }

    val ordered = orderScheduled(state, scheduled)
    var seq = state.lastEventSequence + events.size
    for (item in ordered) {
      val key = usageKey(state, item.cardId, item.ability.id)
      if (item.ability.usageLimit == UsageLimit.ONCE_PER_TURN) {
        val prior = (state.abilityUsage[key] ?: 0) + (consumedKeys[key] ?: 0)
        if (prior >= 1) continue
        consumedKeys[key] = prior + 1
      }

      seq += 1
      val activationEvent = GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.EFFECT_ACTIVATED,
        sourceCardId = item.cardId,
        payload = GameEventPayload.EffectActivatedPayload(
          effectId = EffectId("${state.id.value}:auto:$seq"),
          sourceCardId = item.cardId,
          abilityId = item.ability.id,
        ),
        visibility = EventVisibility.PUBLIC,
      )
      outEvents += activationEvent
      programs += ProgramRequest(
        executionId = ExecutionId(activationEvent.id.value),
        program = item.ability.program,
        context = EffectContext(
          gameId = state.id,
          controllerId = item.controllerId,
          sourceCardId = item.cardId,
          abilityId = item.ability.id,
          triggeringEvent = item.triggerEvent,
        ),
      )
    }

    return GameTransition(events = outEvents, programsToRun = programs)
  }

  fun evaluatePermanentAbilities(
    state: GameState,
    baseSequence: Long = state.lastEventSequence,
  ): GameTransition {
    val desired = mutableMapOf<EffectId, ActiveEffect>()

    for (player in state.players.values) {
      val inPlayCards = mutableListOf<CardInstanceId>()
      inPlayCards += player.leader.cards
      inPlayCards += player.characters.cards
      inPlayCards += player.stage.cards

      for (cardId in inPlayCards) {
        val instance = state.cards[cardId] ?: continue
        val definition = cardCatalog.get(instance.definitionId) ?: continue
        val permanents = definition.abilities.filterIsInstance<PermanentAbilityDefinition>()
        if (permanents.isEmpty()) continue

        val controller = continuous.effectiveController(state, cardId) ?: instance.controllerId
        val baseCtx = EffectContext(
          gameId = state.id,
          controllerId = controller,
          sourceCardId = cardId,
          abilityId = null,
          triggeringEvent = null,
        )

        for (ability in permanents) {
          val effectId = EffectId("permanent:${cardId.value}:${ability.id.value}")

          val conditionHolds = if (ability.condition == null) {
            true
          } else {
            when (val result = evaluator.evaluateCondition(ability.condition, state, baseCtx, emptyMap())) {
              is EvaluationResult.Failed -> false
              is EvaluationResult.Success -> result.value
            }
          }
          if (!conditionHolds) continue

          val targetCardIds = if (ability.selector == null) {
            listOf(cardId)
          } else {
            val selectorCtx = baseCtx.copy(abilityId = ability.id)
            evaluator.selectCardIds(ability.selector, state, selectorCtx) ?: continue
          }

          val existing = state.activeEffects.firstOrNull { it.id == effectId }
          val timestamp = existing?.timestampSequence ?: (baseSequence + 1)

          desired[effectId] = ActiveEffect(
            id = effectId,
            sourceCardId = cardId,
            sourceAbilityId = ability.id,
            controllerId = controller,
            targetCardIds = targetCardIds,
            targetPlayerId = null,
            modifier = ability.modifier,
            duration = Duration.WhileSourceValid,
            timestampSequence = timestamp,
          )
        }
      }
    }

    val existingPermanentEffects = state.activeEffects.filter { it.id.value.startsWith("permanent:") }
    val events = mutableListOf<GameEvent>()
    var seq = baseSequence

    for (existing in existingPermanentEffects) {
      val target = desired[existing.id]
      if (target != existing) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.MODIFIER_EXPIRED,
          sourceCardId = existing.sourceCardId,
          payload = GameEventPayload.ModifierExpiredPayload(existing.id),
          visibility = EventVisibility.PUBLIC,
        )
      }
    }

    for ((id, target) in desired) {
      val existing = existingPermanentEffects.firstOrNull { it.id == id }
      if (existing != target) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.MODIFIER_ADDED,
          sourceCardId = target.sourceCardId,
          payload = GameEventPayload.ModifierAddedPayload(target),
          visibility = EventVisibility.PUBLIC,
        )
      }
    }

    return GameTransition(events, emptyList())
  }

  private data class ScheduledAuto(
    val cardId: CardInstanceId,
    val controllerId: PlayerId,
    val ability: AutoAbilityDefinition,
    val triggerEvent: GameEvent,
    val turnPlayerOwns: Boolean,
  )

  private fun collectAutoAbilities(
    state: GameState,
    event: GameEvent,
    evaluator: RulesEngine,
    consumedKeys: MutableMap<AbilityUsageKey, Int>,
  ): List<ScheduledAuto> {
    val results = mutableListOf<ScheduledAuto>()
    for (cardId in state.cards.keys.sortedBy { it.value }) {
      val instance = state.cards[cardId] ?: continue
      val definition = cardCatalog.get(instance.definitionId) ?: continue
      if (!isInEligibleAutoZone(state, cardId, definition, event)) continue
      val controller = evaluator.effectiveController(state, cardId) ?: instance.controllerId
      val abilities =
        definition.abilities.filterIsInstance<AutoAbilityDefinition>() + grantedAutoAbilities(state, cardId)
      for (ability in abilities) {
        if (continuous.isAbilityNegated(state, cardId, ability.id)) continue
        if (!matchesPattern(ability.trigger, event, state, cardId, controller, evaluator)) continue
        if (ability.condition != null) {
          val ctx = EffectContext(
            gameId = state.id,
            controllerId = controller,
            sourceCardId = cardId,
            abilityId = ability.id,
            triggeringEvent = event,
          )
          when (val cond = evaluator.evaluateCondition(ability.condition, state, ctx, emptyMap())) {
            is EvaluationResult.Failed -> continue
            is EvaluationResult.Success -> if (!cond.value) continue
          }
        }
        if (ability.usageLimit == UsageLimit.ONCE_PER_TURN) {
          val key = usageKey(state, cardId, ability.id)
          val prior = (state.abilityUsage[key] ?: 0) + (consumedKeys[key] ?: 0)
          if (prior >= 1) continue
        }
        val turnPlayer = state.turn?.playerId
        results += ScheduledAuto(
          cardId = cardId,
          controllerId = controller,
          ability = ability,
          triggerEvent = event,
          turnPlayerOwns = turnPlayer != null && controller == turnPlayer,
        )
      }
    }
    return results
  }

  private fun isInEligibleAutoZone(
    state: GameState,
    cardId: CardInstanceId,
    definition: CardDefinition,
    event: GameEvent,
  ): Boolean {
    if (event.sourceCardId == cardId) return true
    val zone = zoneOf(state, cardId) ?: return false
    return when (definition.category) {
      CardCategory.LEADER -> zone == ZoneType.LEADER
      CardCategory.CHARACTER -> zone == ZoneType.CHARACTER
      CardCategory.STAGE -> zone == ZoneType.STAGE
      CardCategory.EVENT -> false
      CardCategory.DON -> false
    }
  }

  private fun isZoneLegalForActivatedAbility(
    state: GameState,
    cardId: CardInstanceId,
    ability: ActivatedAbilityDefinition,
  ): Boolean {
    val instance = state.cards[cardId] ?: return false
    val definition = cardCatalog.get(instance.definitionId) ?: return false
    val zone = zoneOf(state, cardId) ?: return false
    return when (definition.category) {
      CardCategory.LEADER -> zone == ZoneType.LEADER
      CardCategory.CHARACTER -> zone == ZoneType.CHARACTER
      CardCategory.STAGE -> zone == ZoneType.STAGE
      CardCategory.EVENT -> when (ability.timing) {
        ActivationTiming.ON_TRIGGER -> zone == ZoneType.LIFE || zone == ZoneType.HAND || zone == ZoneType.TRASH
        else -> zone == ZoneType.HAND
      }

      CardCategory.DON -> false
    }
  }

  private fun zoneOf(state: GameState, cardId: CardInstanceId): ZoneType? {
    for (player in state.players.values) {
      if (cardId in player.leader.cards) return ZoneType.LEADER
      if (cardId in player.characters.cards) return ZoneType.CHARACTER
      if (cardId in player.stage.cards) return ZoneType.STAGE
      if (cardId in player.hand.cards) return ZoneType.HAND
      if (cardId in player.deck.cards) return ZoneType.DECK
      if (cardId in player.trash.cards) return ZoneType.TRASH
      if (cardId in player.cost.cards) return ZoneType.COST
      if (cardId in player.life.cards) return ZoneType.LIFE
      if (cardId in player.donDeck.cards) return ZoneType.DON_DECK
    }
    if (state.donAttachments.any { cardId in it.donCardIds }) return ZoneType.ATTACHED_DON
    return null
  }

  private fun grantedAutoAbilities(state: GameState, cardId: CardInstanceId): List<AutoAbilityDefinition> {
    return state.activeEffects.mapNotNull { effect ->
      if (!continuous.isEffectActive(state, effect)) return@mapNotNull null
      if (cardId !in effect.targetCardIds && effect.targetCardIds.isNotEmpty()) return@mapNotNull null
      val modifier = effect.modifier as? Modifier.AbilityModifier ?: return@mapNotNull null
      if (!modifier.add) return@mapNotNull null
      modifier.ability as? AutoAbilityDefinition
    }
  }

  private fun grantedActivatedAbilities(state: GameState, cardId: CardInstanceId): List<ActivatedAbilityDefinition> {
    return state.activeEffects.mapNotNull { effect ->
      if (!continuous.isEffectActive(state, effect)) return@mapNotNull null
      if (cardId !in effect.targetCardIds && effect.targetCardIds.isNotEmpty()) return@mapNotNull null
      val modifier = effect.modifier as? Modifier.AbilityModifier ?: return@mapNotNull null
      if (!modifier.add) return@mapNotNull null
      modifier.ability as? ActivatedAbilityDefinition
    }
  }

  private fun extraCostsFor(
    state: GameState,
    sourceCardId: CardInstanceId,
    ability: ActivatedAbilityDefinition,
    evaluator: RulesEngine,
  ): List<Cost> {
    val results = mutableListOf<Cost>()
    val controller = evaluator.effectiveController(state, sourceCardId)
    for (effect in state.activeEffects) {
      if (!continuous.isEffectActive(state, effect)) continue
      val modifier = effect.modifier as? Modifier.ActivationCostModifier ?: continue
      val selector = modifier.selector
      if (selector.abilityId != null && selector.abilityId != ability.id) continue
      if (selector.timing != null && selector.timing != ability.timing) continue
      if (selector.abilityType != null && selector.abilityType != AbilityType.ACTIVATED) continue
      if (selector.cardSelector != null) {
        val ctx = EffectContext(
          gameId = state.id,
          controllerId = controller ?: effect.controllerId,
          sourceCardId = sourceCardId,
          abilityId = ability.id,
          triggeringEvent = null,
        )
        val matched = evaluator.selectCardIds(selector.cardSelector, state, ctx) ?: continue
        if (sourceCardId !in matched) continue
      }
      results += modifier.cost
    }
    return results
  }

  private fun orderScheduled(state: GameState, items: List<ScheduledAuto>): List<ScheduledAuto> {
    return items.sortedWith(
      compareBy<ScheduledAuto> { it.turnPlayerOwns }.thenBy { it.cardId.value }.thenBy { it.ability.id.value }
        .thenBy { it.triggerEvent.sequence },
    )
  }

  internal fun matchesPattern(
    pattern: GameEventPattern,
    event: GameEvent,
    state: GameState,
    sourceCardId: CardInstanceId?,
    controllerId: PlayerId,
    evaluator: RulesEngine,
  ): Boolean {
    if (pattern.eventTypes.isNotEmpty() && event.type !in pattern.eventTypes) return false

    if (pattern.source != null) {
      val sourceId = event.sourceCardId ?: return false
      if (!cardMatchesSelector(
          pattern.source, state, sourceId, controllerId, evaluator, event, sourceCardId
        )
      ) return false
    }

    if (pattern.target != null) {
      val targetIds = extractTargetCardIds(event)
      if (targetIds.isEmpty()) return false
      if (targetIds.none {
          cardMatchesSelector(pattern.target, state, it, controllerId, evaluator, event, sourceCardId)
        }) return false
    }

    if (pattern.player != null) {
      val eventPlayer = extractPlayer(event) ?: return false
      val expected = resolveRelation(pattern.player, state, controllerId) ?: return false
      if (eventPlayer != expected) return false
    }

    if (pattern.targetPlayer != null) {
      val targetPlayer = extractTargetPlayer(event, state, evaluator) ?: return false
      val expected = resolveRelation(pattern.targetPlayer, state, controllerId) ?: return false
      if (targetPlayer != expected) return false
    }

    return true
  }

  private fun cardMatchesSelector(
    selector: CardSelector,
    state: GameState,
    cardId: CardInstanceId,
    controllerId: PlayerId,
    evaluator: RulesEngine,
    event: GameEvent?,
    abilitySourceCardId: CardInstanceId?,
  ): Boolean {
    val instance = state.cards[cardId] ?: return false
    val definition = cardCatalog.get(instance.definitionId) ?: return false

    if (selector.sameAsSource && abilitySourceCardId != cardId) return false
    if (selector.excludeSource && abilitySourceCardId == cardId) return false
    if (selector.orientation != null && instance.orientation != selector.orientation) return false

    if (selector.zones.isNotEmpty()) {
      val zone = zoneOf(state, cardId) ?: return false
      if (zone !in selector.zones) return false
    }

    if (selector.categories.isNotEmpty() && definition.category !in selector.categories) return false
    if (selector.owner != null) {
      val expected = resolveRelation(selector.owner, state, controllerId) ?: return false
      if (instance.ownerId != expected) return false
    }
    if (selector.controller != null) {
      val expected = resolveRelation(selector.controller, state, controllerId) ?: return false
      val effective = evaluator.effectiveController(state, cardId) ?: instance.controllerId
      if (effective != expected) return false
    }
    val effective = evaluator.effectiveCharacteristics(state, cardId) ?: return false
    if (selector.name != null && effective.name != selector.name) return false
    if (selector.colors.isNotEmpty() && selector.colors.none { it in effective.colors }) return false
    if (selector.types.isNotEmpty() && selector.types.none { it in effective.types }) return false
    if (selector.attributes.isNotEmpty() && selector.attributes.none { it in effective.attributes }) return false
    if (selector.keywords.isNotEmpty() && selector.keywords.none { it in effective.keywords }) return false

    if (selector.conditions.isNotEmpty()) {
      val ctx = EffectContext(
        gameId = state.id,
        controllerId = controllerId,
        sourceCardId = abilitySourceCardId ?: event?.sourceCardId,
        abilityId = null,
        triggeringEvent = event,
      )
      for (condition in selector.conditions) {
        when (val result = evaluator.evaluateCondition(condition, state, ctx, emptyMap(), cardId)) {
          is EvaluationResult.Failed -> return false
          is EvaluationResult.Success -> if (!result.value) return false
        }
      }
    }

    return true
  }

  private fun extractTargetCardIds(event: GameEvent): List<CardInstanceId> = when (val p = event.payload) {
    is GameEventPayload.CardMovedPayload -> listOf(p.cardId)
    is GameEventPayload.CardPlayedPayload -> listOf(p.cardId)
    is GameEventPayload.CardKnockedOutPayload -> listOf(p.cardId)
    is GameEventPayload.CardActivatedPayload -> listOf(p.cardId)
    is GameEventPayload.AttackDeclaredPayload -> when (val t = p.target) {
      is BattleTarget.CardTarget -> listOf(t.cardInstanceId)
      is BattleTarget.PlayerTarget -> emptyList()
    }

    is GameEventPayload.BlockActivatedPayload -> when (val t = p.previousTarget) {
      is BattleTarget.CardTarget -> listOf(t.cardInstanceId)
      is BattleTarget.PlayerTarget -> emptyList()
    }

    is GameEventPayload.DonAttachedPayload -> listOf(p.hostCardId)
    is GameEventPayload.CardsDrawnPayload -> p.cardIds
    else -> event.sourceCardId?.let { listOf(it) } ?: emptyList()
  }

  private fun extractPlayer(event: GameEvent): PlayerId? = when (val p = event.payload) {
    is GameEventPayload.TurnStartedPayload -> p.playerId
    is GameEventPayload.PhaseChangedPayload -> p.playerId
    is GameEventPayload.TurnEndedPayload -> p.playerId
    is GameEventPayload.CardPlayedPayload -> p.playerId
    is GameEventPayload.CardsDrawnPayload -> p.playerId
    is GameEventPayload.CardActivatedPayload -> p.playerId
    is GameEventPayload.DonAddedPayload -> p.playerId
    is GameEventPayload.DonRestedPayload -> p.playerId
    is GameEventPayload.DamageProcessedPayload -> p.playerId
    is GameEventPayload.LifeChangedPayload -> p.playerId
    else -> null
  }

  private fun extractTargetPlayer(
    event: GameEvent,
    state: GameState,
    evaluator: RulesEngine,
  ): PlayerId? = when (val p = event.payload) {
    is GameEventPayload.DamageProcessedPayload -> p.playerId
    is GameEventPayload.LifeChangedPayload -> p.playerId
    is GameEventPayload.CardKnockedOutPayload -> p.ownerId
    is GameEventPayload.AttackDeclaredPayload -> resolveTargetPlayer(p.target, state, evaluator)
    is GameEventPayload.BlockActivatedPayload -> resolveTargetPlayer(p.previousTarget, state, evaluator)
    else -> null
  }

  private fun resolveTargetPlayer(
    target: BattleTarget,
    state: GameState,
    evaluator: RulesEngine,
  ): PlayerId? = when (target) {
    is BattleTarget.PlayerTarget -> target.playerId
    is BattleTarget.CardTarget -> state.cards[target.cardInstanceId]?.let { card ->
      evaluator.effectiveController(state, target.cardInstanceId) ?: card.controllerId
    }
  }

  private fun resolveRelation(relation: PlayerRelation, state: GameState, self: PlayerId): PlayerId? {
    return when (relation) {
      PlayerRelation.SELF -> self
      PlayerRelation.OPPONENT -> state.playerOrder.firstOrNull { it != self }
      PlayerRelation.TURN_PLAYER -> state.turn?.playerId
      PlayerRelation.NON_TURN_PLAYER -> state.turn?.playerId?.let { tp -> state.playerOrder.firstOrNull { it != tp } }
    }
  }

  private sealed class AbilityResolveResult {
    data class Ok(
      val cardId: CardInstanceId,
      val ability: ActivatedAbilityDefinition,
      val controllerId: PlayerId,
      val objectId: CardObjectId,
      val costPayment: List<CostPayment>,
    ) : AbilityResolveResult()

    data class Invalid(val violations: List<RuleViolation>) : AbilityResolveResult()
  }

  private fun resolveActivatedAbility(
    state: GameState,
    playerId: PlayerId,
    sourceCardId: CardInstanceId,
    abilityId: AbilityId,
    requiredTiming: ActivationTiming?,
    evaluator: RulesEngine,
    forManual: Boolean,
  ): AbilityResolveResult {
    val violations = mutableListOf<RuleViolation>()
    val card = state.cards[sourceCardId]
    if (card == null) {
      return AbilityResolveResult.Invalid(listOf(RuleViolation("UNKNOWN_CARD", "Source card not found")))
    }
    if (continuous.isAbilityNegated(state, sourceCardId, abilityId)) {
      return AbilityResolveResult.Invalid(
        listOf(RuleViolation("NEGATED_ABILITY", "Ability is negated by a continuous effect"))
      )
    }
    violations += continuous.isActionRestricted(state, RuleAction.ACTIVATE_ABILITY, sourceCardId)
    val controller = evaluator.effectiveController(state, sourceCardId) ?: card.controllerId
    if (controller != playerId) {
      violations += RuleViolation("NOT_CONTROLLER", "Player does not control the source card")
    }
    val definition = cardCatalog.get(card.definitionId)
    if (definition == null) {
      return AbilityResolveResult.Invalid(listOf(RuleViolation("UNKNOWN_DEFINITION", "Card definition missing")))
    }
    val ability = (definition.abilities + grantedActivatedAbilities(
      state, sourceCardId
    )).filterIsInstance<ActivatedAbilityDefinition>().firstOrNull { it.id == abilityId }
    if (ability == null) {
      return AbilityResolveResult.Invalid(listOf(RuleViolation("UNKNOWN_ABILITY", "Activated ability not found")))
    }
    if (requiredTiming != null && ability.timing != requiredTiming) {
      violations += RuleViolation("TIMING_MISMATCH", "Ability timing does not match")
    }
    if (!isTimingLegal(state, ability.timing, playerId, forManual)) {
      violations += RuleViolation("ILLEGAL_TIMING", "Ability cannot be activated in the current window")
    }
    if (!isZoneLegalForActivatedAbility(state, sourceCardId, ability)) {
      violations += RuleViolation("ILLEGAL_ZONE", "Card is not in a zone where its ability can be activated")
    }
    if (ability.condition != null) {
      val ctx = EffectContext(
        gameId = state.id,
        controllerId = playerId,
        sourceCardId = sourceCardId,
        abilityId = ability.id,
        triggeringEvent = null,
      )
      when (val cond = evaluator.evaluateCondition(ability.condition, state, ctx, emptyMap())) {
        is EvaluationResult.Failed -> violations += RuleViolation("CONDITION_ERROR", cond.error.message)
        is EvaluationResult.Success -> if (!cond.value) violations += RuleViolation(
          "CONDITION_FAILED", "Ability condition not met"
        )
      }
    }
    if (ability.usageLimit == UsageLimit.ONCE_PER_TURN) {
      val key = AbilityUsageKey(card.objectId, ability.id, state.turn?.number ?: 0)
      if ((state.abilityUsage[key] ?: 0) >= 1) {
        violations += RuleViolation("USAGE_LIMIT", "Ability already used this turn")
      }
    }
    val extraCosts = extraCostsFor(state, sourceCardId, ability, evaluator)
    val combinedCost = if (extraCosts.isEmpty()) ability.cost else Cost.CombinedCost(listOf(ability.cost) + extraCosts)
    val costPayment = costResolver.preview(state, playerId, combinedCost, sourceCardId, evaluator)
    if (costPayment == null) violations += RuleViolation("COST_UNPAID", "Unable to pay ability cost")
    if (violations.isNotEmpty() || costPayment == null) return AbilityResolveResult.Invalid(violations)
    return AbilityResolveResult.Ok(sourceCardId, ability, playerId, card.objectId, costPayment)
  }

  private fun findActivatedAbilities(
    state: GameState,
    selector: AbilitySelector,
    timing: ActivationTiming?,
    contextControllerId: PlayerId,
    evaluator: RulesEngine,
  ): List<Pair<CardInstanceId, ActivatedAbilityDefinition>> {
    val results = mutableListOf<Pair<CardInstanceId, ActivatedAbilityDefinition>>()
    for ((cardId, instance) in state.cards) {
      val definition = cardCatalog.get(instance.definitionId) ?: continue
      if (selector.cardSelector != null) {
        if (!cardMatchesSelector(
            selector.cardSelector, state, cardId, contextControllerId, evaluator, event = null,
            abilitySourceCardId = null,
          )
        ) continue
      }
      val abilities =
        (definition.abilities + grantedActivatedAbilities(state, cardId)).filterIsInstance<ActivatedAbilityDefinition>()
      for (ability in abilities) {
        if (selector.abilityId != null && ability.id != selector.abilityId) continue
        if (selector.abilityType != null && selector.abilityType != AbilityType.ACTIVATED) continue
        if (timing != null && ability.timing != timing) continue
        if (selector.timing != null && ability.timing != selector.timing) continue
        if (!isZoneLegalForActivatedAbility(state, cardId, ability)) continue
        if (continuous.isAbilityNegated(state, cardId, ability.id)) continue
        if (continuous.isActionRestricted(state, RuleAction.ACTIVATE_ABILITY, cardId).isNotEmpty()) continue
        results += cardId to ability
      }
    }
    return results.sortedBy { it.first.value + it.second.id.value }
  }

  private fun isTimingLegal(
    state: GameState,
    timing: ActivationTiming,
    playerId: PlayerId,
    forManual: Boolean,
  ): Boolean {
    val turn = state.turn
    return when (timing) {
      ActivationTiming.ACTIVATE_MAIN -> turn != null && turn.phase == GamePhase.MAIN && turn.playerId == playerId
      ActivationTiming.COUNTER_STEP -> state.battle?.step == BattleStep.COUNTER
      ActivationTiming.ON_BLOCK -> state.battle?.step == BattleStep.BLOCK
      ActivationTiming.WHEN_ATTACKING -> state.battle?.step == BattleStep.ATTACK
      ActivationTiming.AT_MAIN_PHASE_START -> turn != null && turn.phase == GamePhase.MAIN
      ActivationTiming.AT_TURN_START -> turn != null
      ActivationTiming.END_OF_YOUR_TURN -> turn != null && turn.phase == GamePhase.END && turn.playerId == playerId
      ActivationTiming.END_OF_OPPONENT_TURN -> turn != null && turn.phase == GamePhase.END && turn.playerId != playerId
      ActivationTiming.AT_GAME_START -> state.setup?.step == GameSetupStep.INITIAL_EFFECTS
      ActivationTiming.ON_PLAY, ActivationTiming.ON_KO, ActivationTiming.ON_OPPONENT_ATTACK, ActivationTiming.ON_TRIGGER -> !forManual
    }
  }

  private fun activateAbility(
    state: GameState,
    resolved: AbilityResolveResult.Ok,
    evaluator: RulesEngine,
  ): GameTransition {
    val events = costResolver.events(state, resolved.controllerId, resolved.costPayment).toMutableList()
    val seq = state.lastEventSequence + events.size + 1
    val activationEvent = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = resolved.cardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = EffectId("${state.id.value}:act:$seq"),
        sourceCardId = resolved.cardId,
        abilityId = resolved.ability.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    events += activationEvent

    val request = ProgramRequest(
      executionId = ExecutionId(activationEvent.id.value),
      program = resolved.ability.program,
      context = EffectContext(
        gameId = state.id,
        controllerId = resolved.controllerId,
        sourceCardId = resolved.cardId,
        abilityId = resolved.ability.id,
        triggeringEvent = activationEvent,
      ),
    )
    return GameTransition(events = events, programsToRun = listOf(request))
  }

  private fun usageKey(state: GameState, cardId: CardInstanceId, abilityId: AbilityId): AbilityUsageKey {
    val objectId = state.cards[cardId]?.objectId ?: CardObjectId(cardId.value)
    return AbilityUsageKey(objectId, abilityId, state.turn?.number ?: 0)
  }
}

internal sealed class CostPayment {
  data class RestSource(val cardId: CardInstanceId) : CostPayment()
  data class RestDon(val donIds: List<CardInstanceId>) : CostPayment()
  data class ReturnDon(val donIds: List<CardInstanceId>) : CostPayment()
  data class TrashCards(val cards: List<ZonedCard>) : CostPayment()
}

internal data class ZonedCard(val cardId: CardInstanceId, val zone: ZoneType)

internal class CostResolver {

  fun preview(
    state: GameState,
    playerId: PlayerId,
    cost: Cost,
    sourceCardId: CardInstanceId,
    evaluator: RulesEngine,
  ): List<CostPayment>? = previewPart(state, playerId, cost, sourceCardId, evaluator, mutableSetOf())

  fun events(state: GameState, playerId: PlayerId, payments: List<CostPayment>): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    fun emit(
      type: GameEventType,
      sourceCardId: CardInstanceId?,
      payload: GameEventPayload,
      visibility: EventVisibility,
    ) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = type,
        sourceCardId = sourceCardId,
        payload = payload,
        visibility = visibility,
      )
    }

    for (payment in payments) {
      when (payment) {
        is CostPayment.RestSource -> state.cards[payment.cardId]?.let { card ->
          emit(
            GameEventType.CARD_STATE_CHANGED,
            payment.cardId,
            GameEventPayload.CardStateChangedPayload(payment.cardId, CardOrientation.RESTED, card.faceState),
            EventVisibility.PUBLIC,
          )
        }

        is CostPayment.RestDon -> if (payment.donIds.isNotEmpty()) {
          emit(
            GameEventType.DON_RESTED,
            null,
            GameEventPayload.DonRestedPayload(playerId, payment.donIds),
            EventVisibility.PUBLIC,
          )
        }

        is CostPayment.ReturnDon -> payment.donIds.forEach { donId ->
          emit(
            GameEventType.CARD_MOVED,
            donId,
            GameEventPayload.CardMovedPayload(
              cardId = donId,
              fromPlayerId = playerId,
              fromZone = ZoneType.COST,
              toPlayerId = playerId,
              toZone = ZoneType.DON_DECK,
              destinationIndex = null,
              newObjectId = null,
              orientation = CardOrientation.NOT_APPLICABLE,
              faceState = FaceState.FACE_DOWN,
              visibility = CardVisibility.OWNER_ONLY,
            ),
            EventVisibility.PRIVATE_TO_PLAYER(playerId),
          )
        }

        is CostPayment.TrashCards -> payment.cards.forEach { card ->
          emit(
            GameEventType.CARD_MOVED,
            card.cardId,
            GameEventPayload.CardMovedPayload(
              cardId = card.cardId,
              fromPlayerId = playerId,
              fromZone = card.zone,
              toPlayerId = playerId,
              toZone = ZoneType.TRASH,
              destinationIndex = null,
              newObjectId = null,
              orientation = CardOrientation.NOT_APPLICABLE,
              faceState = FaceState.FACE_UP,
              visibility = CardVisibility.PUBLIC,
            ),
            EventVisibility.PUBLIC,
          )
        }
      }
    }
    return events
  }

  private fun previewPart(
    state: GameState,
    playerId: PlayerId,
    cost: Cost,
    sourceCardId: CardInstanceId,
    evaluator: RulesEngine,
    reserved: MutableSet<CardInstanceId>,
  ): List<CostPayment>? {
    return when (cost) {
      is Cost.NoCost -> emptyList()

      is Cost.RestSourceCost -> {
        val card = state.cards[sourceCardId] ?: return null
        if (card.orientation != CardOrientation.ACTIVE || !reserved.add(sourceCardId)) return null
        listOf(CostPayment.RestSource(sourceCardId))
      }

      is Cost.RestDonCost -> {
        val amount = costAmount(state, playerId, sourceCardId, cost.amount, evaluator) ?: return null
        val donIds = takeDon(state, playerId, CardOrientation.ACTIVE, amount, reserved) ?: return null
        listOf(CostPayment.RestDon(donIds))
      }

      is Cost.ReturnDonCost -> {
        val amount = costAmount(state, playerId, sourceCardId, cost.amount, evaluator) ?: return null
        val donIds = takeDon(state, playerId, null, amount, reserved) ?: return null
        listOf(CostPayment.ReturnDon(donIds))
      }

      is Cost.TrashCardsCost -> {
        val amount = costAmount(state, playerId, sourceCardId, cost.amount, evaluator) ?: return null
        val cards =
          selectTrashCards(state, playerId, sourceCardId, cost.selector, amount, evaluator, reserved) ?: return null
        listOf(CostPayment.TrashCards(cards))
      }

      is Cost.CombinedCost -> {
        val all = mutableListOf<CostPayment>()
        for (part in cost.costs) {
          val paid = previewPart(state, playerId, part, sourceCardId, evaluator, reserved) ?: return null
          all += paid
        }
        all
      }
    }
  }

  private fun costAmount(
    state: GameState,
    playerId: PlayerId,
    sourceCardId: CardInstanceId,
    expr: ValueExpr,
    evaluator: RulesEngine,
  ): Int? {
    val ctx = EffectContext(state.id, playerId, sourceCardId, null, null)
    val value = when (val result = evaluator.evaluateValue(expr, state, ctx, emptyMap())) {
      is EvaluationResult.Failed -> return null
      is EvaluationResult.Success -> (result.value as? StoredValue.IntegerValue)?.value ?: return null
    }
    return value.takeIf { it >= 0 }
  }

  private fun takeDon(
    state: GameState,
    playerId: PlayerId,
    orientation: CardOrientation?,
    amount: Int,
    reserved: MutableSet<CardInstanceId>,
  ): List<CardInstanceId>? {
    val player = state.players[playerId] ?: return null
    val candidates = player.cost.cards.filter { id ->
      id !in reserved && (orientation == null || state.cards[id]?.orientation == orientation)
    }
    if (candidates.size < amount) return null
    val chosen = candidates.take(amount)
    reserved += chosen
    return chosen
  }

  private fun selectTrashCards(
    state: GameState,
    playerId: PlayerId,
    sourceCardId: CardInstanceId,
    selector: CardSelector,
    amount: Int,
    evaluator: RulesEngine,
    reserved: MutableSet<CardInstanceId>,
  ): List<ZonedCard>? {
    if (selector.zones.isEmpty()) return null
    val player = state.players[playerId] ?: return null
    val context = EffectContext(state.id, playerId, sourceCardId, null, null)
    val matches = evaluator.selectCardIds(selector, state, context) ?: return null
    val chosen = matches.asSequence().filter { it !in reserved && state.cards[it]?.ownerId == playerId }
      .mapNotNull { id -> zoneOf(player, id)?.let { ZonedCard(id, it) } }.filter { it.zone in selector.zones }
      .take(amount).toList()
    if (chosen.size < amount) return null
    reserved += chosen.map { it.cardId }
    return chosen
  }

  private fun zoneOf(player: PlayerState, cardId: CardInstanceId): ZoneType? {
    val zones = mapOf(
      ZoneType.DECK to player.deck,
      ZoneType.DON_DECK to player.donDeck,
      ZoneType.HAND to player.hand,
      ZoneType.TRASH to player.trash,
      ZoneType.LEADER to player.leader,
      ZoneType.CHARACTER to player.characters,
      ZoneType.STAGE to player.stage,
      ZoneType.COST to player.cost,
      ZoneType.LIFE to player.life,
    )
    return zones.entries.firstOrNull { cardId in it.value.cards }?.key
  }
}

internal class InformationManager {

  fun isSecret(zone: ZoneType): Boolean = when (zone) {
    ZoneType.DECK, ZoneType.DON_DECK, ZoneType.HAND, ZoneType.LIFE -> true
    else -> false
  }

  fun defaultVisibility(zone: ZoneType, ownerId: PlayerId): CardVisibility = when (zone) {
    ZoneType.DECK, ZoneType.DON_DECK, ZoneType.HAND, ZoneType.LIFE -> CardVisibility.OWNER_ONLY
    ZoneType.ATTACHED_DON -> CardVisibility.OWNER_ONLY
    else -> CardVisibility.PUBLIC
  }

  fun needsTemporaryReveal(fromZone: ZoneType, toZone: ZoneType): Boolean {
    return isSecret(fromZone) && isSecret(toZone)
  }

  fun revealEvent(
    state: GameState,
    cardIds: List<CardInstanceId>,
    cardVisibility: CardVisibility,
    eventVisibility: EventVisibility,
    sequence: Long,
  ): GameEvent {
    return GameEvent(
      id = EventId("${state.id.value}:$sequence"),
      sequence = sequence,
      type = GameEventType.CARD_REVEALED,
      sourceCardId = null,
      payload = GameEventPayload.CardRevealedPayload(cardIds, cardVisibility),
      visibility = eventVisibility,
    )
  }
}