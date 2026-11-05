package com.lucasalfare.opcg.rules

import com.lucasalfare.opcg.*

internal class DamageProcessor(
  private val cardCatalog: CardCatalog,
) {

  fun processDamage(
    state: GameState,
    battle: BattleState,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    state.cards[battle.attackerId] ?: return endBattleOnly(state, battle, BattleOutcome.INTERRUPTED)

    val attackerPower = continuous.effectiveCharacteristics(state, battle.attackerId)?.power ?: 0

    val targetPlayerId: PlayerId
    val targetCardId: CardInstanceId?
    val isLeaderTarget: Boolean

    when (val target = battle.currentTarget) {
      is BattleTarget.CardTarget -> {
        val targetCard =
          state.cards[target.cardInstanceId] ?: return endBattleOnly(state, battle, BattleOutcome.INTERRUPTED)
        val targetController = continuous.effectiveController(state, target.cardInstanceId) ?: targetCard.controllerId
        val player = state.players[targetController] ?: return endBattleOnly(state, battle, BattleOutcome.INTERRUPTED)
        isLeaderTarget = target.cardInstanceId in player.leader.cards
        targetPlayerId = targetController
        targetCardId = target.cardInstanceId
      }

      is BattleTarget.PlayerTarget -> {
        isLeaderTarget = true
        targetPlayerId = target.playerId
        targetCardId = state.players[target.playerId]?.leader?.cards?.firstOrNull()
      }
    }

    val targetBasePower = targetCardId?.let {
      continuous.effectiveCharacteristics(state, it)?.power ?: 0
    } ?: 0
    val finalTargetPower = targetBasePower + battle.counterPower

    if (attackerPower < finalTargetPower) {
      return endBattleOnly(state, battle, BattleOutcome.ATTACKER_LOST)
    }

    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    var seq = state.lastEventSequence

    if (isLeaderTarget) {
      val attackerChars = continuous.effectiveCharacteristics(state, battle.attackerId)
      val isDoubleAttack = attackerChars?.keywords?.contains(KeywordEffect.DOUBLE_ATTACK) == true
      val isBanish = attackerChars?.keywords?.contains(KeywordEffect.BANISH) == true
      val damageCount = if (isDoubleAttack) 2 else 1

      val prevention = evaluateDamagePrevention(state, targetPlayerId, evaluator, continuous)

      val result = applyLifeDamage(
        state = state,
        targetPlayerId = targetPlayerId,
        attackerId = battle.attackerId,
        effectId = null,
        isBanish = isBanish,
        damageCount = damageCount,
        prevention = prevention,
        evaluator = evaluator,
        startSeq = seq,
        enableTrigger = true,
      )
      events += result.events
      programs += result.programs
      seq = result.lastSeq
    } else if (targetCardId != null) {
      val targetCard = state.cards[targetCardId]
      if (targetCard != null) {
        val ownerId = targetCard.ownerId
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_KNOCKED_OUT,
          sourceCardId = targetCardId,
          payload = GameEventPayload.CardKnockedOutPayload(targetCardId, ownerId, battle.attackerId, null),
          visibility = EventVisibility.PUBLIC,
        )

        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = targetCardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = targetCardId,
            fromPlayerId = ownerId,
            fromZone = ZoneType.CHARACTER,
            toPlayerId = ownerId,
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
    }

    seq += 1
    events += endBattleEvent(state, battle, BattleOutcome.ATTACKER_WON, seq)

    return GameTransition(events, programs)
  }

  fun executeKnockOut(
    state: GameState,
    instruction: KnockOut,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    replacementProcessor: ReplacementProcessor,
    continuous: ContinuousEffectProcessor,
    cardCatalog: CardCatalog,
  ): InstructionExecutionResult {
    val cardIds = when (val source = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> source.value
      is StoredValue.CardValue -> listOf(source.value)
      else -> return InstructionExecutionResult.Completed(null, emptyList())
    }

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    for (cardId in cardIds) {
      val card = state.cards[cardId] ?: continue
      val ownerId = card.ownerId

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_KNOCKED_OUT,
        sourceCardId = cardId,
        payload = GameEventPayload.CardKnockedOutPayload(cardId, ownerId, context.sourceCardId, instruction.effectId),
        visibility = EventVisibility.PUBLIC,
      )

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = cardId,
          fromPlayerId = ownerId,
          fromZone = ZoneType.CHARACTER,
          toPlayerId = ownerId,
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

  fun executeDealDamage(
    state: GameState,
    instruction: DealDamage,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    evaluator: RulesEngine,
    continuous: ContinuousEffectProcessor,
  ): InstructionExecutionResult {
    val amountResult = evaluator.evaluateValue(instruction.amount, state, context, variables)
    if (amountResult is EvaluationResult.Failed) return InstructionExecutionResult.Failed(amountResult.error)
    val amount = (amountResult as EvaluationResult.Success).value.let {
      (it as? StoredValue.IntegerValue)?.value ?: return InstructionExecutionResult.Completed(null, emptyList())
    }.coerceAtLeast(0)
    if (amount == 0) return InstructionExecutionResult.Completed(null, emptyList())

    state.players[instruction.playerId] ?: return InstructionExecutionResult.Completed(null, emptyList())

    val isBanish = context.sourceCardId?.let { sourceId ->
      continuous.effectiveCharacteristics(state, sourceId)?.keywords?.contains(KeywordEffect.BANISH) == true
    } ?: false

    val prevention = evaluateDamagePrevention(state, instruction.playerId, evaluator, continuous)

    val result = applyLifeDamage(
      state = state,
      targetPlayerId = instruction.playerId,
      attackerId = context.sourceCardId,
      effectId = instruction.effectId,
      isBanish = isBanish,
      damageCount = amount,
      prevention = prevention,
      evaluator = evaluator,
      startSeq = state.lastEventSequence,
      enableTrigger = false,
    )

    return InstructionExecutionResult.Completed(null, result.events)
  }

  fun executeSetLife(
    state: GameState,
    instruction: SetLife,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = when (val source = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> source.value
      is StoredValue.CardValue -> listOf(source.value)
      else -> return InstructionExecutionResult.Completed(null, emptyList())
    }

    val player = state.players[instruction.playerId] ?: return InstructionExecutionResult.Completed(null, emptyList())
    val trashCards = player.trash.cards.toSet()

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    for (cardId in cardIds) {
      if (cardId !in trashCards) continue

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_MOVED,
        sourceCardId = cardId,
        payload = GameEventPayload.CardMovedPayload(
          cardId = cardId,
          fromPlayerId = instruction.playerId,
          fromZone = ZoneType.TRASH,
          toPlayerId = instruction.playerId,
          toZone = ZoneType.LIFE,
          destinationIndex = when (instruction.position) {
            LifePosition.TOP -> 0
            else -> null
          },
          newObjectId = null,
          orientation = CardOrientation.NOT_APPLICABLE,
          faceState = FaceState.FACE_DOWN,
          visibility = CardVisibility.OWNER_ONLY,
        ),
        visibility = EventVisibility.PUBLIC,
      )

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.LIFE_CHANGED,
        sourceCardId = null,
        payload = GameEventPayload.LifeChangedPayload(instruction.playerId, listOf(cardId), emptyList(), ZoneType.LIFE),
        visibility = EventVisibility.PUBLIC,
      )
    }

    return InstructionExecutionResult.Completed(null, events)
  }

  private fun applyLifeDamage(
    state: GameState,
    targetPlayerId: PlayerId,
    attackerId: CardInstanceId?,
    effectId: EffectId?,
    isBanish: Boolean,
    damageCount: Int,
    prevention: Int,
    evaluator: RulesEngine,
    startSeq: Long,
    enableTrigger: Boolean,
  ): LifeDamageResult {
    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    var seq = startSeq

    val defenderPlayer = state.players[targetPlayerId] ?: return LifeDamageResult(events, programs, seq)
    val remainingLife = defenderPlayer.life.cards.toMutableList()
    var preventionRemaining = prevention
    val consumedTriggerKeys = mutableSetOf<AbilityUsageKey>()

    for (i in 0 until damageCount) {
      if (preventionRemaining > 0) {
        preventionRemaining -= 1
        continue
      }

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DAMAGE_PROCESSED,
        sourceCardId = attackerId,
        payload = GameEventPayload.DamageProcessedPayload(targetPlayerId, 1, attackerId, effectId),
        visibility = EventVisibility.PUBLIC,
      )

      if (remainingLife.isEmpty()) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.PLAYER_LOST,
          sourceCardId = attackerId,
          payload = GameEventPayload.PlayerLostPayload(targetPlayerId, GameEndReason.LIFE_DEFEAT),
          visibility = EventVisibility.PUBLIC,
        )
        break
      }

      val lifeCardId = remainingLife.removeAt(0)

      if (isBanish) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = lifeCardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = lifeCardId,
            fromPlayerId = targetPlayerId,
            fromZone = ZoneType.LIFE,
            toPlayerId = targetPlayerId,
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
          type = GameEventType.LIFE_CHANGED,
          sourceCardId = null,
          payload = GameEventPayload.LifeChangedPayload(
            targetPlayerId, emptyList(), listOf(lifeCardId), ZoneType.TRASH
          ),
          visibility = EventVisibility.PUBLIC,
        )
      } else {
        seq += 1
        val movedEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = lifeCardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = lifeCardId,
            fromPlayerId = targetPlayerId,
            fromZone = ZoneType.LIFE,
            toPlayerId = targetPlayerId,
            toZone = ZoneType.HAND,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN,
            visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PUBLIC,
        )
        events += movedEvent
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.LIFE_CHANGED,
          sourceCardId = null,
          payload = GameEventPayload.LifeChangedPayload(
            targetPlayerId, emptyList(), listOf(lifeCardId), ZoneType.HAND
          ),
          visibility = EventVisibility.PUBLIC,
        )

        if (enableTrigger) {
          val triggerSeq = seq + 1
          val triggerResult = buildTriggerRequest(
            state = state,
            lifeCardId = lifeCardId,
            controllerId = targetPlayerId,
            triggerEvent = movedEvent,
            seq = triggerSeq,
            consumedKeys = consumedTriggerKeys,
          )
          if (triggerResult != null) {
            seq = triggerSeq
            events += triggerResult.first
            programs += triggerResult.second
          }
        }
      }
    }

    return LifeDamageResult(events, programs, seq)
  }

  private fun buildTriggerRequest(
    state: GameState,
    lifeCardId: CardInstanceId,
    controllerId: PlayerId,
    triggerEvent: GameEvent,
    seq: Long,
    consumedKeys: MutableSet<AbilityUsageKey>,
  ): Pair<GameEvent, ProgramRequest>? {
    val card = state.cards[lifeCardId] ?: return null
    val def = cardCatalog.get(card.definitionId) ?: return null
    val triggerAbility = def.abilities.filterIsInstance<ActivatedAbilityDefinition>()
      .firstOrNull { it.timing == ActivationTiming.ON_TRIGGER } ?: return null
    if (triggerAbility.cost !is Cost.NoCost) return null

    if (triggerAbility.usageLimit == UsageLimit.ONCE_PER_TURN) {
      val key = AbilityUsageKey(card.objectId, triggerAbility.id, state.turn?.number ?: 0)
      if (key in consumedKeys) return null
      if ((state.abilityUsage[key] ?: 0) >= 1) return null
      consumedKeys.add(key)
    }

    val activationEvent = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = lifeCardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = EffectId("${state.id.value}:trigger:$seq"),
        sourceCardId = lifeCardId,
        abilityId = triggerAbility.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    val request = ProgramRequest(
      executionId = ExecutionId(activationEvent.id.value),
      program = triggerAbility.program,
      context = EffectContext(
        gameId = state.id,
        controllerId = controllerId,
        sourceCardId = lifeCardId,
        abilityId = triggerAbility.id,
        triggeringEvent = triggerEvent,
      ),
    )

    return activationEvent to request
  }

  private fun evaluateDamagePrevention(
    state: GameState,
    playerId: PlayerId,
    evaluator: RulesEngine,
    continuous: ContinuousEffectProcessor,
  ): Int {
    var total = 0
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.DamagePreventionModifier ?: continue
      if (modifier.playerId != playerId) continue
      if (!continuous.isEffectActive(state, effect)) continue
      val ctx = EffectContext(
        gameId = state.id,
        controllerId = effect.controllerId,
        sourceCardId = effect.sourceCardId,
        abilityId = effect.sourceAbilityId,
        triggeringEvent = null,
      )
      val result = evaluator.evaluateValue(modifier.amount, state, ctx, emptyMap())
      if (result is EvaluationResult.Success) {
        val value = (result.value as? StoredValue.IntegerValue)?.value ?: 0
        total += value
      }
    }
    return total
  }

  private fun endBattleOnly(
    state: GameState,
    battle: BattleState,
    outcome: BattleOutcome,
  ): GameTransition {
    val seq = state.lastEventSequence + 1
    return GameTransition(listOf(endBattleEvent(state, battle, outcome, seq)), emptyList())
  }

  private fun endBattleEvent(
    state: GameState,
    battle: BattleState,
    outcome: BattleOutcome,
    seq: Long,
  ): GameEvent = GameEvent(
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

  private data class LifeDamageResult(
    val events: List<GameEvent>,
    val programs: List<ProgramRequest>,
    val lastSeq: Long,
  )
}

