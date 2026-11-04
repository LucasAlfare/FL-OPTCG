package com.lucasalfare.opcg

class GameStateReducer : EventApplier {

  override fun apply(state: GameState, events: List<GameEvent>): GameState {
    var current = state
    for (event in events) {
      require(event.sequence == current.lastEventSequence + 1) {
        "Expected event sequence ${current.lastEventSequence + 1}, got ${event.sequence}"
      }
      current = applySingle(current, event)
    }
    return current
  }

  private fun applySingle(state: GameState, event: GameEvent): GameState {
    val updated = applyPayload(state, event)
    return updated.copy(lastEventSequence = event.sequence)
  }

  private fun applyPayload(state: GameState, event: GameEvent): GameState {
    return when (val payload = event.payload) {
      is GameEventPayload.GameCreatedPayload -> state

      is GameEventPayload.SetupStepChangedPayload -> {
        val currentSetup = state.setup
        var newSetup =
          currentSetup?.copy(step = payload.newStep) ?: GameSetupState(payload.newStep, emptySet(), emptySet())
        if (payload.newStep == GameSetupStep.MULLIGAN && currentSetup?.step != GameSetupStep.MULLIGAN) {
          newSetup = newSetup.copy(pendingMulliganPlayers = state.playerOrder.toSet())
        }
        state.copy(setup = newSetup)
      }

      is GameEventPayload.GameStartedPayload -> state.copy(
        status = GameStatus.IN_PROGRESS,
        setup = null,
      )

      is GameEventPayload.TurnStartedPayload -> state.copy(
        turn = TurnState(
          number = payload.turnNumber,
          playerId = payload.playerId,
          phase = GamePhase.REFRESH,
        )
      )

      is GameEventPayload.PhaseChangedPayload -> {
        val turn = state.turn
        if (turn == null) state else state.copy(turn = turn.copy(phase = payload.newPhase))
      }

      is GameEventPayload.TurnEndedPayload -> state.copy(turn = null)

      is GameEventPayload.CardPlayedPayload -> state

      is GameEventPayload.CardsDrawnPayload -> state

      is GameEventPayload.CardMovedPayload -> applyCardMoved(state, payload)

      is GameEventPayload.CardRevealedPayload -> {
        var cards = state.cards
        for (cardId in payload.cardIds) {
          val card = cards[cardId] ?: continue
          cards = cards + (cardId to card.copy(visibility = payload.visibility))
        }
        state.copy(cards = cards)
      }

      is GameEventPayload.CardStateChangedPayload -> {
        val card = state.cards[payload.cardId] ?: return state
        state.copy(
          cards = state.cards + (payload.cardId to card.copy(
            orientation = payload.orientation,
            faceState = payload.faceState,
          ))
        )
      }

      is GameEventPayload.CardActivatedPayload -> state

      is GameEventPayload.CardKnockedOutPayload -> state

      is GameEventPayload.DonAddedPayload -> state

      is GameEventPayload.DonRestedPayload -> {
        var cards = state.cards
        for (donId in payload.donCardIds) {
          val card = cards[donId] ?: continue
          cards = cards + (donId to card.copy(orientation = CardOrientation.RESTED))
        }
        state.copy(cards = cards)
      }

      is GameEventPayload.DonAttachedPayload -> applyDonAttached(state, payload)

      is GameEventPayload.DonDetachedPayload -> applyDonDetached(state, payload)

      is GameEventPayload.DonReturnedPayload -> state

      is GameEventPayload.AttackDeclaredPayload -> state.copy(
        battle = BattleState(
          attackerId = payload.attackerId,
          originalTarget = payload.target,
          currentTarget = payload.target,
          step = BattleStep.ATTACK,
          blockerUsed = false,
          counterPower = 0,
        )
      )

      is GameEventPayload.BlockActivatedPayload -> {
        val battle = state.battle ?: return state
        state.copy(
          battle = battle.copy(
            currentTarget = payload.newTarget,
            blockerUsed = true,
          )
        )
      }

      is GameEventPayload.CounterActivatedPayload -> {
        val battle = state.battle ?: return state
        state.copy(battle = battle.copy(counterPower = battle.counterPower + payload.powerAdded))
      }

      is GameEventPayload.BattleEndedPayload -> state.copy(battle = null)

      is GameEventPayload.DamageProcessedPayload -> state

      is GameEventPayload.LifeChangedPayload -> state

      is GameEventPayload.EffectActivatedPayload -> {
        val abilityId = payload.abilityId ?: return state
        val sourceCardId = payload.sourceCardId ?: return state
        val card = state.cards[sourceCardId] ?: return state
        val turnNumber = state.turn?.number ?: 0
        val key = AbilityUsageKey(card.objectId, abilityId, turnNumber)
        state.copy(
          abilityUsage = state.abilityUsage + (key to (state.abilityUsage[key] ?: 0) + 1)
        )
      }

      is GameEventPayload.EffectResolvedPayload -> state

      is GameEventPayload.EffectFailedPayload -> state

      is GameEventPayload.ModifierAddedPayload -> state.copy(
        activeEffects = state.activeEffects + payload.effect
      )

      is GameEventPayload.ModifierExpiredPayload -> state.copy(
        activeEffects = state.activeEffects.filterNot { it.id == payload.effectId })

      is GameEventPayload.ReplacementAppliedPayload -> state

      is GameEventPayload.DeckShuffledPayload -> applyDeckShuffled(state, payload)

      is GameEventPayload.ChoiceSubmittedPayload -> state

      is GameEventPayload.MulliganResolvedPayload -> {
        val setup = state.setup ?: return state
        state.copy(
          setup = setup.copy(
            pendingMulliganPlayers = setup.pendingMulliganPlayers - payload.playerId,
            completedMulligans = setup.completedMulligans + payload.playerId,
          )
        )
      }

      is GameEventPayload.PlayerWonPayload -> {
        val loserIds = state.playerOrder.filter { it != payload.playerId }.toSet()
        state.copy(
          status = GameStatus.FINISHED,
          result = GameResult(payload.playerId, loserIds, payload.reason),
        )
      }

      is GameEventPayload.PlayerLostPayload -> {
        val currentLosers = state.result?.loserIds ?: emptySet()
        val newLosers = currentLosers + payload.playerId
        val allPlayers = state.playerOrder.toSet()
        val winner = if (newLosers.containsAll(allPlayers)) null
        else (allPlayers - newLosers).firstOrNull()
        state.copy(
          status = GameStatus.FINISHED,
          result = GameResult(winner, newLosers, payload.reason),
        )
      }

      is GameEventPayload.GameDrawnPayload -> state.copy(
        status = GameStatus.FINISHED,
        result = GameResult(null, payload.playerIds, payload.reason),
      )

      is GameEventPayload.ProgramFrameChangedPayload -> {
        val frames = if (payload.frame == null) {
          state.activePrograms.filterNot { it.id == payload.executionId }
        } else {
          val index = state.activePrograms.indexOfFirst { it.id == payload.executionId }
          if (index >= 0) {
            state.activePrograms.toMutableList().apply { this[index] = payload.frame }
          } else {
            state.activePrograms + payload.frame
          }
        }
        state.copy(activePrograms = frames)
      }

      is GameEventPayload.PendingChoiceChangedPayload -> {
        val choices = if (payload.choice == null) {
          state.pendingChoices.filterNot { it.id == payload.choiceId }
        } else {
          val index = state.pendingChoices.indexOfFirst { it.id == payload.choiceId }
          if (index >= 0) {
            state.pendingChoices.toMutableList().apply { this[index] = payload.choice }
          } else {
            state.pendingChoices + payload.choice
          }
        }
        state.copy(pendingChoices = choices)
      }
    }
  }

