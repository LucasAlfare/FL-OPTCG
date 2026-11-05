@file:Suppress("unused")

package com.lucasalfare.opcg

import com.lucasalfare.opcg.rules.ProgramRequest
import kotlinx.serialization.Serializable

@Serializable
enum class ProgramStatus {
  QUEUED, RUNNING, SUSPENDED, COMPLETED, FAILED,
}

@Serializable
data class ProgramCounter(
  val programId: ProgramId,
  val blockPath: List<Int>,
  val instructionPointer: Int,
)

@Serializable
sealed class StoredValue {
  @Serializable
  data class IntegerValue(val value: Int) : StoredValue()

  @Serializable
  data class BooleanValue(val value: Boolean) : StoredValue()

  @Serializable
  data class CardIdsValue(val value: List<CardInstanceId>) : StoredValue()

  @Serializable
  data class OptionValue(val value: OptionId) : StoredValue()

  @Serializable
  data class PlayerValue(val value: PlayerId) : StoredValue()

  @Serializable
  data class CardValue(val value: CardInstanceId) : StoredValue()
}

@Serializable
data class ProgramFrame(
  val id: ExecutionId,
  val program: Program,
  val context: EffectContext,
  val instructionPointer: Int,
  val variables: Map<VariableId, StoredValue>,
  val callStack: List<ProgramCounter>,
  val status: ProgramStatus,
  val pendingChoiceId: ChoiceId?,
  val pendingPrograms: List<ProgramRequest> = emptyList(),
  val followUps: List<ProgramRequest> = emptyList(),
  val stepCount: Int = 0,
)

@Serializable
enum class ChoiceType {
  OPTION, NUMBER, CARD_SELECTION, CARD_ORDER, BOOLEAN,
}

@Serializable
sealed class ChoiceCandidate {
  @Serializable
  data class OptionCandidate(val optionId: OptionId) : ChoiceCandidate()

  @Serializable
  data class NumberCandidate(val value: Int) : ChoiceCandidate()

  @Serializable
  data class CardCandidate(val cardId: CardInstanceId) : ChoiceCandidate()
}

@Serializable
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

@Serializable
data class PlayerChoice(
  val choiceId: ChoiceId,
  val playerId: PlayerId,
  val answer: ChoiceAnswer,
)

@Serializable
data class VmError(
  val code: String,
  val message: String,
  val instructionPointer: Int,
)

@Serializable
sealed class InstructionExecutionResult {
  @Serializable
  data class Completed(
    val value: StoredValue?,
    val events: List<GameEvent>,
    val programs: List<ProgramRequest> = emptyList(),
  ) : InstructionExecutionResult()

  @Serializable
  data class Suspended(
    val choice: PendingChoice,
  ) : InstructionExecutionResult()

  @Serializable
  data class Failed(
    val error: VmError,
  ) : InstructionExecutionResult()
}

@Serializable
sealed class EvaluationResult<out T> {
  @Serializable
  data class Success<T>(val value: T) : EvaluationResult<T>()

  @Serializable
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

@Serializable
sealed class VmTransition {
  @Serializable
  data class VmRunning(
    val frame: ProgramFrame,
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()

  @Serializable
  data class VmSuspended(
    val frame: ProgramFrame,
    val choice: PendingChoice,
    val state: GameState,
    val events: List<GameEvent>,
  ) : VmTransition()

  @Serializable
  data class VmCompleted(
    val state: GameState,
    val events: List<GameEvent>,
    val programs: List<ProgramRequest> = emptyList(),
  ) : VmTransition()

  @Serializable
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
    followUps: List<ProgramRequest> = emptyList(),
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
      followUps = followUps,
    )
    val (stateWithFrame, frameEvents) = emitFrameChanged(state, executionId, frame)
    return run(frame, stateWithFrame, frameEvents)
  }

