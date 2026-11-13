package com.lucasalfare.opcg.core

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class CardId(val value: String)

@JvmInline
@Serializable
value class InstanceId(val value: Long)

@JvmInline
@Serializable
value class PlayerId(val value: Int)

@JvmInline
@Serializable
value class AbilityId(val value: String)

@JvmInline
@Serializable
value class EffectId(val value: String)

@JvmInline
@Serializable
value class EventId(val value: Long)

@JvmInline
@Serializable
value class ExecutionId(val value: Long)