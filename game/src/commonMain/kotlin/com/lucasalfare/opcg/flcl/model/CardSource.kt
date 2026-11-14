package com.lucasalfare.opcg.flcl.model

enum class CardSourceReferenceKind {
  CR, FAQ, ERRATA, OFFICIAL_CARD_TEXT
}

private fun isIsoDate(value: String): Boolean {
  val match = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(value) ?: return false
  val (_, month, day) = match.destructured
  return month.toInt() in 1..12 && day.toInt() in 1..31
}

data class CardSourceReference(
  val id: FlclRuleRef,
  val kind: CardSourceReferenceKind,
  val document: String,
  val url: String,
  val version: String? = null,
  val section: String? = null,
  val date: String? = null
) {
  init {
    require(document.isNotBlank()) { "reference document must not be blank" }
    require(url.isNotBlank()) { "reference url must not be blank" }
    if (version != null) require(version.isNotBlank()) { "reference version must not be blank when present" }
    if (section != null) require(section.isNotBlank()) { "reference section must not be blank when present" }
    if (date != null) require(isIsoDate(date)) { "reference date must be ISO YYYY-MM-DD, got '$date'" }
  }
}

data class CardSource(
  val rawText: String,
  val currentText: String,
  val language: String,
  val region: String,
  val edition: String? = null,
  val references: List<CardSourceReference> = emptyList()
) {
  init {
    require(rawText.isNotBlank()) { "source raw_text must not be blank" }
    require(currentText.isNotBlank()) { "source current_text must not be blank" }
    require(language.isNotBlank()) { "source language must not be blank" }
    require(region.isNotBlank()) { "source region must not be blank" }
    if (edition != null) require(edition.isNotBlank()) { "source edition must not be blank when present" }
    require(references.map { it.id }.distinct().size == references.size) {
      "source reference ids must be unique"
    }
  }
}