  fun resume(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
  ): VmTransition {
    if (frame.status != ProgramStatus.SUSPENDED || frame.pendingChoiceId == null) {
      return fail(frame, state, emptyList(), "INVALID_RESUME", "Frame is not suspended")
    }
    if (frame.stepCount >= MAX_INSTRUCTION_STEPS) {
      return failAndEmit(
        frame, state, emptyList(),
        VmError("STEP_LIMIT_EXCEEDED", "Program exceeded $MAX_INSTRUCTION_STEPS steps", frame.instructionPointer),
      )
    }
    if (choice.choiceId != frame.pendingChoiceId) {
      return fail(frame, state, emptyList(), "CHOICE_MISMATCH", "Choice id does not match pending choice")
    }

    val pending = state.pendingChoices.firstOrNull { it.id == frame.pendingChoiceId } ?: return fail(
      frame, state, emptyList(), "MISSING_CHOICE", "Pending choice not found in state"
    )

    if (choice.playerId != pending.playerId) {
      return fail(frame, state, emptyList(), "PLAYER_MISMATCH", "Player id does not match pending choice")
    }

    val validationError = validateAnswer(pending, choice.answer, frame.instructionPointer)
    if (validationError != null) {
      return VmTransition.VmFailed(validationError, state, emptyList())
    }

    val block = currentBlock(frame)
    if (frame.instructionPointer !in block.indices) {
      return fail(frame, state, emptyList(), "INVALID_POINTER", "Instruction pointer out of range on resume")
    }

    val (stateWithoutChoice, clearEvents) = emitChoiceChanged(state, choice.choiceId, null)

    return when (val current = block[frame.instructionPointer]) {
      is Optional -> resumeOptional(frame, choice, stateWithoutChoice, clearEvents, current)
      else -> resumeExecutorChoice(frame, choice, stateWithoutChoice, clearEvents, current, pending)
    }
  }

  private fun resumeOptional(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
    priorEvents: List<GameEvent>,
    optional: Optional,
  ): VmTransition {
    if (choice.answer !is ChoiceAnswer.BooleanAnswer) {
      return fail(frame, state, priorEvents, "INVALID_ANSWER", "Optional requires boolean answer")
    }
    val answer = choice.answer as ChoiceAnswer.BooleanAnswer
    val cleared = frame.copy(status = ProgramStatus.RUNNING, pendingChoiceId = null)
    val (stateWithFrame, frameEvents) = emitFrameChanged(state, frame.id, cleared)
    val events = priorEvents + frameEvents
    return if (answer.value && optional.instructions.isNotEmpty()) {
      run(enterBlock(cleared, listOf(frame.instructionPointer), frame.instructionPointer + 1), stateWithFrame, events)
    } else {
      run(cleared.copy(instructionPointer = frame.instructionPointer + 1), stateWithFrame, events)
    }
  }

  private fun resumeExecutorChoice(
    frame: ProgramFrame,
    choice: PlayerChoice,
    state: GameState,
    priorEvents: List<GameEvent>,
    instruction: Instruction,
    pending: PendingChoice?,
  ): VmTransition {
    val stored = storedFromAnswer(choice.answer)
    val destination = destinationOf(instruction)
    val variables = if (destination != null) {
      frame.variables + (destination to stored)
    } else {
      frame.variables + (VariableId("__choice_result") to stored)
    }
    // Advance past the instruction that produced the choice — do not re-execute it
    val advanced = frame.copy(
      status = ProgramStatus.RUNNING,
      pendingChoiceId = null,
      variables = variables,
      instructionPointer = frame.instructionPointer + 1,
    )
    val (stateWithFrame, frameEvents) = emitFrameChanged(state, frame.id, advanced)
    return run(advanced, stateWithFrame, priorEvents + frameEvents)
  }

