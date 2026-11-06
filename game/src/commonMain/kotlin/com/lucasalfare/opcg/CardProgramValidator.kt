package com.lucasalfare.opcg

data class ProgramValidationIssue(
  val code: String,
  val message: String,
  val path: List<Int> = emptyList(),
)

data class ProgramValidationResult(
  val valid: Boolean,
  val issues: List<ProgramValidationIssue>,
)

class CardProgramValidator {

  fun validateDefinition(definition: CardDefinition): ProgramValidationResult {
    val issues = mutableListOf<ProgramValidationIssue>()
    val seenAbilityIds = mutableSetOf<AbilityId>()
    definition.abilities.forEachIndexed { index, ability ->
      if (!seenAbilityIds.add(ability.id)) {
        issues += ProgramValidationIssue(
          code = "DUPLICATE_ABILITY_ID",
          message = "Ability id ${ability.id.value} appears more than once",
          path = listOf(index),
        )
      }
      issues += validateAbility(ability, listOf(index), 0)
    }
    return ProgramValidationResult(valid = issues.isEmpty(), issues = issues)
  }

  fun validateProgram(program: Program): ProgramValidationResult {
    val issues = validateProgramInternal(program, emptyList(), 0)
    return ProgramValidationResult(valid = issues.isEmpty(), issues = issues)
  }

  private fun validateAbility(ability: AbilityDefinition, path: List<Int>, depth: Int): List<ProgramValidationIssue> {
    if (depth > MAX_NESTING_DEPTH) {
      return listOf(ProgramValidationIssue("MAX_NESTING", "Ability nesting exceeds $MAX_NESTING_DEPTH", path))
    }
    return when (ability) {
      is AutoAbilityDefinition -> {
        val issues = validatePattern(ability.trigger, path, depth).toMutableList()
        ability.condition?.let { issues += validateCondition(it, path) }
        issues += validateProgramInternal(ability.program, path, depth + 1)
        issues
      }

      is ActivatedAbilityDefinition -> {
        val issues = validateCost(ability.cost, path).toMutableList()
        ability.condition?.let { issues += validateCondition(it, path) }
        issues += validateProgramInternal(ability.program, path, depth + 1)
        issues
      }

      is PermanentAbilityDefinition -> {
        val issues = mutableListOf<ProgramValidationIssue>()
        ability.condition?.let { issues += validateCondition(it, path) }
        ability.selector?.let { issues += validateSelector(it, path) }
        issues += validateModifier(ability.modifier, path, depth + 1)
        issues
      }

      is ReplacementAbilityDefinition -> validateReplacementDefinition(ability.replacement, path, depth + 1)
    }
  }

  private fun validateProgramInternal(program: Program, basePath: List<Int>, depth: Int): List<ProgramValidationIssue> {
    if (depth > MAX_NESTING_DEPTH) {
      return listOf(ProgramValidationIssue("MAX_NESTING", "Program nesting exceeds $MAX_NESTING_DEPTH", basePath))
    }
    val issues = mutableListOf<ProgramValidationIssue>()
    program.instructions.forEachIndexed { index, instruction ->
      issues += validateInstruction(instruction, basePath + index, depth)
    }
    return issues
  }

