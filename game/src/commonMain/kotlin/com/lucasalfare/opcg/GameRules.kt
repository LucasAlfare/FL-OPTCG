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
  private val continuousEffectProcessor = ContinuousEffectProcessor()
  private val replacementProcessor = ReplacementProcessor()
  private val ruleProcessor = RuleProcessor()
  private val informationManager = InformationManager()
  private val loopProcessor = LoopProcessor()

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

  override fun execute(
    instruction: Instruction,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun evaluateValue(
    expression: ValueExpr,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<StoredValue> {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun evaluateCondition(
    condition: Condition,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId?,
  ): EvaluationResult<Boolean> {
    throw UnsupportedOperationException("Not implemented")
  }
}

internal class TurnController

internal class MainPhaseController

internal class BattleResolver

internal class DamageProcessor

internal class CostProcessor

internal class EffectScheduler

internal class ContinuousEffectProcessor

internal class ReplacementProcessor

internal class RuleProcessor

internal class InformationManager

internal class LoopProcessor