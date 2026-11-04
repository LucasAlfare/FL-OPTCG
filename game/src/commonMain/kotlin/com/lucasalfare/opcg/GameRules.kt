package com.lucasalfare.opcg

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
  private val battleResolver = BattleResolver(cardCatalog)
  private val damageProcessor = DamageProcessor(cardCatalog)
  private val costProcessor = CostProcessor()
  private val effectScheduler = EffectScheduler(cardCatalog)
  private val continuousEffectProcessor = ContinuousEffectProcessor(cardCatalog)
  private val replacementProcessor = ReplacementProcessor()
  private val ruleProcessor = RuleProcessor()
  private val informationManager = InformationManager()
  private val loopProcessor = LoopProcessor()
  private val deckValidator = DeckValidator(cardCatalog)
  private val gameFactory = GameFactory(cardCatalog)
  private val zoneInstructionExecutor = ZoneInstructionExecutor()
  private val donInstructionExecutor = DonInstructionExecutor()

  fun validate(state: GameState, command: GameCommand): ValidationResult {
    if (state.status == GameStatus.FINISHED) {
      return ValidationResult(false, listOf(RuleViolation("GAME_FINISHED", "Game is finished")))
    }
    return when (command) {
      is PlayCardCommand -> mainPhaseController.validatePlayCard(state, command, cardCatalog, continuousEffectProcessor)
      is GiveDonCommand -> mainPhaseController.validateGiveDon(state, command, continuousEffectProcessor)
      is EndMainPhaseCommand -> mainPhaseController.validateEndMain(state, command)
      is ActivateAbilityCommand -> effectScheduler.validateActivate(state, command, this)
      is DeclareAttackCommand -> battleResolver.validateDeclareAttack(state, command, continuousEffectProcessor)
      is ActivateBlockerCommand -> battleResolver.validateActivateBlocker(state, command, continuousEffectProcessor)
      is UseCharacterCounterCommand -> battleResolver.validateCharacterCounter(
        state, command, continuousEffectProcessor
      )

      is UseEventCounterCommand -> battleResolver.validateEventCounter(state, command, continuousEffectProcessor)
      is SubmitChoiceCommand -> validateSubmitChoice(state, command)
      is ResolveMulliganCommand -> validateMulligan(state, command)
      is ConcedeCommand -> validateConcede(state, command)
      else -> ValidationResult(true, emptyList())
    }
  }

  fun process(state: GameState, command: GameCommand): GameTransition {
    val validation = validate(state, command)
    if (!validation.valid) {
      return GameTransition(events = emptyList(), programsToRun = emptyList())
    }
    return when (command) {
      is PlayCardCommand -> mainPhaseController.processPlayCard(
        state, command, cardCatalog, continuousEffectProcessor, costProcessor, this
      )

      is GiveDonCommand -> mainPhaseController.processGiveDon(state, command, continuousEffectProcessor)
      is EndMainPhaseCommand -> turnController.endMainPhase(state, command.playerId)
      is ActivateAbilityCommand -> effectScheduler.processActivate(state, command, this)
      is DeclareAttackCommand -> battleResolver.processDeclareAttack(state, command, continuousEffectProcessor, this)
      is ActivateBlockerCommand -> battleResolver.processActivateBlocker(
        state, command, continuousEffectProcessor, this
      )

      is UseCharacterCounterCommand -> battleResolver.processCharacterCounter(state, command, continuousEffectProcessor)
      is UseEventCounterCommand -> battleResolver.processEventCounter(
        state, command, continuousEffectProcessor, costProcessor, this
      )

      is SubmitChoiceCommand -> processSubmitChoice(state, command)
      is ResolveMulliganCommand -> processMulligan(state, command)
      is ConcedeCommand -> processConcede(state, command)
      else -> GameTransition(events = emptyList(), programsToRun = emptyList())
    }
  }

  fun advanceBattleStep(state: GameState): GameTransition {
    return battleResolver.advanceStep(state, continuousEffectProcessor, damageProcessor, this)
  }

  fun processEvents(state: GameState, events: List<GameEvent>): GameTransition {
    return effectScheduler.processEvents(state, events, this)
  }

  fun processAutomaticRules(state: GameState): GameTransition {
    return ruleProcessor.processRules(state)
  }

  fun advancePhase(state: GameState): GameTransition {
    return turnController.advancePhase(state, continuousEffectProcessor, this)
  }

  fun startTurn(state: GameState, playerId: PlayerId, turnNumber: Int): GameTransition {
    return turnController.startTurn(state, playerId, turnNumber, continuousEffectProcessor, this)
  }

  fun createInitialState(setup: GameSetup): GameCreationResult {
    return gameFactory.create(setup)
  }

  fun effectiveCharacteristics(state: GameState, cardId: CardInstanceId): EffectiveCharacteristics? {
    return continuousEffectProcessor.effectiveCharacteristics(state, cardId)
  }

  fun effectiveController(state: GameState, cardId: CardInstanceId): PlayerId? {
    return continuousEffectProcessor.effectiveController(state, cardId)
  }


  override fun execute(
    instruction: Instruction,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    return when (instruction) {
      is ModifyCharacteristic -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val valueResult = evaluateValue(instruction.value, state, context, variables)
        if (valueResult is EvaluationResult.Failed) return valueResult.asExecution()
        val intValue = (valueResult as EvaluationResult.Success).value.asInt() ?: return failExec(
          "INVALID_VALUE", "Expected integer value", 0
        )
        val modifier = Modifier.CharacteristicModifier(
          instruction.characteristic,
          instruction.operation,
          ValueExpr.NumberValue(intValue),
        )
        emitModifiers(state, context, cardIds, modifier, instruction.duration)
      }

      is SetBaseCharacteristic -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val valueResult = evaluateValue(instruction.value, state, context, variables)
        if (valueResult is EvaluationResult.Failed) return valueResult.asExecution()
        val intValue = (valueResult as EvaluationResult.Success).value.asInt() ?: return failExec(
          "INVALID_VALUE", "Expected integer value", 0
        )
        val baseChar = when (instruction.characteristic) {
          NumericCharacteristic.POWER -> NumericCharacteristic.BASE_POWER
          NumericCharacteristic.COST -> NumericCharacteristic.BASE_COST
          else -> instruction.characteristic
        }
        val modifier = Modifier.CharacteristicModifier(baseChar, NumericOperation.SET, ValueExpr.NumberValue(intValue))
        emitModifiers(state, context, cardIds, modifier, instruction.duration)
      }

      is SetPowerToZero -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val effects = mutableListOf<ActiveEffect>()
        var seq = state.lastEventSequence
        for (cardId in cardIds) {
          val power = continuousEffectProcessor.effectiveCharacteristics(state, cardId)?.power ?: 0
          seq += 1
          effects += activeEffect(
            state, context, listOf(cardId), null,
            Modifier.CharacteristicModifier(
              NumericCharacteristic.POWER,
              NumericOperation.SUBTRACT,
              ValueExpr.NumberValue(power),
            ),
            instruction.duration,
            seq,
          )
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(state, effect, state.lastEventSequence + 1 + index)
          },
        )
      }

      is SetCardProperty -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.PropertyModifier(instruction.property, instruction.value),
          instruction.duration,
        )
      }

      is CopyCardProperties -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val refId = resolveCardReference(instruction.reference, variables) ?: return failExec(
          "INVALID_REFERENCE", "CopyCardProperties reference unresolved", 0
        )
        val sourceChars = continuousEffectProcessor.effectiveCharacteristics(state, refId) ?: return failExec(
          "MISSING_CARD", "Reference card has no characteristics", 0
        )
        val effects = mutableListOf<ActiveEffect>()
        var seq = state.lastEventSequence
        for (cardId in cardIds) {
          for (property in instruction.properties) {
            val value = propertyValueFrom(sourceChars, property) ?: continue
            seq += 1
            effects += activeEffect(
              state, context, listOf(cardId), null,
              Modifier.PropertyModifier(property, value),
              instruction.duration,
              seq,
            )
          }
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(state, effect, state.lastEventSequence + 1 + index)
          },
        )
      }

      is GrantKeyword -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.KeywordModifier(instruction.keyword, KeywordOperation.ADD),
          instruction.duration,
        )
      }

      is RemoveKeyword -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.KeywordModifier(instruction.keyword, KeywordOperation.REMOVE),
          instruction.duration,
        )
      }

      is GrantAbility -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.AbilityModifier(instruction.ability, add = true),
          instruction.duration,
        )
      }

      is CopyAbilities -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val refId = resolveCardReference(instruction.reference, variables) ?: return failExec(
          "INVALID_REFERENCE", "CopyAbilities reference unresolved", 0
        )
        val def = state.cards[refId]?.let { cardCatalog.get(it.definitionId) } ?: return failExec(
          "MISSING_CARD", "Reference card definition not found", 0
        )
        val effects = mutableListOf<ActiveEffect>()
        var seq = state.lastEventSequence
        for (cardId in cardIds) {
          for (ability in def.abilities) {
            seq += 1
            effects += activeEffect(
              state, context, listOf(cardId), null,
              Modifier.AbilityModifier(ability, add = true),
              instruction.duration,
              seq,
            )
          }
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(state, effect, state.lastEventSequence + 1 + index)
          },
        )
      }

      is NegateEffects -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.EffectNegationModifier(instruction.scope),
          instruction.duration,
        )
      }

      is ChangeControl -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.ControlModifier(instruction.playerId),
          instruction.duration,
        )
      }

      is PreventDamage -> {
        val amountResult = evaluateValue(instruction.amount, state, context, variables)
        if (amountResult is EvaluationResult.Failed) return amountResult.asExecution()
        val amount = (amountResult as EvaluationResult.Success).value.asInt() ?: return failExec(
          "INVALID_VALUE", "Expected integer amount", 0
        )
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state, context, emptyList(), instruction.playerId,
          Modifier.DamagePreventionModifier(instruction.playerId, ValueExpr.NumberValue(amount)),
          instruction.duration,
          seq,
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is ApplyRestriction -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.RestrictionModifier(instruction.restriction),
          instruction.duration,
        )
      }

      is GrantPermission -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds,
          Modifier.PermissionModifier(instruction.permission),
          instruction.duration,
        )
      }

      is AddActivationCost -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state, context, emptyList(), null,
          Modifier.ActivationCostModifier(instruction.selector, instruction.cost),
          instruction.duration,
          seq,
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is ModifyDeckRule -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state, context, emptyList(), null,
          Modifier.DeckRuleEffectModifier(instruction.modifier),
          instruction.duration,
          seq,
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is RegisterReplacement -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state, context, emptyList(), null,
          Modifier.ReplacementModifier(instruction.definition),
          instruction.definition.duration,
          seq,
        ).copy(id = instruction.definition.id, sourceAbilityId = context.abilityId)
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is KnockOut -> damageProcessor.executeKnockOut(
        state, instruction, context, variables, replacementProcessor, continuousEffectProcessor, cardCatalog
      )

      is DealDamage -> damageProcessor.executeDealDamage(
        state, instruction, context, variables, this, continuousEffectProcessor
      )

      is SetLife -> damageProcessor.executeSetLife(state, instruction, context, variables)

      is Draw -> zoneInstructionExecutor.draw(state, instruction, context, variables, this)
      is MoveCards -> zoneInstructionExecutor.moveCards(
        state, instruction, context, variables, this, informationManager
      )

      is TrashFromHand -> zoneInstructionExecutor.trashFromHand(state, instruction, context, variables, this)
      is PlayCard -> zoneInstructionExecutor.playCard(
        state, instruction, context, variables, cardCatalog, continuousEffectProcessor, costProcessor, this
      )

      is ShuffleZone -> zoneInstructionExecutor.shuffleZone(state, instruction)
      is AddDon -> donInstructionExecutor.addDon(state, instruction, context, variables, this)
      is RestDon -> donInstructionExecutor.restDon(state, instruction, context, variables, this)
      is SetDonActive -> donInstructionExecutor.setDonActive(state, instruction, context, variables)
      is AttachDon -> donInstructionExecutor.attachDon(state, instruction, context, variables)
      is ReturnDon -> donInstructionExecutor.returnDon(state, instruction, context, variables, this)
      is DetachDon -> donInstructionExecutor.detachDon(state, instruction, context, variables, this)
      is RestCards -> zoneInstructionExecutor.restCards(state, instruction, context, variables)
      is SetCardState -> zoneInstructionExecutor.setCardState(state, instruction, context, variables)

      is ActivateAbility -> effectScheduler.executeActivateInstruction(state, instruction, context, variables, this)

      is SelectCards -> executeSelectCards(state, instruction, context, variables)
      is CountCards -> executeCountCards(state, instruction, context, variables)
      is LookAtTop -> executeLookAtTop(state, instruction, context, variables)
      is RevealCards -> executeRevealCards(state, instruction, context, variables)
      is ChooseOption -> executeChooseOption(state, instruction, context, variables)
      is ChooseNumber -> executeChooseNumber(state, instruction, context, variables)
      is OrderCards -> executeOrderCards(state, instruction, context, variables)
      is CheckCondition -> executeCheckCondition(state, instruction, context, variables)
      is SetGameResult -> executeSetGameResult(state, instruction, context)

      else -> failExec("UNSUPPORTED_INSTRUCTION", "Instruction not handled: ${instruction::class.simpleName}", 0)
    }
  }

  private fun invalid(code: String, message: String): ValidationResult =
    ValidationResult(false, listOf(RuleViolation(code, message)))

  private fun emptyTransition(): GameTransition = GameTransition(emptyList(), emptyList())

  private fun validateConcede(state: GameState, command: ConcedeCommand): ValidationResult {
    if (state.players[command.playerId] == null) return invalid("UNKNOWN_PLAYER", "Player not found")
    return ValidationResult(true, emptyList())
  }

  private fun validateSubmitChoice(state: GameState, command: SubmitChoiceCommand): ValidationResult {
    val choice = state.pendingChoices.firstOrNull { it.id == command.choiceId } ?: return invalid(
      "UNKNOWN_CHOICE", "Pending choice not found"
    )
    if (choice.playerId != command.playerId) return invalid("WRONG_PLAYER", "Player does not own this choice")
    val error = validateChoiceAnswer(choice, command.answer)
    if (error != null) return invalid("INVALID_ANSWER", error)
    return ValidationResult(true, emptyList())
  }

  private fun validateChoiceAnswer(choice: PendingChoice, answer: ChoiceAnswer): String? {
    return when (choice.type) {
      ChoiceType.OPTION -> {
        if (answer !is ChoiceAnswer.OptionAnswer) return "Expected OptionAnswer"
        val allowed = choice.candidates.filterIsInstance<ChoiceCandidate.OptionCandidate>().map { it.optionId }
        if (allowed.isNotEmpty() && answer.optionId !in allowed) return "Option not in candidates"
        null
      }

      ChoiceType.NUMBER -> {
        if (answer !is ChoiceAnswer.NumberAnswer) return "Expected NumberAnswer"
        if (answer.value < choice.minimum || answer.value > choice.maximum) return "Number out of range"
        null
      }

      ChoiceType.CARD_SELECTION -> {
        if (answer !is ChoiceAnswer.CardSelectionAnswer) return "Expected CardSelectionAnswer"
        val count = answer.cardIds.size
        if (count < choice.minimum || count > choice.maximum) return "Selection count out of range"
        val allowed = choice.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }.toSet()
        if (allowed.isNotEmpty() && answer.cardIds.any { it !in allowed }) return "Card not in candidates"
        null
      }

      ChoiceType.CARD_ORDER -> {
        if (answer !is ChoiceAnswer.CardOrderAnswer) return "Expected CardOrderAnswer"
        val allowed = choice.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }
        if (allowed.isNotEmpty() && answer.cardIds.toSet() != allowed.toSet()) return "Invalid card order"
        null
      }

      ChoiceType.BOOLEAN -> {
        if (answer !is ChoiceAnswer.BooleanAnswer) return "Expected BooleanAnswer"
        null
      }
    }
  }

  private fun processSubmitChoice(state: GameState, command: SubmitChoiceCommand): GameTransition {
    val choice = state.pendingChoices.firstOrNull { it.id == command.choiceId } ?: return emptyTransition()
    var seq = state.lastEventSequence
    val events = mutableListOf<GameEvent>()

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CHOICE_SUBMITTED,
      sourceCardId = null,
      payload = GameEventPayload.ChoiceSubmittedPayload(command.playerId, command.choiceId, command.answer),
      visibility = choice.visibility,
    )

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.PENDING_CHOICE_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.PendingChoiceChangedPayload(command.choiceId, null),
      visibility = EventVisibility.INTERNAL,
    )

    return GameTransition(
      events = events,
      programsToRun = emptyList(),
      choiceToResume = PlayerChoice(command.choiceId, command.playerId, command.answer),
    )
  }

  private fun validateMulligan(state: GameState, command: ResolveMulliganCommand): ValidationResult {
    val setup = state.setup ?: return invalid("NOT_IN_SETUP", "Game is not in setup")
    if (setup.step != GameSetupStep.MULLIGAN) return invalid("NOT_MULLIGAN_STEP", "Not in Mulligan step")
    if (command.playerId !in setup.pendingMulliganPlayers) {
      return invalid("NOT_PENDING", "Player is not awaiting mulligan")
    }
    val player = state.players[command.playerId] ?: return invalid("UNKNOWN_PLAYER", "Player not found")
    if (player.hand.cards.size != 5) {
      return invalid("INVALID_HAND", "Mulligan requires exactly 5 cards in hand")
    }
    return ValidationResult(true, emptyList())
  }

  private fun processMulligan(state: GameState, command: ResolveMulliganCommand): GameTransition {
    val player = state.players[command.playerId] ?: return emptyTransition()
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    var randomState = state.randomState
    val returned = if (command.redraw) player.hand.cards.toList() else emptyList()

    if (command.redraw) {
      for (cardId in returned) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId,
            fromPlayerId = command.playerId,
            fromZone = ZoneType.HAND,
            toPlayerId = command.playerId,
            toZone = ZoneType.DECK,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN,
            visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }

      val deckAfter = player.deck.cards + returned
      val (shuffled, randomAfter) = RandomSupport.fisherYates(deckAfter.toMutableList(), randomState)
      randomState = randomAfter

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DECK_SHUFFLED,
        sourceCardId = null,
        payload = GameEventPayload.DeckShuffledPayload(
          playerId = command.playerId,
          zone = ZoneType.DECK,
          resultingOrder = shuffled,
          randomStateAfter = randomState,
        ),
        visibility = EventVisibility.PUBLIC,
      )

      val drawn = shuffled.take(minOf(5, shuffled.size))
      for (cardId in drawn) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId,
            fromPlayerId = command.playerId,
            fromZone = ZoneType.DECK,
            toPlayerId = command.playerId,
            toZone = ZoneType.HAND,
            destinationIndex = null,
            newObjectId = null,
            orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN,
            visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }
      if (drawn.isNotEmpty()) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARDS_DRAWN,
          sourceCardId = null,
          payload = GameEventPayload.CardsDrawnPayload(command.playerId, drawn),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }
    }

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.MULLIGAN_RESOLVED,
      sourceCardId = null,
      payload = GameEventPayload.MulliganResolvedPayload(
        playerId = command.playerId,
        redrawn = command.redraw,
        returnedCardIds = returned,
      ),
      visibility = EventVisibility.PUBLIC,
    )

    return GameTransition(events = events, programsToRun = emptyList())
  }

  private fun processConcede(state: GameState, command: ConcedeCommand): GameTransition {
    val opponent = state.playerOrder.firstOrNull { it != command.playerId } ?: return emptyTransition()
    var seq = state.lastEventSequence
    val events = mutableListOf<GameEvent>()

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.PLAYER_LOST,
      sourceCardId = null,
      payload = GameEventPayload.PlayerLostPayload(command.playerId, GameEndReason.CONCESSION),
      visibility = EventVisibility.PUBLIC,
    )

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.PLAYER_WON,
      sourceCardId = null,
      payload = GameEventPayload.PlayerWonPayload(opponent, GameEndReason.CONCESSION),
      visibility = EventVisibility.PUBLIC,
    )

    return GameTransition(events = events, programsToRun = emptyList())
  }

  private fun evaluateInt(
    expr: ValueExpr,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): Int? {
    return when (val r = evaluateValue(expr, state, context, variables)) {
      is EvaluationResult.Failed -> null
      is EvaluationResult.Success -> (r.value as? StoredValue.IntegerValue)?.value
    }
  }

  private fun executeSelectCards(
    state: GameState,
    instruction: SelectCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val candidates = when (val r = selectCards(instruction.selector, state, context, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    val minVal = evaluateInt(instruction.minimum, state, context, variables) ?: 0
    val maxVal = evaluateInt(instruction.maximum, state, context, variables) ?: candidates.size
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:sel:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId,
      frameId = ExecutionId("pending"),
      type = ChoiceType.CARD_SELECTION,
      minimum = minVal.coerceAtLeast(0),
      maximum = maxVal.coerceAtLeast(minVal),
      candidates = candidates.map { ChoiceCandidate.CardCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeCountCards(
    state: GameState,
    instruction: CountCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val candidates = when (val r = selectCards(instruction.selector, state, context, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    return InstructionExecutionResult.Completed(
      StoredValue.IntegerValue(candidates.size),
      emptyList(),
    )
  }

  private fun executeLookAtTop(
    state: GameState,
    instruction: LookAtTop,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val amount = evaluateInt(instruction.amount, state, context, variables) ?: 0
    val player = state.players[instruction.playerId] ?: return failExec("UNKNOWN_PLAYER", "Player not found", 0)
    val cards = when (instruction.zone) {
      ZoneType.DECK -> player.deck.cards.take(amount)
      ZoneType.LIFE -> player.life.cards.take(amount)
      ZoneType.DON_DECK -> player.donDeck.cards.take(amount)
      else -> emptyList()
    }
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_REVEALED,
      sourceCardId = context.sourceCardId,
      payload = GameEventPayload.CardRevealedPayload(
        cards,
        CardVisibility.PLAYERS(setOf(instruction.playerId)),
      ),
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Completed(StoredValue.CardIdsValue(cards), listOf(event))
  }

  private fun executeRevealCards(
    state: GameState,
    instruction: RevealCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = when (val v = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> v.value
      is StoredValue.CardValue -> listOf(v.value)
      else -> return failExec("INVALID_SOURCE", "RevealCards requires card ids", 0)
    }
    if (cardIds.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())

    val isPublic = Viewer.ALL_PLAYERS in instruction.viewers
    val viewers = mutableSetOf<PlayerId>()
    for (viewer in instruction.viewers) {
      when (viewer) {
        Viewer.ALL_PLAYERS -> Unit
        Viewer.SOURCE_CONTROLLER -> viewers += context.controllerId
        Viewer.OPPONENT -> state.playerOrder.firstOrNull { it != context.controllerId }?.let { viewers += it }
        Viewer.OWNER -> cardIds.forEach { id ->
          state.cards[id]?.let { viewers += it.ownerId }
        }
      }
    }
    val cardVisibility = if (isPublic) CardVisibility.PUBLIC else CardVisibility.PLAYERS(viewers)
    val eventVisibility = if (isPublic) EventVisibility.PUBLIC else EventVisibility.PRIVATE_TO_PLAYERS(viewers)

    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.CARD_REVEALED,
      sourceCardId = context.sourceCardId,
      payload = GameEventPayload.CardRevealedPayload(cardIds, cardVisibility),
      visibility = eventVisibility,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  private fun executeChooseOption(
    state: GameState,
    instruction: ChooseOption,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:opt:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId,
      frameId = ExecutionId("pending"),
      type = ChoiceType.OPTION,
      minimum = 1,
      maximum = 1,
      candidates = instruction.options.map { ChoiceCandidate.OptionCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeChooseNumber(
    state: GameState,
    instruction: ChooseNumber,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val minVal = evaluateInt(instruction.minimum, state, context, variables) ?: 0
    val maxVal = evaluateInt(instruction.maximum, state, context, variables) ?: minVal
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:num:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId,
      frameId = ExecutionId("pending"),
      type = ChoiceType.NUMBER,
      minimum = minVal,
      maximum = maxVal,
      candidates = (minVal..maxVal).map { ChoiceCandidate.NumberCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeOrderCards(
    state: GameState,
    instruction: OrderCards,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val cardIds = when (val v = variables[instruction.source]) {
      is StoredValue.CardIdsValue -> v.value
      is StoredValue.CardValue -> listOf(v.value)
      else -> return failExec("INVALID_SOURCE", "OrderCards requires card ids", 0)
    }
    if (cardIds.isEmpty()) {
      return InstructionExecutionResult.Completed(StoredValue.CardIdsValue(emptyList()), emptyList())
    }
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:order:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId,
      frameId = ExecutionId("pending"),
      type = ChoiceType.CARD_ORDER,
      minimum = cardIds.size,
      maximum = cardIds.size,
      candidates = cardIds.map { ChoiceCandidate.CardCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeCheckCondition(
    state: GameState,
    instruction: CheckCondition,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    return when (val r = evaluateCondition(instruction.condition, state, context, variables)) {
      is EvaluationResult.Failed -> InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> InstructionExecutionResult.Completed(
        StoredValue.BooleanValue(r.value),
        emptyList(),
      )
    }
  }

  private fun executeSetGameResult(
    state: GameState,
    instruction: SetGameResult,
    context: EffectContext,
  ): InstructionExecutionResult {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    val result = instruction.result

    if (result.reason == GameEndReason.DRAW || (result.winnerId == null && result.loserIds.isEmpty())) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.GAME_DRAWN,
        sourceCardId = context.sourceCardId,
        payload = GameEventPayload.GameDrawnPayload(state.players.keys, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
      return InstructionExecutionResult.Completed(null, events)
    }

    result.winnerId?.let { winner ->
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.PLAYER_WON,
        sourceCardId = context.sourceCardId,
        payload = GameEventPayload.PlayerWonPayload(winner, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
    }
    for (loser in result.loserIds) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.PLAYER_LOST,
        sourceCardId = context.sourceCardId,
        payload = GameEventPayload.PlayerLostPayload(loser, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  private fun emitModifiers(
    state: GameState,
    context: EffectContext,
    cardIds: List<CardInstanceId>,
    modifier: Modifier,
    duration: Duration,
  ): InstructionExecutionResult {
    if (cardIds.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
    val effects = cardIds.mapIndexed { index, cardId ->
      activeEffect(state, context, listOf(cardId), null, modifier, duration, state.lastEventSequence + 1 + index)
    }
    val events = effects.mapIndexed { index, effect ->
      modifierAddedEvent(state, effect, state.lastEventSequence + 1 + index)
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  private fun activeEffect(
    state: GameState,
    context: EffectContext,
    targetCardIds: List<CardInstanceId>,
    targetPlayerId: PlayerId?,
    modifier: Modifier,
    duration: Duration,
    sequence: Long,
  ): ActiveEffect {
    return ActiveEffect(
      id = EffectId("${state.id.value}:effect:$sequence"),
      sourceCardId = context.sourceCardId,
      sourceAbilityId = context.abilityId,
      controllerId = context.controllerId,
      targetCardIds = targetCardIds,
      targetPlayerId = targetPlayerId,
      modifier = modifier,
      duration = duration,
      timestampSequence = sequence,
    )
  }

  private fun modifierAddedEvent(state: GameState, effect: ActiveEffect, sequence: Long): GameEvent {
    return GameEvent(
      id = EventId("${state.id.value}:$sequence"),
      sequence = sequence,
      type = GameEventType.MODIFIER_ADDED,
      sourceCardId = effect.sourceCardId,
      payload = GameEventPayload.ModifierAddedPayload(effect),
      visibility = EventVisibility.PUBLIC,
    )
  }

  private fun resolveSourceCards(
    source: VariableId,
    variables: Map<VariableId, StoredValue>,
  ): List<CardInstanceId>? {
    return when (val value = variables[source]) {
      is StoredValue.CardIdsValue -> value.value
      is StoredValue.CardValue -> listOf(value.value)
      null -> null
      else -> emptyList()
    }
  }

  private fun resolveCardReference(
    reference: CardReference,
    variables: Map<VariableId, StoredValue>,
  ): CardInstanceId? {
    reference.instanceId?.let { return it }
    val variableId = reference.variableId ?: return null
    return when (val value = variables[variableId]) {
      is StoredValue.CardValue -> value.value
      is StoredValue.CardIdsValue -> value.value.singleOrNull()
      else -> null
    }
  }

  private fun propertyValueFrom(
    chars: EffectiveCharacteristics,
    property: CardProperty,
  ): PropertyValue? = when (property) {
    CardProperty.NAME -> PropertyValue.StringValue(chars.name)
    CardProperty.COLOR -> PropertyValue.ColorSetValue(chars.colors)
    CardProperty.TYPE -> PropertyValue.TypeSetValue(chars.types)
    CardProperty.ATTRIBUTE -> PropertyValue.AttributeSetValue(chars.attributes)
    CardProperty.KEYWORDS -> PropertyValue.KeywordSetValue(chars.keywords)
  }

  private fun failExec(code: String, message: String, pointer: Int): InstructionExecutionResult.Failed {
    return InstructionExecutionResult.Failed(VmError(code, message, pointer))
  }

  private fun EvaluationResult.Failed.asExecution(): InstructionExecutionResult.Failed {
    return InstructionExecutionResult.Failed(error)
  }

  private fun StoredValue.asInt(): Int? = when (this) {
    is StoredValue.IntegerValue -> value
    is StoredValue.BooleanValue -> if (value) 1 else 0
    else -> null
  }

  override fun evaluateValue(
    expression: ValueExpr,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<StoredValue> {
    return when (expression) {
      is ValueExpr.NumberValue -> EvaluationResult.Success(StoredValue.IntegerValue(expression.value))

      is ValueExpr.VariableValue -> {
        val stored = variables[expression.variableId] ?: return evalFail(
          "MISSING_VARIABLE", "Variable not found: ${expression.variableId.value}"
        )
        EvaluationResult.Success(stored)
      }

      is ValueExpr.CountValue -> {
        when (val selected = selectCards(expression.selector, state, context, variables)) {
          is EvaluationResult.Failed -> selected
          is EvaluationResult.Success -> EvaluationResult.Success(StoredValue.IntegerValue(selected.value.size))
        }
      }

      is ValueExpr.CharacteristicValue -> {
        when (val selected = selectCards(expression.selector, state, context, variables)) {
          is EvaluationResult.Failed -> selected
          is EvaluationResult.Success -> {
            if (selected.value.size != 1) {
              return evalFail(
                "SELECTOR_CARD_COUNT", "CharacteristicValue requires exactly one card, found ${selected.value.size}"
              )
            }
            val cardId = selected.value.single()
            val characteristic = readCharacteristic(state, cardId, expression.characteristic) ?: return evalFail(
              "MISSING_CHARACTERISTIC", "Characteristic ${expression.characteristic} unavailable"
            )
            EvaluationResult.Success(StoredValue.IntegerValue(characteristic))
          }
        }
      }

      is ValueExpr.CurrentCardValue -> {
        if (candidateCardId == null) {
          return evalFail("NO_CANDIDATE", "CurrentCardValue requires candidateCardId")
        }
        val characteristic = readCharacteristic(state, candidateCardId, expression.characteristic)
          ?: return evalFail("MISSING_CHARACTERISTIC", "Characteristic ${expression.characteristic} unavailable")
        EvaluationResult.Success(StoredValue.IntegerValue(characteristic))
      }

      is ValueExpr.ArithmeticValue -> {
        val leftResult = evaluateValue(expression.left, state, context, variables, candidateCardId)
        if (leftResult is EvaluationResult.Failed) return leftResult
        val rightResult = evaluateValue(expression.right, state, context, variables, candidateCardId)
        if (rightResult is EvaluationResult.Failed) return rightResult
        val left = (leftResult as EvaluationResult.Success).value.asInt() ?: return evalFail(
          "INVALID_VALUE", "Arithmetic left is not integer"
        )
        val right = (rightResult as EvaluationResult.Success).value.asInt() ?: return evalFail(
          "INVALID_VALUE", "Arithmetic right is not integer"
        )
        val result = computeArithmetic(expression.operator, left, right) ?: return evalFail(
          "ARITHMETIC_ERROR", "Invalid arithmetic operation"
        )
        EvaluationResult.Success(StoredValue.IntegerValue(result))
      }
    }
  }

  override fun evaluateCondition(
    condition: Condition,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<Boolean> {
    return when (condition) {
      is Condition.AllConditions -> {
        if (condition.conditions.isEmpty()) return EvaluationResult.Success(true)
        for (child in condition.conditions) {
          when (val result = evaluateCondition(child, state, context, variables, candidateCardId)) {
            is EvaluationResult.Failed -> return result
            is EvaluationResult.Success -> if (!result.value) return EvaluationResult.Success(false)
          }
        }
        EvaluationResult.Success(true)
      }

      is Condition.AnyCondition -> {
        if (condition.conditions.isEmpty()) return EvaluationResult.Success(false)
        for (child in condition.conditions) {
          when (val result = evaluateCondition(child, state, context, variables, candidateCardId)) {
            is EvaluationResult.Failed -> return result
            is EvaluationResult.Success -> if (result.value) return EvaluationResult.Success(true)
          }
        }
        EvaluationResult.Success(false)
      }

      is Condition.NotCondition -> {
        when (val result = evaluateCondition(condition.condition, state, context, variables, candidateCardId)) {
          is EvaluationResult.Failed -> result
          is EvaluationResult.Success -> EvaluationResult.Success(!result.value)
        }
      }

      is Condition.Exists -> {
        when (val selected = selectCards(condition.selector, state, context, variables)) {
          is EvaluationResult.Failed -> selected
          is EvaluationResult.Success -> EvaluationResult.Success(selected.value.isNotEmpty())
        }
      }

      is Condition.Comparison -> {
        val leftResult = evaluateValue(condition.left, state, context, variables, candidateCardId)
        if (leftResult is EvaluationResult.Failed) return leftResult
        val rightResult = evaluateValue(condition.right, state, context, variables, candidateCardId)
        if (rightResult is EvaluationResult.Failed) return rightResult
        val left = (leftResult as EvaluationResult.Success).value.asInt() ?: return evalFail(
          "INVALID_VALUE", "Comparison left is not integer"
        )
        val right = (rightResult as EvaluationResult.Success).value.asInt() ?: return evalFail(
          "INVALID_VALUE", "Comparison right is not integer"
        )
        EvaluationResult.Success(compare(condition.operator, left, right))
      }
    }
  }

  private fun computeArithmetic(operator: ArithmeticOperator, left: Int, right: Int): Int? {
    return when (operator) {
      ArithmeticOperator.ADD -> {
        val result = left.toLong() + right.toLong()
        if (result < Int.MIN_VALUE || result > Int.MAX_VALUE) null else result.toInt()
      }

      ArithmeticOperator.SUBTRACT -> {
        val result = left.toLong() - right.toLong()
        if (result < Int.MIN_VALUE || result > Int.MAX_VALUE) null else result.toInt()
      }

      ArithmeticOperator.MULTIPLY -> {
        val result = left.toLong() * right.toLong()
        if (result < Int.MIN_VALUE || result > Int.MAX_VALUE) null else result.toInt()
      }

      ArithmeticOperator.DIVIDE -> if (right == 0) null else left / right
      ArithmeticOperator.MIN -> minOf(left, right)
      ArithmeticOperator.MAX -> maxOf(left, right)
    }
  }

  private fun compare(operator: ComparisonOperator, left: Int, right: Int): Boolean = when (operator) {
    ComparisonOperator.EQUAL -> left == right
    ComparisonOperator.NOT_EQUAL -> left != right
    ComparisonOperator.LESS_THAN -> left < right
    ComparisonOperator.LESS_THAN_OR_EQUAL -> left <= right
    ComparisonOperator.GREATER_THAN -> left > right
    ComparisonOperator.GREATER_THAN_OR_EQUAL -> left >= right
  }

  private fun readCharacteristic(
    state: GameState,
    cardId: CardInstanceId,
    characteristic: NumericCharacteristic,
  ): Int? {
    val effective = continuousEffectProcessor.effectiveCharacteristics(state, cardId) ?: return null
    return when (characteristic) {
      NumericCharacteristic.POWER -> effective.power
      NumericCharacteristic.BASE_POWER -> effective.power
      NumericCharacteristic.COST -> effective.cost
      NumericCharacteristic.BASE_COST -> effective.cost
      NumericCharacteristic.LIFE -> effective.life
      NumericCharacteristic.COUNTER -> effective.counter
    }
  }

  private fun selectCards(
    selector: CardSelector,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): EvaluationResult<List<CardInstanceId>> {
    val candidates = mutableListOf<CardInstanceId>()
    val zoneOrder = listOf(
      ZoneType.DECK, ZoneType.DON_DECK, ZoneType.HAND, ZoneType.TRASH, ZoneType.LEADER,
      ZoneType.CHARACTER, ZoneType.STAGE, ZoneType.COST, ZoneType.LIFE, ZoneType.ATTACHED_DON,
    )

    for (playerId in state.playerOrder) {
      val player = state.players[playerId] ?: continue
      for (zone in zoneOrder) {
        if (selector.zones.isNotEmpty() && zone !in selector.zones) continue
        val cardIds = when (zone) {
          ZoneType.DECK -> player.deck.cards
          ZoneType.DON_DECK -> player.donDeck.cards
          ZoneType.HAND -> player.hand.cards
          ZoneType.TRASH -> player.trash.cards
          ZoneType.LEADER -> player.leader.cards
          ZoneType.CHARACTER -> player.characters.cards
          ZoneType.STAGE -> player.stage.cards
          ZoneType.COST -> player.cost.cards
          ZoneType.LIFE -> player.life.cards
          ZoneType.ATTACHED_DON -> state.donAttachments.filter { host ->
            val hostCard = state.cards[host.hostCardId]
            hostCard != null && hostCard.ownerId == playerId
          }.flatMap { it.donCardIds }
        }
        for (cardId in cardIds) {
          if (matchesSelectorFilters(selector, state, context, cardId, playerId, zone)) {
            candidates += cardId
          }
        }
      }
    }

    if (selector.conditions.isEmpty()) {
      return EvaluationResult.Success(candidates)
    }

    val filtered = mutableListOf<CardInstanceId>()
    for (cardId in candidates) {
      var ok = true
      for (condition in selector.conditions) {
        when (val result = evaluateCondition(condition, state, context, variables, cardId)) {
          is EvaluationResult.Failed -> return result
          is EvaluationResult.Success -> if (!result.value) {
            ok = false
            break
          }
        }
      }
      if (ok) filtered += cardId
    }
    return EvaluationResult.Success(filtered)
  }

  private fun matchesSelectorFilters(
    selector: CardSelector,
    state: GameState,
    context: EffectContext,
    cardId: CardInstanceId,
    zoneOwnerId: PlayerId,
    zone: ZoneType,
  ): Boolean {
    val instance = state.cards[cardId] ?: return false
    val definition = cardCatalog.get(instance.definitionId) ?: return false

    if (selector.owner != null) {
      val expected = resolvePlayerRelation(selector.owner, state, context) ?: return false
      if (instance.ownerId != expected) return false
    }
    if (selector.controller != null) {
      val expected = resolvePlayerRelation(selector.controller, state, context) ?: return false
      val controller = continuousEffectProcessor.effectiveController(state, cardId) ?: instance.controllerId
      if (controller != expected) return false
    }
    if (selector.categories.isNotEmpty() && definition.category !in selector.categories) return false
    if (selector.name != null && definition.name != selector.name) return false

    val effective = continuousEffectProcessor.effectiveCharacteristics(state, cardId) ?: return false
    if (selector.colors.isNotEmpty() && selector.colors.none { it in effective.colors }) return false
    if (selector.types.isNotEmpty() && selector.types.none { it in effective.types }) return false
    if (selector.attributes.isNotEmpty() && selector.attributes.none { it in effective.attributes }) return false

    return true
  }

  private fun resolvePlayerRelation(
    relation: PlayerRelation,
    state: GameState,
    context: EffectContext,
  ): PlayerId? {
    return when (relation) {
      PlayerRelation.SELF -> context.controllerId
      PlayerRelation.OPPONENT -> state.playerOrder.firstOrNull { it != context.controllerId }
      PlayerRelation.TURN_PLAYER -> state.turn?.playerId
      PlayerRelation.NON_TURN_PLAYER -> {
        val turnPlayer = state.turn?.playerId ?: return null
        state.playerOrder.firstOrNull { it != turnPlayer }
      }
    }
  }

  private fun evalFail(code: String, message: String): EvaluationResult.Failed {
    return EvaluationResult.Failed(VmError(code, message, 0))
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
    var cost = (baseCost ?: 0) + costDelta

    val controller = effectiveController(state, cardId) ?: instance.controllerId
    if (state.turn?.playerId == controller && definition.category in setOf(
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
      power = if (definition.basePower != null || powerDelta != 0 || (state.turn?.playerId == controller)) power else definition.basePower,
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

    phaseChanged(state, playerId, null, GamePhase.REFRESH, seq)

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
      GamePhase.END -> finishEndPhase(state, turn.playerId, continuous)
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
  ): GameTransition {
    val turn = state.turn ?: return GameTransition(emptyList(), emptyList())
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    val expired = continuous.effectsToExpire(
      state,
      listOf(
        GameEvent(
          id = EventId("preview"),
          sequence = 0,
          type = GameEventType.TURN_ENDED,
          sourceCardId = null,
          payload = GameEventPayload.TurnEndedPayload(turn.number, playerId),
          visibility = EventVisibility.PUBLIC,
        ),
      ),
    )
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
    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.TURN_STARTED,
      sourceCardId = null,
      payload = GameEventPayload.TurnStartedPayload(nextTurnNumber, nextPlayer),
      visibility = EventVisibility.PUBLIC,
    )

    return GameTransition(events, emptyList())
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
          payload = GameEventPayload.CardStateChangedPayload(donId, CardOrientation.RESTED, FaceState.FACE_UP),
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

  fun validatePlayCard(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val violations = mutableListOf<RuleViolation>()
    val turn = state.turn
    if (turn == null || turn.phase != GamePhase.MAIN || turn.playerId != command.playerId) {
      violations += RuleViolation("NOT_MAIN_PHASE", "Play card only during your Main Phase")
    }
    val card = state.cards[command.cardId]
    if (card == null) {
      violations += RuleViolation("UNKNOWN_CARD", "Card not found")
      return ValidationResult(false, violations)
    }
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
    } else if (def.category == CardCategory.LEADER) {
      violations += RuleViolation("CANNOT_PLAY_LEADER", "Leader cannot be played during the game")
    } else if (def.category !in setOf(CardCategory.CHARACTER, CardCategory.EVENT, CardCategory.STAGE)) {
      violations += RuleViolation("INVALID_CATEGORY", "Card category cannot be played")
    }
    return ValidationResult(violations.isEmpty(), violations)
  }

  fun processPlayCard(
    state: GameState,
    command: PlayCardCommand,
    catalog: CardCatalog,
    continuous: ContinuousEffectProcessor,
    costProcessor: CostProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    val card = state.cards[command.cardId] ?: return GameTransition(emptyList(), emptyList())
    val def = catalog.get(card.definitionId) ?: return GameTransition(emptyList(), emptyList())
    val effectiveCost = costProcessor.effectiveCost(state, command.cardId, continuous, catalog)
    val payment = costProcessor.selectActiveDon(state, command.playerId, effectiveCost)
    if (payment.size < effectiveCost) {
      return GameTransition(emptyList(), emptyList())
    }

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
        return GameTransition(events, emptyList())
      }

      CardCategory.STAGE -> {
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
        return GameTransition(events, emptyList())
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
        val programs = def.abilities.filterIsInstance<ActivatedAbilityDefinition>().map { ability ->
          val activationSeq = seq
          ProgramRequest(
            executionId = ExecutionId("${state.id.value}:event:$activationSeq:${ability.id.value}"),
            program = ability.program,
            context = EffectContext(
              gameId = state.id,
              controllerId = command.playerId,
              sourceCardId = command.cardId,
              abilityId = ability.id,
              triggeringEvent = events.last(),
            ),
          )
        }
        return GameTransition(events, programs)
      }

      else -> return GameTransition(emptyList(), emptyList())
    }
  }

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
    var seq = state.lastEventSequence + 1
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

internal class ZoneInstructionExecutor {

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
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val drawn = player.deck.cards.take(amount)
    if (drawn.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
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
        visibility = EventVisibility.PUBLIC,
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
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    for (cardId in cardIds) {
      val located = locateCard(state, cardId) ?: continue
      val fromZone = located.second
      val ownerId = state.cards[cardId]?.ownerId ?: instruction.destination.playerId
      val toZone = instruction.destination.zone

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
    }
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val selected = player.hand.cards.take(amount)
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
    costProcessor: CostProcessor,
    engine: RulesEngine,
  ): InstructionExecutionResult {
    val command = PlayCardCommand(instruction.playerId, instruction.cardId)
    val transition = MainPhaseController().processPlayCard(
      state, command, catalog, continuous, costProcessor, engine,
    )
    return InstructionExecutionResult.Completed(null, transition.events)
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
    if (moved.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
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
    return InstructionExecutionResult.Completed(null, events)
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
    }
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val candidates = player.cost.cards.filter { id ->
      val card = state.cards[id] ?: return@filter false
      val orientationOk =
        instruction.selector.orientation == null || card.orientation == instruction.selector.orientation
      orientationOk && card.orientation == CardOrientation.ACTIVE
    }.take(amount)
    if (candidates.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_RESTED,
      sourceCardId = null,
      payload = GameEventPayload.DonRestedPayload(instruction.playerId, candidates),
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
    }
    val player = state.players[instruction.playerId] ?: return fail("UNKNOWN_PLAYER", "Player not found")
    val candidates = player.cost.cards.take(amount)
    if (candidates.isEmpty()) return InstructionExecutionResult.Completed(null, emptyList())
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"),
      sequence = seq,
      type = GameEventType.DON_RETURNED,
      sourceCardId = null,
      payload = GameEventPayload.DonReturnedPayload(instruction.playerId, candidates),
      visibility = EventVisibility.PUBLIC,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
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

internal class BattleResolver(
  private val cardCatalog: CardCatalog,
) {

  fun validateDeclareAttack(
    state: GameState,
    command: DeclareAttackCommand,
    continuous: ContinuousEffectProcessor,
  ): ValidationResult {
    val violations = mutableListOf<RuleViolation>()
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

    val attacker = state.cards[command.attackerId]
    if (attacker == null) {
      return ValidationResult(false, violations + RuleViolation("UNKNOWN_ATTACKER", "Attacker not found"))
    }
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

    when (val target = command.targetId) {
      is BattleTarget.PlayerTarget -> {
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
          if (!isOppLeader && !isOppCharacter) {
            violations += RuleViolation("INVALID_TARGET_ZONE", "Target must be opponent Leader or Character")
          }
          if (isOppCharacter && targetCard.orientation != CardOrientation.RESTED) {
            violations += RuleViolation("TARGET_NOT_RESTED", "Character targets must be rested")
          }
        }
      }
    }

    return ValidationResult(violations.isEmpty(), violations)
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
  ): ValidationResult {
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
    costProcessor: CostProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    val card = state.cards[command.cardId] ?: return GameTransition(emptyList(), emptyList())
    val def = cardCatalog.get(card.definitionId) ?: return GameTransition(emptyList(), emptyList())
    val ability = def.abilities.filterIsInstance<ActivatedAbilityDefinition>()
      .firstOrNull { it.timing == ActivationTiming.COUNTER_STEP } ?: return GameTransition(emptyList(), emptyList())

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    val programs = mutableListOf<ProgramRequest>()

    when (val cost = ability.cost) {
      is Cost.NoCost -> Unit
      is Cost.RestDonCost -> {
        val amount = when (val r = evaluator.evaluateValue(
          cost.amount,
          state,
          EffectContext(state.id, command.playerId, command.cardId, ability.id, null),
          emptyMap(),
        )) {
          is EvaluationResult.Success -> (r.value as? StoredValue.IntegerValue)?.value ?: 0
          is EvaluationResult.Failed -> 0
        }
        val dons = costProcessor.selectActiveDon(state, command.playerId, amount)
        if (dons.size < amount) return GameTransition(emptyList(), emptyList())
        if (dons.isNotEmpty()) {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.DON_RESTED,
            sourceCardId = null,
            payload = GameEventPayload.DonRestedPayload(command.playerId, dons),
            visibility = EventVisibility.PUBLIC,
          )
        }
      }

      else -> Unit
    }

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
  ): GameTransition {
    val battle = state.battle ?: return GameTransition(emptyList(), emptyList())
    if (!participantsPresent(state, battle)) {
      return endBattle(state, battle, BattleOutcome.INTERRUPTED)
    }
    return when (battle.step) {
      BattleStep.ATTACK -> moveToStep(state, BattleStep.BLOCK)
      BattleStep.BLOCK -> moveToStep(state, BattleStep.COUNTER)
      BattleStep.COUNTER -> moveToStep(state, BattleStep.DAMAGE)
      BattleStep.DAMAGE -> damageProcessor.processDamage(state, battle, continuous, evaluator)
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
    return GameTransition(emptyList(), emptyList())
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

internal class DamageProcessor(
  private val cardCatalog: CardCatalog,
) {

  fun processDamage(
    state: GameState,
    battle: BattleState,
    continuous: ContinuousEffectProcessor,
    evaluator: RulesEngine,
  ): GameTransition {
    state.cards[battle.attackerId] ?: return endBattleResult(state, battle, BattleOutcome.INTERRUPTED)
    val attackerPower = continuous.effectiveCharacteristics(state, battle.attackerId)?.power ?: 0

    val targetPlayerId: PlayerId
    val targetCardId: CardInstanceId?
    val isLeaderTarget: Boolean

    when (val target = battle.currentTarget) {
      is BattleTarget.CardTarget -> {
        val targetCard =
          state.cards[target.cardInstanceId] ?: return endBattleResult(state, battle, BattleOutcome.INTERRUPTED)
        val targetController = continuous.effectiveController(state, target.cardInstanceId) ?: targetCard.controllerId
        val player = state.players[targetController] ?: return endBattleResult(state, battle, BattleOutcome.INTERRUPTED)
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

    val targetBasePower = targetCardId?.let { continuous.effectiveCharacteristics(state, it)?.power ?: 0 } ?: 0
    val finalTargetPower = targetBasePower + battle.counterPower

    if (attackerPower < finalTargetPower) {
      return endBattleResult(state, battle, BattleOutcome.ATTACKER_LOST)
    }

    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    var seq = state.lastEventSequence

    if (isLeaderTarget) {
      val defenderPlayer =
        state.players[targetPlayerId] ?: return endBattleResult(state, battle, BattleOutcome.INTERRUPTED)
      if (defenderPlayer.life.cards.isEmpty()) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.PLAYER_LOST,
          sourceCardId = battle.attackerId,
          payload = GameEventPayload.PlayerLostPayload(targetPlayerId, GameEndReason.LIFE_DEFEAT),
          visibility = EventVisibility.PUBLIC,
        )
        return GameTransition(events, programs)
      }

      val attackerChars = continuous.effectiveCharacteristics(state, battle.attackerId)
      val isDoubleAttack = attackerChars?.keywords?.contains(KeywordEffect.DOUBLE_ATTACK) == true
      val isBanish = attackerChars?.keywords?.contains(KeywordEffect.BANISH) == true
      val damageCount = if (isDoubleAttack) 2 else 1

      for (i in 0 until damageCount) {
        val currentDefender = state.players[targetPlayerId] ?: break
        if (currentDefender.life.cards.isEmpty()) {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.PLAYER_LOST,
            sourceCardId = battle.attackerId,
            payload = GameEventPayload.PlayerLostPayload(targetPlayerId, GameEndReason.LIFE_DEFEAT),
            visibility = EventVisibility.PUBLIC,
          )
          break
        }

        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.DAMAGE_PROCESSED,
          sourceCardId = battle.attackerId,
          payload = GameEventPayload.DamageProcessedPayload(targetPlayerId, 1, battle.attackerId, null),
          visibility = EventVisibility.PUBLIC,
        )

        val lifeCardId = currentDefender.life.cards.first()
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
        }
      }
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

    val endBattleTransition = endBattleResult(state, battle, BattleOutcome.ATTACKER_WON)
    return GameTransition(events + endBattleTransition.events, programs + endBattleTransition.programsToRun)
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

    val player = state.players[instruction.playerId] ?: return InstructionExecutionResult.Completed(null, emptyList())

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    val remainingLife = player.life.cards.toMutableList()
    var defeated = false

    for (i in 0 until amount) {
      if (defeated) break

      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.DAMAGE_PROCESSED,
        sourceCardId = context.sourceCardId,
        payload = GameEventPayload.DamageProcessedPayload(
          instruction.playerId, 1, context.sourceCardId, instruction.effectId,
        ),
        visibility = EventVisibility.PUBLIC,
      )

      if (remainingLife.isNotEmpty()) {
        val lifeCardId = remainingLife.removeAt(0)
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.CARD_MOVED,
          sourceCardId = lifeCardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = lifeCardId,
            fromPlayerId = instruction.playerId,
            fromZone = ZoneType.LIFE,
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
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.LIFE_CHANGED,
          sourceCardId = null,
          payload = GameEventPayload.LifeChangedPayload(
            instruction.playerId, emptyList(), listOf(lifeCardId), ZoneType.HAND,
          ),
          visibility = EventVisibility.PUBLIC,
        )
      } else {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.PLAYER_LOST,
          sourceCardId = context.sourceCardId,
          payload = GameEventPayload.PlayerLostPayload(instruction.playerId, GameEndReason.LIFE_DEFEAT),
          visibility = EventVisibility.PUBLIC,
        )
        defeated = true
      }
    }

    return InstructionExecutionResult.Completed(null, events)
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

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    for (cardId in cardIds) {
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

  private fun endBattleResult(state: GameState, battle: BattleState, outcome: BattleOutcome): GameTransition {
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
) {

  private val ignoredTriggerTypes = setOf(
    GameEventType.PROGRAM_FRAME_CHANGED,
    GameEventType.PENDING_CHOICE_CHANGED,
  )

  fun validateActivate(
    state: GameState,
    command: ActivateAbilityCommand,
    evaluator: RulesEngine,
  ): ValidationResult {
    val result =
      resolveActivatedAbility(state, command.playerId, command.sourceCardId, command.abilityId, null, evaluator)
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
    val resolved =
      resolveActivatedAbility(state, command.playerId, command.sourceCardId, command.abilityId, null, evaluator)
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
    val matches = findActivatedAbilities(state, instruction.selector, instruction.timing)
    if (matches.isEmpty()) {
      return InstructionExecutionResult.Completed(null, emptyList())
    }
    val events = mutableListOf<GameEvent>()
    val programs = mutableListOf<ProgramRequest>()
    var working = state
    for ((cardId, ability) in matches) {
      val card = working.cards[cardId] ?: continue
      val controller = evaluator.effectiveController(working, cardId) ?: card.controllerId
      val resolved = resolveActivatedAbility(
        working, controller, cardId, ability.id, instruction.timing, evaluator,
      )
      if (resolved is AbilityResolveResult.Invalid) continue
      resolved as AbilityResolveResult.Ok
      val transition = activateAbility(working, resolved, evaluator)
      events += transition.events
      programs += transition.programsToRun
    }
    return InstructionExecutionResult.Completed(null, events)
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
      val controller = evaluator.effectiveController(state, cardId) ?: instance.controllerId
      val abilities =
        definition.abilities.filterIsInstance<AutoAbilityDefinition>() + grantedAutoAbilities(state, cardId)
      for (ability in abilities) {
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

  private fun grantedAutoAbilities(state: GameState, cardId: CardInstanceId): List<AutoAbilityDefinition> {
    return state.activeEffects.mapNotNull { effect ->
      if (cardId !in effect.targetCardIds && effect.targetCardIds.isNotEmpty()) return@mapNotNull null
      val modifier = effect.modifier as? Modifier.AbilityModifier ?: return@mapNotNull null
      if (!modifier.add) return@mapNotNull null
      modifier.ability as? AutoAbilityDefinition
    }
  }

  private fun orderScheduled(state: GameState, items: List<ScheduledAuto>): List<ScheduledAuto> {
    return items.sortedWith(
      compareBy<ScheduledAuto> { it.turnPlayerOwns }.thenBy { it.cardId.value }.thenBy { it.ability.id.value }
        .thenBy { it.triggerEvent.sequence },
    )
  }

  private fun matchesPattern(
    pattern: GameEventPattern,
    event: GameEvent,
    state: GameState,
    sourceCardId: CardInstanceId,
    controllerId: PlayerId,
    evaluator: RulesEngine,
  ): Boolean {
    if (pattern.eventTypes.isNotEmpty() && event.type !in pattern.eventTypes) return false

    if (pattern.source != null) {
      val sourceId = event.sourceCardId ?: return false
      if (!cardMatchesSelector(pattern.source, state, sourceId, controllerId, evaluator, event)) return false
    }

    if (pattern.target != null) {
      val targetIds = extractTargetCardIds(event)
      if (targetIds.isEmpty()) return false
      if (targetIds.none {
          cardMatchesSelector(
            pattern.target, state, it, controllerId, evaluator, event
          )
        }) return false
    }

    if (pattern.player != null) {
      val eventPlayer = extractPlayer(event) ?: return false
      val expected = resolveRelation(pattern.player, state, controllerId) ?: return false
      if (eventPlayer != expected) return false
    }

    if (pattern.targetPlayer != null) {
      val targetPlayer = extractTargetPlayer(event) ?: return false
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
    event: GameEvent,
  ): Boolean {
    val instance = state.cards[cardId] ?: return false
    val definition = cardCatalog.get(instance.definitionId) ?: return false
    if (selector.categories.isNotEmpty() && definition.category !in selector.categories) return false
    if (selector.name != null && definition.name != selector.name) return false
    if (selector.owner != null) {
      val expected = resolveRelation(selector.owner, state, controllerId) ?: return false
      if (instance.ownerId != expected) return false
    }
    if (selector.controller != null) {
      val expected = resolveRelation(selector.controller, state, controllerId) ?: return false
      val effective = evaluator.effectiveController(state, cardId) ?: instance.controllerId
      if (effective != expected) return false
    }
    val effective = evaluator.effectiveCharacteristics(state, cardId)
    if (effective != null) {
      if (selector.colors.isNotEmpty() && selector.colors.none { it in effective.colors }) return false
      if (selector.types.isNotEmpty() && selector.types.none { it in effective.types }) return false
      if (selector.attributes.isNotEmpty() && selector.attributes.none { it in effective.attributes }) return false
    }
    return true
  }

  private fun extractTargetCardIds(event: GameEvent): List<CardInstanceId> = when (val p = event.payload) {
    is GameEventPayload.CardMovedPayload -> listOf(p.cardId)
    is GameEventPayload.CardPlayedPayload -> listOf(p.cardId)
    is GameEventPayload.CardKnockedOutPayload -> listOf(p.cardId)
    is GameEventPayload.CardActivatedPayload -> listOf(p.cardId)
    is GameEventPayload.AttackDeclaredPayload -> listOf(p.attackerId)
    is GameEventPayload.BlockActivatedPayload -> listOf(p.blockerId)
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

  private fun extractTargetPlayer(event: GameEvent): PlayerId? = when (val p = event.payload) {
    is GameEventPayload.DamageProcessedPayload -> p.playerId
    is GameEventPayload.LifeChangedPayload -> p.playerId
    is GameEventPayload.CardKnockedOutPayload -> p.ownerId
    is GameEventPayload.AttackDeclaredPayload -> when (val t = p.target) {
      is BattleTarget.PlayerTarget -> t.playerId
      is BattleTarget.CardTarget -> null
    }

    else -> null
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
      val costPayment: List<CardInstanceId>,
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
  ): AbilityResolveResult {
    val violations = mutableListOf<RuleViolation>()
    val card = state.cards[sourceCardId]
    if (card == null) {
      return AbilityResolveResult.Invalid(listOf(RuleViolation("UNKNOWN_CARD", "Source card not found")))
    }
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
    if (!isTimingLegal(state, ability.timing, playerId)) {
      violations += RuleViolation("ILLEGAL_TIMING", "Ability cannot be activated in the current window")
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
    val costPayment = mutableListOf<CardInstanceId>()
    if (ability.cost !is Cost.NoCost) {
      val paid = payCostPreview(state, playerId, ability.cost, sourceCardId, evaluator)
      if (paid == null) {
        violations += RuleViolation("COST_UNPAID", "Unable to pay ability cost")
      } else {
        costPayment += paid
      }
    }
    if (violations.isNotEmpty()) return AbilityResolveResult.Invalid(violations)
    return AbilityResolveResult.Ok(sourceCardId, ability, playerId, card.objectId, costPayment)
  }

  private fun grantedActivatedAbilities(state: GameState, cardId: CardInstanceId): List<ActivatedAbilityDefinition> {
    return state.activeEffects.mapNotNull { effect ->
      if (cardId !in effect.targetCardIds && effect.targetCardIds.isNotEmpty()) return@mapNotNull null
      val modifier = effect.modifier as? Modifier.AbilityModifier ?: return@mapNotNull null
      if (!modifier.add) return@mapNotNull null
      modifier.ability as? ActivatedAbilityDefinition
    }
  }

  private fun findActivatedAbilities(
    state: GameState,
    selector: AbilitySelector,
    timing: ActivationTiming?,
  ): List<Pair<CardInstanceId, ActivatedAbilityDefinition>> {
    val results = mutableListOf<Pair<CardInstanceId, ActivatedAbilityDefinition>>()
    for ((cardId, instance) in state.cards) {
      if (selector.cardSelector != null) {
        val def = cardCatalog.get(instance.definitionId) ?: continue
        val sel = selector.cardSelector
        if (sel.categories.isNotEmpty() && def.category !in sel.categories) continue
        if (sel.name != null && def.name != sel.name) continue
      }
      val def = cardCatalog.get(instance.definitionId) ?: continue
      val abilities =
        (def.abilities + grantedActivatedAbilities(state, cardId)).filterIsInstance<ActivatedAbilityDefinition>()
      for (ability in abilities) {
        if (selector.abilityId != null && ability.id != selector.abilityId) continue
        if (selector.abilityType != null && selector.abilityType != AbilityType.ACTIVATED) continue
        if (timing != null && ability.timing != timing) continue
        if (selector.timing != null && ability.timing != selector.timing) continue
        results += cardId to ability
      }
    }
    return results.sortedBy { it.first.value + it.second.id.value }
  }

  private fun isTimingLegal(state: GameState, timing: ActivationTiming, playerId: PlayerId): Boolean {
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
      ActivationTiming.ON_PLAY, ActivationTiming.ON_KO, ActivationTiming.ON_OPPONENT_ATTACK, ActivationTiming.ON_TRIGGER, ActivationTiming.AT_GAME_START -> true
    }
  }

  private fun payCostPreview(
    state: GameState,
    playerId: PlayerId,
    cost: Cost,
    sourceCardId: CardInstanceId,
    evaluator: RulesEngine,
  ): List<CardInstanceId>? {
    return when (cost) {
      is Cost.NoCost -> emptyList()
      is Cost.RestSourceCost -> {
        val card = state.cards[sourceCardId] ?: return null
        if (card.orientation != CardOrientation.ACTIVE) return null
        listOf(sourceCardId)
      }

      is Cost.RestDonCost -> {
        val amount = evaluateCostAmount(state, playerId, sourceCardId, cost.amount, evaluator) ?: return null
        val player = state.players[playerId] ?: return null
        val dons = player.cost.cards.filter { state.cards[it]?.orientation == CardOrientation.ACTIVE }
        if (dons.size < amount) return null
        dons.take(amount)
      }

      is Cost.ReturnDonCost -> {
        val amount = evaluateCostAmount(state, playerId, sourceCardId, cost.amount, evaluator) ?: return null
        val player = state.players[playerId] ?: return null
        if (player.cost.cards.size < amount) return null
        player.cost.cards.take(amount)
      }

      is Cost.CombinedCost -> {
        val all = mutableListOf<CardInstanceId>()
        for (part in cost.costs) {
          val paid = payCostPreview(state, playerId, part, sourceCardId, evaluator) ?: return null
          all += paid
        }
        all
      }

      is Cost.TrashCardsCost -> emptyList()
    }
  }

  private fun evaluateCostAmount(
    state: GameState,
    playerId: PlayerId,
    sourceCardId: CardInstanceId,
    amount: ValueExpr,
    evaluator: RulesEngine,
  ): Int? {
    val ctx = EffectContext(state.id, playerId, sourceCardId, null, null)
    return when (val result = evaluator.evaluateValue(amount, state, ctx, emptyMap())) {
      is EvaluationResult.Failed -> null
      is EvaluationResult.Success -> (result.value as? StoredValue.IntegerValue)?.value
    }
  }

  private fun activateAbility(
    state: GameState,
    resolved: AbilityResolveResult.Ok,
    evaluator: RulesEngine,
  ): GameTransition {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    if (resolved.costPayment.isNotEmpty()) {
      when (resolved.ability.cost) {
        is Cost.RestSourceCost -> {
          for (cardId in resolved.costPayment) {
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
        }

        is Cost.RestDonCost, is Cost.CombinedCost -> {
          val dons = resolved.costPayment.filter { id ->
            state.players[resolved.controllerId]?.cost?.cards?.contains(id) == true
          }
          if (dons.isNotEmpty()) {
            seq += 1
            events += GameEvent(
              id = EventId("${state.id.value}:$seq"),
              sequence = seq,
              type = GameEventType.DON_RESTED,
              sourceCardId = null,
              payload = GameEventPayload.DonRestedPayload(resolved.controllerId, dons),
              visibility = EventVisibility.PUBLIC,
            )
          }
        }

        is Cost.ReturnDonCost -> {
          seq += 1
          events += GameEvent(
            id = EventId("${state.id.value}:$seq"),
            sequence = seq,
            type = GameEventType.DON_RETURNED,
            sourceCardId = null,
            payload = GameEventPayload.DonReturnedPayload(resolved.controllerId, resolved.costPayment),
            visibility = EventVisibility.PUBLIC,
          )
        }

        else -> Unit
      }
    }

    seq += 1
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

internal class RuleProcessor {

  fun processRules(state: GameState): GameTransition {
    if (state.status == GameStatus.FINISHED) return GameTransition(emptyList(), emptyList())

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    val defeated = mutableSetOf<PlayerId>()
    val reasons = mutableMapOf<PlayerId, GameEndReason>()

    for (playerId in state.playerOrder) {
      val player = state.players[playerId] ?: continue
      if (player.deck.cards.isEmpty()) {
        defeated += playerId
        reasons[playerId] = GameEndReason.DECK_OUT
      }
    }

    if (defeated.isEmpty()) return GameTransition(emptyList(), emptyList())

    if (defeated.size == state.players.size) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"),
        sequence = seq,
        type = GameEventType.GAME_DRAWN,
        sourceCardId = null,
        payload = GameEventPayload.GameDrawnPayload(defeated, GameEndReason.DRAW),
        visibility = EventVisibility.PUBLIC,
      )
    } else {
      for (playerId in defeated.sortedBy { it.value }) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"),
          sequence = seq,
          type = GameEventType.PLAYER_LOST,
          sourceCardId = null,
          payload = GameEventPayload.PlayerLostPayload(playerId, reasons.getValue(playerId)),
          visibility = EventVisibility.PUBLIC,
        )
      }
    }
    return GameTransition(events, emptyList())
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

internal class LoopProcessor {

  data class LoopResolution(
    val events: List<GameEvent>,
    val repetitions: Int?,
    val draw: Boolean,
  )

  fun detectRepeatedState(
    current: GameState,
    history: List<GameState>,
    window: Int = 3,
  ): Boolean {
    if (history.size < window) return false
    val signature = stateSignature(current)
    return history.takeLast(window).all { stateSignature(it) == signature }
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

  private fun stateSignature(state: GameState): String = buildString {
    append(state.turn?.number ?: 0).append('|')
    append(state.turn?.phase?.name ?: "").append('|')
    for (playerId in state.playerOrder) {
      val player = state.players[playerId] ?: continue
      append(player.deck.cards.size).append(',')
      append(player.hand.cards.size).append(',')
      append(player.life.cards.size).append(',')
      append(player.cost.cards.size).append(',')
      append(player.characters.cards.size).append(',')
    }
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