  private fun applyCardMoved(
    state: GameState,
    payload: GameEventPayload.CardMovedPayload,
  ): GameState {
    val cardId = payload.cardId
    var newPlayers = state.players
    var newDonAttachments = state.donAttachments

    if (payload.fromZone == ZoneType.ATTACHED_DON) {
      newDonAttachments = newDonAttachments.mapNotNull { attachment ->
        if (cardId in attachment.donCardIds) {
          val remaining = attachment.donCardIds - cardId
          if (remaining.isEmpty()) null else attachment.copy(donCardIds = remaining)
        } else {
          attachment
        }
      }
    } else {
      val fromPlayer = newPlayers[payload.fromPlayerId]
      if (fromPlayer != null) {
        newPlayers = newPlayers + (payload.fromPlayerId to removeCardFromZone(fromPlayer, payload.fromZone, cardId))
      }
    }

    if (payload.toZone != ZoneType.ATTACHED_DON) {
      val toPlayer = newPlayers[payload.toPlayerId]
      if (toPlayer != null) {
        newPlayers = newPlayers + (payload.toPlayerId to addCardToZone(
          toPlayer, payload.toZone, cardId, payload.destinationIndex,
        ))
      }
    }

    val newCards = state.cards[cardId]?.let { card ->
      state.cards + (cardId to card.copy(
        orientation = payload.orientation,
        faceState = payload.faceState,
        visibility = payload.visibility,
      ))
    } ?: state.cards

    return state.copy(
      players = newPlayers,
      donAttachments = newDonAttachments,
      cards = newCards,
    )
  }

