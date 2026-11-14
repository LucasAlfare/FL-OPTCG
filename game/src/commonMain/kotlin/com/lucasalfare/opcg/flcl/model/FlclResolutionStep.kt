package com.lucasalfare.opcg.flcl.model

sealed interface FlclResolutionStep

private fun requireSteps(steps: List<FlclResolutionStep>, kind: String) {
  require(steps.isNotEmpty()) { "$kind requires at least one child step" }
}

data class FlclSelectStep(
  val selection: FlclSelection
) : FlclResolutionStep

data class FlclRemainderStep(
  val remainder: FlclRemainder
) : FlclResolutionStep

data class FlclOperationStep(
  val id: FlclStepId, val operation: FlclOperation
) : FlclResolutionStep

data class FlclIfStep(
  val id: FlclStepId, val condition: FlclCondition, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "IF")
  }
}

data class FlclThenStep(
  val id: FlclStepId, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "THEN")
  }
}

data class FlclIfYouDoStep(
  val id: FlclStepId, val dependsOn: FlclStepId, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "IF_YOU_DO")
  }
}

data class FlclOptionalStep(
  val id: FlclStepId, val chooser: FlclPlayerReference, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "OPTIONAL")
  }
}

data class FlclModeOption(
  val id: FlclStepId, val steps: List<FlclResolutionStep>
) {
  init {
    requireSteps(steps, "CHOOSE_MODE option")
  }
}

data class FlclChooseModeStep(
  val id: FlclStepId, val chooser: FlclPlayerReference, val options: List<FlclModeOption>
) : FlclResolutionStep {
  init {
    require(options.isNotEmpty()) { "CHOOSE_MODE requires at least one option" }
    require(options.distinctBy { it.id }.size == options.size) { "CHOOSE_MODE options must have unique ids" }
  }
}

data class FlclSimultaneousStep(
  val id: FlclStepId, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "SIMULTANEOUS")
  }
}

data class FlclRepeatStep(
  val id: FlclStepId, val quantity: FlclFiniteQuantity, val steps: List<FlclResolutionStep>
) : FlclResolutionStep {
  init {
    requireSteps(steps, "REPEAT")
  }
}

data class FlclResolution(
  val steps: List<FlclResolutionStep>
) {
  init {
    require(steps.isNotEmpty()) { "resolution requires at least one step" }
  }
}