internal class RuleProcessor {

  fun processRules(state: GameState): GameTransition {
    return GameTransition(emptyList(), emptyList())
  }
}

internal class LoopProcessor {

  data class LoopResolution(
    val events: List<GameEvent>,
    val repetitions: Int?,
    val draw: Boolean,
  )

  fun signatureOf(state: GameState): String = GameSerialization.encodeState(state.copy(lastEventSequence = 0))

  fun isRepeated(state: GameState, seenSignatures: Collection<String>): Boolean {
    return signatureOf(state) in seenSignatures
  }

  fun resolve(
    state: GameState,
    interruptibleBy: Set<PlayerId>,
    repetitions: Map<PlayerId, Int>,
  ): LoopResolution {
    if (interruptibleBy.isEmpty()) {
      return LoopResolution(
        events = listOf(drawEvent(state)),
        repetitions = null,
        draw = true,
      )
    }
    if (interruptibleBy.size == 1) {
      val player = interruptibleBy.first()
      return LoopResolution(
        events = emptyList(),
        repetitions = repetitions[player] ?: 0,
        draw = false,
      )
    }
    val turnPlayer = state.turn?.playerId ?: state.playerOrder.first()
    val nonTurnPlayer = state.playerOrder.firstOrNull { it != turnPlayer } ?: turnPlayer
    val turnReps = repetitions[turnPlayer] ?: 0
    val nonTurnReps = repetitions[nonTurnPlayer] ?: 0
    return LoopResolution(
      events = emptyList(),
      repetitions = minOf(turnReps, nonTurnReps),
      draw = false,
    )
  }