  private fun applyDonAttached(
    state: GameState,
    payload: GameEventPayload.DonAttachedPayload,
  ): GameState {
    var newPlayers = state.players
    for (donId in payload.donCardIds) {
      for ((playerId, player) in newPlayers) {
        if (donId in player.cost.cards) {
          newPlayers = newPlayers + (playerId to player.copy(cost = ZoneState(player.cost.cards - donId)))
          break
        }
      }
    }

    val existing = state.donAttachments.find { it.hostCardId == payload.hostCardId }
    val newAttachments = if (existing != null) {
      state.donAttachments.map {
        if (it.hostCardId == payload.hostCardId) {
          it.copy(donCardIds = it.donCardIds + payload.donCardIds)
        } else {
          it
        }
      }
    } else {
      state.donAttachments + DonAttachment(payload.hostCardId, payload.donCardIds)
    }

    return state.copy(players = newPlayers, donAttachments = newAttachments)
  }

  private fun applyDonDetached(
    state: GameState,
    payload: GameEventPayload.DonDetachedPayload,
  ): GameState {
    val newAttachments = state.donAttachments.mapNotNull { attachment ->
      if (attachment.hostCardId == payload.hostCardId) {
        val remaining = attachment.donCardIds - payload.donCardIds.toSet()
        if (remaining.isEmpty()) null else attachment.copy(donCardIds = remaining)
      } else {
        attachment
      }
    }

    val newPlayers: Map<PlayerId, PlayerState> = state.players[payload.destinationPlayerId]?.let { destPlayer ->
      var updated: PlayerState = destPlayer
      payload.donCardIds.forEachIndexed { index, donId ->
        val insertIndex = payload.destinationIndex?.let { it + index }
        updated = addCardToZone(updated, payload.destinationZone, donId, insertIndex)
      }
      state.players + (payload.destinationPlayerId to updated)
    } ?: state.players

    return state.copy(players = newPlayers, donAttachments = newAttachments)
  }

  private fun applyDeckShuffled(
    state: GameState,
    payload: GameEventPayload.DeckShuffledPayload,
  ): GameState {
    val player = state.players[payload.playerId] ?: return state
    val updated = setZoneCards(player, payload.zone, payload.resultingOrder)
    return state.copy(
      players = state.players + (payload.playerId to updated),
      randomState = payload.randomStateAfter,
    )
  }

