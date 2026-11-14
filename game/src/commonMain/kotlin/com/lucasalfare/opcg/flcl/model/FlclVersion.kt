package com.lucasalfare.opcg.flcl.model

const val FLCL_FORMAT = "FL_CARD_LANGUAGE"

data class FlclVersion(
  val major: Int, val minor: Int, val patch: Int
) : Comparable<FlclVersion> {

  override fun compareTo(other: FlclVersion): Int =
    compareValuesBy(this, other, FlclVersion::major, FlclVersion::minor, FlclVersion::patch)

  override fun toString(): String = "$major.$minor.$patch"

  companion object {
    val TARGET = FlclVersion(1, 10, 0)

    private val pattern = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

    fun parse(text: String): FlclVersion {
      val match = pattern.matchEntire(text)
        ?: throw IllegalArgumentException("language_version must be major.minor.patch, got '$text'")
      val (major, minor, patch) = match.destructured.toList().map {
        it.toIntOrNull() ?: throw IllegalArgumentException("language_version component out of range: '$text'")
      }
      return FlclVersion(major, minor, patch)
    }

    fun parseSupported(text: String): FlclVersion {
      val version = parse(text)
      require(version <= TARGET) {
        "language_version $version is newer than the supported FLCL target $TARGET"
      }
      return version
    }
  }
}

enum class FlclFeature(val minimumVersion: FlclVersion) {
  RETURN_CHARACTER_TO_OWNER_HAND(FlclVersion(1, 4, 0)), RESTRICT_ATTACK_TARGETS(
    FlclVersion(
      1,
      5,
      0
    )
  ),
  MULTI_CARD_REST_CARD_SELECTION(FlclVersion(1, 5, 0)), ON_OPPONENT_CHARACTER_KO(FlclVersion(1, 6, 0)), SCALE(
    FlclVersion(1, 7, 0)
  ),
  COUNT_GROUPS(FlclVersion(1, 8, 0)), PREVENT_ATTACK(FlclVersion(1, 8, 0)), UNTIL_END_OF_NEXT_TURN(
    FlclVersion(
      1,
      8,
      0
    )
  ),
  INSTANCE_IDENTITY_COMPARE(FlclVersion(1, 9, 0)), ALL_QUANTITY_MODE(
    FlclVersion(
      1,
      9,
      0
    )
  ),
  ADDITIONAL_NAMES(FlclVersion(1, 10, 0)), RESTRICT_KEYWORD_EFFECT_ACTIVATION(FlclVersion(1, 10, 0));

  fun isAvailableIn(version: FlclVersion): Boolean = version >= minimumVersion
}