  private fun validateInstruction(instruction: Instruction, path: List<Int>, depth: Int): List<ProgramValidationIssue> {
    return when (instruction) {
      is Sequence -> instruction.instructions.flatMapIndexed { i, inst -> validateInstruction(inst, path + i, depth) }
      is Conditional -> {
        val issues = validateCondition(instruction.condition, path).toMutableList()
        instruction.ifTrue.forEachIndexed { i, inst -> issues += validateInstruction(inst, path + i, depth) }
        instruction.ifFalse.forEachIndexed { i, inst -> issues += validateInstruction(inst, path + i, depth) }
        issues
      }

      is Optional -> instruction.instructions.flatMapIndexed { i, inst -> validateInstruction(inst, path + i, depth) }
      is ForEach -> instruction.instructions.flatMapIndexed { i, inst -> validateInstruction(inst, path + i, depth) }
      is RepeatWhile -> {
        val issues = validateCondition(instruction.condition, path).toMutableList()
        instruction.instructions.forEachIndexed { i, inst -> issues += validateInstruction(inst, path + i, depth) }
        issues
      }

      is SelectCards -> {
        val issues = validateSelector(instruction.selector, path).toMutableList()
        issues += validateNumericBounds(instruction.minimum, instruction.maximum, path)
        issues
      }

      is SelectFromVariable -> {
        val issues = validateSelector(instruction.selector, path).toMutableList()
        issues += validateNumericBounds(instruction.minimum, instruction.maximum, path)
        if (instruction.selector.zones.isNotEmpty()) {
          issues += ProgramValidationIssue(
            "UNSUPPORTED_SELECTOR_ZONES",
            "SelectFromVariable cannot filter by zones",
            path,
          )
        }
        if (instruction.destination == instruction.source) {
          issues += ProgramValidationIssue(
            "INVALID_DESTINATION",
            "SelectFromVariable destination must differ from source",
            path,
          )
        }
        instruction.remainderDestination?.let { remainder ->
          if (remainder == instruction.source) {
            issues += ProgramValidationIssue(
              "INVALID_DESTINATION",
              "SelectFromVariable remainder destination must differ from source",
              path,
            )
          }
          if (remainder == instruction.destination) {
            issues += ProgramValidationIssue(
              "DUPLICATE_DESTINATION",
              "SelectFromVariable destinations must differ",
              path,
            )
          }
        }
        issues
      }

      is ReturnCardsToOwnerHand -> emptyList()
      is CountCards -> validateSelector(instruction.selector, path)
      is LookAtTop -> {
        val allowed = setOf(ZoneType.DECK, ZoneType.LIFE, ZoneType.DON_DECK)
        val issues = mutableListOf<ProgramValidationIssue>()
        if (instruction.zone !in allowed) {
          issues += ProgramValidationIssue(
            "INVALID_LOOK_ZONE",
            "LookAtTop zone ${instruction.zone} is not readable",
            path,
          )
        }
        issues += validateValue(instruction.amount, path)
        issues
      }

      is RevealCards -> emptyList()
      is ChooseOption -> {
        if (instruction.options.isEmpty()) {
          listOf(ProgramValidationIssue("EMPTY_OPTIONS", "ChooseOption must declare at least one option", path))
        } else if (instruction.options.size != instruction.options.toSet().size) {
          listOf(ProgramValidationIssue("DUPLICATE_OPTIONS", "ChooseOption contains duplicate option ids", path))
        } else emptyList()
      }

      is ChooseNumber -> validateNumericBounds(instruction.minimum, instruction.maximum, path)
      is OrderCards -> emptyList()
      is CheckCondition -> validateCondition(instruction.condition, path)
      is Draw -> validateValue(instruction.amount, path)
      is MoveCards -> {
        if (instruction.destination.zone == ZoneType.ATTACHED_DON) {
          listOf(
            ProgramValidationIssue(
              "INVALID_DESTINATION",
              "MoveCards cannot target ATTACHED_DON; use AttachDon",
              path,
            )
          )
        } else emptyList()
      }

      is TrashFromHand -> {
        val issues = validateSelector(instruction.selector, path).toMutableList()
        issues += validateValue(instruction.amount, path)
        issues
      }

      is KnockOut -> emptyList()
      is PlayCard -> {
        val issues = mutableListOf<ProgramValidationIssue>()
        instruction.payment.additionalCosts.forEachIndexed { i, cost -> issues += validateCost(cost, path + i) }
        issues
      }

      is ShuffleZone -> {
        val allowed = setOf(ZoneType.DECK, ZoneType.DON_DECK, ZoneType.HAND, ZoneType.TRASH, ZoneType.LIFE)
        if (instruction.zone !in allowed) {
          listOf(ProgramValidationIssue("INVALID_SHUFFLE_ZONE", "Zone ${instruction.zone} cannot be shuffled", path))
        } else emptyList()
      }

      is DealDamage -> validateValue(instruction.amount, path)
      is SetLife -> emptyList()
      is PreventDamage -> {
        val issues = validateValue(instruction.amount, path).toMutableList()
        issues += validateDuration(instruction.duration, path)
        issues
      }

      is AddDon -> {
        val issues = validateValue(instruction.amount, path).toMutableList()
        instruction.destination?.let { issues += validateVariableId(it, path) }
        issues
      }

      is RestDon -> {
        val issues = validateValue(instruction.amount, path).toMutableList()
        issues += validateDonSelector(instruction.selector, path)
        issues
      }

      is SetDonActive -> emptyList()
      is AttachDon -> validateVariableId(instruction.source, path)
      is ReturnDon -> {
        val issues = validateValue(instruction.amount, path).toMutableList()
        issues += validateDonSelector(instruction.selector, path)
        issues
      }
      is DetachDon -> validateValue(instruction.amount, path)
      is RestCards -> emptyList()
      is SetCardState -> emptyList()
      is ChangeControl -> validateDuration(instruction.duration, path)
      is ModifyCharacteristic -> {
        val issues = validateValue(instruction.value, path).toMutableList()
        issues += validateDuration(instruction.duration, path)
        issues
      }

      is SetBaseCharacteristic -> {
        val issues = validateValue(instruction.value, path).toMutableList()
        issues += validateDuration(instruction.duration, path)
        issues
      }

      is SetPowerToZero -> validateDuration(instruction.duration, path)
      is SetCardProperty -> validateDuration(instruction.duration, path)
      is CopyCardProperties -> validateDuration(instruction.duration, path)
      is GrantKeyword -> validateDuration(instruction.duration, path)
      is RemoveKeyword -> validateDuration(instruction.duration, path)
      is GrantAbility -> {
        val issues = validateDuration(instruction.duration, path).toMutableList()
        issues += validateAbility(instruction.ability, path, depth + 1)
        issues
      }

      is CopyAbilities -> validateDuration(instruction.duration, path)
      is NegateEffects -> validateDuration(instruction.duration, path)
      is ApplyRestriction -> {
        val issues = validateDuration(instruction.duration, path).toMutableList()
        instruction.restriction.subject?.let { issues += validateSelector(it, path) }
        instruction.restriction.maximumUses?.let { issues += validateValue(it, path) }
        instruction.restriction.condition?.let { issues += validateCondition(it, path) }
        issues
      }

      is GrantPermission -> {
        val issues = validateDuration(instruction.duration, path).toMutableList()
        instruction.permission.subject?.let { issues += validateSelector(it, path) }
        instruction.permission.condition?.let { issues += validateCondition(it, path) }
        issues
      }

      is AddActivationCost -> {
        val issues = validateDuration(instruction.duration, path).toMutableList()
        instruction.selector.cardSelector?.let { issues += validateSelector(it, path) }
        issues += validateCost(instruction.cost, path)
        issues
      }

      is ModifyDeckRule -> validateDuration(instruction.duration, path)
      is RegisterReplacement -> validateReplacementDefinition(instruction.definition, path, depth + 1)
      is ActivateAbility -> instruction.selector.cardSelector?.let { validateSelector(it, path) } ?: emptyList()
      is SetGameResult -> emptyList()
    }
  }

