package com.lucasalfare.opcg.flcl.model

enum class ThumbnailRole {
  SAMPLE, CUSTOM, FINAL
}

sealed interface EditorialExtraValue

sealed interface EditorialExtraScalar : EditorialExtraValue

data class EditorialExtraString(
  val value: String
) : EditorialExtraScalar

data class EditorialExtraInteger(
  val value: Long
) : EditorialExtraScalar

data class EditorialExtraFloat(
  val value: Double
) : EditorialExtraScalar

data class EditorialExtraBoolean(
  val value: Boolean
) : EditorialExtraScalar

data class EditorialExtraArray(
  val items: List<EditorialExtraScalar>
) : EditorialExtraValue {
  init {
    require(items.map { it::class }.distinct().size <= 1) {
      "editorial.extra arrays must be homogeneous"
    }
  }
}

data class EditorialPrinting(
  val setId: String? = null,
  val rarity: String? = null,
  val artId: String,
  val illustrator: String? = null,
  val thumbnailRole: ThumbnailRole? = null,
  val thumbnailUrl: String? = null,
  val rights: String? = null
) {
  init {
    require(artId.isNotBlank()) { "printing art_id must not be blank" }
  }
}

data class EditorialMetadata(
  val rarity: String? = null,
  val setId: String? = null,
  val setName: String? = null,
  val illustrator: String? = null,
  val thumbnailUrl: String? = null,
  val rights: String? = null,
  val blockNumber: Int? = null,
  val printings: List<EditorialPrinting> = emptyList(),
  val extra: Map<String, EditorialExtraValue> = emptyMap()
) {
  init {
    require(printings.map { it.artId }.distinct().size == printings.size) {
      "editorial printings must have unique art_id values"
    }
    require(extra.keys.all { it.isNotBlank() }) { "editorial.extra keys must not be blank" }
  }
}