package com.lucasalfare.opcg.fcl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FclPayment {
  val payer: FclReference
  val optional: Boolean
}

@Serializable
@SerialName("REST_DON")
data class RestDonPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("DON_MINUS")
data class DonMinusPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("REST_CARD")
data class RestCardPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection? = null,
  val subject: FclPaymentSubject? = null,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("TRASH_HAND_CARD")
data class TrashHandCardPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("RETURN_CHARACTER_TO_OWNER_HAND")
data class ReturnCharacterToOwnerHandPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("ADD_LIFE_TO_HAND")
data class AddLifeToHandPayment(
  override val payer: FclReference,
  val quantity: FclQuantity,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
@SerialName("PLACE_HAND_CARD_BOTTOM_DECK")
data class PlaceHandCardBottomDeckPayment(
  override val payer: FclReference,
  val selection: FclPaymentSelection,
  override val optional: Boolean = false,
) : FclPayment

@Serializable
data class FclPaymentSelection(
  @SerialName("object") val objectKind: FclObjectKind,
  val areas: List<FclAreaReference>,
  val quantity: FclQuantity,
  val chooser: FclReference,
  val information: FclInformation,
  val filter: FclCondition? = null,
)

@Serializable
enum class FclPaymentSubjectKind { SOURCE }

@Serializable
data class FclPaymentSubject(val kind: FclPaymentSubjectKind)