  private fun getZoneCards(player: PlayerState, zone: ZoneType): List<CardInstanceId> = when (zone) {
    ZoneType.DECK -> player.deck.cards
    ZoneType.DON_DECK -> player.donDeck.cards
    ZoneType.HAND -> player.hand.cards
    ZoneType.TRASH -> player.trash.cards
    ZoneType.LEADER -> player.leader.cards
    ZoneType.CHARACTER -> player.characters.cards
    ZoneType.STAGE -> player.stage.cards
    ZoneType.COST -> player.cost.cards
    ZoneType.LIFE -> player.life.cards
    ZoneType.ATTACHED_DON -> emptyList()
  }

  private fun setZoneCards(
    player: PlayerState,
    zone: ZoneType,
    cards: List<CardInstanceId>,
  ): PlayerState = when (zone) {
    ZoneType.DECK -> player.copy(deck = ZoneState(cards))
    ZoneType.DON_DECK -> player.copy(donDeck = ZoneState(cards))
    ZoneType.HAND -> player.copy(hand = ZoneState(cards))
    ZoneType.TRASH -> player.copy(trash = ZoneState(cards))
    ZoneType.LEADER -> player.copy(leader = ZoneState(cards))
    ZoneType.CHARACTER -> player.copy(characters = ZoneState(cards))
    ZoneType.STAGE -> player.copy(stage = ZoneState(cards))
    ZoneType.COST -> player.copy(cost = ZoneState(cards))
    ZoneType.LIFE -> player.copy(life = ZoneState(cards))
    ZoneType.ATTACHED_DON -> player
  }

  private fun removeCardFromZone(
    player: PlayerState,
    zone: ZoneType,
    cardId: CardInstanceId,
  ): PlayerState {
    val cards = getZoneCards(player, zone)
    return setZoneCards(player, zone, cards - cardId)
  }

  private fun addCardToZone(
    player: PlayerState,
    zone: ZoneType,
    cardId: CardInstanceId,
    index: Int?,
  ): PlayerState {
    val cards = getZoneCards(player, zone).toMutableList()
    if (index != null && index in 0..cards.size) {
      cards.add(index, cardId)
    } else {
      cards.add(cardId)
    }
    return setZoneCards(player, zone, cards)
  }
}

data class GameSnapshot(
  val manifest: GameManifest,
  val state: GameState,
  val lastLogEntrySequence: Long,
)

data class GameLogEntry(
  val sequence: Long,
  val command: GameCommand?,
  val events: List<GameEvent>,
)

data class GameLog(
  val manifest: GameManifest,
  val initialSnapshot: GameSnapshot,
  val initialEvents: List<GameEvent>,
  val entries: List<GameLogEntry>,
  val snapshots: List<GameSnapshot>,
)

