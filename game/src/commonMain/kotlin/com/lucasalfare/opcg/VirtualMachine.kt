package com.lucasalfare.opcg

enum class ProgramStatus {
  QUEUED, RUNNING, SUSPENDED, COMPLETED, FAILED,
}

data class ProgramCounter(
  val programId: ProgramId,
  val blockPath: List<Int>,
  val instructionPointer: Int,
)

sealed class StoredValue {
  data class IntegerValue(val value: Int) : StoredValue()
  data class BooleanValue(val value: Boolean) : StoredValue()
  data class CardIdsValue(val value: List<CardInstanceId>) : StoredValue()
  data class OptionValue(val value: OptionId) : StoredValue()
  data class PlayerValue(val value: PlayerId) : StoredValue()
  data class CardValue(val value: CardInstanceId) : StoredValue()
}

data class ProgramFrame(
  val id: ExecutionId,
  val program: Program,
  val context: EffectContext,
  val instructionPointer: Int,
  val variables: Map<VariableId, StoredValue>,
  val callStack: List<ProgramCounter>,
  val status: ProgramStatus,
  val pendingChoiceId: ChoiceId?,
)

enum class ChoiceType {
  OPTION, NUMBER, CARD_SELECTION, CARD_ORDER, BOOLEAN,
}

sealed class ChoiceCandidate {
  data class OptionCandidate(val optionId: OptionId) : ChoiceCandidate()
  data class NumberCandidate(val value: Int) : ChoiceCandidate()
  data class CardCandidate(val cardId: CardInstanceId) : ChoiceCandidate()
}

data class PendingChoice(
  val id: ChoiceId,
  val playerId: PlayerId,
  val frameId: ExecutionId,
  val type: ChoiceType,
  val minimum: Int,
  val maximum: Int,
  val candidates: List<ChoiceCandidate>,
  val visibility: EventVisibility,
)

data class PlayerChoice(
  val choiceId: ChoiceId,
  val playerId: PlayerId,
  val answer: ChoiceAnswer,
)

data class VmError(
  val code: String,
  val message: String,
  val instructionPointer: Int,
)

sealed class InstructionExecutionResult {
  data class Completed(
    val value: StoredValue?,
    val events: List<GameEvent>,
  ) : InstructionExecutionResult()

  data class Suspended(
    val choice: PendingChoice,
  ) : InstructionExecutionResult()

  data class Failed(
    val error: VmError,
  ) : InstructionExecutionResult()
}

sealed class EvaluationResult<out T> {
  data class Success<T>(val value: T) : EvaluationResult<T>()
  data class Failed(val error: VmError) : EvaluationResult<Nothing>()
}

interface InstructionExecutor {
  fun execute(
    instruction: Instruction,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
  ): InstructionExecutionResult
}

interface ProgramEvaluator {
  fun evaluateValue(
    expression: ValueExpr,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId? = null,
  ): EvaluationResult<StoredValue>

  fun evaluateCondition(
    condition: Condition,
    state: GameState,
    context: EffectContext,
    variables: Map<VariableId, StoredValue>,
    candidateCardId: CardInstanceId? = null,
  ): EvaluationResult<Boolean>
}

interface EventApplier {
  fun apply(state: GameState, events: List<GameEvent>): GameState
}

sealed class VmTransition {
  data class VmRunning(
    val frame: ProgramFrame,
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()

  data class VmSuspended(
    val frame: ProgramFrame,
    val choice: PendingChoice,
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()

  data class VmCompleted(
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()

  data class VmFailed(
    val error: VmError,
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()
}