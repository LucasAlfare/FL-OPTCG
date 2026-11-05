package com.lucasalfare.opcg.rules

import com.lucasalfare.opcg.*

internal class TurnController {

  fun startTurn(
    state: GameState,
    playerId: PlayerId,
    turnNumber: Int,
    continuous: ContinuousEffectProcessor,
    evaluator: ProgramEvaluator,
  ): GameTransition {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.TURN_STARTED,
      sourceCardId = null,
      payload = GameEventPayload.TurnStartedPayload(turnNumber, playerId),
      visibility = EventVisibility.PUBLIC,
    )

    val refresh = runRefresh(state, playerId, turnNumber, seq, continuous)
    events += refresh.events
    seq = refresh.lastSequence

    val draw = runDraw(state, playerId, turnNumber, seq)
    events += draw.events
    seq = draw.lastSequence

    val don = runDon(state, playerId, turnNumber, seq)
    events += don.events
    seq = don.lastSequence

    val mainStart = phaseChanged(state, playerId, GamePhase.DON, GamePhase.MAIN, seq)
    events += mainStart.events
    seq = mainStart.lastSequence

    return GameTransition(events = events, programsToRun = emptyList())
  }

  fun advancePhase(
    state: GameState,
    continuous: ContinuousEffectProcessor,
    evaluator: ProgramEvaluator,
  ): GameTransition {
    val turn = state.turn ?: return GameTransition(emptyList(), emptyList())
    return when (turn.phase) {
      GamePhase.REFRESH -> {
        val seq = state.lastEventSequence
        val draw = runDraw(state, turn.playerId, turn.number, seq)
        GameTransition(draw.events, emptyList())
      }

      GamePhase.DRAW -> {
        val seq = state.lastEventSequence
        val don = runDon(state, turn.playerId, turn.number, seq)
        GameTransition(don.events, emptyList())
      }

      GamePhase.DON -> {
        val result = phaseChanged(state, turn.playerId, GamePhase.DON, GamePhase.MAIN, state.lastEventSequence)
        GameTransition(result.events, emptyList())
      }

      GamePhase.MAIN -> endMainPhase(state, turn.playerId)
      GamePhase.END -> finishEndPhase(state, turn.playerId, continuous, evaluator)
    }
  }

  fun endMainPhase(state: GameState, playerId: PlayerId): GameTransition {
    val turn = state.turn ?: return GameTransition(emptyList(), emptyList())
    if (turn.playerId != playerId || turn.phase != GamePhase.MAIN) {
      return GameTransition(emptyList(), emptyList())
    }
    val result = phaseChanged(state, playerId, GamePhase.MAIN, GamePhase.END, state.lastEventSequence)
    return GameTransition(result.events, emptyList())
  }

  fun finishEndPhase(
    state: GameState,
    playerId: PlayerId,
    continuous: ContinuousEffectProcessor,
    evaluator: ProgramEvaluator,
  ): GameTransition {
    val turn = state.turn ?: return GameTransition(emptyList(), emptyList())
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    var activeEffects = state.activeEffects

    val preview = GameEvent(
      id = EventId("${state.id.value}:${seq + 1}"),
      sequence = seq + 1,
      type = GameEventType.TURN_ENDED,
      sourceCardId = null,
      payload = GameEventPayload.TurnEndedPayload(turn.number, playerId),
      visibility = EventVisibility.PUBLIC,
    )

    val expired = continuous.effectsToExpire(state, listOf(preview))
    for (effect in expired) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.MODIFIER_EXPIRED,
        sourceCardId = effect.sourceCardId,
        payload = GameEventPayload.ModifierExpiredPayload(effect.id),
        visibility = EventVisibility.PUBLIC,
      )
      activeEffects = activeEffects.filterNot { it.id == effect.id }
    }

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.TURN_ENDED,
      sourceCardId = null,
      payload = GameEventPayload.TurnEndedPayload(turn.number, playerId),
      visibility = EventVisibility.PUBLIC,
    )

    val nextPlayer = state.playerOrder.first { it != playerId }
    val nextTurnNumber = turn.number + 1

    val stateAfterEnd = state.copy(
      turn = null,
      activeEffects = activeEffects,
      lastEventSequence = seq,
    )

    val nextTurn = startTurn(stateAfterEnd, nextPlayer, nextTurnNumber, continuous, evaluator)
    events += nextTurn.events

    return GameTransition(events = events, programsToRun = emptyList())
  }

  private data class PhaseResult(val events: List<GameEvent>, val lastSequence: Long)

  private fun phaseChanged(
    state: GameState,
    playerId: PlayerId,
    previous: GamePhase?,
    next: GamePhase,
    seqStart: Long,
  ): PhaseResult {
    val seq = seqStart + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.PHASE_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.PhaseChangedPayload(
        playerId = playerId,
        previousPhase = previous ?: next,
        newPhase = next,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return PhaseResult(listOf(event), seq)
  }

  private fun runRefresh(
    state: GameState,
    playerId: PlayerId,
    turnNumber: Int,
    seqStart: Long,
    continuous: ContinuousEffectProcessor,
  ): PhaseResult {
    val events = mutableListOf<GameEvent>()
    var seq = seqStart
    val phase = phaseChanged(state, playerId, null, GamePhase.REFRESH, seq)
    events += phase.events
    seq = phase.lastSequence

    val player = state.players[playerId] ?: return PhaseResult(events, seq)

    for (attachment in state.donAttachments) {
      val host = state.cards[attachment.hostCardId] ?: continue
      val controller = continuous.effectiveController(state, host.id) ?: host.controllerId
      if (controller != playerId) continue
      if (host.id !in player.leader.cards && host.id !in player.characters.cards) continue
      for (donId in attachment.donCardIds) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.DON_DETACHED,
          sourceCardId = null,
          payload = GameEventPayload.DonDetachedPayload(
            hostCardId = host.id,
            donCardIds = listOf(donId),
            destinationPlayerId = playerId,
            destinationZone = ZoneType.COST,
            destinationIndex = null,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.DON_RETURNED,
          sourceCardId = null,
          payload = GameEventPayload.DonReturnedPayload(playerId, listOf(donId)),
          visibility = EventVisibility.PUBLIC,
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_STATE_CHANGED,
          sourceCardId = donId,
          payload = GameEventPayload.CardStateChangedPayload(donId, CardOrientation.ACTIVE, FaceState.FACE_UP),
          visibility = EventVisibility.PUBLIC,
        )
      }
    }

    val zonesToActivate = listOf(player.leader.cards, player.characters.cards, player.stage.cards, player.cost.cards)
    for (zoneCards in zonesToActivate) {
      for (cardId in zoneCards) {
        val card = state.cards[cardId] ?: continue
        if (card.orientation == CardOrientation.RESTED) {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.CARD_STATE_CHANGED,
            sourceCardId = cardId,
            payload = GameEventPayload.CardStateChangedPayload(cardId, CardOrientation.ACTIVE, card.faceState),
            visibility = EventVisibility.PUBLIC,
          )
        }
      }
    }

    return PhaseResult(events, seq)
  }

  private fun runDraw(
    state: GameState,
    playerId: PlayerId,
    turnNumber: Int,
    seqStart: Long,
  ): PhaseResult {
    val events = mutableListOf<GameEvent>()
    var seq = seqStart
    val phase = phaseChanged(state, playerId, GamePhase.REFRESH, GamePhase.DRAW, seq)
    events += phase.events
    seq = phase.lastSequence

    val skipDraw = turnNumber == 1 && playerId == state.firstPlayerId
    if (!skipDraw) {
      val player = state.players[playerId]
      val top = player?.deck?.cards?.firstOrNull()
      if (top != null) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = top,
          payload = GameEventPayload.CardMovedPayload(
            cardId = top,
            fromPlayerId = playerId,
            fromZone = ZoneType.DECK,
            toPlayerId = playerId,
            toZone = ZoneType.HAND,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN,
            visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(playerId),
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARDS_DRAWN,
          sourceCardId = null,
          payload = GameEventPayload.CardsDrawnPayload(playerId, listOf(top)),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(playerId),
        )
      }
    }
    return PhaseResult(events, seq)
  }

  private fun runDon(
    state: GameState,
    playerId: PlayerId,
    turnNumber: Int,
    seqStart: Long,
  ): PhaseResult {
    val events = mutableListOf<GameEvent>()
    var seq = seqStart
    val phase = phaseChanged(state, playerId, GamePhase.DRAW, GamePhase.DON, seq)
    events += phase.events
    seq = phase.lastSequence

    val player = state.players[playerId] ?: return PhaseResult(events, seq)
    val available = player.donDeck.cards.size
    val requested = when {
      turnNumber == 1 && playerId == state.firstPlayerId -> 1
      else -> 2
    }
    val amount = minOf(requested, available)
    if (amount > 0) {
      val moved = player.donDeck.cards.take(amount)
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DON_ADDED,
        sourceCardId = null,
        payload = GameEventPayload.DonAddedPayload(playerId, moved),
        visibility = EventVisibility.PUBLIC,
      )
      for (donId in moved) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = donId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = donId,
            fromPlayerId = playerId,
            fromZone = ZoneType.DON_DECK,
            toPlayerId = playerId,
            toZone = ZoneType.COST,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.ACTIVE,
            faceState = FaceState.FACE_UP,
            visibility = CardVisibility.PUBLIC,
          ),
          visibility = EventVisibility.PUBLIC,
        )
      }
    }
    return PhaseResult(events, seq)
  }
}

