package com.lucasalfare.opcg.rules

import com.lucasalfare.opcg.*

internal class ZoneInstructionExecutor {

  private val costResolver = CostResolver()

  fun selectAllCards(
    state: GameState,
    instruction: SelectAllCards,
    context: EffectContext,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val matched = evaluator.selectCardIds(instruction.selector, state, context)
      ?: return fail("SELECTOR_ERROR", "Failed to evaluate card selector")
    return InstructionExecutionResult.Completed(StoredValue.CardIdsValue(matched), emptyList())
  }

  fun draw(
    state: GameState,
    instruction: Draw,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      when (it) {
        is StoredValue.IntegerValue -> it.value
        else -> return fail("INVALID_VALUE", "Draw amount must be integer")
      }
    }.coerceAtLeast(0)
    if (amount == 0) return InstructionExecutionResult.Completed(null, emptyList())
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val available = player.deck.cards
    if (available.isEmpty()) {
      val seq = state.lastEventSequence + 1
      val lostEvent = GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.PLAYER_LOST,
        sourceCardId = null,
        payload = GameEventPayload.PlayerLostPayload(instruction.playerId, GameEndReason.DECK_OUT),
        visibility = EventVisibility.PUBLIC,
      )
      return InstructionExecutionResult.Completed(null, listOf(lostEvent))
    }
    val drawn = available.take(amount)
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in drawn) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = cardId,
          fromPlayerId = instruction.playerId,
          fromZone = ZoneType.DECK,
          toPlayerId = instruction.playerId,
          toZone = ZoneType.HAND,
          destinationIndex = null,
          newObjectId = null,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ),
        visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
      )
    }
    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARDS_DRAWN,
      sourceCardId = null,
      payload = GameEventPayload.CardsDrawnPayload(instruction.playerId, drawn),
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    if (drawn.size < amount) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.PLAYER_LOST,
        sourceCardId = null,
        payload = GameEventPayload.PlayerLostPayload(instruction.playerId, GameEndReason.DECK_OUT),
        visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  fun moveCards(
    state: GameState,
    instruction: MoveCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
    informationManager: InformationManager,
  ): InstructionExecutionResult {
    val cardIds = when (val source = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> source.value
      is StoredValue.CardValue -> listOf(source.value)
      null -> return fail("MISSING_VARIABLE", "MoveCards source missing")
      else -> return fail("INVALID_SOURCE", "MoveCards source must be cards")
    }
    val toZone = instruction.destination.zone
    if (toZone == ZoneType.ATTACHED_DON) {
      return fail("INVALID_DESTINATION", "ATTACHED_DON is not a valid MoveCards destination")
    }
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in cardIds) {
      if (state.cards[cardId] == null) return fail("UNKNOWN_CARD", "Card $cardId not found")
      val located = locateCard(state, cardId) ?: return fail("CARD_NOT_LOCATED", "Card $cardId is not in any zone")
      val fromZone = located.second
      val ownerId = state.cards[cardId]!!.ownerId

      if (informationManager.needsTemporaryReveal(fromZone, toZone)) {
        seq += 1
        events += informationManager.revealEvent(
          state = state,
          cardIds = listOf(cardId),
          cardVisibility = CardVisibility.PLAYERS(setOf(ownerId)),
          eventVisibility = EventVisibility.PRIVATE_TO_PLAYER(ownerId),
          sequence = seq,
        )
      }

      seq += 1
      val visibility = informationManager.defaultVisibility(toZone, ownerId)
      val eventVisibility = if (informationManager.isSecret(toZone)) {
        EventVisibility.PRIVATE_TO_PLAYER(ownerId)
      } else {
        EventVisibility.PUBLIC
      }
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = cardId,
          fromPlayerId = located.first,
          fromZone = fromZone,
          toPlayerId = instruction.destination.playerId,
          toZone = toZone,
          destinationIndex = when (instruction.placement) {
            Placement.TOP -> 0
            Placement.BOTTOM -> null
            Placement.DEFAULT -> null
          },
          newObjectId = null,
          orientation = defaultOrientation(toZone),
          faceState = defaultFace(toZone),
          visibility = visibility,
        ),
        visibility = eventVisibility,
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  fun returnCardsToOwnerHand(
    state: GameState,
    instruction: ReturnCardsToOwnerHand,
    variables: Map<VariableId, StoredValue>,
    informationManager: InformationManager,
  ): InstructionExecutionResult {
    val cardIds = when (val source = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> source.value
      is StoredValue.CardValue -> listOf(source.value)
      null -> return fail("MISSING_VARIABLE", "ReturnCardsToOwnerHand source missing")
      else -> return fail("INVALID_SOURCE", "ReturnCardsToOwnerHand source must be cards")
    }
    if (cardIds.toSet().size != cardIds.size) {
      return fail("DUPLICATE_CARDS", "ReturnCardsToOwnerHand source contains duplicate cards")
    }

    val returns = cardIds.map { cardId ->
      val instance = state.cards[cardId] ?: return fail("UNKNOWN_CARD", "Card $cardId not found")
      val (fromPlayerId, fromZone) = locateCard(state, cardId) ?: return fail(
        "CARD_NOT_LOCATED", "Card $cardId is not in any zone"
      )
      if (fromZone == ZoneType.DON_DECK || fromZone == ZoneType.ATTACHED_DON) {
        return fail("INVALID_SOURCE_ZONE", "Card $cardId in $fromZone cannot return to hand")
      }
      HandReturn(cardId, instance.ownerId, fromPlayerId, fromZone)
    }

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (entry in returns) {
      if (informationManager.needsTemporaryReveal(entry.fromZone, ZoneType.HAND)) {
        seq += 1
        events += informationManager.revealEvent(
          state = state,
          cardIds = listOf(entry.cardId),
          cardVisibility = CardVisibility.PLAYERS(setOf(entry.ownerId)),
          eventVisibility = EventVisibility.PRIVATE_TO_PLAYER(entry.ownerId),
          sequence = seq,
        )
      }
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = entry.cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = entry.cardId,
          fromPlayerId = entry.fromPlayerId,
          fromZone = entry.fromZone,
          toPlayerId = entry.ownerId,
          toZone = ZoneType.HAND,
          destinationIndex = null,
          newObjectId = null,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ),
        visibility = EventVisibility.PRIVATE_TO_PLAYER(entry.ownerId),
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  fun trashFromHand(
    state: GameState,
    instruction: TrashFromHand,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return fail("INVALID_VALUE", "Amount must be integer")
    }.coerceAtLeast(0)
    if (amount == 0) return InstructionExecutionResult.Completed(null, emptyList())
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")

    // Respeita o CardSelector: intersecta as cartas da mão do player com o conjunto
    // filtrado pelo seletor. Cartas que não casam com o filtro não são trashed.
    val matched = evaluator.selectCardIds(instruction.selector, state, context) ?: return fail(
      "SELECTOR_ERROR", "Failed to evaluate card selector"
    )
    val matchedSet = matched.toSet()
    val selected = player.hand.cards.filter { it in matchedSet }.take(amount)

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in selected) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = cardId,
          fromPlayerId = instruction.playerId,
          fromZone = ZoneType.HAND,
          toPlayerId = instruction.playerId,
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
    return InstructionExecutionResult.Completed(null, events)
  }

  fun playCard(
    state: GameState,
    instruction: PlayCard,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    engine: RulesEngine,
  ): InstructionExecutionResult {
    val cardId = when (val resolved = engine.resolveCardForPlay(instruction.card, state, context, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(resolved.error)
      is EvaluationResult.Success -> resolved.value
    }
    val card = state.cards[cardId] ?: return fail("UNKNOWN_CARD", "Card not found")
    val (holderId, holderZone) = locateCard(state, cardId) ?: return fail(
      "CARD_NOT_LOCATED", "Card is not in any zone",
    )
    if (holderZone != instruction.fromZone.zone) {
      return fail("CARD_NOT_IN_FROM_ZONE", "Card is in $holderZone, expected ${instruction.fromZone.zone}")
    }
    val controllerId = engine.resolvePlayer(instruction.controller, state, context) ?: return fail(
      "UNKNOWN_PLAYER", "Controller reference did not resolve",
    )
    val player = state.players[controllerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    if (continuous.isActionRestricted(state, RuleAction.PLAY_CARD, cardId).isNotEmpty()) {
      return fail("RESTRICTED_ACTION", "Card play is restricted by a continuous effect")
    }
    val def = catalog.get(card.definitionId) ?: return fail("UNKNOWN_DEFINITION", "Card definition missing")
    val mainPhase = MainPhaseController()

    val orientation = when (def.category) {
      CardCategory.CHARACTER, CardCategory.STAGE -> when (instruction.orientation) {
        null, PlayOrientation.ACTIVE -> CardOrientation.ACTIVE
        PlayOrientation.RESTED -> CardOrientation.RESTED
      }

      CardCategory.EVENT -> {
        if (instruction.orientation != null) return fail("INVALID_ORIENTATION", "Event cannot declare an orientation")
        if (mainPhase.isCounterOnlyEvent(def)) {
          return fail("COUNTER_EVENT_NOT_PLAYABLE", "Counter event cannot be played by effect")
        }
        CardOrientation.NOT_APPLICABLE
      }

      CardCategory.LEADER -> return fail("CANNOT_PLAY_LEADER", "Leader cannot be played by effect")
      CardCategory.DON -> return fail("INVALID_CATEGORY", "DON!! cannot be played by effect")
    }

    if (def.category == CardCategory.CHARACTER && player.characters.cards.size >= MainPhaseController.MAX_CHARACTERS) {
      return fail("CHARACTER_LIMIT", "Character area already has ${MainPhaseController.MAX_CHARACTERS} characters")
    }

    val restedDonIds = when (val variable = instruction.payment.restedDon) {
      null -> emptyList<CardInstanceId>()
      else -> when (val value = variables[variable]) {
        is StoredValue.CardIdsValue -> value.value
        is StoredValue.CardValue -> listOf(value.value)
        null -> return fail("MISSING_VARIABLE", "DON!! payment variable not found: ${variable.value}")
        else -> return fail("INVALID_SOURCE", "DON!! payment must reference card ids")
      }
    }
    if (restedDonIds.toSet().size != restedDonIds.size) {
      return fail("DUPLICATE_CARDS", "DON!! payment contains duplicate cards")
    }
    for (donId in restedDonIds) {
      if (donId !in player.cost.cards) return fail("DON_NOT_IN_COST", "DON!! must be in the controller's Cost area")
      if (state.cards[donId]?.orientation != CardOrientation.ACTIVE) {
        return fail("DON_NOT_ACTIVE", "DON!! must be active to be rested")
      }
    }

    val additionalCosts = instruction.payment.additionalCosts
    val costPayments = if (additionalCosts.isEmpty()) {
      emptyList<CostPayment>()
    } else {
      costResolver.preview(state, controllerId, Cost.CombinedCost(additionalCosts), cardId, engine)
        ?: return fail("COST_NOT_PAYABLE", "Additional costs cannot be paid")
    }
    val donUsedByCosts = costPayments.flatMap { payment ->
      when (payment) {
        is CostPayment.RestDon -> payment.donIds
        is CostPayment.ReturnDon -> payment.donIds
        else -> emptyList<CardInstanceId>()
      }
    }
    if (donUsedByCosts.any { it in restedDonIds }) {
      return fail("DON_PAYMENT_CONFLICT", "Additional costs and rested DON!! reference the same DON!!")
    }

    val events = mutableListOf<GameEvent>()
    events += costResolver.events(state, controllerId, costPayments)
    var seq = events.lastOrNull()?.sequence ?: state.lastEventSequence

    if (restedDonIds.isNotEmpty()) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DON_RESTED,
        sourceCardId = null,
        payload = GameEventPayload.DonRestedPayload(controllerId, restedDonIds),
        visibility = EventVisibility.PUBLIC,
      )
    }
    if (holderZone == ZoneType.DECK || holderZone == ZoneType.LIFE) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_REVEALED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardRevealedPayload(listOf(cardId), CardVisibility.PUBLIC),
        visibility = EventVisibility.PUBLIC,
      )
    }

    when (def.category) {
      CardCategory.CHARACTER -> {
        seq += 1
        events += cardMovedEvent(
          state, seq, cardId, holderId, holderZone, controllerId, ZoneType.CHARACTER, orientation,
        )
        seq += 1
        events += cardPlayedEvent(state, seq, cardId, controllerId, holderZone, ZoneType.CHARACTER)
        val programs = mainPhase.abilityPrograms(
          state, cardId, controllerId, def, setOf(ActivationTiming.ON_PLAY), events.last(),
        )
        return InstructionExecutionResult.Completed(null, events, programs)
      }

      CardCategory.STAGE -> {
        val previousStage = player.stage.cards.firstOrNull()
        if (previousStage != null) {
          seq += 1
          events += cardMovedEvent(
            state, seq, previousStage, controllerId, ZoneType.STAGE,
            state.cards[previousStage]?.ownerId ?: controllerId, ZoneType.TRASH, CardOrientation.NOT_APPLICABLE,
          )
        }
        seq += 1
        events += cardMovedEvent(
          state, seq, cardId, holderId, holderZone, controllerId, ZoneType.STAGE, orientation,
        )
        seq += 1
        events += cardPlayedEvent(state, seq, cardId, controllerId, holderZone, ZoneType.STAGE)
        val programs = mainPhase.abilityPrograms(
          state, cardId, controllerId, def, setOf(ActivationTiming.ON_PLAY), events.last(),
        )
        return InstructionExecutionResult.Completed(null, events, programs)
      }

      CardCategory.EVENT -> {
        seq += 1
        events += cardMovedEvent(
          state, seq, cardId, holderId, holderZone, card.ownerId, ZoneType.TRASH, orientation,
        )
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_ACTIVATED,
          sourceCardId = cardId,
          payload = GameEventPayload.CardActivatedPayload(cardId, controllerId),
          visibility = EventVisibility.PUBLIC,
        )
        val programs = mainPhase.abilityPrograms(
          state, cardId, controllerId, def, setOf(ActivationTiming.ACTIVATE_MAIN), events.last(),
        )
        return InstructionExecutionResult.Completed(null, events, programs)
      }

      else -> return fail("INVALID_CATEGORY", "Category ${def.category} cannot be played by effect")
    }
  }

  fun shuffleZone(state: GameState, instruction: ShuffleZone): InstructionExecutionResult {
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val zoneCards = when (instruction.zone) {
      ZoneType.DECK -> player.deck.cards
      ZoneType.DON_DECK -> player.donDeck.cards
      ZoneType.HAND -> player.hand.cards
      ZoneType.TRASH -> player.trash.cards
      ZoneType.LIFE -> player.life.cards
      else -> return fail("INVALID_ZONE", "Zone cannot be shuffled")
    }
    val (order, randomAfter) = RandomSupport.fisherYates(zoneCards.toMutableList(), state.randomState)
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DECK_SHUFFLED,
      sourceCardId = null,
      payload = GameEventPayload.DeckShuffledPayload(
        playerId = instruction.playerId,
        zone = instruction.zone,
        resultingOrder = order,
        randomStateAfter = randomAfter,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  fun restCards(
    state: GameState,
    instruction: RestCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = sourceCards(instruction.source, variables) ?: return fail("MISSING_VARIABLE", "Source missing")
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in cardIds) {
      val card = state.cards[cardId] ?: continue
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_STATE_CHANGED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardStateChangedPayload(cardId, CardOrientation.RESTED, card.faceState),
        visibility = EventVisibility.PUBLIC,
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  fun setCardState(
    state: GameState,
    instruction: SetCardState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = sourceCards(instruction.source, variables) ?: return fail("MISSING_VARIABLE", "Source missing")
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in cardIds) {
      val card = state.cards[cardId] ?: continue
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_STATE_CHANGED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardStateChangedPayload(cardId, instruction.orientation, card.faceState),
        visibility = EventVisibility.PUBLIC,
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  private data class HandReturn(
    val cardId: CardInstanceId,
    val ownerId: PlayerId,
    val fromPlayerId: PlayerId,
    val fromZone: ZoneType,
  )

  private fun cardMovedEvent(
    state: GameState,
    sequence: Long,
    cardId: CardInstanceId,
    fromPlayerId: PlayerId,
    fromZone: ZoneType,
    toPlayerId: PlayerId,
    toZone: ZoneType,
    orientation: CardOrientation,
  ): GameEvent = GameEvent(
    id = EventId("${state.id.value}:$sequence"),
    sequence = sequence,
    type = GameEventType.CARD_MOVED,
    sourceCardId = cardId,
    payload = GameEventPayload.CardMovedPayload(
      cardId = cardId,
      fromPlayerId = fromPlayerId,
      fromZone = fromZone,
      toPlayerId = toPlayerId,
      toZone = toZone,
      destinationIndex = null,
      newObjectId = null,
      orientation = orientation,
      faceState = FaceState.FACE_UP,
      visibility = CardVisibility.PUBLIC,
    ),
    visibility = EventVisibility.PUBLIC,
  )

  private fun cardPlayedEvent(
    state: GameState,
    sequence: Long,
    cardId: CardInstanceId,
    playerId: PlayerId,
    fromZone: ZoneType,
    toZone: ZoneType,
  ): GameEvent = GameEvent(
    id = EventId("${state.id.value}:$sequence"),
    sequence = sequence,
    type = GameEventType.CARD_PLAYED,
    sourceCardId = cardId,
    payload = GameEventPayload.CardPlayedPayload(
      cardId = cardId,
      playerId = playerId,
      fromZone = fromZone,
      toZone = toZone,
    ),
    visibility = EventVisibility.PUBLIC,
  )

  private fun sourceCards(source: VariableId, variables: Map<VariableId, StoredValue>): List<CardInstanceId>? {
    return when (val value = variables[source]) {
      is StoredValue.CardIdsValue -> value.value
      is StoredValue.CardValue -> listOf(value.value)
      null -> null
      else -> emptyList()
    }
  }

  private fun locateCard(state: GameState, cardId: CardInstanceId): Pair<PlayerId, ZoneType>? {
    for ((playerId, player) in state.players) {
      when {
        cardId in player.deck.cards -> return playerId to ZoneType.DECK
        cardId in player.donDeck.cards -> return playerId to ZoneType.DON_DECK
        cardId in player.hand.cards -> return playerId to ZoneType.HAND
        cardId in player.trash.cards -> return playerId to ZoneType.TRASH
        cardId in player.leader.cards -> return playerId to ZoneType.LEADER
        cardId in player.characters.cards -> return playerId to ZoneType.CHARACTER
        cardId in player.stage.cards -> return playerId to ZoneType.STAGE
        cardId in player.cost.cards -> return playerId to ZoneType.COST
        cardId in player.life.cards -> return playerId to ZoneType.LIFE
      }
    }
    if (state.donAttachments.any { cardId in it.donCardIds }) {
      val host = state.donAttachments.first { cardId in it.donCardIds }.hostCardId
      val owner = state.cards[host]?.ownerId
      if (owner != null) return owner to ZoneType.ATTACHED_DON
    }
    return null
  }

  private fun defaultOrientation(zone: ZoneType): CardOrientation = when (zone) {
    ZoneType.LEADER, ZoneType.CHARACTER, ZoneType.STAGE, ZoneType.COST -> CardOrientation.ACTIVE
    else -> CardOrientation.NOT_APPLICABLE
  }

  private fun defaultFace(zone: ZoneType): FaceState = when (zone) {
    ZoneType.DECK, ZoneType.DON_DECK, ZoneType.HAND, ZoneType.LIFE -> FaceState.FACE_DOWN
    else -> FaceState.FACE_UP
  }

  private fun fail(code: String, message: String) = InstructionExecutionResult.Failed(VmError(code, message, 0))
}

internal class DonInstructionExecutor {

  private data class ReturnDonCandidate(
    val cardId: CardInstanceId,
    val sourceZone: ZoneType,
    val hostCardId: CardInstanceId?,
  )

  fun addDon(
    state: GameState,
    instruction: AddDon,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return fail("INVALID_VALUE", "Amount must be integer")
    }.coerceAtLeast(0)
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val moved = player.donDeck.cards.take(amount)
    val identities = if (instruction.destination == null) null else StoredValue.CardIdsValue(moved)
    if (moved.isEmpty()) return InstructionExecutionResult.Completed(identities, emptyList())
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_ADDED,
      sourceCardId = null,
      payload = GameEventPayload.DonAddedPayload(instruction.playerId, moved),
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
          fromPlayerId = instruction.playerId,
          fromZone = ZoneType.DON_DECK,
          toPlayerId = instruction.playerId,
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
    return InstructionExecutionResult.Completed(identities, events)
  }

  fun restDon(
    state: GameState,
    instruction: RestDon,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return fail("INVALID_VALUE", "Amount must be integer")
    }.coerceAtLeast(0)
    if (amount == 0) return InstructionExecutionResult.Completed(null, emptyList())
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")

    // Default (zones vazio) preserva o comportamento anterior: apenas COST.
    val zones = if (instruction.selector.zones.isEmpty()) setOf(ZoneType.COST) else instruction.selector.zones
    val requestedOrientation = instruction.selector.orientation

    // "Rest" só faz sentido em DON ACTIVE. Orientação explicitamente não-ACTIVE = sem candidatos.
    if (requestedOrientation != null && requestedOrientation != CardOrientation.ACTIVE) {
      return InstructionExecutionResult.Completed(null, emptyList())
    }

    val candidateIds = mutableListOf<CardInstanceId>()
    if (ZoneType.COST in zones) {
      candidateIds += player.cost.cards
    }
    if (ZoneType.ATTACHED_DON in zones) {
      candidateIds += state.donAttachments.flatMap { it.donCardIds }
        .filter { id -> state.cards[id]?.ownerId == instruction.playerId }
    }

    val listed = when (val r = listedDonIds(state, instruction.selector.source, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    val eligible = candidateIds.filter { id ->
      val card = state.cards[id] ?: return@filter false
      (listed == null || id in listed) && card.orientation == CardOrientation.ACTIVE
    }.take(amount)

    if (eligible.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())

    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_RESTED,
      sourceCardId = null,
      payload = GameEventPayload.DonRestedPayload(instruction.playerId, eligible),
      visibility = EventVisibility.PUBLIC,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  fun setDonActive(
    state: GameState,
    instruction: SetDonActive,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = when (val value = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> value.value
      is StoredValue.CardValue -> listOf(value.value)
      null -> return fail("MISSING_VARIABLE", "Source missing")
      else -> emptyList()
    }
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in cardIds) {
      val card = state.cards[cardId] ?: continue
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
    return InstructionExecutionResult.Completed(null, events)
  }

  fun attachDon(
    state: GameState,
    instruction: AttachDon,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val donIds = when (val value = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> value.value
      is StoredValue.CardValue -> listOf(value.value)
      null -> return fail("MISSING_VARIABLE", "Source missing")
      else -> emptyList()
    }
    if (donIds.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())

    state.cards[instruction.cardId] ?: return fail("UNKNOWN_TARGET", "Target card not found")
    val player = state.players[context.controllerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val inField = instruction.cardId in player.leader.cards || instruction.cardId in player.characters.cards
    if (!inField) {
      return fail("INVALID_TARGET_ZONE", "Target must be Leader or Character")
    }

    if (donIds.toSet().size != donIds.size) {
      return fail("DUPLICATE_CARDS", "Attached DON ids must be distinct")
    }
    for (donId in donIds) {
      if (donId !in player.cost.cards) {
        return fail("DON_NOT_IN_COST", "DON must be in Cost area")
      }
    }

    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_ATTACHED,
      sourceCardId = instruction.cardId,
      payload = GameEventPayload.DonAttachedPayload(instruction.cardId, donIds),
      visibility = EventVisibility.PUBLIC,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  private fun listedDonIds(
    state: GameState,
    source: VariableId?,
    variables: Map<VariableId, StoredValue>,
  ): EvaluationResult<Set<CardInstanceId>?> {
    if (source == null) return EvaluationResult.Success(null)
    val ids = when (val value = variables[source]) {
      is StoredValue.CardIdsValue -> value.value
      is StoredValue.CardValue -> listOf(value.value)
      null -> return EvaluationResult.Failed(VmError("MISSING_VARIABLE", "DON source missing: ${source.value}", 0))
      else -> return EvaluationResult.Failed(VmError("INVALID_SOURCE", "DON source must be card ids", 0))
    }
    if (ids.toSet().size != ids.size) {
      return EvaluationResult.Failed(VmError("DUPLICATE_CARDS", "DON source contains duplicate cards", 0))
    }
    if (ids.any { state.cards[it] == null }) {
      return EvaluationResult.Failed(VmError("UNKNOWN_CARD", "DON source references a card not in the game", 0))
    }
    return EvaluationResult.Success(ids.toSet())
  }

  fun returnDon(
    state: GameState,
    instruction: ReturnDon,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return fail("INVALID_VALUE", "Amount must be integer")
    }.coerceAtLeast(0)
    if (amount == 0) return InstructionExecutionResult.Completed(null, emptyList())
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")

    // Default (zones vazio) preserva o comportamento anterior: apenas COST.
    val zones = if (instruction.selector.zones.isEmpty()) setOf(ZoneType.COST) else instruction.selector.zones
    val requestedOrientation = instruction.selector.orientation

    val listed = when (val r = listedDonIds(state, instruction.selector.source, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    val candidates = mutableListOf<ReturnDonCandidate>()
    if (ZoneType.COST in zones) {
      for (id in player.cost.cards) {
        val card = state.cards[id] ?: continue
        if (listed != null && id !in listed) continue
        if (requestedOrientation != null && card.orientation != requestedOrientation) continue
        candidates += ReturnDonCandidate(id, ZoneType.COST, null)
      }
    }
    if (ZoneType.ATTACHED_DON in zones) {
      for (attachment in state.donAttachments) {
        for (donId in attachment.donCardIds) {
          val don = state.cards[donId] ?: continue
          if (don.ownerId != instruction.playerId) continue
          if (listed != null && donId !in listed) continue
          if (requestedOrientation != null && don.orientation != requestedOrientation) continue
          candidates += ReturnDonCandidate(donId, ZoneType.ATTACHED_DON, attachment.hostCardId)
        }
      }
    }

    if (candidates.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
    val selected = candidates.take(amount)

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    // Origem COST → destino DON_DECK (retorno padrão).
    for (candidate in selected.filter { it.sourceZone == ZoneType.COST }) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = candidate.cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = candidate.cardId,
          fromPlayerId = instruction.playerId,
          fromZone = ZoneType.COST,
          toPlayerId = instruction.playerId,
          toZone = ZoneType.DON_DECK,
          destinationIndex = null,
          newObjectId = null,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ),
        visibility = EventVisibility.PUBLIC,
      )
    }

    // Origem ATTACHED_DON → destino COST (detach e devolve à Cost Area).
    val attachedByHost = selected.filter { it.sourceZone == ZoneType.ATTACHED_DON }.groupBy { it.hostCardId!! }
    for ((hostId, donCandidates) in attachedByHost) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DON_DETACHED,
        sourceCardId = hostId,
        payload = GameEventPayload.DonDetachedPayload(
          hostCardId = hostId,
          donCardIds = donCandidates.map { it.cardId },
          destinationPlayerId = instruction.playerId,
          destinationZone = ZoneType.COST,
          destinationIndex = null,
        ),
        visibility = EventVisibility.PUBLIC,
      )
    }

    return InstructionExecutionResult.Completed(null, events)
  }

  fun detachDon(
    state: GameState,
    instruction: DetachDon,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return fail("INVALID_VALUE", "Amount must be integer")
    }
    val attachment = state.donAttachments.firstOrNull { it.hostCardId == instruction.cardId }
      ?: return InstructionExecutionResult.Completed(null, emptyList())
    val donIds = attachment.donCardIds.take(amount)
    if (donIds.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_DETACHED,
      sourceCardId = instruction.cardId,
      payload = GameEventPayload.DonDetachedPayload(
        hostCardId = instruction.cardId,
        donCardIds = donIds,
        destinationPlayerId = instruction.destination.playerId,
        destinationZone = instruction.destination.zone,
        destinationIndex = null,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  private fun fail(code: String, message: String) = InstructionExecutionResult.Failed(VmError(code, message, 0))
}