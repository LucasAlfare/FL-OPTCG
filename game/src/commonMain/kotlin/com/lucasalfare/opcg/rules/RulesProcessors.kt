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
    replacementProcessor: ReplacementProcessor,
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
    } else if (targetCardId != null && !continuous.isKnockOutPrevented(
        state, targetCardId, KnockOutCause.BATTLE, battle.attackerId,
      )
    ) {
      val targetCard = state.cards[targetCardId]
      if (targetCard != null) {
        val ownerId = targetCard.ownerId
        seq += 1
        val koEvent = GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_KNOCKED_OUT,
          sourceCardId = targetCardId,
          payload = GameEventPayload.CardKnockedOutPayload(targetCardId, ownerId, battle.attackerId, null),
          visibility = EventVisibility.PUBLIC,
        )
        events += koEvent

        val replacement = replacementProcessor.matchingReplacements(state, koEvent, continuous, evaluator).firstOrNull()

        val activation = replacement?.let {
          replacementProcessor.buildActivation(state, seq + 1, it, koEvent)
        }

        if (activation != null) {
          events += activation.events
          programs += activation.request
          seq += 2
        } else {
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
    evaluator: RulesEngine,
  ): InstructionExecutionResult {
    val cardIds = when (val source = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> source.value
      is StoredValue.CardValue -> listOf(source.value)
      else -> return InstructionExecutionResult.Completed(null, emptyList())
    }

    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    var seq = state.lastEventSequence

    for (cardId in cardIds) {
      val card = state.cards[cardId] ?: continue
      if (continuous.isKnockOutPrevented(state, cardId, KnockOutCause.EFFECT, context.sourceCardId)) continue
      val ownerId = card.ownerId

      seq += 1
      val koEvent = GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.CARD_KNOCKED_OUT,
        sourceCardId = cardId,
        payload = GameEventPayload.CardKnockedOutPayload(cardId, ownerId, context.sourceCardId, instruction.effectId),
        visibility = EventVisibility.PUBLIC,
      )
      events += koEvent

      val replacement = replacementProcessor.matchingReplacements(state, koEvent, continuous, evaluator).firstOrNull()

      val activation = replacement?.let {
        replacementProcessor.buildActivation(state, seq + 1, it, koEvent)
      }

      if (activation != null) {
        events += activation.events
        programs += activation.request
        seq += 2
      } else {
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
    }

    return InstructionExecutionResult.Completed(null, events, programs)
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
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_REVEALED,
          sourceCardId = lifeCardId,
          payload = GameEventPayload.CardRevealedPayload(listOf(lifeCardId), CardVisibility.PUBLIC),
          visibility = EventVisibility.PUBLIC,
        )

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

        var handled = false
        if (enableTrigger) {
          val built = buildTriggerProgram(
            state = state,
            lifeCardId = lifeCardId,
            controllerId = targetPlayerId,
            startSeq = seq + 1,
            consumedKeys = consumedTriggerKeys,
          )
          if (built != null) {
            events += built.events
            programs += built.request
            seq = built.lastSeq
            handled = true
          }
        }

        if (!handled) {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.CARD_MOVED,
            sourceCardId = lifeCardId,
            payload = GameEventPayload.CardMovedPayload(
              cardId = lifeCardId,
              fromPlayerId = targetPlayerId,
              fromZone = ZoneType.TRASH,
              toPlayerId = targetPlayerId,
              toZone = ZoneType.HAND,
              destinationIndex = null,
              newObjectId = null,
              orientation = CardOrientation.NOT_APPLICABLE,
              faceState = FaceState.FACE_DOWN,
              visibility = CardVisibility.OWNER_ONLY,
            ),
            visibility = EventVisibility.PRIVATE_TO_PLAYER(targetPlayerId),
          )
        }
      }
    }

    return LifeDamageResult(events, programs, seq)
  }

  private fun buildTriggerProgram(
    state: GameState,
    lifeCardId: CardInstanceId,
    controllerId: PlayerId,
    startSeq: Long,
    consumedKeys: MutableSet<AbilityUsageKey>,
  ): TriggerProgramBuild? {
    val card = state.cards[lifeCardId] ?: return null
    val def = cardCatalog.get(card.definitionId) ?: return null
    val triggerAbility = def.abilities.filterIsInstance<ActivatedAbilityDefinition>()
      .firstOrNull { it.timing == ActivationTiming.ON_TRIGGER } ?: return null

    // Trigger não paga custo. Se a habilidade exigir custo, a carta não é
    // ativável como trigger opcional — tratamos como "sem trigger".
    if (triggerAbility.cost !is Cost.NoCost) return null

    // Respeita usage limit (raro em triggers, mas o modelo permite).
    if (triggerAbility.usageLimit == UsageLimit.ONCE_PER_TURN) {
      val key = AbilityUsageKey(card.objectId, triggerAbility.id, state.turn?.number ?: 0)
      if (key in consumedKeys) return null
      if ((state.abilityUsage[key] ?: 0) >= 1) return null
      consumedKeys.add(key)
    }

    val triggerCardVar = VariableId("${state.id.value}:trigger:${lifeCardId.value}:card")
    val decisionVar = VariableId("${state.id.value}:trigger:${lifeCardId.value}:decision")

    // Programa de Trigger:
    //   1. ChooseNumber(0..1) — "Ativar Trigger?" (1 = Sim, 0 = Não)
    //   2. Conditional:
    //      - ifTrue  → executa o efeito do Trigger (a carta já está no Trash,
    //                   e permanece lá ao final, salvo se o efeito mover).
    //      - ifFalse → move a carta do Trash para a mão do controlador.
    val program = Program(
      id = ProgramId("trigger:${lifeCardId.value}:$startSeq"),
      instructions = listOf(
        ChooseNumber(
          playerId = controllerId,
          minimum = ValueExpr.NumberValue(0),
          maximum = ValueExpr.NumberValue(1),
          destination = decisionVar,
        ),
        Conditional(
          condition = Condition.Comparison(
            left = ValueExpr.VariableValue(decisionVar),
            operator = ComparisonOperator.EQUAL,
            right = ValueExpr.NumberValue(1),
          ),
          ifTrue = triggerAbility.program.instructions,
          ifFalse = listOf(
            MoveCards(
              source = triggerCardVar,
              destination = ZoneDestination(controllerId, ZoneType.HAND),
              placement = Placement.DEFAULT,
            )
          ),
        ),
      ),
    )

    val activationEvent = GameEvent(
      id = EventId("${state.id.value}:$startSeq"),
      sequence = startSeq,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = lifeCardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = EffectId("${state.id.value}:trigger:$startSeq"),
        sourceCardId = lifeCardId,
        abilityId = triggerAbility.id,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    val request = ProgramRequest(
      executionId = ExecutionId(activationEvent.id.value),
      program = program,
      context = EffectContext(
        gameId = state.id,
        controllerId = controllerId,
        sourceCardId = lifeCardId,
        abilityId = triggerAbility.id,
        triggeringEvent = null,
      ),
      initialVariables = mapOf(triggerCardVar to StoredValue.CardValue(lifeCardId)),
    )

    return TriggerProgramBuild(
      events = listOf(activationEvent),
      request = request,
      lastSeq = startSeq,
    )
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

  private data class TriggerProgramBuild(
    val events: List<GameEvent>,
    val request: ProgramRequest,
    val lastSeq: Long,
  )
}

internal class RuleProcessor(
  private val loopProcessor: LoopProcessor = LoopProcessor(),
) {

  private val seenSignatures = LinkedHashSet<String>()
  private val maxHistory = 128

  fun resetLoopDetection() {
    seenSignatures.clear()
  }

  fun processRules(state: GameState): GameTransition {
    if (state.status == GameStatus.FINISHED) return GameTransition(emptyList(), emptyList())
    if (state.pendingChoices.isNotEmpty()) return GameTransition(emptyList(), emptyList())

    val signature = loopProcessor.signatureOf(state)
    if (signature in seenSignatures) {
      seenSignatures.clear()
      return GameTransition(loopProcessor.resolve(state, emptySet(), emptyMap()).events, emptyList())
    }
    seenSignatures += signature
    while (seenSignatures.size > maxHistory) {
      seenSignatures.remove(seenSignatures.first())
    }
    return GameTransition(emptyList(), emptyList())
  }
}

internal class LoopProcessor {

  data class LoopResolution(
    val events: List<GameEvent>,
    val repetitions: Int?,
    val draw: Boolean,
  )

  fun signatureOf(state: GameState): String {
    val normalized = state.copy(
      lastEventSequence = 0,
      abilityUsage = emptyMap(),
      activeEffects = state.activeEffects.map { it.copy(timestampSequence = 0) }.sortedBy { it.id.value },
      activePrograms = state.activePrograms.map { it.copy(stepCount = 0) }.sortedBy { it.id.value },
      randomState = state.randomState.copy(drawCount = 0),
    )
    return GameSerialization.encodeState(normalized)
  }

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

internal class DynamicModifierException(
  val code: String,
  message: String,
) : RuntimeException(message)

internal class ContinuousEffectProcessor(
  private val cardCatalog: CardCatalog,
  private val evaluator: RulesEngine,
) {

  private val dynamicEvaluations = mutableSetOf<EffectId>()

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
    val aliases = linkedSetOf<String>()

    val effects = state.activeEffects.filter { effect ->
      (effect.targetCardIds.isEmpty() || cardId in effect.targetCardIds) && isEffectActive(state, effect)
    }.sortedBy { it.timestampSequence }

    for (effect in effects) {
      when (val modifier = effect.modifier) {
        is Modifier.CharacteristicModifier -> {
          when (modifier.characteristic) {
            NumericCharacteristic.BASE_POWER -> {
              basePower = applyNumeric(basePower, modifier.operation, modifierValue(state, effect, modifier.value))
            }

            NumericCharacteristic.BASE_COST -> {
              baseCost = applyNumeric(baseCost, modifier.operation, modifierValue(state, effect, modifier.value))
            }

            NumericCharacteristic.POWER -> {
              powerDelta =
                applyDelta(powerDelta, modifier.operation, modifierValue(state, effect, modifier.value), basePower)
            }

            NumericCharacteristic.COST -> {
              costDelta =
                applyDelta(costDelta, modifier.operation, modifierValue(state, effect, modifier.value), baseCost)
            }

            NumericCharacteristic.LIFE -> {
              life = applyNumeric(life, modifier.operation, modifierValue(state, effect, modifier.value))
            }

            NumericCharacteristic.COUNTER -> {
              counter = applyNumeric(counter, modifier.operation, modifierValue(state, effect, modifier.value))
            }

            NumericCharacteristic.ATTACHED_DON_COUNT -> Unit
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

            CardProperty.CATEGORY -> Unit
          }
        }

        is Modifier.KeywordModifier -> {
          when (modifier.operation) {
            KeywordOperation.ADD -> keywords += modifier.keyword
            KeywordOperation.REMOVE -> keywords -= modifier.keyword
          }
        }

        is Modifier.NameAliasModifier -> aliases += modifier.name

        else -> Unit
      }
    }

    var power = (basePower ?: 0) + powerDelta
    val cost = (baseCost ?: 0) + costDelta

    effectiveController(state, cardId) ?: instance.controllerId
    val attachedDonCount = state.donAttachments.firstOrNull { it.hostCardId == cardId }?.donCardIds?.size ?: 0
    if (definition.category in setOf(
        CardCategory.LEADER, CardCategory.CHARACTER
      )
    ) {
      power += attachedDonCount * 1000
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
      attachedDonCount = attachedDonCount,
      aliases = aliases.toSet(),
    )
  }

  fun isEffectActive(state: GameState, effect: ActiveEffect): Boolean {
    return when (val duration = effect.duration) {
      is Duration.Permanent -> true
      is Duration.DuringBattle -> state.battle != null
      is Duration.WhileSourceValid -> isSourceValid(state, effect.sourceCardId)
      is Duration.WhileCondition -> isConditionSatisfied(state, effect, duration.condition)
      is Duration.UntilEndOfTurn, is Duration.UntilEndOfPhase, is Duration.UntilEvent, is Duration.UntilEndOfNextOpponentTurn -> true
    }
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
          if (triggeringEvents.any { matchesPattern(duration.pattern, it) }) expired += effect
        }

        is Duration.WhileSourceValid -> {
          if (!isSourceValid(state, effect.sourceCardId)) expired += effect
        }

        is Duration.WhileCondition -> {
          if (!isConditionSatisfied(state, effect, duration.condition)) expired += effect
        }

        is Duration.UntilEndOfNextOpponentTurn -> {
          val marker = effect.nextOpponentTurn
          if (marker != null && triggeringEvents.any { isOpponentTurnEnd(it, marker) }) expired += effect
        }

        is Duration.Permanent -> Unit
      }
    }
    return expired
  }

  fun isAbilityNegated(
    state: GameState,
    sourceCardId: CardInstanceId?,
    abilityId: AbilityId?,
  ): Boolean {
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.EffectNegationModifier ?: continue
      if (!isEffectActive(state, effect)) continue
      when (val scope = modifier.scope) {
        is EffectScope.All -> return true
        is EffectScope.Ability -> if (abilityId != null && scope.abilityId == abilityId) return true
        is EffectScope.Card -> if (sourceCardId != null && scope.cardInstanceId == sourceCardId) return true
        is EffectScope.CurrentBattle -> if (state.battle != null) return true
      }
    }
    return false
  }

  fun isActionRestricted(
    state: GameState,
    action: RuleAction,
    subjectCardId: CardInstanceId?,
  ): List<RuleViolation> {
    val violations = mutableListOf<RuleViolation>()
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.RestrictionModifier ?: continue
      if (!isEffectActive(state, effect)) continue
      val restriction = modifier.restriction
      if (restriction.action != action) continue
      if (!selectorMatches(state, effect, restriction.subject, subjectCardId)) continue
      if (!conditionHolds(state, effect, restriction.condition)) continue
      violations += RuleViolation("RESTRICTED_ACTION", "Action is restricted by continuous effect")
    }
    return violations
  }

  fun hasPermissionFor(
    state: GameState,
    action: RuleAction,
    subjectCardId: CardInstanceId?,
    targetCardId: CardInstanceId? = null,
  ): Boolean {
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.PermissionModifier ?: continue
      if (!isEffectActive(state, effect)) continue
      val permission = modifier.permission
      if (permission.action != action) continue
      if (permission.target == null && action in ACTIONS_REQUIRING_PERMISSION_TARGET) continue
      if (!selectorMatches(state, effect, permission.subject, subjectCardId)) continue
      if (!selectorMatches(state, effect, permission.target, targetCardId)) continue
      if (!conditionHolds(state, effect, permission.condition)) continue
      return true
    }
    return false
  }

  fun isKnockOutPrevented(
    state: GameState,
    targetCardId: CardInstanceId,
    cause: KnockOutCause,
    sourceCardId: CardInstanceId?,
  ): Boolean {
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.RestrictionModifier ?: continue
      if (!isEffectActive(state, effect)) continue
      val restriction = modifier.restriction
      if (restriction.action != RuleAction.BE_KNOCKED_OUT) continue
      val expectedCause = restriction.cause ?: continue
      if (expectedCause != KnockOutCause.ANY && expectedCause != cause) continue
      if (!selectorMatches(state, effect, restriction.subject, targetCardId)) continue
      if (restriction.source != null && !selectorMatches(state, effect, restriction.source, sourceCardId)) continue
      if (!conditionHolds(state, effect, restriction.condition)) continue
      return true
    }
    return false
  }

  fun activeDeckModifiers(state: GameState): List<DeckRuleModifier> {
    val results = mutableListOf<DeckRuleModifier>()
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.DeckRuleEffectModifier ?: continue
      if (!isEffectActive(state, effect)) continue
      results += modifier.modifier
    }
    return results
  }

  private fun selectorMatches(
    state: GameState,
    effect: ActiveEffect,
    selector: CardSelector?,
    subjectCardId: CardInstanceId?,
  ): Boolean {
    if (selector == null) return true
    if (subjectCardId == null) return false
    val ctx = EffectContext(
      gameId = state.id,
      controllerId = effect.controllerId,
      sourceCardId = effect.sourceCardId,
      abilityId = effect.sourceAbilityId,
      triggeringEvent = null,
    )
    val matched = evaluator.selectCardIds(selector, state, ctx) ?: return false
    return subjectCardId in matched
  }

  private fun conditionHolds(
    state: GameState,
    effect: ActiveEffect,
    condition: Condition?,
  ): Boolean {
    if (condition == null) return true
    val ctx = EffectContext(
      gameId = state.id,
      controllerId = effect.controllerId,
      sourceCardId = effect.sourceCardId,
      abilityId = effect.sourceAbilityId,
      triggeringEvent = null,
    )
    return when (val result = evaluator.evaluateCondition(condition, state, ctx, emptyMap())) {
      is EvaluationResult.Failed -> false
      is EvaluationResult.Success -> result.value
    }
  }

  private fun isSourceValid(state: GameState, sourceCardId: CardInstanceId?): Boolean {
    if (sourceCardId == null) return false
    if (state.cards[sourceCardId] == null) return false
    val zone = zoneOf(state, sourceCardId) ?: return false
    val definition = state.cards[sourceCardId]?.let { cardCatalog.get(it.definitionId) } ?: return true
    return when (definition.category) {
      CardCategory.LEADER -> zone == ZoneType.LEADER
      CardCategory.CHARACTER -> zone == ZoneType.CHARACTER
      CardCategory.STAGE -> zone == ZoneType.STAGE
      CardCategory.EVENT -> zone == ZoneType.TRASH || zone == ZoneType.HAND
      CardCategory.DON -> zone == ZoneType.COST || zone == ZoneType.DON_DECK || zone == ZoneType.ATTACHED_DON
    }
  }

  private fun isConditionSatisfied(
    state: GameState,
    effect: ActiveEffect,
    condition: Condition,
  ): Boolean {
    val ctx = EffectContext(
      gameId = state.id,
      controllerId = effect.controllerId,
      sourceCardId = effect.sourceCardId,
      abilityId = effect.sourceAbilityId,
      triggeringEvent = null,
    )
    return when (val result = evaluator.evaluateCondition(condition, state, ctx, emptyMap())) {
      is EvaluationResult.Failed -> false
      is EvaluationResult.Success -> result.value
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

  private fun isOpponentTurnEnd(event: GameEvent, marker: NextOpponentTurnMarker): Boolean {
    if (event.type != GameEventType.TURN_ENDED) return false
    val payload = event.payload as? GameEventPayload.TurnEndedPayload ?: return false
    return payload.playerId == marker.opponentId && payload.turnNumber >= marker.expiresOnTurnNumber
  }

  private fun matchesPattern(pattern: GameEventPattern, event: GameEvent): Boolean {
    return !(pattern.eventTypes.isNotEmpty() && event.type !in pattern.eventTypes)
  }

  private fun modifierValue(state: GameState, effect: ActiveEffect, expression: ValueExpr): Int {
    if (expression is ValueExpr.NumberValue) return expression.value
    if (!dynamicEvaluations.add(effect.id)) {
      throw DynamicModifierException(
        "DYNAMIC_MODIFIER_CYCLE", "Cyclic dependency while evaluating modifier ${effect.id.value}",
      )
    }
    try {
      val context = EffectContext(
        gameId = state.id,
        controllerId = effect.controllerId,
        sourceCardId = effect.sourceCardId,
        abilityId = effect.sourceAbilityId,
        triggeringEvent = null,
      )
      return when (val result = evaluator.evaluateValue(expression, state, context, emptyMap())) {
        is EvaluationResult.Failed -> throw DynamicModifierException(
          result.error.code, result.error.message,
        )

        is EvaluationResult.Success -> (result.value as? StoredValue.IntegerValue)?.value
          ?: throw DynamicModifierException(
            "DYNAMIC_MODIFIER_FAILED", "Modifier ${effect.id.value} did not evaluate to an integer",
          )
      }
    } finally {
      dynamicEvaluations.remove(effect.id)
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

internal class ReplacementProcessor(
  private val cardCatalog: CardCatalog,
  private val effectScheduler: EffectScheduler,
) {

  data class Activation(
    val events: List<GameEvent>,
    val request: ProgramRequest,
  )

  fun matchingReplacements(
    state: GameState,
    event: GameEvent,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): List<ActiveEffect> {
    val candidates = mutableListOf<Pair<ActiveEffect, Int>>()
    for (effect in state.activeEffects) {
      val modifier = effect.modifier as? Modifier.ReplacementModifier ?: continue
      val definition = modifier.definition

      if (!continuous.isEffectActive(state, effect)) continue
      if (!effectScheduler.matchesPattern(
          definition.pattern, event, state, effect.sourceCardId, effect.controllerId, evaluator,
        )
      ) continue
      if (!conditionHolds(
          state,
          event,
          effect.controllerId,
          effect.sourceCardId,
          effect.sourceAbilityId,
          definition.condition,
          evaluator
        )
      ) continue

      candidates += effect to definition.priority
    }

    for (player in state.players.values) {
      val inPlay = player.leader.cards + player.characters.cards + player.stage.cards
      for (cardId in inPlay) {
        val instance = state.cards[cardId] ?: continue
        val cardDef = cardCatalog.get(instance.definitionId) ?: continue
        val declarative = cardDef.abilities.filterIsInstance<ReplacementAbilityDefinition>()
        if (declarative.isEmpty()) continue

        val controller = continuous.effectiveController(state, cardId) ?: instance.controllerId

        for (ability in declarative) {
          val definition = ability.replacement
          if (!effectScheduler.matchesPattern(definition.pattern, event, state, cardId, controller, evaluator)) continue
          if (!conditionHolds(state, event, controller, cardId, ability.id, definition.condition, evaluator)) continue

          val synthetic = ActiveEffect(
            id = EffectId("declarative-repl:${cardId.value}:${ability.id.value}"),
            sourceCardId = cardId,
            sourceAbilityId = ability.id,
            controllerId = controller,
            targetCardIds = emptyList(),
            targetPlayerId = null,
            modifier = Modifier.ReplacementModifier(definition),
            duration = Duration.WhileSourceValid,
            timestampSequence = 0L,
          )
          candidates += synthetic to definition.priority
        }
      }
    }

    return candidates.sortedWith(
      compareBy<Pair<ActiveEffect, Int>> { it.second }.thenBy { it.first.timestampSequence }
        .thenBy { it.first.id.value },
    ).map { it.first }
  }

  fun buildActivation(
    state: GameState,
    nextSequence: Long,
    effect: ActiveEffect,
    replacedEvent: GameEvent,
  ): Activation? {
    val modifier = effect.modifier as? Modifier.ReplacementModifier ?: return null
    val activationId = EffectId("${state.id.value}:repl:$nextSequence")
    val activated = GameEvent(
      id = EventId("${state.id.value}:$nextSequence"),
      sequence = nextSequence,
      type = GameEventType.EFFECT_ACTIVATED,
      sourceCardId = effect.sourceCardId,
      payload = GameEventPayload.EffectActivatedPayload(
        effectId = activationId,
        sourceCardId = effect.sourceCardId,
        abilityId = null,
      ),
      visibility = EventVisibility.PUBLIC,
    )
    val appliedSequence = nextSequence + 1
    val applied = GameEvent(
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
    val request = ProgramRequest(
      executionId = ExecutionId("${activated.id.value}:exec"),
      program = modifier.definition.program,
      context = EffectContext(
        gameId = state.id,
        controllerId = effect.controllerId,
        sourceCardId = effect.sourceCardId,
        abilityId = effect.sourceAbilityId,
        triggeringEvent = replacedEvent,
      ),
    )
    return Activation(listOf(activated, applied), request)
  }

  private fun conditionHolds(
    state: GameState,
    event: GameEvent,
    controllerId: PlayerId,
    sourceCardId: CardInstanceId?,
    abilityId: AbilityId?,
    condition: Condition?,
    evaluator: RulesEngine,
  ): Boolean {
    if (condition == null) return true
    val ctx = EffectContext(
      gameId = state.id,
      controllerId = controllerId,
      sourceCardId = sourceCardId,
      abilityId = abilityId,
      triggeringEvent = event,
    )
    return when (val result = evaluator.evaluateCondition(condition, state, ctx, emptyMap())) {
      is EvaluationResult.Failed -> false
      is EvaluationResult.Success -> result.value
    }
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