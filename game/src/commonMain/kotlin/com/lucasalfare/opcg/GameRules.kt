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
  private val continuousEffectProcessor = ContinuousEffectProcessor(cardCatalog)
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

      else -> failExec("UNSUPPORTED_INSTRUCTION", "Instruction not handled: ${instruction::class.simpleName}", 0)
    }
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

internal class TurnController

internal class MainPhaseController

internal class BattleResolver

internal class DamageProcessor

internal class CostProcessor

internal class EffectScheduler

internal class RuleProcessor

internal class InformationManager

internal class LoopProcessor