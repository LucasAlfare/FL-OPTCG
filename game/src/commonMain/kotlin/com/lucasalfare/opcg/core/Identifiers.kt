package com.lucasalfare.opcg.core

@JvmInline
value class LogicalCardId(val value: String) {
  init {
    require(value.isNotBlank()) { "LogicalCardId must not be blank" }
  }
}

@JvmInline
value class PrintingId(val value: String) {
  init {
    require(value.isNotBlank()) { "PrintingId must not be blank" }
  }
}

@JvmInline
value class CardInstanceId(val value: String) {
  init {
    require(value.isNotBlank()) { "CardInstanceId must not be blank" }
  }
}

@JvmInline
value class PlayerId(val value: String) {
  init {
    require(value.isNotBlank()) { "PlayerId must not be blank" }
  }
}

@JvmInline
value class AreaId(val value: String) {
  init {
    require(value.isNotBlank()) { "AreaId must not be blank" }
  }
}

@JvmInline
value class AbilityId(val value: String) {
  init {
    require(value.isNotBlank()) { "AbilityId must not be blank" }
  }
}

@JvmInline
value class EffectId(val value: String) {
  init {
    require(value.isNotBlank()) { "EffectId must not be blank" }
  }
}

@JvmInline
value class EventId(val value: String) {
  init {
    require(value.isNotBlank()) { "EventId must not be blank" }
  }
}

@JvmInline
value class ExecutionId(val value: String) {
  init {
    require(value.isNotBlank()) { "ExecutionId must not be blank" }
  }
}