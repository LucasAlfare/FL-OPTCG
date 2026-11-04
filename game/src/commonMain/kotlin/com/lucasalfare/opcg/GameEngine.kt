package com.lucasalfare.opcg

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
data class GameSnapshot(
  val manifest: GameManifest,
  val state: GameState,
  val lastLogEntrySequence: Long,
)

@Serializable
data class GameLogEntry(
  val sequence: Long,
  val command: GameCommand?,
  val events: List<GameEvent>,
)

@Serializable
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

@Serializable
sealed class CommandResult {
  @Serializable
  data class Rejected(val violations: List<RuleViolation>) : CommandResult()

  @Serializable
  data class Completed(val events: List<GameEvent>, val lastEventSequence: Long) : CommandResult()

  @Serializable
  data class Suspended(val choiceId: ChoiceId, val events: List<GameEvent>) : CommandResult()

  @Serializable
  data class Failed(val error: VmError, val events: List<GameEvent>, val lastEventSequence: Long) : CommandResult()
}

data class CardView(
  val id: CardInstanceId?,
  val definition: CardDefinition?,
  val controllerId: PlayerId?,
  val orientation: CardOrientation,
  val faceState: FaceState,
  val characteristics: EffectiveCharacteristics?,
  val attachedDonCount: Int,
)

data class PlayerPublicView(
  val playerId: PlayerId,
  val leader: CardView?,
  val characters: List<CardView>,
  val stage: CardView?,
  val lifeCount: Int,
  val trash: List<CardView>,
  val deckCount: Int,
  val costDon: List<CardView>,
  val attachedDon: Map<CardInstanceId, Int>,
)

data class PlayerView(
  val gameId: GameId,
  val viewerId: PlayerId,
  val status: GameStatus,
  val turn: TurnState?,
  val battle: BattleState?,
  val players: List<PlayerPublicView>,
  val ownHand: List<CardView>,
  val opponentHandCount: Int,
  val revealedCards: List<CardView>,
  val publicEvents: List<GameEvent>,
  val pendingChoice: PendingChoice?,
)

