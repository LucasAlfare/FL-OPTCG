package com.lucasalfare.opcg.core

enum class CardCategory {
  LEADER, CHARACTER, EVENT, STAGE
}

enum class CardColor {
  RED, GREEN, BLUE, PURPLE, BLACK, YELLOW
}

enum class CardAttribute {
  STRIKE, SLASH, RANGED, WISDOM, SPECIAL
}

enum class GameArea {
  DECK, DON_DECK, COST_AREA, HAND, CHARACTER_AREA, STAGE_AREA, LEADER_AREA, TRASH, LIFE
}

enum class ActiveState {
  ACTIVE, RESTED
}

enum class CardFace {
  FACE_UP, FACE_DOWN
}