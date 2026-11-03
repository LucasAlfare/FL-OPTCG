package com.lucasalfare.opcg

data class Program(
  val id: ProgramId,
  val instructions: List<Instruction>,
)

data class Sequence(
  val instructions: List<Instruction>,
) : Instruction

data class Conditional(
  val condition: Condition,
  val ifTrue: List<Instruction>,
  val ifFalse: List<Instruction>,
) : Instruction

data class Optional(
  val instructions: List<Instruction>,
) : Instruction

data class ForEach(
  val source: VariableId,
  val item: VariableId,
  val instructions: List<Instruction>,
) : Instruction

data class RepeatWhile(
  val condition: Condition,
  val instructions: List<Instruction>,
) : Instruction