package com.lucasalfare.opcg

import com.lucasalfare.flturns.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

class FLTurnsAdapter(
  private val engine: GameEngine,
  private val players: List<PlayerId>,
  events: TurnEventSink = TurnEventSink {},
) {
  init {
    require(players.isNotEmpty()) { "At least one player is required" }
  }

  private val coordinator = OpportunityCoordinator(players)
  private val flow = OpcgTurnFlow(coordinator)
  private val turnsEngine = TurnsEngine(
    handler = { context -> handleTurn(context) },
    flow = flow,
    isEligible = { turn -> coordinator.isEligible(turn) },
    events = events,
  )
  private val channels: Map<PlayerId, Channel<IncomingCommand>> = players.associateWith { Channel(Channel.UNLIMITED) }

  init {
    coordinator.refresh(engine.snapshot().state)
  }

  suspend fun enqueue(command: GameCommand): CommandResult {
    val channel =
      channels[command.playerId] ?: throw IllegalArgumentException("Unknown player ${command.playerId.value}")
    val completion = CompletableDeferred<CommandResult>()
    channel.send(IncomingCommand(command, completion))
    return completion.await()
  }

  suspend fun executeNextOpportunity(): Execution = turnsEngine.executeNextTurn()

  fun currentOpportunity(): Opportunity? = coordinator.next()

  fun isFinished(): Boolean = coordinator.isEnded()

  fun turnsSnapshot(): TurnsSnapshot = turnsEngine.snapshot()

  fun snapshot(): GameSnapshot = engine.snapshot()

  fun view(playerId: PlayerId): PlayerView = engine.view(playerId)

  private suspend fun handleTurn(context: TurnContext): FlowDecision {
    val playerId = PlayerId(context.turnActor.id)
    val incoming = channels.getValue(playerId).receive()
    try {
      val result = engine.submit(incoming.command)
      incoming.completion.complete(result)
      coordinator.refresh(engine.snapshot().state)

      val next = coordinator.next()
      if (next != null && next.kind == OpportunityKind.RESPONSE && next.playerId != playerId) {
        context.scope.execute(TurnActor(next.playerId.value))
        coordinator.refresh(engine.snapshot().state)
      }

      return if (coordinator.isEnded()) FlowDecision.End else FlowDecision.Continue
    } catch (e: Throwable) {
      incoming.completion.completeExceptionally(e)
      throw e
    }
  }

  private data class IncomingCommand(
    val command: GameCommand,
    val completion: CompletableDeferred<CommandResult>,
  )
}

enum class OpportunityKind {
  SETUP, CHOICE, TURN, RESPONSE,
}

data class Opportunity(
  val playerId: PlayerId,
  val kind: OpportunityKind,
)

internal class OpportunityCoordinator(private val players: List<PlayerId>) {
  private var state: GameState? = null
  private var ended: Boolean = false

  fun refresh(newState: GameState) {
    state = newState
    if (newState.status == GameStatus.FINISHED) ended = true
  }

  fun next(): Opportunity? {
    val current = state ?: return null
    if (current.status == GameStatus.FINISHED) return null

    current.setup?.let { setup ->
      if (setup.step == GameSetupStep.MULLIGAN) {
        val pending = setup.pendingMulliganPlayers.minByOrNull { it.value } ?: return null
        return Opportunity(pending, OpportunityKind.SETUP)
      }
    }

    current.pendingChoices.firstOrNull()?.let { choice ->
      return Opportunity(choice.playerId, OpportunityKind.CHOICE)
    }

    current.battle?.let { battle ->
      if (battle.step == BattleStep.BLOCK || battle.step == BattleStep.COUNTER) {
        val attackerController = current.cards[battle.attackerId]?.controllerId
        if (attackerController != null) {
          val defender = players.firstOrNull { it != attackerController }
          if (defender != null) {
            return Opportunity(defender, OpportunityKind.RESPONSE)
          }
        }
      }
    }

    current.turn?.let { turn ->
      if (turn.phase == GamePhase.MAIN) {
        return Opportunity(turn.playerId, OpportunityKind.TURN)
      }
    }

    return null
  }

  fun isEligible(turn: Turn): Boolean {
    val opportunity = next() ?: return false
    return opportunity.playerId.value == turn.turnActor.id
  }

  fun isEnded(): Boolean = ended

  fun end() {
    ended = true
  }
}

internal class OpcgTurnFlow(
  private val coordinator: OpportunityCoordinator,
) : TurnFlow {
  private var counter: Long = 0L

  override fun next(isEligible: (Turn) -> Boolean): Turn {
    val opportunity = coordinator.next() ?: throw NoExecutableTurnException(0)
    counter += 1
    val turn = Turn(TurnId(counter), TurnActor(opportunity.playerId.value))
    if (!isEligible(turn)) throw NoExecutableTurnException(1)
    return turn
  }

  override fun isEnded(): Boolean = coordinator.isEnded()

  override fun apply(decision: FlowDecision) {
    when (decision) {
      FlowDecision.End -> coordinator.end()
      else -> Unit
    }
  }
}