internal class MainPhaseController {

  private companion object {
    const val MAX_CHARACTERS = 5
  }

  fun validatePlayCard(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    costProcessor: CostProcessor,
  ): ValidationResult {
    val violations = mutableListOf<RuleViolation>()
    val turn = state.turn
    if (turn == null || turn.phase != GamePhase.MAIN || turn.playerId != command.playerId) {
      violations += RuleViolation("NOT_MAIN_PHASE", "Play card only during your Main Phase")
    }
    violations += playViolations(state, command, catalog, continuous, costProcessor)
    return ValidationResult(violations.isEmpty(), violations)
  }

  private fun playViolations(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    costProcessor: CostProcessor,
  ): List<RuleViolation> {
    val card = state.cards[command.cardId] ?: return listOf(RuleViolation("UNKNOWN_CARD", "Card not found"))
    val violations = mutableListOf<RuleViolation>()
    if (card.ownerId != command.playerId) {
      violations += RuleViolation("NOT_OWNER", "Player does not own the card")
    }
    val player = state.players[command.playerId]
    if (player == null || command.cardId !in player.hand.cards) {
      violations += RuleViolation("NOT_IN_HAND", "Card is not in hand")
    }
    val def = catalog.get(card.definitionId)
    if (def == null) {
      violations += RuleViolation("UNKNOWN_DEFINITION", "Card definition missing")
      return violations
    }
    when (def.category) {
      CardCategory.LEADER -> violations += RuleViolation(
        "CANNOT_PLAY_LEADER",
        "Leader cannot be played during the game"
      )

      CardCategory.CHARACTER -> if (player != null && player.characters.cards.size >= MAX_CHARACTERS) {
        violations += RuleViolation("CHARACTER_LIMIT", "Character area already has $MAX_CHARACTERS characters")
      }

      CardCategory.EVENT -> if (isCounterOnlyEvent(def)) {
        violations += RuleViolation("COUNTER_EVENT_NOT_PLAYABLE", "Counter event cannot be played in Main Phase")
      }

      CardCategory.STAGE -> Unit

      CardCategory.DON -> violations += RuleViolation("INVALID_CATEGORY", "Card category cannot be played")
    }
    if (donPaymentFor(state, command, catalog, continuous, costProcessor) == null) {
      violations += RuleViolation("INSUFFICIENT_DON", "Not enough active DON!! to pay the play cost")
    }
    return violations
  }