  private fun drawEvent(state: GameState): GameEvent {
    val seq = state.lastEventSequence + 1
    return GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.GAME_DRAWN,
      sourceCardId = null,
      payload = GameEventPayload.GameDrawnPayload(state.players.keys, GameEndReason.DRAW),
      visibility = EventVisibility.PUBLIC,
    )
  }
}

internal class ContinuousEffectProcessor(
  private val cardCatalog: CardCatalog,
) {

  fun effectiveController(state: GameState, cardId: CardInstanceId): PlayerId? {
    val instance = state.cards[cardId] ?: return null
    val controlEffects = state.activeEffects.filter { effect ->
      effect.modifier is Modifier.ControlModifier && (effect.targetCardIds.isEmpty() || cardId in effect.targetCardIds) && isEffectActive(
        state, effect
      )
    }.sortedBy { it.timestampSequence }
    if (controlEffects.isEmpty()) return instance.controllerId
    val last = controlEffects.last().modifier as Modifier.ControlModifier
    return last.controllerId
  }

  fun effectiveCharacteristics(state: GameState, cardId: CardInstanceId): EffectiveCharacteristics? {
    val instance = state.cards[cardId] ?: return null
    val definition = cardCatalog.get(instance.definitionId) ?: return null

    var name = definition.name
    var colors = definition.colors.toMutableSet()
    var types = definition.types.toMutableSet()
    var attributes = definition.attributes.toMutableSet()
    var keywords = definition.keywords.toMutableSet()
    var basePower = definition.basePower
    var baseCost = definition.baseCost
    var life = definition.life
    var counter = definition.counter
    var powerDelta = 0
    var costDelta = 0

    val effects = state.activeEffects.filter { effect ->
      (effect.targetCardIds.isEmpty() || cardId in effect.targetCardIds) && isEffectActive(state, effect)
    }.sortedBy { it.timestampSequence }

    for (effect in effects) {
      when (val modifier = effect.modifier) {
        is Modifier.CharacteristicModifier -> {
          when (modifier.characteristic) {
            NumericCharacteristic.BASE_POWER -> {
              basePower = applyNumeric(basePower, modifier.operation, constantValue(modifier.value))
            }

            NumericCharacteristic.BASE_COST -> {
              baseCost = applyNumeric(baseCost, modifier.operation, constantValue(modifier.value))
            }

            NumericCharacteristic.POWER -> {
              powerDelta = applyDelta(powerDelta, modifier.operation, constantValue(modifier.value), basePower)
            }

            NumericCharacteristic.COST -> {
              costDelta = applyDelta(costDelta, modifier.operation, constantValue(modifier.value), baseCost)
            }

            NumericCharacteristic.LIFE -> {
              life = applyNumeric(life, modifier.operation, constantValue(modifier.value))
            }

            NumericCharacteristic.COUNTER -> {
              counter = applyNumeric(counter, modifier.operation, constantValue(modifier.value))
            }
          }
        }

        is Modifier.PropertyModifier -> {
          when (modifier.property) {
            CardProperty.NAME -> if (modifier.value is PropertyValue.StringValue) name = modifier.value.value
            CardProperty.COLOR -> if (modifier.value is PropertyValue.ColorSetValue) colors =
              modifier.value.value.toMutableSet()

            CardProperty.TYPE -> if (modifier.value is PropertyValue.TypeSetValue) types =
              modifier.value.value.toMutableSet()

            CardProperty.ATTRIBUTE -> if (modifier.value is PropertyValue.AttributeSetValue) attributes =
              modifier.value.value.toMutableSet()

            CardProperty.KEYWORDS -> if (modifier.value is PropertyValue.KeywordSetValue) keywords =
              modifier.value.value.toMutableSet()
          }
        }

        is Modifier.KeywordModifier -> {
          when (modifier.operation) {
            KeywordOperation.ADD -> keywords += modifier.keyword
            KeywordOperation.REMOVE -> keywords -= modifier.keyword
          }
        }

        else -> Unit
      }
    }

    var power = (basePower ?: 0) + powerDelta
    val cost = (baseCost ?: 0) + costDelta

    effectiveController(state, cardId) ?: instance.controllerId
    if (definition.category in setOf(
        CardCategory.LEADER, CardCategory.CHARACTER
      )
    ) {
      val attached = state.donAttachments.firstOrNull { it.hostCardId == cardId }?.donCardIds?.size ?: 0
      power += attached * 1000
    }

    return EffectiveCharacteristics(
      name = name,
      colors = colors,
      types = types,
      attributes = attributes,
      cost = if (definition.baseCost != null || costDelta != 0) cost else definition.baseCost,
      power = if (definition.basePower != null || powerDelta != 0 || state.donAttachments.any { it.hostCardId == cardId }) power else definition.basePower,
      life = life,
      counter = counter,
      keywords = keywords,
    )
  }

  fun isEffectActive(state: GameState, effect: ActiveEffect): Boolean {
    if (effect.duration is Duration.WhileSourceValid) {
      val source = effect.sourceCardId ?: return false
      if (source !in state.cards) return false
    }
    if (effect.duration is Duration.Permanent) return true
    if (effect.duration is Duration.DuringBattle) return state.battle != null
    return true
  }

  fun effectsToExpire(state: GameState, triggeringEvents: List<GameEvent>): List<ActiveEffect> {
    val expired = mutableListOf<ActiveEffect>()
    for (effect in state.activeEffects) {
      when (val duration = effect.duration) {
        is Duration.UntilEndOfTurn -> {
          if (triggeringEvents.any { it.type == GameEventType.TURN_ENDED }) expired += effect
        }

        is Duration.UntilEndOfPhase -> {
          if (triggeringEvents.any { it.type == GameEventType.PHASE_CHANGED }) expired += effect
        }

        is Duration.DuringBattle -> {
          if (triggeringEvents.any { it.type == GameEventType.BATTLE_ENDED }) expired += effect
        }

        is Duration.UntilEvent -> {
          if (triggeringEvents.any { matchesPattern(duration.pattern, it, state) }) expired += effect
        }

        is Duration.WhileSourceValid -> {
          val source = effect.sourceCardId
          if (source == null || source !in state.cards) expired += effect
        }

        else -> Unit
      }
    }
    return expired
  }

  private fun matchesPattern(pattern: GameEventPattern, event: GameEvent, state: GameState): Boolean {
    return !(pattern.eventTypes.isNotEmpty() && event.type !in pattern.eventTypes)
  }

  private fun constantValue(expr: ValueExpr): Int {
    return when (expr) {
      is ValueExpr.NumberValue -> expr.value
      else -> 0
    }
  }

  private fun applyNumeric(current: Int?, operation: NumericOperation, value: Int): Int {
    val base = current ?: 0
    return when (operation) {
      NumericOperation.ADD -> base + value
      NumericOperation.SUBTRACT -> base - value
      NumericOperation.SET -> value
    }
  }

  private fun applyDelta(currentDelta: Int, operation: NumericOperation, value: Int, base: Int?): Int {
    return when (operation) {
      NumericOperation.ADD -> currentDelta + value
      NumericOperation.SUBTRACT -> currentDelta - value
      NumericOperation.SET -> value - (base ?: 0)
    }
  }
}