class GameEngine private constructor(
  private var state: GameState,
  private val manifest: GameManifest,
  private val cardCatalog: CardCatalog,
  private val rulesEngine: RulesEngine,
  private val virtualMachine: VirtualMachine,
  private val reducer: GameStateReducer,
  private val history: GameHistory,
) {

  companion object {

    fun create(
      setup: GameSetup,
      manifest: GameManifest,
      cardCatalog: CardCatalog,
      rulesEngine: RulesEngine,
      virtualMachine: VirtualMachine,
      reducer: GameStateReducer,
      history: GameHistory,
    ): GameEngine {
      val creationResult = rulesEngine.createInitialState(setup)
      val stateAfterInit = reducer.apply(creationResult.initialState, creationResult.initialEvents)

      val initialSnapshot = GameSnapshot(
        manifest = manifest,
        state = stateAfterInit,
        lastLogEntrySequence = 0L,
      )
      history.initialize(initialSnapshot, creationResult.initialEvents)

      val engine = GameEngine(stateAfterInit, manifest, cardCatalog, rulesEngine, virtualMachine, reducer, history)
      engine.runSetupPhase()
      return engine
    }

    fun restore(
      log: GameLog,
      cardCatalog: CardCatalog,
      rulesEngine: RulesEngine,
      virtualMachine: VirtualMachine,
      reducer: GameStateReducer,
      history: GameHistory,
    ): GameEngine {
      val stored = log.manifest
      require(stored.rulesVersion == log.manifest.rulesVersion) { "Rules version mismatch" }
      require(stored.cardCatalogVersion == cardCatalog.version) { "Card catalog version mismatch" }
      require(stored.cardCatalogHash == cardCatalog.hash) { "Card catalog hash mismatch" }

      history.load(log)

      val base = log.snapshots.lastOrNull() ?: log.initialSnapshot
      val allEntries = log.entries
      val state = history.replay(base, allEntries)

      return GameEngine(state, log.manifest, cardCatalog, rulesEngine, virtualMachine, reducer, history)
    }
  }

  fun submit(command: GameCommand): CommandResult {
    if (state.status == GameStatus.FINISHED) {
      return CommandResult.Rejected(listOf(RuleViolation("GAME_FINISHED", "Game is already finished")))
    }

    val setup = state.setup
    if (setup != null) {
      val allowed = isSetupCommand(command, setup)
      if (!allowed) {
        return CommandResult.Rejected(
          listOf(
            RuleViolation(
              "SETUP_IN_PROGRESS", "Only setup commands are accepted during setup"
            )
          )
        )
      }
    }

    val validation = rulesEngine.validate(state, command)
    if (!validation.valid) {
      return CommandResult.Rejected(validation.violations)
    }

    val transactionEvents = mutableListOf<GameEvent>()

    val transition = rulesEngine.process(state, command)
    if (transition.events.isNotEmpty()) {
      state = reducer.apply(state, transition.events)
      transactionEvents += transition.events
    }

    if (transition.choiceToResume != null) {
      val choice = transition.choiceToResume
      val frame = state.activePrograms.firstOrNull { it.pendingChoiceId == choice.choiceId }
      if (frame != null) {
        val vmResult = runVmResume(frame, choice, transactionEvents)
        if (vmResult != null) return vmResult
      }
    }

    for (request in transition.programsToRun) {
      val vmResult = runVmStart(request, transactionEvents)
      if (vmResult != null) return vmResult
    }

    val autoResult = drainAutomaticWork(transactionEvents)
    if (autoResult != null) return autoResult

    if (command is ResolveMulliganCommand) {
      checkMulligansComplete(transactionEvents)
    }

    val logEntry = buildEntry(command, transactionEvents)
    history.append(logEntry)

    return if (state.pendingChoices.isNotEmpty()) {
      val blocking = state.pendingChoices.first()
      CommandResult.Suspended(blocking.id, transactionEvents)
    } else {
      CommandResult.Completed(transactionEvents, state.lastEventSequence)
    }
  }

  fun snapshot(): GameSnapshot = history.createSnapshot(state, history.log!!.entries.lastOrNull()?.sequence ?: 0L)

  fun view(playerId: PlayerId): PlayerView {
    val opponentId = state.playerOrder.firstOrNull { it != playerId }
    val opponentHandCount = opponentId?.let { state.players[it]?.hand?.cards?.size } ?: 0

    val revealedIds = state.cards.values.filter { card ->
      isVisibleTo(
        card.visibility, playerId, card.ownerId
      ) && card.faceState == FaceState.FACE_UP
    }.map { it.id }.toSet()

    val publicViews = state.playerOrder.map { pid -> buildPublicView(pid, playerId) }

    val ownHand = state.players[playerId]?.hand?.cards?.map { cardId ->
      buildCardView(cardId, playerId, authorized = true)
    } ?: emptyList()

    val revealedCards = revealedIds.filter { id -> state.players[playerId]?.hand?.cards?.contains(id) != true }
      .map { cardId -> buildCardView(cardId, playerId, authorized = true) }

    val pendingChoice = state.pendingChoices.firstOrNull { it.playerId == playerId }

    val publicEvents = collectPublicEvents(playerId)

    return PlayerView(
      gameId = state.id,
      viewerId = playerId,
      status = state.status,
      turn = state.turn,
      battle = state.battle,
      players = publicViews,
      ownHand = ownHand,
      opponentHandCount = opponentHandCount,
      revealedCards = revealedCards,
      publicEvents = publicEvents,
      pendingChoice = pendingChoice,
    )
  }

  private fun runSetupPhase() {
    val setupEvents = mutableListOf<GameEvent>()

    val initialEffectsResult = processInitialLeaderEffects(setupEvents)
    if (initialEffectsResult != null) return

    advanceToOpeningHand(setupEvents)
    recordAutoEntry(setupEvents)
  }

  private fun processInitialLeaderEffects(accumulated: MutableList<GameEvent>): Unit? {
    val chooser = state.chooserPlayerId
    val other = state.playerOrder.firstOrNull { it != chooser } ?: return null

    for (playerId in listOf(chooser, other)) {
      val player = state.players[playerId] ?: continue
      val leaderCardId = player.leader.cards.firstOrNull() ?: continue
      val leaderDef = cardCatalog.get(state.cards[leaderCardId]?.definitionId ?: continue) ?: continue

      for (ability in leaderDef.abilities) {
        if (ability !is ActivatedAbilityDefinition) continue
        if (ability.timing != ActivationTiming.AT_GAME_START) continue

        val command = ActivateAbilityCommand(playerId, leaderCardId, ability.id)
        val transition = rulesEngine.process(state, command)
        if (transition.events.isNotEmpty()) {
          state = reducer.apply(state, transition.events)
          accumulated += transition.events
        }
        for (request in transition.programsToRun) {
          val vmResult = runVmStart(request, accumulated)
          if (vmResult != null) {
            recordAutoEntry(accumulated)
            return Unit
          }
        }
        val autoResult = drainAutomaticWork(accumulated)
        if (autoResult != null) {
          recordAutoEntry(accumulated)
          return Unit
        }
        if (state.pendingChoices.isNotEmpty()) {
          recordAutoEntry(accumulated)
          return Unit
        }
      }
    }

    return null
  }

  private fun advanceToOpeningHand(accumulated: MutableList<GameEvent>) {
    val seq1 = state.lastEventSequence + 1
    val openingHandEvent = GameEvent(
      id = EventId("${state.id.value}:$seq1"),
      sequence = seq1,
      type = GameEventType.SETUP_STEP_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.SetupStepChangedPayload(GameSetupStep.INITIAL_EFFECTS, GameSetupStep.OPENING_HAND),
      visibility = EventVisibility.PUBLIC,
    )
    state = reducer.apply(state, listOf(openingHandEvent))
    accumulated += openingHandEvent

    for (playerId in state.playerOrder) {
      val player = state.players[playerId] ?: continue
      val toDraw = player.deck.cards.take(5)
      for (cardId in toDraw) {
        val seq = state.lastEventSequence + 1
        val moveEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId,
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
        state = reducer.apply(state, listOf(moveEvent))
        accumulated += moveEvent
      }
      if (toDraw.isNotEmpty()) {
        val seq = state.lastEventSequence + 1
        val drawnEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARDS_DRAWN,
          sourceCardId = null,
          payload = GameEventPayload.CardsDrawnPayload(playerId, toDraw),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(playerId),
        )
        state = reducer.apply(state, listOf(drawnEvent))
        accumulated += drawnEvent
      }
    }

    val seq2 = state.lastEventSequence + 1
    val mulliganEvent = GameEvent(
      id = EventId("${state.id.value}:$seq2"),
      sequence = seq2,
      type = GameEventType.SETUP_STEP_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.SetupStepChangedPayload(GameSetupStep.OPENING_HAND, GameSetupStep.MULLIGAN),
      visibility = EventVisibility.PUBLIC,
    )
    state = reducer.apply(state, listOf(mulliganEvent))
    accumulated += mulliganEvent
  }

  private fun advanceToLife(accumulated: MutableList<GameEvent>) {
    val seq1 = state.lastEventSequence + 1
    val lifeStepEvent = GameEvent(
      id = EventId("${state.id.value}:$seq1"),
      sequence = seq1,
      type = GameEventType.SETUP_STEP_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.SetupStepChangedPayload(GameSetupStep.MULLIGAN, GameSetupStep.LIFE),
      visibility = EventVisibility.PUBLIC,
    )
    state = reducer.apply(state, listOf(lifeStepEvent))
    accumulated += lifeStepEvent

    for (playerId in state.playerOrder) {
      val player = state.players[playerId] ?: continue
      val leaderCardId = player.leader.cards.firstOrNull() ?: continue
      val effectiveLife = rulesEngine.effectiveCharacteristics(state, leaderCardId)?.life ?: 0
      val toDraw = player.deck.cards.take(effectiveLife)
      for (cardId in toDraw) {
        val seq = state.lastEventSequence + 1
        val moveEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId,
            fromPlayerId = playerId,
            fromZone = ZoneType.DECK,
            toPlayerId = playerId,
            toZone = ZoneType.LIFE,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN,
            visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(playerId),
        )
        state = reducer.apply(state, listOf(moveEvent))
        accumulated += moveEvent
      }
      if (toDraw.isNotEmpty()) {
        val seq = state.lastEventSequence + 1
        val lifeChangedEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.LIFE_CHANGED,
          sourceCardId = null,
          payload = GameEventPayload.LifeChangedPayload(
            playerId = playerId,
            addedCardIds = toDraw,
            removedCardIds = emptyList(),
            destinationZone = null,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        state = reducer.apply(state, listOf(lifeChangedEvent))
        accumulated += lifeChangedEvent
      }
    }

    val seq2 = state.lastEventSequence + 1
    val readyEvent = GameEvent(
      id = EventId("${state.id.value}:$seq2"),
      sequence = seq2,
      type = GameEventType.SETUP_STEP_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.SetupStepChangedPayload(GameSetupStep.LIFE, GameSetupStep.READY),
      visibility = EventVisibility.PUBLIC,
    )
    state = reducer.apply(state, listOf(readyEvent))
    accumulated += readyEvent

    val seq3 = state.lastEventSequence + 1
    val startedEvent = GameEvent(
      id = EventId("${state.id.value}:$seq3"),
      sequence = seq3,
      type = GameEventType.GAME_STARTED,
      sourceCardId = null,
      payload = GameEventPayload.GameStartedPayload(state.firstPlayerId),
      visibility = EventVisibility.PUBLIC,
    )
    state = reducer.apply(state, listOf(startedEvent))
    accumulated += startedEvent

    val firstTurnTransition = rulesEngine.startTurn(state, state.firstPlayerId, 1)
    if (firstTurnTransition.events.isNotEmpty()) {
      state = reducer.apply(state, firstTurnTransition.events)
      accumulated += firstTurnTransition.events
    }
  }

  private fun runVmStart(
    request: ProgramRequest,
    accumulated: MutableList<GameEvent>,
  ): CommandResult? {
    val vmTransition = virtualMachine.start(request.program, request.context, state, request.executionId)
    return applyVmTransition(vmTransition, accumulated)
  }

  private fun runVmResume(
    frame: ProgramFrame,
    choice: PlayerChoice,
    accumulated: MutableList<GameEvent>,
  ): CommandResult? {
    val vmTransition = virtualMachine.resume(frame, choice, state)
    return applyVmTransition(vmTransition, accumulated)
  }

  private fun applyVmTransition(
    vmTransition: VmTransition,
    accumulated: MutableList<GameEvent>,
  ): CommandResult? {
    return when (vmTransition) {
      is VmTransition.VmRunning -> {
        if (vmTransition.events.isNotEmpty()) {
          state = reducer.apply(state, vmTransition.events)
          accumulated += vmTransition.events
        }
        null
      }

      is VmTransition.VmCompleted -> {
        if (vmTransition.events.isNotEmpty()) {
          state = reducer.apply(state, vmTransition.events)
          accumulated += vmTransition.events
        }
        null
      }

      is VmTransition.VmSuspended -> {
        if (vmTransition.events.isNotEmpty()) {
          state = reducer.apply(state, vmTransition.events)
          accumulated += vmTransition.events
        }
        null
      }

      is VmTransition.VmFailed -> {
        if (vmTransition.events.isNotEmpty()) {
          state = reducer.apply(state, vmTransition.events)
          accumulated += vmTransition.events
        }
        val seq = state.lastEventSequence + 1
        val failEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.EFFECT_FAILED,
          sourceCardId = null,
          payload = GameEventPayload.EffectFailedPayload(
            effectId = EffectId("${state.id.value}:fail:$seq"),
            errorCode = vmTransition.error.code,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        state = reducer.apply(state, listOf(failEvent))
        accumulated += failEvent
        CommandResult.Failed(vmTransition.error, accumulated.toList(), state.lastEventSequence)
      }
    }
  }

  private fun drainAutomaticWork(accumulated: MutableList<GameEvent>): CommandResult? {
    var iterations = 0
    val maxIterations = 1000
    while (iterations++ < maxIterations) {
      if (state.pendingChoices.isNotEmpty()) break
      if (state.status == GameStatus.FINISHED) break

      val autoEvents = rulesEngine.processEvents(state, accumulated.takeLast(accumulated.size))
      val autoRules = rulesEngine.processAutomaticRules(state)

      val hasAutoEventWork = autoEvents.events.isNotEmpty() || autoEvents.programsToRun.isNotEmpty()
      val hasAutoRuleWork = autoRules.events.isNotEmpty() || autoRules.programsToRun.isNotEmpty()

      if (!hasAutoEventWork && !hasAutoRuleWork) break

      for (transition in listOf(autoEvents, autoRules)) {
        if (transition.events.isNotEmpty()) {
          state = reducer.apply(state, transition.events)
          accumulated += transition.events
        }
        for (request in transition.programsToRun) {
          val vmResult = runVmStart(request, accumulated)
          if (vmResult is CommandResult.Failed) return vmResult
        }
        if (transition.choiceToResume != null) {
          val choice = transition.choiceToResume
          val frame = state.activePrograms.firstOrNull { it.pendingChoiceId == choice.choiceId }
          if (frame != null) {
            val vmResult = runVmResume(frame, choice, accumulated)
            if (vmResult is CommandResult.Failed) return vmResult
          }
        }
      }
    }
    return null
  }

  private fun buildEntry(command: GameCommand?, events: List<GameEvent>): GameLogEntry {
    val lastSequence = history.log!!.entries.lastOrNull()?.sequence ?: 0L
    return GameLogEntry(
      sequence = lastSequence + 1,
      command = command,
      events = events,
    )
  }

  private fun recordAutoEntry(events: List<GameEvent>) {
    if (events.isEmpty()) return
    val entry = buildEntry(null, events)
    history.append(entry)
  }

  private fun isSetupCommand(command: GameCommand, setup: GameSetupState): Boolean {
    return when (command) {
      is ResolveMulliganCommand -> setup.step == GameSetupStep.MULLIGAN
      is SubmitChoiceCommand -> true
      is ConcedeCommand -> true
      else -> false
    }
  }

  private fun checkMulligansComplete(accumulated: MutableList<GameEvent>) {
    val setup = state.setup ?: return
    if (setup.step == GameSetupStep.MULLIGAN && setup.pendingMulliganPlayers.isEmpty()) {
      advanceToLife(accumulated)
      recordAutoEntry(accumulated)
    }
  }

  private fun buildPublicView(targetPlayerId: PlayerId, viewerId: PlayerId): PlayerPublicView {
    val player = state.players[targetPlayerId] ?: return PlayerPublicView(
      playerId = targetPlayerId,
      leader = null,
      characters = emptyList(),
      stage = null,
      lifeCount = 0,
      trash = emptyList(),
      deckCount = 0,
      costDon = emptyList(),
      attachedDon = emptyMap(),
    )

    val leaderCardId = player.leader.cards.firstOrNull()
    val leaderView = leaderCardId?.let { buildCardView(it, viewerId, authorized = true) }

    val characterViews = player.characters.cards.map { buildCardView(it, viewerId, authorized = true) }

    val stageCardId = player.stage.cards.firstOrNull()
    val stageView = stageCardId?.let { buildCardView(it, viewerId, authorized = true) }

    val trashViews = player.trash.cards.map { cardId ->
      val card = state.cards[cardId]
      val revealed = card != null && isVisibleTo(card.visibility, viewerId, card.ownerId)
      buildCardView(cardId, viewerId, authorized = revealed)
    }

    val costDonViews = player.cost.cards.map { buildCardView(it, viewerId, authorized = true) }

    val attachedDonMap = state.donAttachments.filter { attachment ->
      val host = state.cards[attachment.hostCardId]
      host != null && (host.ownerId == targetPlayerId || rulesEngine.effectiveController(
        state, host.id
      ) == targetPlayerId)
    }.associate { it.hostCardId to it.donCardIds.size }

    return PlayerPublicView(
      playerId = targetPlayerId,
      leader = leaderView,
      characters = characterViews,
      stage = stageView,
      lifeCount = player.life.cards.size,
      trash = trashViews,
      deckCount = player.deck.cards.size,
      costDon = costDonViews,
      attachedDon = attachedDonMap,
    )
  }

  private fun buildCardView(cardId: CardInstanceId, viewerId: PlayerId, authorized: Boolean): CardView {
    val card = state.cards[cardId] ?: return CardView(
      id = null,
      definition = null,
      controllerId = null,
      orientation = CardOrientation.NOT_APPLICABLE,
      faceState = FaceState.FACE_DOWN,
      characteristics = null,
      attachedDonCount = 0,
    )

    val canSeeIdentity = authorized && isVisibleTo(card.visibility, viewerId, card.ownerId)
    val definition = if (canSeeIdentity) cardCatalog.get(card.definitionId) else null
    val characteristics = if (canSeeIdentity) rulesEngine.effectiveCharacteristics(state, cardId) else null
    val donCount = state.donAttachments.firstOrNull { it.hostCardId == cardId }?.donCardIds?.size ?: 0

    return CardView(
      id = if (canSeeIdentity) cardId else null,
      definition = definition,
      controllerId = if (canSeeIdentity) rulesEngine.effectiveController(state, cardId) ?: card.controllerId else null,
      orientation = card.orientation,
      faceState = card.faceState,
      characteristics = characteristics,
      attachedDonCount = donCount,
    )
  }

  private fun isVisibleTo(visibility: CardVisibility, playerId: PlayerId, ownerId: PlayerId? = null): Boolean =
    when (visibility) {
      is CardVisibility.PUBLIC -> true
      is CardVisibility.OWNER_ONLY -> ownerId == playerId
      is CardVisibility.PLAYERS -> playerId in visibility.playerIds
    }

  private fun collectPublicEvents(viewerId: PlayerId): List<GameEvent> {
    val log = history.log ?: return emptyList()
    val allEvents = (log.initialEvents + log.entries.flatMap { it.events })
    return allEvents.filter { event ->
      when (val vis = event.visibility) {
        is EventVisibility.PUBLIC -> true
        is EventVisibility.PRIVATE_TO_PLAYER -> vis.playerId == viewerId
        is EventVisibility.PRIVATE_TO_PLAYERS -> viewerId in vis.playerIds
        is EventVisibility.INTERNAL -> false
      }
    }
  }
}