  private fun validateNumericBounds(min: ValueExpr, max: ValueExpr, path: List<Int>): List<ProgramValidationIssue> {
    val issues = mutableListOf<ProgramValidationIssue>()
    issues += validateValue(min, path)
    issues += validateValue(max, path)
    if (min is ValueExpr.NumberValue && min.value < 0) {
      issues += ProgramValidationIssue("NEGATIVE_MINIMUM", "Minimum ${min.value} cannot be negative", path)
    }
    if (max is ValueExpr.NumberValue && max.value < 0) {
      issues += ProgramValidationIssue("NEGATIVE_MAXIMUM", "Maximum ${max.value} cannot be negative", path)
    }
    if (min is ValueExpr.NumberValue && max is ValueExpr.NumberValue && max.value < min.value) {
      issues += ProgramValidationIssue(
        "MIN_GREATER_THAN_MAX",
        "Minimum ${min.value} is greater than maximum ${max.value}",
        path,
      )
    }
    return issues
  }

  private fun validateValue(expr: ValueExpr, path: List<Int>): List<ProgramValidationIssue> {
    return when (expr) {
      is ValueExpr.NumberValue -> emptyList()
      is ValueExpr.VariableValue -> emptyList()
      is ValueExpr.CountValue -> validateSelector(expr.selector, path)
      is ValueExpr.CharacteristicValue -> validateSelector(expr.selector, path)
      is ValueExpr.CurrentCardValue -> emptyList()
      is ValueExpr.ArithmeticValue -> {
        val issues = validateValue(expr.left, path).toMutableList()
        issues += validateValue(expr.right, path)
        if (expr.operator == ArithmeticOperator.DIVIDE && expr.right is ValueExpr.NumberValue && expr.right.value == 0) {
          issues += ProgramValidationIssue("DIVIDE_BY_ZERO", "Division by constant zero", path)
        }
        issues
      }

      is ValueExpr.CardCharacteristic -> validateCardReference(expr.cardReference, path)
    }
  }

