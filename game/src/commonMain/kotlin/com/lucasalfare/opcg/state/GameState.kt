package com.lucasalfare.opcg.state

import com.lucasalfare.opcg.core.CardInstanceId
import com.lucasalfare.opcg.core.GameArea
import com.lucasalfare.opcg.core.PlayerId

class GameState(
  players: List<PlayerState>, cardInstances: List<CardInstance>, donInstances: List<DonInstance>
) {
  val players: List<PlayerState> = players.toList()

  private val playersById: Map<PlayerId, PlayerState> = this.players.associateBy { it.id }
  private val cardsById: Map<CardInstanceId, CardInstance>
  private val donById: Map<CardInstanceId, DonInstance>
  private val areaByInstance: Map<CardInstanceId, AreaState>
  private val cardByAttachedDon: Map<CardInstanceId, CardInstanceId>

  init {
    require(playersById.size == this.players.size) { "Player identifiers must be unique" }

    cardsById = cardInstances.associateBy { it.id }
    require(cardsById.size == cardInstances.size) { "Card instance identifiers must be unique" }

    donById = donInstances.associateBy { it.id }
    require(donById.size == donInstances.size) { "DON!! instance identifiers must be unique" }

    require(cardsById.keys.none { it in donById }) {
      "Card and DON!! instances must not share identifiers"
    }

    for (card in cardInstances) {
      require(card.owner in playersById) { "Card ${card.id.value} has unknown owner ${card.owner.value}" }
      require(card.controller in playersById) { "Card ${card.id.value} has unknown controller ${card.controller.value}" }
    }
    for (don in donInstances) {
      require(don.owner in playersById) { "DON!! ${don.id.value} has unknown owner ${don.owner.value}" }
      require(don.controller in playersById) { "DON!! ${don.id.value} has unknown controller ${don.controller.value}" }
    }

    val locations = HashMap<CardInstanceId, AreaState>()
    for (player in this.players) {
      for (kind in GameArea.entries) {
        val area = player.area(kind)
        for (instanceId in area.cardInstances) {
          require(instanceId in cardsById || instanceId in donById) {
            "Area ${area.id.value} references unknown instance ${instanceId.value}"
          }
          require(instanceId !in locations) {
            "Instance ${instanceId.value} is located in more than one area"
          }
          locations[instanceId] = area
        }
      }
    }
    areaByInstance = locations

    val attached = HashMap<CardInstanceId, CardInstanceId>()
    for (card in cardInstances) {
      for (donId in card.attachedDon) {
        require(donId in donById) { "Card ${card.id.value} attaches unknown DON!! ${donId.value}" }
        require(donId !in attached) { "DON!! ${donId.value} is attached more than once" }
        require(donId !in locations) { "DON!! ${donId.value} is both located in an area and attached" }
        attached[donId] = card.id
      }
    }
    cardByAttachedDon = attached

    for (card in cardInstances) {
      require(card.id in locations) { "Card instance ${card.id.value} is not located in any area" }
    }
    for (don in donInstances) {
      require(don.id in locations || don.id in attached) {
        "DON!! ${don.id.value} is neither located in an area nor attached to a card"
      }
    }
  }

  fun player(id: PlayerId): PlayerState = playersById.getValue(id)

  fun cardInstance(id: CardInstanceId): CardInstance? = cardsById[id]

  fun donInstance(id: CardInstanceId): DonInstance? = donById[id]

  fun areaOf(id: CardInstanceId): AreaState? = areaByInstance[id]

  fun cardToWhichDonIsAttached(donId: CardInstanceId): CardInstanceId? = cardByAttachedDon[donId]
}