  private fun donPaymentFor(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    costProcessor: CostProcessor,
  ): List<CardInstanceId>? {
    val effectiveCost = costProcessor.effectiveCost(state, command.cardId, continuous, catalog)
    val payment = costProcessor.selectActiveDon(state, command.playerId, effectiveCost)
    return payment.takeIf { payment.size >= effectiveCost }
  }

  private fun isCounterOnlyEvent(def: CardDefinition): Boolean {
    val timings = def.abilities.filterIsInstance<ActivatedAbilityDefinition>().map { it.timing }
    return ActivationTiming.COUNTER_STEP in timings && ActivationTiming.ACTIVATE_MAIN !in timings
  }

  fun processPlayCard(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    costProcessor: CostProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    if (playViolations(state, command, catalog, continuous, costProcessor).isNotEmpty()) {
      return emptyTransition()
    }
    val card = state.cards[command.cardId] ?: return emptyTransition()
    val def = catalog.get(card.definitionId) ?: return emptyTransition()
    val payment = donPaymentFor(state, command, catalog, continuous, costProcessor) ?: return emptyTransition()

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    if (payment.isNotEmpty()) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DON_RESTED,
        sourceCardId = null,
        payload = GameEventPayload.DonRestedPayload(command.playerId, payment),
        visibility = EventVisibility.PUBLIC,
      )
    }

    when (def.category) {
      CardCategory.CHARACTER -> {
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
            toZone = ZoneType.CHARACTER,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.ACTIVE,
            faceState = FaceState.FACE_UP,
            visibility = CardVisibility.PUBLIC,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_PLAYED,
          sourceCardId = command.cardId,
          payload = GameEventPayload.CardPlayedPayload(
            cardId = command.cardId,
            playerId = command.playerId,
            fromZone = ZoneType.HAND,
            toZone = ZoneType.CHARACTER,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        val programs = abilityPrograms(state, command, def, setOf(ActivationTiming.ON_PLAY), events.last())
        return GameTransition(events, programs)
      }

      CardCategory.STAGE -> {
        val previousStage = state.players.getValue(command.playerId).stage.cards.firstOrNull()
        if (previousStage != null) {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.CARD_MOVED,
            sourceCardId = previousStage,
            payload = GameEventPayload.CardMovedPayload(
              cardId = previousStage,
              fromPlayerId = command.playerId,
              fromZone = ZoneType.STAGE,
              toPlayerId = state.cards[previousStage]?.ownerId ?: command.playerId,
              toZone = ZoneType.TRASH,
              destinationIndex = null,
              newObjectId = null,
              orientation = CardOrientation.NOT_APPLICABLE,
              faceState = FaceState.FACE_UP,
              visibility = CardVisibility.PUBLIC,
            ),
            visibility = EventVisibility.PUBLIC,
          )
        }
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
            toZone = ZoneType.STAGE,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.ACTIVE,
            faceState = FaceState.FACE_UP,
            visibility = CardVisibility.PUBLIC,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_PLAYED,
          sourceCardId = command.cardId,
          payload = GameEventPayload.CardPlayedPayload(
            cardId = command.cardId,
            playerId = command.playerId,
            fromZone = ZoneType.HAND,
            toZone = ZoneType.STAGE,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        val programs = abilityPrograms(state, command, def, setOf(ActivationTiming.ON_PLAY), events.last())
        return GameTransition(events, programs)
      }

      CardCategory.EVENT -> {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_REVEALED,
          sourceCardId = command.cardId,
          payload = GameEventPayload.CardRevealedPayload(
            cardIds = listOf(command.cardId),
            visibility = CardVisibility.PUBLIC,
          ),
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
        val programs = abilityPrograms(state, command, def, setOf(ActivationTiming.ACTIVATE_MAIN), events.last())
        return GameTransition(events, programs)
      }

      else -> error("Category ${def.category} cannot be played")
    }
  }

  private fun abilityPrograms(
    state: GameState,
    command: PlayCardCommand,
    def: CardDefinition,
    timings: Set<ActivationTiming>,
    triggeringEvent: GameEvent,
  ): List<ProgramRequest> {
    return def.abilities.filterIsInstance<ActivatedAbilityDefinition>().filter { it.timing in timings }.map { ability ->
        ProgramRequest(
          executionId = ExecutionId(
            "${state.id.value}:play:${command.cardId.value}:${ability.id.value}:${triggeringEvent.sequence}"
          ),
          program = ability.program,
          context = EffectContext(
            gameId = state.id,
            controllerId = command.playerId,
            sourceCardId = command.cardId,
            abilityId = ability.id,
            triggeringEvent = triggeringEvent,
          ),
        )
      }
  }

  private fun emptyTransition(): GameTransition = GameTransition(emptyList(), emptyList())

  fun validateGiveDon(
    state: GameState,
    command: GiveDonCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val violations = mutableListOf<RuleViolation>()
    val turn = state.turn
    if (turn == null || turn.phase != GamePhase.MAIN || turn.playerId != command.playerId) {
      violations += RuleViolation("NOT_MAIN_PHASE", "Give DON only during your Main Phase")
    }
    val don = state.cards[command.donId]
    val target = state.cards[command.targetId]
    if (don == null || target == null) {
      violations += RuleViolation("UNKNOWN_CARD", "DON or target not found")
      return ValidationResult(false, violations)
    }
    val player = state.players[command.playerId]
    if (player == null || command.donId !in player.cost.cards) {
      violations += RuleViolation("DON_NOT_IN_COST", "DON must be in Cost area")
    }
    if (don.orientation != CardOrientation.ACTIVE) {
      violations += RuleViolation("DON_NOT_ACTIVE", "DON must be active")
    }
    val controller = continuous.effectiveController(state, command.targetId) ?: target.controllerId
    if (controller != command.playerId) {
      violations += RuleViolation("INVALID_TARGET", "Target must be controlled by the player")
    }
    val inField =
      player != null && (command.targetId in player.leader.cards || command.targetId in player.characters.cards)
    if (!inField) {
      violations += RuleViolation("INVALID_TARGET_ZONE", "Target must be Leader or Character")
    }
    return ValidationResult(violations.isEmpty(), violations)
  }

  fun processGiveDon(
    state: GameState,
    command: GiveDonCommand,
    continuous: ContinuousEffectProcessor,
  ): GameTransition {
    val seq = state.lastEventSequence + 1
    val events = listOf(
      GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DON_ATTACHED,
        sourceCardId = command.targetId,
        payload = GameEventPayload.DonAttachedPayload(command.targetId, listOf(command.donId)),
        visibility = EventVisibility.PUBLIC,
      ),
    )
    return GameTransition(events, emptyList())
  }

  fun validateEndMain(state: GameState, command: EndMainPhaseCommand): ValidationResult {
    val turn = state.turn
    val ok = turn != null && turn.phase == GamePhase.MAIN && turn.playerId == command.playerId
    return if (ok) ValidationResult(true, emptyList())
    else ValidationResult(false, listOf(RuleViolation("NOT_MAIN_PHASE", "Cannot end Main Phase now")))
  }
}