  private fun validateVariableId(variableId: VariableId, path: List<Int>): List<ProgramValidationIssue> {
    return if (variableId.value.isBlank()) {
      listOf(ProgramValidationIssue("INVALID_VARIABLE_ID", "Variable id must not be blank", path))
    } else {
      emptyList()
    }
  }

  private fun validateDonSelector(selector: DonSelector, path: List<Int>): List<ProgramValidationIssue> {
    val issues = mutableListOf<ProgramValidationIssue>()
    val invalidZones = selector.zones - setOf(ZoneType.COST, ZoneType.ATTACHED_DON)
    if (invalidZones.isNotEmpty()) {
      issues += ProgramValidationIssue(
        "INVALID_DON_ZONE",
        "DON selector cannot use zones $invalidZones",
        path,
      )
    }
    selector.source?.let { issues += validateVariableId(it, path) }
    return issues
  }

  private fun validateCardReference(
    reference: CardReference,
    path: List<Int>,
  ): List<ProgramValidationIssue> {
    val issues = mutableListOf<ProgramValidationIssue>()
    val hasInstance = reference.instanceId != null
    val hasVariable = reference.variableId != null
    if (hasInstance == hasVariable) {
      issues += ProgramValidationIssue(
        "INVALID_CARD_REFERENCE",
        "CardReference must have exactly one of instanceId or variableId",
        path,
      )
    }
    reference.variableId?.let { variableId ->
      if (variableId.value.isBlank()) {
        issues += ProgramValidationIssue(
          "INVALID_VARIABLE_ID",
          "CardReference variableId must not be blank",
          path,
        )
      }
    }
    return issues
  }

  private fun validateCondition(condition: Condition, path: List<Int>): List<ProgramValidationIssue> {
    return when (condition) {
      is Condition.Comparison -> {
        val issues = validateValue(condition.left, path).toMutableList()
        issues += validateValue(condition.right, path)
        issues
      }

      is Condition.AllConditions -> condition.conditions.flatMap { validateCondition(it, path) }
      is Condition.AnyCondition -> condition.conditions.flatMap { validateCondition(it, path) }
      is Condition.NotCondition -> validateCondition(condition.condition, path)
      is Condition.Exists -> validateSelector(condition.selector, path)
    }
  }