object GameSerialization {
  const val FORMAT_VERSION = 1

  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = true
    classDiscriminator = "type"
  }

  @Serializable
  private data class Envelope<T>(val formatVersion: Int, val payload: T)

  private fun <T> encode(serializer: KSerializer<T>, payload: T): String =
    json.encodeToString(Envelope.serializer(serializer), Envelope(FORMAT_VERSION, payload))

  private fun <T> decode(serializer: KSerializer<T>, raw: String): T {
    val envelope = json.decodeFromString(Envelope.serializer(serializer), raw)
    require(envelope.formatVersion == FORMAT_VERSION) {
      "Unsupported game format version ${envelope.formatVersion}, expected $FORMAT_VERSION"
    }
    return envelope.payload
  }

  fun encodeLog(log: GameLog): String = encode(GameLog.serializer(), log)

  fun decodeLog(raw: String): GameLog = decode(GameLog.serializer(), raw)

  fun decodeLog(raw: String, expectedManifest: GameManifest): GameLog {
    val log = decodeLog(raw)
    require(log.manifest == expectedManifest) {
      "Log manifest does not match expected: expected=$expectedManifest, actual=${log.manifest}"
    }
    return log
  }

  fun encodeSnapshot(snapshot: GameSnapshot): String = encode(GameSnapshot.serializer(), snapshot)

  fun decodeSnapshot(raw: String): GameSnapshot = decode(GameSnapshot.serializer(), raw)

  fun encodeEntry(entry: GameLogEntry): String = encode(GameLogEntry.serializer(), entry)

  fun decodeEntry(raw: String): GameLogEntry = decode(GameLogEntry.serializer(), raw)

  fun encodeState(state: GameState): String = encode(GameState.serializer(), state)

  fun decodeState(raw: String): GameState = decode(GameState.serializer(), raw)

  fun encodeFrame(frame: ProgramFrame): String = encode(ProgramFrame.serializer(), frame)

  fun decodeFrame(raw: String): ProgramFrame = decode(ProgramFrame.serializer(), raw)

  fun encodeChoice(choice: PendingChoice): String = encode(PendingChoice.serializer(), choice)

  fun decodeChoice(raw: String): PendingChoice = decode(PendingChoice.serializer(), raw)
}