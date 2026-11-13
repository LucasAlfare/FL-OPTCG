package com.lucasalfare.opcg.core

import kotlinx.serialization.Serializable

@Serializable
enum class AbilityCategory {
  AUTO,
  ACTIVATE,
  PERMANENT,
  REPLACEMENT,
}

@Serializable
enum class Color {
  RED,
  GREEN,
  BLUE,
  PURPLE,
  BLACK,
  YELLOW,
}

@Serializable
enum class Attribute {
  SLASH,
  STRIKE,
  RANGED,
  SPECIAL,
  WISDOM,
  QUESTION_MARK,
}

@Serializable
enum class Orientation {
  ACTIVE,
  RESTED,
}

@Serializable
enum class Face {
  FACE_UP,
  FACE_DOWN,
}

@Serializable
enum class Area {
  DECK,
  HAND,
  LIFE,
  LEADER,
  CHARACTER,
  STAGE,
  TRASH,
  DON_DECK,
  DON_AREA,
}

@Serializable
enum class KeywordEffect {
  BLOCKER,
  RUSH,
  DOUBLE_ATTACK,
  BANISH,
  TRIGGER,
}