  private fun validateSelector(selector: CardSelector, path: List<Int>): List<ProgramValidationIssue> {
    val issues = mutableListOf<ProgramValidationIssue>()
    if (selector.sameAsSource && selector.excludeSource) {
      issues += ProgramValidationIssue(
        "CONFLICTING_SOURCE_FILTER",
        "CardSelector cannot set both sameAsSource and excludeSource",
        path,
      )
    }
    if (selector.orientation == CardOrientation.NOT_APPLICABLE) {
      issues += ProgramValidationIssue(
        "INVALID_ORIENTATION_FILTER",
        "CardSelector orientation filter cannot be NOT_APPLICABLE",
        path,
      )
    }
    issues += selector.conditions.flatMap { validateCondition(it, path) }
    return issues
  }

  private fun validateCost(cost: Cost, path: List<Int>): List<ProgramValidationIssue> {
    return when (cost) {
      is Cost.NoCost -> emptyList()
      is Cost.CombinedCost -> cost.costs.flatMapIndexed { i, c -> validateCost(c, path + i) }
      is Cost.RestSourceCost -> emptyList()
      is Cost.RestDonCost -> validateValue(cost.amount, path)
      is Cost.ReturnDonCost -> validateValue(cost.amount, path)
      is Cost.TrashCardsCost -> {
        val issues = validateSelector(cost.selector, path).toMutableList()
        issues += validateValue(cost.amount, path)
        issues
      }
    }
  }

  private fun validateDuration(duration: Duration, path: List<Int>): List<ProgramValidationIssue> {
    return when (duration) {
      is Duration.UntilEvent -> validatePattern(duration.pattern, path, 0)
      is Duration.WhileCondition -> validateCondition(duration.condition, path)
      else -> emptyList()
    }
  }

  private fun validateReplacementDefinition(
    definition: ReplacementDefinition,
    path: List<Int>,
    depth: Int,
  ): List<ProgramValidationIssue> {
    if (depth > MAX_NESTING_DEPTH) {
      return listOf(ProgramValidationIssue("MAX_NESTING", "Replacement nesting exceeds $MAX_NESTING_DEPTH", path))
    }
    val issues = validatePattern(definition.pattern, path, depth).toMutableList()
    definition.condition?.let { issues += validateCondition(it, path) }
    issues += validateProgramInternal(definition.program, path, depth + 1)
    issues += validateDuration(definition.duration, path)
    return issues
  }

  private fun validatePattern(pattern: GameEventPattern, path: List<Int>, depth: Int): List<ProgramValidationIssue> {
    val issues = mutableListOf<ProgramValidationIssue>()
    pattern.source?.let { issues += validateSelector(it, path) }
    pattern.target?.let { issues += validateSelector(it, path) }
    return issues
  }

  private fun validateModifier(modifier: Modifier, path: List<Int>, depth: Int): List<ProgramValidationIssue> {
    return when (modifier) {
      is Modifier.CharacteristicModifier -> validateValue(modifier.value, path)
      is Modifier.PropertyModifier -> emptyList()
      is Modifier.KeywordModifier -> emptyList()
      is Modifier.ControlModifier -> emptyList()
      is Modifier.DamagePreventionModifier -> validateValue(modifier.amount, path)
      is Modifier.PermissionModifier -> modifier.permission.condition?.let { validateCondition(it, path) }
        ?: emptyList()

      is Modifier.RestrictionModifier -> modifier.restriction.condition?.let { validateCondition(it, path) }
        ?: emptyList()

      is Modifier.EffectNegationModifier -> emptyList()
      is Modifier.ActivationCostModifier -> validateCost(modifier.cost, path)
      is Modifier.DeckRuleEffectModifier -> emptyList()
      is Modifier.ReplacementModifier -> validateReplacementDefinition(modifier.definition, path, depth + 1)
      is Modifier.AbilityModifier -> validateAbility(modifier.ability, path, depth + 1)
    }
  }

  companion object {
    private const val MAX_NESTING_DEPTH = 128
  }
}