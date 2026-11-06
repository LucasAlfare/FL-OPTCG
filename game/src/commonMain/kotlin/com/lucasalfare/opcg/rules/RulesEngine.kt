package com.lucasalfare.opcg.rules

import com.lucasalfare.opcg.*

class RulesEngine(private val cardCatalog: CardCatalog) : InstructionExecutor, ProgramEvaluator {

  private val turnController = TurnController()
  private val mainPhaseController = MainPhaseController()
  private val battleResolver = BattleResolver(cardCatalog)
  private val damageProcessor = DamageProcessor(cardCatalog)
  private val costProcessor = CostProcessor()
  private val continuousEffectProcessor = ContinuousEffectProcessor(cardCatalog, this)
  private val effectScheduler = EffectScheduler(cardCatalog, continuousEffectProcessor, this)
  private val replacementProcessor = ReplacementProcessor(cardCatalog, effectScheduler)
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
    if (state.battle != null && isMainPhaseCommand(command)) {
      return ValidationResult(
        false,
        listOf(RuleViolation("BATTLE_IN_PROGRESS", "Main phase actions are not allowed during a battle")),
      )
    }
    val base = when (command) {
      is PlayCardCommand -> mainPhaseController.validatePlayCard(
        state, command, cardCatalog, continuousEffectProcessor, costProcessor
      )

      is GiveDonCommand -> mainPhaseController.validateGiveDon(state, command, continuousEffectProcessor)
      is EndMainPhaseCommand -> mainPhaseController.validateEndMain(state, command)
      is ActivateAbilityCommand -> effectScheduler.validateActivate(state, command, this)
      is DeclareAttackCommand -> battleResolver.validateDeclareAttack(state, command, continuousEffectProcessor)
      is PassBattleStepCommand -> battleResolver.validatePassBattleStep(state, command, continuousEffectProcessor)
      is ActivateBlockerCommand -> battleResolver.validateActivateBlocker(state, command, continuousEffectProcessor)
      is UseCharacterCounterCommand -> battleResolver.validateCharacterCounter(
        state, command, continuousEffectProcessor
      )

      is UseEventCounterCommand -> battleResolver.validateEventCounter(
        state, command, continuousEffectProcessor, this
      )

      is SubmitChoiceCommand -> validateSubmitChoice(state, command)
      is ResolveMulliganCommand -> validateMulligan(state, command)
      is ConcedeCommand -> validateConcede(state, command)
    }
    return applyRuleModifiers(state, command, base)
  }

  private fun actionOf(command: GameCommand): Pair<RuleAction, CardInstanceId?>? = when (command) {
    is PlayCardCommand -> RuleAction.PLAY_CARD to command.cardId
    is ActivateAbilityCommand -> RuleAction.ACTIVATE_ABILITY to command.sourceCardId
    is DeclareAttackCommand -> RuleAction.DECLARE_ATTACK to command.attackerId
    is ActivateBlockerCommand -> RuleAction.ACTIVATE_BLOCKER to command.blockerId
    is UseCharacterCounterCommand -> RuleAction.USE_COUNTER to command.cardId
    is UseEventCounterCommand -> RuleAction.USE_COUNTER to command.cardId
    is GiveDonCommand -> RuleAction.ATTACH_DON to null
    else -> null
  }

  private fun applyRuleModifiers(
    state: GameState,
    command: GameCommand,
    base: ValidationResult,
  ): ValidationResult {
    val (action, subject) = actionOf(command) ?: return base

    val restrictions = continuousEffectProcessor.isActionRestricted(state, action, subject)
    if (restrictions.isNotEmpty()) {
      return ValidationResult(false, base.violations + restrictions)
    }

    if (!base.valid && continuousEffectProcessor.hasPermissionFor(state, action, subject)) {
      val overridable = setOf(
        "NOT_MAIN_PHASE",
        "FIRST_TURN",
        "NOT_COUNTER_STEP",
        "NOT_BLOCK_STEP",
        "ILLEGAL_TIMING",
        "TIMING_MISMATCH",
        "ILLEGAL_ZONE",
      )
      val remaining = base.violations.filterNot { it.code in overridable }
      return ValidationResult(remaining.isEmpty(), remaining)
    }

    return base
  }

  private fun isMainPhaseCommand(command: GameCommand): Boolean = when (command) {
    is PlayCardCommand, is GiveDonCommand, is EndMainPhaseCommand -> true
    else -> false
  }

  fun process(state: GameState, command: GameCommand): GameTransition {
    val validation = validate(state, command)
    if (!validation.valid) return GameTransition(events = emptyList(), programsToRun = emptyList())
    ruleProcessor.resetLoopDetection()
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

      is PassBattleStepCommand -> advanceBattleStep(state)
      is UseCharacterCounterCommand -> battleResolver.processCharacterCounter(state, command, continuousEffectProcessor)
      is UseEventCounterCommand -> battleResolver.processEventCounter(
        state, command, continuousEffectProcessor, this
      )

      is SubmitChoiceCommand -> processSubmitChoice(state, command)
      is ResolveMulliganCommand -> processMulligan(state, command)
      is ConcedeCommand -> processConcede(state, command)
    }
  }

  fun advanceBattleStep(state: GameState): GameTransition = battleResolver.advanceStep(
    state, continuousEffectProcessor, damageProcessor, this, replacementProcessor,
  )

  fun processEvents(state: GameState, events: List<GameEvent>): GameTransition {
    if (events.isEmpty()) return GameTransition(emptyList(), emptyList())
    val expired = continuousEffectProcessor.effectsToExpire(state, events)
    val expirationEvents = expired.mapIndexed { index, effect ->
      GameEvent(
        id = EventId("${state.id.value}:${state.lastEventSequence + 1 + index}"),
        sequence = state.lastEventSequence + 1 + index,
        type = GameEventType.MODIFIER_EXPIRED,
        sourceCardId = effect.sourceCardId,
        payload = GameEventPayload.ModifierExpiredPayload(effect.id),
        visibility = EventVisibility.PUBLIC,
      )
    }
    val transition = effectScheduler.processEvents(state, events, this)
    return GameTransition(
      events = expirationEvents + transition.events,
      programsToRun = transition.programsToRun,
      choiceToResume = transition.choiceToResume,
    )
  }

  fun processAutomaticRules(state: GameState): GameTransition = ruleProcessor.processRules(state)

  fun advancePhase(state: GameState): GameTransition =
    turnController.advancePhase(state, continuousEffectProcessor, this)

  fun startTurn(state: GameState, playerId: PlayerId, turnNumber: Int): GameTransition =
    turnController.startTurn(state, playerId, turnNumber, continuousEffectProcessor, this)

  fun createInitialState(setup: GameSetup): GameCreationResult = gameFactory.create(setup)

  fun deckRuleModifiers(state: GameState): List<DeckRuleModifier> = continuousEffectProcessor.activeDeckModifiers(state)

  fun effectiveCharacteristics(state: GameState, cardId: CardInstanceId): EffectiveCharacteristics? =
    continuousEffectProcessor.effectiveCharacteristics(state, cardId)

  fun evaluatePermanentAbilities(
    state: GameState,
    baseSequence: Long = state.lastEventSequence,
  ): GameTransition = effectScheduler.evaluatePermanentAbilities(state, baseSequence)

  fun effectiveController(state: GameState, cardId: CardInstanceId): PlayerId? =
    continuousEffectProcessor.effectiveController(state, cardId)

  fun selectCardIds(selector: CardSelector, state: GameState, context: EffectContext): List<CardInstanceId>? =
    (selectCards(selector, state, context, emptyMap()) as? EvaluationResult.Success)?.value

  fun stateSignature(state: GameState): String = loopProcessor.signatureOf(state)

  fun resolveRepeatedState(state: GameState, seenSignatures: Collection<String>): GameTransition? {
    if (!loopProcessor.isRepeated(state, seenSignatures)) return null
    return GameTransition(loopProcessor.resolve(state, emptySet(), emptyMap()).events, emptyList())
  }

  override fun execute(
    instruction: Instruction, state: GameState, context: EffectContext,
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
          instruction.characteristic, instruction.operation, ValueExpr.NumberValue(intValue),
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
              NumericCharacteristic.POWER, NumericOperation.SUBTRACT, ValueExpr.NumberValue(power)
            ),
            instruction.duration, seq,
          )
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(
              state, effect, state.lastEventSequence + 1 + index
            )
          },
        )
      }

      is SetCardProperty -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state,
          context,
          cardIds,
          Modifier.PropertyModifier(instruction.property, instruction.value),
          instruction.duration
        )
      }

      is CopyCardProperties -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val refId = resolveCardReference(instruction.reference, variables, context) ?: return failExec(
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
              state,
              context,
              listOf(cardId),
              null,
              Modifier.PropertyModifier(property, value),
              instruction.duration,
              seq
            )
          }
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(
              state, effect, state.lastEventSequence + 1 + index
            )
          },
        )
      }

      is GrantKeyword -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state,
          context,
          cardIds,
          Modifier.KeywordModifier(instruction.keyword, KeywordOperation.ADD),
          instruction.duration
        )
      }

      is RemoveKeyword -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state,
          context,
          cardIds,
          Modifier.KeywordModifier(instruction.keyword, KeywordOperation.REMOVE),
          instruction.duration
        )
      }

      is GrantAbility -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds, Modifier.AbilityModifier(instruction.ability, add = true), instruction.duration
        )
      }

      is CopyAbilities -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        val refId = resolveCardReference(instruction.reference, variables, context) ?: return failExec(
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
              state,
              context,
              listOf(cardId),
              null,
              Modifier.AbilityModifier(ability, add = true),
              instruction.duration,
              seq
            )
          }
        }
        InstructionExecutionResult.Completed(
          null,
          effects.mapIndexed { index, effect ->
            modifierAddedEvent(
              state, effect, state.lastEventSequence + 1 + index
            )
          },
        )
      }

      is NegateEffects -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds, Modifier.EffectNegationModifier(instruction.scope), instruction.duration
        )
      }

      is ChangeControl -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds, Modifier.ControlModifier(instruction.playerId), instruction.duration
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
          state,
          context,
          emptyList(),
          instruction.playerId,
          Modifier.DamagePreventionModifier(instruction.playerId, ValueExpr.NumberValue(amount)),
          instruction.duration,
          seq
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is ApplyRestriction -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds, Modifier.RestrictionModifier(instruction.restriction), instruction.duration
        )
      }

      is GrantPermission -> {
        val cardIds = resolveSourceCards(instruction.source, variables) ?: return failExec(
          "MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0
        )
        emitModifiers(
          state, context, cardIds, Modifier.PermissionModifier(instruction.permission), instruction.duration
        )
      }

      is AddActivationCost -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state,
          context,
          emptyList(),
          null,
          Modifier.ActivationCostModifier(instruction.selector, instruction.cost),
          instruction.duration,
          seq
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is ModifyDeckRule -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state,
          context,
          emptyList(),
          null,
          Modifier.DeckRuleEffectModifier(instruction.modifier),
          instruction.duration,
          seq
        )
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is RegisterReplacement -> {
        val seq = state.lastEventSequence + 1
        val effect = activeEffect(
          state,
          context,
          emptyList(),
          null,
          Modifier.ReplacementModifier(instruction.definition),
          instruction.definition.duration,
          seq
        ).copy(id = instruction.definition.id, sourceAbilityId = context.abilityId)
        InstructionExecutionResult.Completed(null, listOf(modifierAddedEvent(state, effect, seq)))
      }

      is KnockOut -> damageProcessor.executeKnockOut(
        state, instruction, context, variables, replacementProcessor, continuousEffectProcessor, cardCatalog, this,
      )

      is DealDamage -> damageProcessor.executeDealDamage(
        state, instruction, context, variables, this, continuousEffectProcessor,
      )

      is SetLife -> damageProcessor.executeSetLife(state, instruction, context, variables)
      is Draw -> zoneInstructionExecutor.draw(state, instruction, context, variables, this)
      is MoveCards -> zoneInstructionExecutor.moveCards(
        state, instruction, context, variables, this, informationManager,
      )

      is ReturnCardsToOwnerHand -> zoneInstructionExecutor.returnCardsToOwnerHand(
        state, instruction, variables, informationManager,
      )

      is TrashFromHand -> zoneInstructionExecutor.trashFromHand(state, instruction, context, variables, this)
      is PlayCard -> zoneInstructionExecutor.playCard(
        state, instruction, context, variables, cardCatalog, continuousEffectProcessor, costProcessor, this,
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
      is SelectFromVariable -> executeSelectFromVariable(state, instruction, context, variables)
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
        if (allowed.isEmpty()) return "No options available"
        if (answer.optionId !in allowed) return "Option not in candidates"
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
        if (answer.cardIds.toSet().size != answer.cardIds.size) return "Duplicate cards in selection"
        val allowed = choice.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }.toSet()
        if (allowed.isEmpty() && choice.minimum > 0) return "No card candidates available"
        if (allowed.isNotEmpty() && answer.cardIds.any { it !in allowed }) return "Card not in candidates"
        null
      }

      ChoiceType.CARD_ORDER -> {
        if (answer !is ChoiceAnswer.CardOrderAnswer) return "Expected CardOrderAnswer"
        val allowed = choice.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }
        if (allowed.isEmpty()) return "No card candidates available"
        if (answer.cardIds.size != allowed.size) return "Invalid card order size"
        if (answer.cardIds.toSet().size != answer.cardIds.size) return "Duplicate cards in order"
        if (answer.cardIds.toSet() != allowed.toSet()) return "Invalid card order"
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
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.CHOICE_SUBMITTED, sourceCardId = null,
      payload = GameEventPayload.ChoiceSubmittedPayload(command.playerId, command.choiceId, command.answer),
      visibility = choice.visibility,
    )
    return GameTransition(
      events = events, programsToRun = emptyList(),
      choiceToResume = PlayerChoice(command.choiceId, command.playerId, command.answer),
    )
  }

  private fun validateMulligan(state: GameState, command: ResolveMulliganCommand): ValidationResult {
    val setup = state.setup ?: return invalid("NOT_IN_SETUP", "Game is not in setup")
    if (setup.step != GameSetupStep.MULLIGAN) return invalid("NOT_MULLIGAN_STEP", "Not in Mulligan step")
    if (command.playerId !in setup.pendingMulliganPlayers) return invalid(
      "NOT_PENDING", "Player is not awaiting mulligan"
    )
    val player = state.players[command.playerId] ?: return invalid("UNKNOWN_PLAYER", "Player not found")
    if (player.hand.cards.size != 5) return invalid("INVALID_HAND", "Mulligan requires exactly 5 cards in hand")
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
          id = EventId("${state.id.value}:$seq"), sequence = seq,
          type = GameEventType.CARD_MOVED, sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId, fromPlayerId = command.playerId, fromZone = ZoneType.HAND,
            toPlayerId = command.playerId, toZone = ZoneType.DECK, destinationIndex = null,
            newObjectId = null, orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN, visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }
      val deckAfter = player.deck.cards + returned
      val (shuffled, randomAfter) = RandomSupport.fisherYates(deckAfter.toMutableList(), randomState)
      randomState = randomAfter
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"), sequence = seq,
        type = GameEventType.DECK_SHUFFLED, sourceCardId = null,
        payload = GameEventPayload.DeckShuffledPayload(
          playerId = command.playerId, zone = ZoneType.DECK,
          resultingOrder = shuffled, randomStateAfter = randomState,
        ),
        visibility = EventVisibility.PUBLIC,
      )
      val drawn = shuffled.take(minOf(5, shuffled.size))
      for (cardId in drawn) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"), sequence = seq,
          type = GameEventType.CARD_MOVED, sourceCardId = cardId,
          payload = GameEventPayload.CardMovedPayload(
            cardId = cardId, fromPlayerId = command.playerId, fromZone = ZoneType.DECK,
            toPlayerId = command.playerId, toZone = ZoneType.HAND, destinationIndex = null,
            newObjectId = null, orientation = CardOrientation.NOT_APPLICABLE,
            faceState = FaceState.FACE_DOWN, visibility = CardVisibility.OWNER_ONLY,
          ),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }
      if (drawn.isNotEmpty()) {
        seq += 1
        events += GameEvent(
          id = EventId("${state.id.value}:$seq"), sequence = seq,
          type = GameEventType.CARDS_DRAWN, sourceCardId = null,
          payload = GameEventPayload.CardsDrawnPayload(command.playerId, drawn),
          visibility = EventVisibility.PRIVATE_TO_PLAYER(command.playerId),
        )
      }
    }

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.MULLIGAN_RESOLVED, sourceCardId = null,
      payload = GameEventPayload.MulliganResolvedPayload(
        playerId = command.playerId, redrawn = command.redraw, returnedCardIds = returned,
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
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.PLAYER_LOST, sourceCardId = null,
      payload = GameEventPayload.PlayerLostPayload(command.playerId, GameEndReason.CONCESSION),
      visibility = EventVisibility.PUBLIC,
    )
    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.PLAYER_WON, sourceCardId = null,
      payload = GameEventPayload.PlayerWonPayload(opponent, GameEndReason.CONCESSION),
      visibility = EventVisibility.PUBLIC,
    )
    return GameTransition(events = events, programsToRun = emptyList())
  }

  private fun evaluateInt(
    expr: ValueExpr, state: GameState, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): Int? {
    return when (val r = evaluateValue(expr, state, context, variables)) {
      is EvaluationResult.Failed -> null
      is EvaluationResult.Success -> (r.value as? StoredValue.IntegerValue)?.value
    }
  }

  private fun executeSelectCards(
    state: GameState, instruction: SelectCards, context: EffectContext,
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
      playerId = instruction.playerId, frameId = ExecutionId("pending"),
      type = ChoiceType.CARD_SELECTION,
      minimum = minVal.coerceAtLeast(0), maximum = maxVal.coerceAtLeast(minVal),
      candidates = candidates.map { ChoiceCandidate.CardCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeSelectFromVariable(
    state: GameState, instruction: SelectFromVariable, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    if (instruction.selector.zones.isNotEmpty()) {
      return failExec("UNSUPPORTED_SELECTOR", "SelectFromVariable cannot filter by zones", 0)
    }
    if (instruction.destination == instruction.source || instruction.remainderDestination == instruction.source) {
      return failExec("INVALID_DESTINATION", "SelectFromVariable cannot overwrite its source variable", 0)
    }
    if (instruction.remainderDestination != null && instruction.remainderDestination == instruction.destination) {
      return failExec("DUPLICATE_DESTINATION", "SelectFromVariable destinations must differ", 0)
    }
    val sourceIds = when (val v = variables[instruction.source]) {
      null -> return failExec("MISSING_VARIABLE", "Source variable not found: ${instruction.source.value}", 0)
      is StoredValue.CardIdsValue -> v.value
      else -> return failExec("INVALID_SOURCE", "SelectFromVariable requires card ids", 0)
    }
    if (sourceIds.toSet().size != sourceIds.size) {
      return failExec("DUPLICATE_CARDS", "Source variable contains duplicate cards", 0)
    }
    if (sourceIds.any { state.cards[it] == null }) {
      return failExec("UNKNOWN_CARD", "Source variable references a card not in the game", 0)
    }

    val matching = sourceIds.filter { matchesSelectorFilters(instruction.selector, state, context, it) }
    val candidates = when (val r = filterByConditions(instruction.selector, state, context, variables, matching)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    val minVal = evaluateInt(instruction.minimum, state, context, variables)
      ?: return failExec("INVALID_RANGE", "SelectFromVariable minimum must evaluate to an integer", 0)
    val maxVal = evaluateInt(instruction.maximum, state, context, variables)
      ?: return failExec("INVALID_RANGE", "SelectFromVariable maximum must evaluate to an integer", 0)
    if (minVal < 0 || maxVal < minVal) {
      return failExec("INVALID_RANGE", "Selection range $minVal..$maxVal is invalid", 0)
    }

    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:selvar:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId, frameId = ExecutionId("pending"),
      type = ChoiceType.CARD_SELECTION,
      minimum = minOf(minVal, candidates.size), maximum = minOf(maxVal, candidates.size),
      candidates = candidates.map { ChoiceCandidate.CardCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeCountCards(
    state: GameState, instruction: CountCards, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val candidates = when (val r = selectCards(instruction.selector, state, context, variables)) {
      is EvaluationResult.Failed -> return InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> r.value
    }
    return InstructionExecutionResult.Completed(StoredValue.IntegerValue(candidates.size), emptyList())
  }

  private fun executeLookAtTop(
    state: GameState, instruction: LookAtTop, context: EffectContext,
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

    val viewer = context.controllerId

    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence

    seq += 1
    events += GameEvent(
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.CARD_REVEALED, sourceCardId = context.sourceCardId,
      payload = GameEventPayload.CardRevealedPayload(cards, CardVisibility.PLAYERS(setOf(viewer))),
      visibility = EventVisibility.PRIVATE_TO_PLAYER(viewer),
    )

    for (cardId in cards) {
      val card = state.cards[cardId] ?: continue
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"), sequence = seq,
        type = GameEventType.CARD_STATE_CHANGED, sourceCardId = cardId,
        payload = GameEventPayload.CardStateChangedPayload(cardId, card.orientation, FaceState.FACE_UP),
        visibility = EventVisibility.PRIVATE_TO_PLAYER(viewer),
      )
    }

    return InstructionExecutionResult.Completed(StoredValue.CardIdsValue(cards), events)
  }

  private fun executeRevealCards(
    state: GameState, instruction: RevealCards, context: EffectContext,
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
        Viewer.OWNER -> cardIds.forEach { id -> state.cards[id]?.let { viewers += it.ownerId } }
      }
    }
    val cardVisibility = if (isPublic) CardVisibility.PUBLIC else CardVisibility.PLAYERS(viewers)
    val eventVisibility = if (isPublic) EventVisibility.PUBLIC else EventVisibility.PRIVATE_TO_PLAYERS(viewers)
    val seq = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$seq"), sequence = seq,
      type = GameEventType.CARD_REVEALED, sourceCardId = context.sourceCardId,
      payload = GameEventPayload.CardRevealedPayload(cardIds, cardVisibility),
      visibility = eventVisibility,
    )
    return InstructionExecutionResult.Completed(null, listOf(event))
  }

  private fun executeChooseOption(
    state: GameState, instruction: ChooseOption, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:opt:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId, frameId = ExecutionId("pending"),
      type = ChoiceType.OPTION, minimum = 1, maximum = 1,
      candidates = instruction.options.map { ChoiceCandidate.OptionCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeChooseNumber(
    state: GameState, instruction: ChooseNumber, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    val minVal = evaluateInt(instruction.minimum, state, context, variables) ?: 0
    val maxVal = evaluateInt(instruction.maximum, state, context, variables) ?: minVal
    val choice = PendingChoice(
      id = ChoiceId("${state.id.value}:num:${state.lastEventSequence + 1}"),
      playerId = instruction.playerId, frameId = ExecutionId("pending"),
      type = ChoiceType.NUMBER, minimum = minVal, maximum = maxVal,
      candidates = (minVal..maxVal).map { ChoiceCandidate.NumberCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeOrderCards(
    state: GameState, instruction: OrderCards, context: EffectContext,
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
      playerId = instruction.playerId, frameId = ExecutionId("pending"),
      type = ChoiceType.CARD_ORDER, minimum = cardIds.size, maximum = cardIds.size,
      candidates = cardIds.map { ChoiceCandidate.CardCandidate(it) },
      visibility = EventVisibility.PRIVATE_TO_PLAYER(instruction.playerId),
    )
    return InstructionExecutionResult.Suspended(choice)
  }

  private fun executeCheckCondition(
    state: GameState, instruction: CheckCondition, context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    return when (val r = evaluateCondition(instruction.condition, state, context, variables)) {
      is EvaluationResult.Failed -> InstructionExecutionResult.Failed(r.error)
      is EvaluationResult.Success -> InstructionExecutionResult.Completed(
        StoredValue.BooleanValue(r.value), emptyList()
      )
    }
  }

  private fun executeSetGameResult(
    state: GameState, instruction: SetGameResult, context: EffectContext,
  ): InstructionExecutionResult {
    val events = mutableListOf<GameEvent>()
    var seq = state.lastEventSequence
    val result = instruction.result

    if (result.reason == GameEndReason.DRAW || (result.winnerId == null && result.loserIds.isEmpty())) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"), sequence = seq,
        type = GameEventType.GAME_DRAWN, sourceCardId = context.sourceCardId,
        payload = GameEventPayload.GameDrawnPayload(state.players.keys, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
      return InstructionExecutionResult.Completed(null, events)
    }

    result.winnerId?.let { winner ->
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"), sequence = seq,
        type = GameEventType.PLAYER_WON, sourceCardId = context.sourceCardId,
        payload = GameEventPayload.PlayerWonPayload(winner, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
    }
    for (loser in result.loserIds) {
      seq += 1
      events += GameEvent(
        id = EventId("${state.id.value}:$seq"), sequence = seq,
        type = GameEventType.PLAYER_LOST, sourceCardId = context.sourceCardId,
        payload = GameEventPayload.PlayerLostPayload(loser, result.reason),
        visibility = EventVisibility.PUBLIC,
      )
    }
    return InstructionExecutionResult.Completed(null, events)
  }

  private fun emitModifiers(
    state: GameState, context: EffectContext, cardIds: List<CardInstanceId>,
    modifier: Modifier, duration: Duration,
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
    state: GameState, context: EffectContext, targetCardIds: List<CardInstanceId>,
    targetPlayerId: PlayerId?, modifier: Modifier, duration: Duration, sequence: Long,
  ): ActiveEffect {
    return ActiveEffect(
      id = EffectId("${state.id.value}:effect:$sequence"),
      sourceCardId = context.sourceCardId, sourceAbilityId = context.abilityId,
      controllerId = context.controllerId, targetCardIds = targetCardIds,
      targetPlayerId = targetPlayerId, modifier = modifier,
      duration = duration, timestampSequence = sequence,
      nextOpponentTurn = nextOpponentTurnMarker(state, context.controllerId, duration),
    )
  }

  private fun nextOpponentTurnMarker(
    state: GameState, controllerId: PlayerId, duration: Duration,
  ): NextOpponentTurnMarker? {
    if (duration !is Duration.UntilEndOfNextOpponentTurn) return null
    val turn = state.turn ?: return null
    val opponentId = state.playerOrder.firstOrNull { it != controllerId } ?: return null
    val expiresOnTurnNumber = if (turn.playerId == controllerId) turn.number + 1 else turn.number + 2
    return NextOpponentTurnMarker(opponentId, expiresOnTurnNumber)
  }

  private fun modifierAddedEvent(state: GameState, effect: ActiveEffect, sequence: Long): GameEvent {
    return GameEvent(
      id = EventId("${state.id.value}:$sequence"), sequence = sequence,
      type = GameEventType.MODIFIER_ADDED, sourceCardId = effect.sourceCardId,
      payload = GameEventPayload.ModifierAddedPayload(effect),
      visibility = EventVisibility.PUBLIC,
    )
  }

  private fun resolveSourceCards(source: VariableId, variables: Map<VariableId, StoredValue>): List<CardInstanceId>? {
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
    context: EffectContext,
  ): CardInstanceId? {
    reference.instanceId?.let { return it }
    val variableId = reference.variableId ?: return null
    if (variableId == SOURCE_CARD) return context.sourceCardId
    return when (val value = variables[variableId]) {
      is StoredValue.CardValue -> value.value
      is StoredValue.CardIdsValue -> value.value.singleOrNull()
      else -> null
    }
  }

  private fun propertyValueFrom(chars: EffectiveCharacteristics, property: CardProperty): PropertyValue? =
    when (property) {
      CardProperty.NAME -> PropertyValue.StringValue(chars.name)
      CardProperty.COLOR -> PropertyValue.ColorSetValue(chars.colors)
      CardProperty.TYPE -> PropertyValue.TypeSetValue(chars.types)
      CardProperty.ATTRIBUTE -> PropertyValue.AttributeSetValue(chars.attributes)
      CardProperty.KEYWORDS -> PropertyValue.KeywordSetValue(chars.keywords)
    }

  @Suppress("SameParameterValue")
  private fun failExec(code: String, message: String, pointer: Int): InstructionExecutionResult.Failed =
    InstructionExecutionResult.Failed(VmError(code, message, pointer))

  private fun EvaluationResult.Failed.asExecution(): InstructionExecutionResult.Failed =
    InstructionExecutionResult.Failed(error)

  private fun StoredValue.asInt(): Int? = when (this) {
    is StoredValue.IntegerValue -> value
    is StoredValue.BooleanValue -> if (value) 1 else 0
    else -> null
  }

  override fun evaluateValue(
    expression: ValueExpr, state: GameState, context: EffectContext,
    variables: Map<VariableId, StoredValue>, candidateCardId: CardInstanceId?,
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
        if (candidateCardId == null) return evalFail("NO_CANDIDATE", "CurrentCardValue requires candidateCardId")
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

      is ValueExpr.CardCharacteristic -> {
        val cardId = resolveCardReference(expression.cardReference, variables, context) ?: return evalFail(
          "MISSING_CARD_REFERENCE",
          "Card reference did not resolve to exactly one card",
        )
        val value = readCharacteristic(state, cardId, expression.characteristic) ?: return evalFail(
          "MISSING_CHARACTERISTIC",
          "Characteristic ${expression.characteristic} unavailable for card ${cardId.value}",
        )
        EvaluationResult.Success(StoredValue.IntegerValue(value))
      }
    }
  }

  override fun evaluateCondition(
    condition: Condition, state: GameState, context: EffectContext,
    variables: Map<VariableId, StoredValue>, candidateCardId: CardInstanceId?,
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
    state: GameState, cardId: CardInstanceId, characteristic: NumericCharacteristic,
  ): Int? {
    val effective = continuousEffectProcessor.effectiveCharacteristics(state, cardId) ?: return null
    return when (characteristic) {
      NumericCharacteristic.POWER -> effective.power
      NumericCharacteristic.BASE_POWER -> effective.power
      NumericCharacteristic.COST -> effective.cost
      NumericCharacteristic.BASE_COST -> effective.cost
      NumericCharacteristic.LIFE -> effective.life
      NumericCharacteristic.COUNTER -> effective.counter
      NumericCharacteristic.ATTACHED_DON_COUNT -> effective.attachedDonCount
    }
  }

  private fun selectCards(
    selector: CardSelector, state: GameState, context: EffectContext,
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
          if (matchesSelectorFilters(selector, state, context, cardId)) candidates += cardId
        }
      }
    }

    return filterByConditions(selector, state, context, variables, candidates)
  }

  private fun filterByConditions(
    selector: CardSelector, state: GameState, context: EffectContext,
    variables: Map<VariableId, StoredValue>, cardIds: List<CardInstanceId>,
  ): EvaluationResult<List<CardInstanceId>> {
    if (selector.conditions.isEmpty()) return EvaluationResult.Success(cardIds)

    val filtered = mutableListOf<CardInstanceId>()
    for (cardId in cardIds) {
      var ok = true
      for (condition in selector.conditions) {
        when (val result = evaluateCondition(condition, state, context, variables, cardId)) {
          is EvaluationResult.Failed -> return result
          is EvaluationResult.Success -> if (!result.value) {
            ok = false; break
          }
        }
      }
      if (ok) filtered += cardId
    }
    return EvaluationResult.Success(filtered)
  }

  private fun matchesSelectorFilters(
    selector: CardSelector, state: GameState, context: EffectContext,
    cardId: CardInstanceId,
  ): Boolean {
    val instance = state.cards[cardId] ?: return false
    val definition = cardCatalog.get(instance.definitionId) ?: return false
    if (selector.sameAsSource && context.sourceCardId != cardId) return false
    if (selector.excludeSource && context.sourceCardId == cardId) return false
    if (selector.orientation != null && instance.orientation != selector.orientation) return false
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
    val effective = continuousEffectProcessor.effectiveCharacteristics(state, cardId) ?: return false
    if (selector.name != null && effective.name != selector.name) return false
    if (selector.colors.isNotEmpty() && selector.colors.none { it in effective.colors }) return false
    if (selector.types.isNotEmpty() && selector.types.none { it in effective.types }) return false
    if (selector.attributes.isNotEmpty() && selector.attributes.none { it in effective.attributes }) return false
    if (selector.keywords.isNotEmpty() && selector.keywords.none { it in effective.keywords }) return false
    return true
  }

  private fun resolvePlayerRelation(relation: PlayerRelation, state: GameState, context: EffectContext): PlayerId? =
    when (relation) {
      PlayerRelation.SELF -> context.controllerId
      PlayerRelation.OPPONENT -> state.playerOrder.firstOrNull { it != context.controllerId }
      PlayerRelation.TURN_PLAYER -> state.turn?.playerId
      PlayerRelation.NON_TURN_PLAYER -> {
        val turnPlayer = state.turn?.playerId ?: return null
        state.playerOrder.firstOrNull { it != turnPlayer }
      }
    }

  private fun evalFail(code: String, message: String): EvaluationResult.Failed =
    EvaluationResult.Failed(VmError(code, message, 0))
}