internal class ReplacementProcessor {

  fun matchingReplacements(state: GameState, event: GameEvent): List<ActiveEffect> {
    return state.activeEffects.filter { effect ->
      val modifier = effect.modifier
      modifier is Modifier.ReplacementModifier && event.type in modifier.definition.pattern.eventTypes
    }.sortedWith(compareBy<ActiveEffect> {
      (it.modifier as Modifier.ReplacementModifier).definition.priority
    }.thenBy { it.timestampSequence })
  }

  fun buildActivation(
    state: GameState,
    effect: ActiveEffect,
    replacedEvent: GameEvent,
  ): Pair<GameEvent, ProgramRequest>? {
    val modifier = effect.modifier as? Modifier.ReplacementModifier ?: return null
    val sequence = state.lastEventSequence + 1
    val activationId = EffectId("${state.id.value}:repl:$sequence")
    val activated = GameEvent(
      id = EventId("${state.id.value}:$sequence"),
      sequence = sequence,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = effect.sourceCardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = activationId,
        sourceCardId = effect.sourceCardId,
        abilityId = effect.sourceAbilityId,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    val appliedSequence = sequence + 1
    GameEvent(
      id = EventId("${state.id.value}:$appliedSequence"),
      sequence = appliedSequence,
      type = GameEventType.REPLACEMENT_APPLIED,
      sourceCardId = effect.sourceCardId,
      payload = GameEventPayload.ReplacementAppliedPayload(
        replacementId = modifier.definition.id,
        replacedEventId = replacedEvent.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    val executionId = ExecutionId("${activated.id.value}:exec")
    val request = ProgramRequest(
      executionId = executionId,
      program = modifier.definition.program,
      context = EffectContext(
        gameId = state.id,
        controllerId = effect.controllerId,
        sourceCardId = effect.sourceCardId,
        abilityId = effect.sourceAbilityId,
        triggeringEvent = replacedEvent,
      ),
    )
    return activated to request
  }
}

internal class CostProcessor {

  fun effectiveCost(
    state: GameState,
    cardId: CardInstanceId,
    continuous: ContinuousEffectProcessor,
    catalog: CardCatalog,
  ): Int {
    val effective = continuous.effectiveCharacteristics(state, cardId)
    if (effective?.cost != null) return effective.cost.coerceAtLeast(0)
    val def = state.cards[cardId]?.let { catalog.get(it.definitionId) }
    return def?.baseCost?.coerceAtLeast(0) ?: 0
  }

  fun selectActiveDon(state: GameState, playerId: PlayerId, amount: Int): List<CardInstanceId> {
    if (amount <= 0) return emptyList()
    val player = state.players[playerId] ?: return emptyList()
    return player.cost.cards.filter { id -> state.cards[id]?.orientation == CardOrientation.ACTIVE }.take(amount)
  }
}