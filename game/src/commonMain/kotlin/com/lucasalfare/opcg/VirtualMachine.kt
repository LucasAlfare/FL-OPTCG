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

class VirtualMachine(
  private val executor: InstructionExecutor,
  private val evaluator: ProgramEvaluator,
  private val eventApplier: EventApplier,
) {

  fun start(
    program: Program,
    context: EffectContext,
    state: GameState,
    executionId: ExecutionId,
  ): VmTransition {
    val frame = ProgramFrame(
      id = executionId,
      program = program,
      context = context,
      instructionPointer = 0,
      variables = emptyMap(),
      callStack = emptyList(),
      status = ProgramStatus.RUNNING,
      pendingChoiceId = null,
    )
    return run(frame, state, emptyList())
  }

  fun resume(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
  ): VmTransition {
    if (frame.status != ProgramStatus.SUSPENDED || frame.pendingChoiceId == null) {
      return fail(frame, state, emptyList(), "INVALID_RESUME", "Frame is not suspended")
    }
    if (choice.choiceId != frame.pendingChoiceId) {
      return fail(frame, state, emptyList(), "CHOICE_MISMATCH", "Choice id does not match pending choice")
    }

    val block = currentBlock(frame)
    if (frame.instructionPointer !in block.indices) {
      return fail(frame, state, emptyList(), "INVALID_POINTER", "Instruction pointer out of range on resume")
    }

    return when (val current = block[frame.instructionPointer]) {
      is Optional -> resumeOptional(frame, choice, state, current)
      else -> resumeExecutorChoice(frame, choice, state)
    }
  }

  private fun resumeOptional(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
    optional: Optional,
  ): VmTransition {
    if (choice.answer !is ChoiceAnswer.BooleanAnswer) {
      return fail(frame, state, emptyList(), "INVALID_ANSWER", "Optional requires boolean answer")
    }
    val cleared = frame.copy(status = ProgramStatus.RUNNING, pendingChoiceId = null)
    val answer = choice.answer as ChoiceAnswer.BooleanAnswer
    return if (answer.value && optional.instructions.isNotEmpty()) {
      run(enterBlock(cleared, listOf(frame.instructionPointer), frame.instructionPointer + 1), state, emptyList())
    } else {
      run(cleared.copy(instructionPointer = frame.instructionPointer + 1), state, emptyList())
    }
  }

  private fun resumeExecutorChoice(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
  ): VmTransition {
    val variables = frame.variables + (VariableId("__choice_result") to storedFromAnswer(choice.answer))
    val advanced = frame.copy(
      status = ProgramStatus.RUNNING,
      pendingChoiceId = null,
      variables = variables,
      instructionPointer = frame.instructionPointer + 1,
    )
    return run(advanced, state, emptyList())
  }

  private fun storedFromAnswer(answer: ChoiceAnswer): StoredValue = when (answer) {
    is ChoiceAnswer.OptionAnswer -> StoredValue.OptionValue(answer.optionId)
    is ChoiceAnswer.NumberAnswer -> StoredValue.IntegerValue(answer.value)
    is ChoiceAnswer.CardSelectionAnswer -> StoredValue.CardIdsValue(answer.cardIds)
    is ChoiceAnswer.CardOrderAnswer -> StoredValue.CardIdsValue(answer.cardIds)
    is ChoiceAnswer.BooleanAnswer -> StoredValue.BooleanValue(answer.value)
  }

  private fun run(
    initialFrame: ProgramFrame,
    initialState: GameState,
    initialEvents: List<GameEvent>,
  ): VmTransition {
    var frame = initialFrame
    var state = initialState
    val accumulated = initialEvents.toMutableList()

    while (true) {
      val block = currentBlock(frame)

      if (frame.instructionPointer >= block.size) {
        if (frame.callStack.isEmpty()) {
          return VmTransition.VmCompleted(state = state, events = accumulated)
        }
        frame = exitBlock(frame)
        continue
      }

      when (val instruction = block[frame.instructionPointer]) {
        is Sequence -> {
          frame = if (instruction.instructions.isEmpty()) {
            frame.copy(instructionPointer = frame.instructionPointer + 1)
          } else {
            enterBlock(frame, listOf(frame.instructionPointer), frame.instructionPointer + 1)
          }
        }

        is Conditional -> {
          when (val result = evaluator.evaluateCondition(
            instruction.condition, state, frame.context, frame.variables,
          )) {
            is EvaluationResult.Failed -> return VmTransition.VmFailed(result.error, state, accumulated)

            is EvaluationResult.Success -> {
              val branch = if (result.value) instruction.ifTrue else instruction.ifFalse
              val branchCode = if (result.value) BRANCH_TRUE else BRANCH_FALSE
              frame = if (branch.isEmpty()) {
                frame.copy(instructionPointer = frame.instructionPointer + 1)
              } else {
                enterBlock(
                  frame,
                  listOf(frame.instructionPointer, branchCode),
                  frame.instructionPointer + 1,
                )
              }
            }
          }
        }

        is Optional -> {
          val choiceId = ChoiceId("${frame.id.value}:opt:${pathKey(frame)}:${frame.instructionPointer}")
          val choice = PendingChoice(
            id = choiceId,
            playerId = frame.context.controllerId,
            frameId = frame.id,
            type = ChoiceType.BOOLEAN,
            minimum = 0,
            maximum = 1,
            candidates = emptyList(),
            visibility = EventVisibility.PRIVATE_TO_PLAYER(frame.context.controllerId),
          )
          return VmTransition.VmSuspended(
            frame = frame.copy(status = ProgramStatus.SUSPENDED, pendingChoiceId = choiceId),
            choice = choice,
            state = state,
            events = accumulated,
          )
        }

        is ForEach -> {
          val sourceValue = frame.variables[instruction.source] ?: return fail(
            frame, state, accumulated, "MISSING_VARIABLE",
            "ForEach source not found: ${instruction.source.value}",
          )
          val items = when (sourceValue) {
            is StoredValue.CardIdsValue -> sourceValue.value
            is StoredValue.CardValue -> listOf(sourceValue.value)
            else -> return fail(
              frame, state, accumulated, "INVALID_SOURCE",
              "ForEach source must be card ids",
            )
          }
          if (items.isEmpty() || instruction.instructions.isEmpty()) {
            frame = frame.copy(instructionPointer = frame.instructionPointer + 1)
          } else {
            val depth = frame.callStack.size
            val listKey = VariableId("${frame.id.value}:fe:$depth:${frame.instructionPointer}:list")
            val indexKey = VariableId("${frame.id.value}:fe:$depth:${frame.instructionPointer}:idx")
            val variables = frame.variables + mapOf(
              listKey to StoredValue.CardIdsValue(items),
              indexKey to StoredValue.IntegerValue(0),
              instruction.item to StoredValue.CardValue(items[0]),
            )
            frame = enterBlock(
              frame.copy(variables = variables),
              listOf(frame.instructionPointer),
              frame.instructionPointer + 1,
            )
          }
        }

        is RepeatWhile -> {
          when (val result = evaluator.evaluateCondition(
            instruction.condition, state, frame.context, frame.variables,
          )) {
            is EvaluationResult.Failed -> return VmTransition.VmFailed(result.error, state, accumulated)

            is EvaluationResult.Success -> {
              frame = if (result.value && instruction.instructions.isNotEmpty()) {
                enterBlock(frame, listOf(frame.instructionPointer), frame.instructionPointer)
              } else {
                frame.copy(instructionPointer = frame.instructionPointer + 1)
              }
            }
          }
        }

        else -> {
          when (val result = executor.execute(instruction, state, frame.context, frame.variables)) {
            is InstructionExecutionResult.Failed -> return VmTransition.VmFailed(result.error, state, accumulated)

            is InstructionExecutionResult.Suspended -> return VmTransition.VmSuspended(
              frame = frame.copy(
                status = ProgramStatus.SUSPENDED,
                pendingChoiceId = result.choice.id,
              ),
              choice = result.choice,
              state = state,
              events = accumulated,
            )

            is InstructionExecutionResult.Completed -> {
              if (result.events.isNotEmpty()) {
                state = eventApplier.apply(state, result.events)
                accumulated += result.events
              }
              val variables = if (result.value != null) {
                frame.variables + (VariableId("__last") to result.value)
              } else {
                frame.variables
              }
              frame = frame.copy(
                instructionPointer = frame.instructionPointer + 1,
                variables = variables,
              )
            }
          }
        }
      }
    }
  }

  private fun currentBlock(frame: ProgramFrame): List<Instruction> {
    if (frame.callStack.isEmpty()) return frame.program.instructions
    return resolvePath(frame.program.instructions, frame.callStack.last().blockPath)
  }

  private fun resolvePath(root: List<Instruction>, path: List<Int>): List<Instruction> {
    var current = root
    var i = 0
    while (i < path.size) {
      val index = path[i]
      if (index !in current.indices) return emptyList()
      when (val instruction = current[index]) {
        is Sequence -> {
          current = instruction.instructions
          i += 1
        }

        is Optional -> {
          current = instruction.instructions
          i += 1
        }

        is ForEach -> {
          current = instruction.instructions
          i += 1
        }

        is RepeatWhile -> {
          current = instruction.instructions
          i += 1
        }

        is Conditional -> {
          if (i + 1 >= path.size) return emptyList()
          val branch = path[i + 1]
          current = if (branch == BRANCH_TRUE) instruction.ifTrue else instruction.ifFalse
          i += 2
        }

        else -> return emptyList()
      }
    }
    return current
  }

  private fun enterBlock(
    frame: ProgramFrame,
    pathSuffix: List<Int>,
    returnPointer: Int,
  ): ProgramFrame {
    val parentPath = if (frame.callStack.isEmpty()) {
      emptyList()
    } else {
      frame.callStack.last().blockPath
    }
    val returnCounter = ProgramCounter(
      programId = frame.program.id,
      blockPath = parentPath + pathSuffix,
      instructionPointer = returnPointer,
    )
    return frame.copy(
      callStack = frame.callStack + returnCounter,
      instructionPointer = 0,
    )
  }

  private fun exitBlock(frame: ProgramFrame): ProgramFrame {
    val returnCounter = frame.callStack.last()
    val remainingStack = frame.callStack.dropLast(1)
    val parentPath = if (remainingStack.isEmpty()) emptyList() else remainingStack.last().blockPath
    val parentBlock = resolvePath(frame.program.instructions, parentPath)
    val ownerIndex = ownerIndexOf(returnCounter.blockPath, parentPath)

    if (ownerIndex != null && ownerIndex in parentBlock.indices) {
      when (val owner = parentBlock[ownerIndex]) {
        is ForEach -> {
          val depth = remainingStack.size
          val listKey = VariableId("${frame.id.value}:fe:$depth:$ownerIndex:list")
          val indexKey = VariableId("${frame.id.value}:fe:$depth:$ownerIndex:idx")
          val listValue = frame.variables[listKey]
          val indexValue = frame.variables[indexKey]
          if (listValue is StoredValue.CardIdsValue && indexValue is StoredValue.IntegerValue) {
            val nextIndex = indexValue.value + 1
            if (nextIndex < listValue.value.size) {
              val variables = frame.variables + mapOf(
                indexKey to StoredValue.IntegerValue(nextIndex),
                owner.item to StoredValue.CardValue(listValue.value[nextIndex]),
              )
              return enterBlock(
                frame.copy(
                  callStack = remainingStack,
                  instructionPointer = ownerIndex,
                  variables = variables,
                ),
                listOf(ownerIndex),
                returnCounter.instructionPointer,
              )
            }
          }
        }

        is RepeatWhile -> {
          return frame.copy(
            callStack = remainingStack,
            instructionPointer = returnCounter.instructionPointer,
            variables = frame.variables,
          )
        }

        else -> Unit
      }
    }

    return frame.copy(
      callStack = remainingStack,
      instructionPointer = returnCounter.instructionPointer,
      variables = frame.variables,
    )
  }

  private fun ownerIndexOf(fullPath: List<Int>, parentPath: List<Int>): Int? {
    if (fullPath.size <= parentPath.size) return null
    return fullPath[parentPath.size]
  }

  private fun pathKey(frame: ProgramFrame): String {
    return if (frame.callStack.isEmpty()) {
      "root"
    } else {
      frame.callStack.last().blockPath.joinToString(".")
    }
  }

  private fun fail(
    frame: ProgramFrame,
    state: GameState,
    events: List<GameEvent>,
    code: String,
    message: String,
  ): VmTransition.VmFailed = VmTransition.VmFailed(
    error = VmError(code, message, frame.instructionPointer),
    state = state,
    events = events,
  )

  companion object {
    private const val BRANCH_TRUE = 0
    private const val BRANCH_FALSE = 1
  }
}