class GameHistory(
  private val reducer: GameStateReducer = GameStateReducer(),
) {

  var log: GameLog? = null
    private set

  fun initialize(snapshot: GameSnapshot, initialEvents: List<GameEvent>) {
    require(snapshot.lastLogEntrySequence == 0L) {
      "Initial snapshot must have lastLogEntrySequence = 0, got ${snapshot.lastLogEntrySequence}"
    }
    require(snapshot.state.lastEventSequence == expectedEventSequenceAfter(initialEvents)) {
      "Initial snapshot state does not reflect initialEvents"
    }
    log = GameLog(
      manifest = snapshot.manifest,
      initialSnapshot = snapshot,
      initialEvents = initialEvents,
      entries = emptyList(),
      snapshots = emptyList(),
    )
  }

  fun load(log: GameLog) {
    validate(log)
    this.log = log
  }

  fun append(entry: GameLogEntry) {
    val current = log ?: error("History not initialized")
    val lastSequence = current.entries.lastOrNull()?.sequence ?: 0L
    val expectedSequence = lastSequence + 1
    require(entry.sequence == expectedSequence) {
      "Expected log entry sequence $expectedSequence, got ${entry.sequence}"
    }
    log = current.copy(entries = current.entries + entry)
  }

  fun createSnapshot(state: GameState, lastLogEntrySequence: Long): GameSnapshot {
    val current = log ?: error("History not initialized")
    val maxSequence = current.entries.lastOrNull()?.sequence ?: 0L
    require(lastLogEntrySequence in 0L..maxSequence) {
      "Invalid lastLogEntrySequence: $lastLogEntrySequence (max $maxSequence)"
    }
    val expectedEventSequence = computeEventSequenceAt(current, lastLogEntrySequence)
    require(state.lastEventSequence == expectedEventSequence) {
      "State lastEventSequence ${state.lastEventSequence} does not match expected " + "$expectedEventSequence at log entry $lastLogEntrySequence"
    }
    val snapshot = GameSnapshot(current.manifest, state, lastLogEntrySequence)
    log = current.copy(snapshots = current.snapshots + snapshot)
    return snapshot
  }

  fun replay(snapshot: GameSnapshot, entries: List<GameLogEntry>): GameState {
    val eventsToApply =
      entries.asSequence().sortedBy { it.sequence }.filter { it.sequence > snapshot.lastLogEntrySequence }
        .flatMap { it.events.asSequence() }.toList()
    return reducer.apply(snapshot.state, eventsToApply)
  }

  private fun validate(log: GameLog) {
    require(log.initialSnapshot.manifest == log.manifest) {
      "Initial snapshot manifest does not match log manifest"
    }
    require(log.initialSnapshot.lastLogEntrySequence == 0L) {
      "Initial snapshot must have lastLogEntrySequence = 0"
    }
    require(log.initialSnapshot.state.lastEventSequence == expectedEventSequenceAfter(log.initialEvents)) {
      "Initial snapshot state does not reflect initialEvents"
    }

    log.entries.forEachIndexed { index, entry ->
      val expectedSequence = index + 1L
      require(entry.sequence == expectedSequence) {
        "Log entry sequence mismatch: expected $expectedSequence, got ${entry.sequence}"
      }
    }

    var expectedEventSequence = log.initialSnapshot.state.lastEventSequence + 1
    for (entry in log.entries) {
      for (event in entry.events) {
        require(event.sequence == expectedEventSequence) {
          "Event sequence mismatch in log entry ${entry.sequence}: " + "expected $expectedEventSequence, got ${event.sequence}"
        }
        expectedEventSequence += 1
      }
    }

    for (snapshot in log.snapshots) {
      require(snapshot.manifest == log.manifest) {
        "Snapshot manifest does not match log manifest"
      }
      val maxSequence = log.entries.lastOrNull()?.sequence ?: 0L
      require(snapshot.lastLogEntrySequence in 0L..maxSequence) {
        "Snapshot lastLogEntrySequence ${snapshot.lastLogEntrySequence} out of range"
      }
      val expected = computeEventSequenceAt(log, snapshot.lastLogEntrySequence)
      require(snapshot.state.lastEventSequence == expected) {
        "Snapshot state lastEventSequence ${snapshot.state.lastEventSequence} does not " + "match expected $expected at log entry ${snapshot.lastLogEntrySequence}"
      }
    }
  }

  private fun expectedEventSequenceAfter(events: List<GameEvent>): Long {
    return events.lastOrNull()?.sequence ?: 0L
  }

  private fun computeEventSequenceAt(log: GameLog, lastLogEntrySequence: Long): Long {
    if (lastLogEntrySequence == 0L) {
      return log.initialSnapshot.state.lastEventSequence
    }
    var sequence = log.initialSnapshot.state.lastEventSequence
    for (entry in log.entries) {
      if (entry.sequence > lastLogEntrySequence) break
      if (entry.events.isNotEmpty()) {
        sequence = entry.events.last().sequence
      }
    }
    return sequence
  }
}