  private fun validateAnswer(
    pending: PendingChoice,
    answer: ChoiceAnswer,
    instructionPointer: Int,
  ): VmError? {
    return when (pending.type) {
      ChoiceType.OPTION -> {
        if (answer !is ChoiceAnswer.OptionAnswer) {
          return VmError("INVALID_ANSWER", "Expected option answer", instructionPointer)
        }
        val allowed = pending.candidates.filterIsInstance<ChoiceCandidate.OptionCandidate>().map { it.optionId }
        if (allowed.isEmpty()) {
          return VmError("NO_CANDIDATES", "No options available", instructionPointer)
        }
        if (answer.optionId !in allowed) {
          return VmError("INVALID_OPTION", "Option not in candidates", instructionPointer)
        }
        null
      }

      ChoiceType.NUMBER -> {
        if (answer !is ChoiceAnswer.NumberAnswer) {
          return VmError("INVALID_ANSWER", "Expected number answer", instructionPointer)
        }
        if (answer.value < pending.minimum || answer.value > pending.maximum) {
          return VmError("OUT_OF_RANGE", "Number outside limits", instructionPointer)
        }
        null
      }

      ChoiceType.CARD_SELECTION -> {
        if (answer !is ChoiceAnswer.CardSelectionAnswer) {
          return VmError("INVALID_ANSWER", "Expected card selection answer", instructionPointer)
        }
        val count = answer.cardIds.size
        if (count < pending.minimum || count > pending.maximum) {
          return VmError("OUT_OF_RANGE", "Selection count outside limits", instructionPointer)
        }
        if (answer.cardIds.toSet().size != answer.cardIds.size) {
          return VmError("DUPLICATE_CARDS", "Duplicate cards in selection", instructionPointer)
        }
        val allowed = pending.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }.toSet()
        if (allowed.isEmpty() && pending.minimum > 0) {
          return VmError("NO_CANDIDATES", "No card candidates available", instructionPointer)
        }
        if (allowed.isNotEmpty() && answer.cardIds.any { it !in allowed }) {
          return VmError("INVALID_CARD", "Card not in candidates", instructionPointer)
        }
        null
      }

      ChoiceType.CARD_ORDER -> {
        if (answer !is ChoiceAnswer.CardOrderAnswer) {
          return VmError("INVALID_ANSWER", "Expected card order answer", instructionPointer)
        }
        val allowed = pending.candidates.filterIsInstance<ChoiceCandidate.CardCandidate>().map { it.cardId }
        if (allowed.isEmpty()) {
          return VmError("NO_CANDIDATES", "No card candidates available", instructionPointer)
        }
        if (answer.cardIds.size != allowed.size) {
          return VmError("INVALID_ORDER", "Order must contain exactly the candidate cards", instructionPointer)
        }
        if (answer.cardIds.toSet().size != answer.cardIds.size) {
          return VmError("DUPLICATE_CARDS", "Duplicate cards in order", instructionPointer)
        }
        if (answer.cardIds.toSet() != allowed.toSet()) {
          return VmError("INVALID_ORDER", "Order must contain exactly the candidate cards", instructionPointer)
        }
        null
      }

      ChoiceType.BOOLEAN -> {
        if (answer !is ChoiceAnswer.BooleanAnswer) {
          return VmError("INVALID_ANSWER", "Expected boolean answer", instructionPointer)
        }
        null
      }
    }
  }

  private fun storedFromAnswer(answer: ChoiceAnswer): StoredValue = when (answer) {
    is ChoiceAnswer.OptionAnswer -> StoredValue.OptionValue(answer.optionId)
    is ChoiceAnswer.NumberAnswer -> StoredValue.IntegerValue(answer.value)
    is ChoiceAnswer.CardSelectionAnswer -> StoredValue.CardIdsValue(answer.cardIds)
    is ChoiceAnswer.CardOrderAnswer -> StoredValue.CardIdsValue(answer.cardIds)
    is ChoiceAnswer.BooleanAnswer -> StoredValue.BooleanValue(answer.value)
  }

  private fun destinationOf(instruction: Instruction): VariableId? = when (instruction) {
    is SelectCards -> instruction.destination
    is CountCards -> instruction.destination
    is LookAtTop -> instruction.destination
    is ChooseOption -> instruction.destination
    is ChooseNumber -> instruction.destination
    is OrderCards -> instruction.destination
    is CheckCondition -> instruction.destination
    else -> null
  }

  private fun run(
    initialFrame: ProgramFrame,
    initialState: GameState,
    initialEvents: List<GameEvent>,
  ): VmTransition {
    var frame = initialFrame
    var state = initialState
    val accumulated = initialEvents.toMutableList()
    val derived = initialFrame.pendingPrograms.toMutableList()

    while (true) {
      if (frame.stepCount >= MAX_INSTRUCTION_STEPS) {
        return failAndEmit(
          frame,
          state,
          accumulated,
          VmError("STEP_LIMIT_EXCEEDED", "Program exceeded $MAX_INSTRUCTION_STEPS steps", frame.instructionPointer),
        )
      }
      frame = frame.copy(stepCount = frame.stepCount + 1)
      val block = currentBlock(frame)

      if (frame.instructionPointer >= block.size) {
        if (frame.callStack.isEmpty()) {
          frame.copy(status = ProgramStatus.COMPLETED, pendingChoiceId = null)
          val (finalState, frameEvents) = emitFrameChanged(state, frame.id, null)
          accumulated += frameEvents
          return VmTransition.VmCompleted(
            state = finalState,
            events = accumulated,
            programs = frame.followUps + derived,
          )
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
            is EvaluationResult.Failed -> return failAndEmit(frame, state, accumulated, result.error)

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
          val suspended = frame.copy(
            status = ProgramStatus.SUSPENDED,
            pendingChoiceId = choiceId,
            pendingPrograms = derived.toList(),
          )
          var nextState = state
          val extra = mutableListOf<GameEvent>()
          emitFrameChanged(nextState, frame.id, suspended).let { (s, e) ->
            nextState = s
            extra += e
          }
          emitChoiceChanged(nextState, choiceId, choice).let { (s, e) ->
            nextState = s
            extra += e
          }
          accumulated += extra
          return VmTransition.VmSuspended(suspended, choice, nextState, accumulated)
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
            is EvaluationResult.Failed -> return failAndEmit(frame, state, accumulated, result.error)

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
            is InstructionExecutionResult.Failed -> return failAndEmit(frame, state, accumulated, result.error)

            is InstructionExecutionResult.Suspended -> {
              val choice = result.choice.copy(frameId = frame.id)
              val suspended = frame.copy(
                status = ProgramStatus.SUSPENDED,
                pendingChoiceId = choice.id,
                pendingPrograms = derived.toList(),
              )
              var nextState = state
              val extra = mutableListOf<GameEvent>()
              emitFrameChanged(nextState, frame.id, suspended).let { (s, e) ->
                nextState = s
                extra += e
              }
              emitChoiceChanged(nextState, choice.id, choice).let { (s, e) ->
                nextState = s
                extra += e
              }
              accumulated += extra
              return VmTransition.VmSuspended(suspended, choice, nextState, accumulated)
            }

            is InstructionExecutionResult.Completed -> {
              if (result.events.isNotEmpty()) {
                state = eventApplier.apply(state, result.events)
                accumulated += result.events
              }
              derived += result.programs
              val destination = destinationOf(instruction)
              val variables = when {
                destination != null && result.value != null -> frame.variables + (destination to result.value)
                result.value != null -> frame.variables + (VariableId("__last") to result.value)
                else -> frame.variables
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

  private fun failAndEmit(
    frame: ProgramFrame,
    state: GameState,
    events: List<GameEvent>,
    error: VmError,
  ): VmTransition.VmFailed {
    val failed = frame.copy(status = ProgramStatus.FAILED)
    val (nextState, frameEvents) = emitFrameChanged(state, frame.id, failed)
    return VmTransition.VmFailed(error, nextState, events + frameEvents)
  }

  private fun emitFrameChanged(
    state: GameState,
    executionId: ExecutionId,
    frame: ProgramFrame?,
  ): Pair<GameState, List<GameEvent>> {
    val sequence = state.lastEventSequence + 1
    val event = GameEvent(
      id = EventId("${state.id.value}:$sequence"),
      sequence = sequence,
      type = GameEventType.PROGRAM_FRAME_CHANGED,
      sourceCardId = frame?.context?.sourceCardId,
      payload = GameEventPayload.ProgramFrameChangedPayload(executionId, frame),
      visibility = EventVisibility.INTERNAL,
    )
    val nextState = eventApplier.apply(state, listOf(event))
    return nextState to listOf(event)
  }

  private fun emitChoiceChanged(
    state: GameState,
    choiceId: ChoiceId,
    choice: PendingChoice?,
  ): Pair<GameState, List<GameEvent>> {
    val sequence = state.lastEventSequence + 1
    val visibility = choice?.visibility ?: EventVisibility.INTERNAL
    val event = GameEvent(
      id = EventId("${state.id.value}:$sequence"),
      sequence = sequence,
      type = GameEventType.PENDING_CHOICE_CHANGED,
      sourceCardId = null,
      payload = GameEventPayload.PendingChoiceChangedPayload(choiceId, choice),
      visibility = visibility,
    )
    val nextState = eventApplier.apply(state, listOf(event))
    return nextState to listOf(event)
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
    private const val MAX_INSTRUCTION_STEPS = 10_000
  }
}