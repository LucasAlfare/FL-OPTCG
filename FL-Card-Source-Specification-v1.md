# FL Card Source Specification v1

**Status:** Frozen authoring contract  
**Format:** TOML 1.0  
**Scope:** One Piece Card Game card definitions, gameplay abilities, catalog metadata and thumbnail references  
**Canonical extension:** `.toml`  
**Specification language:** Portuguese; identifiers and enum values are English

---

## 1. Purpose

FL Card Source is the human-authored source format for the FL-OPTCG card catalog. A language model converts trusted raw card records into one TOML file per logical card definition. A separate JVM-only Gradle module, `content-compiler`, will validate and compile these files into a binary catalog. A later integration in the `game` module will load that binary catalog and convert its records into the domain models consumed by `GameEngine` and `CardCatalog`.

The pipeline is:

```text
Trusted raw card data
    -> LLM translation
    -> FL Card Source TOML files
    -> content-compiler (JVM)
    -> binary card catalog, including packaged thumbnail assets
    -> future game-side catalog loader / factory
    -> CardCatalog and GameEngine
```

The TOML files are the editable source of truth. The binary catalog is a generated artifact and must be reproducible from the same source files and asset contents.

This specification defines the TOML source contract. It does **not** define the physical byte layout, compression algorithm, encryption, or extension of the future binary catalog. Those belong to a separate binary-format specification.

## 2. Normative rules

The words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, and **MAY** are normative.

1. Every source file MUST be valid TOML 1.0 encoded as UTF-8.
2. Every file MUST describe exactly one logical card definition.
3. The root MUST contain only the keys defined in this specification.
4. Keys MUST use `snake_case` exactly as shown.
5. Enum values and instruction opcodes MUST use the exact uppercase spelling shown in this document.
6. Unknown keys, unknown enum values, duplicate keys, and duplicate identifiers MUST be rejected. The compiler MUST NOT silently ignore them.
7. Optional fields MUST be omitted when they have no value. TOML `null` does not exist and MUST NOT be simulated with strings such as `"null"`.
8. Collection fields MUST be present, even when empty, unless this document explicitly marks the whole field as optional.
9. Arrays preserve source order. This is significant for abilities, instructions, branches, costs, options and printings.
10. A language model MUST NOT invent missing card statistics, card text, rarity, printing data, or URLs. Unknown information MUST be omitted where optional or explicitly reported as unavailable.
11. A card MUST NOT be marked `READY` if any printed gameplay text or keyword remains untranslated, ambiguous, or unsupported by the source instruction vocabulary.
12. Metadata MUST NOT change gameplay rules or be interpreted as an instruction.

## 3. Repository organization and identity

Recommended source directory:

```text
card-data/
├── OP01/
│   ├── OP01-001.toml
│   ├── OP01-002.toml
│   └── ...
├── OP02/
│   └── ...
└── ST01/
    └── ...
```

Directory names are organizational only. They MUST NOT determine a card's rules, color, rarity, or identity.

### 3.1 Logical card versus physical printing

- `card.id` identifies one logical gameplay definition and MUST be unique within the complete catalog.
- `card.number` is the official card number printed on the card. It is not guaranteed to be globally unique across all product reprints or printings.
- `metadata.printings[]` describes physical releases/versions of that logical card. A reprint or alternate-art printing MUST be represented as another printing entry, not as another gameplay ability definition, when its gameplay text and characteristics are identical.
- `metadata.printings[].id` identifies one printing and MUST be unique within the logical card. It SHOULD be globally unique in the catalog.
- Two printings with different art but identical gameplay MUST share the same `card.id` and ability definitions.
- If two cards sharing a printed number have different gameplay definitions, they MUST have different `card.id` values. The catalog author is responsible for choosing stable, unambiguous IDs.

For a first catalog, `card.id` SHOULD equal `card.number` when that produces a unique logical definition. Do not append an art variant to `card.id` merely because the printing has alternate art.

## 4. Root document structure

A valid file has this structure:

```toml
schema_version = 1

[card]
# Required gameplay identity and characteristics.

[metadata]
# Client/catalog metadata.

[[metadata.printings]]
# Zero or more physical printings. Omit all printing tables if none are known.

[source]
# Provenance and original printed text.

[translation]
# Translation completeness state.

[[abilities]]
# Zero or more abilities. Each table represents one ability.
```

The exact root keys are:

| Root key | Required | Type |
|---|---:|---|
| `schema_version` | Yes | Integer; MUST equal `1` |
| `card` | Yes | Table |
| `metadata` | Yes | Table |
| `source` | Yes | Table |
| `translation` | Yes | Table |
| `abilities` | No | Array of tables; omitted when the card has no abilities |

A card with no abilities MUST omit every `[[abilities]]` table. Do not create a dummy or empty ability.

## 5. `[card]`: gameplay definition

The `[card]` table maps to the logical information represented by Kotlin `CardDefinition`.

| Field | Required | TOML type | Domain meaning / rule |
|---|---:|---|---|
| `id` | Yes | String | Unique logical `CardDefinitionId` |
| `number` | Yes | String | Official printed card number |
| `name` | Yes | String | Card name in the catalog language |
| `category` | Yes | String enum | `CardCategory` |
| `colors` | Yes | Array of strings | `CardColor`; empty only if the source truly has no color |
| `types` | Yes | Array of strings | Card types/traits; preserve canonical official terms in the catalog language |
| `attributes` | Yes | Array of strings | `CardAttribute`; use `[]` when not applicable |
| `base_cost` | No | Integer | Printed cost; omit when not applicable or unavailable; preserve an actual value of `0` |
| `base_power` | No | Integer | Printed power; omit when not applicable or unavailable; preserve an actual value of `0` |
| `life` | No | Integer | Printed life; omit when not applicable or unavailable; preserve an actual value of `0` |
| `counter` | No | Integer | Printed counter; omit when the card has no counter value; do not substitute `0` for absence |
| `keywords` | Yes | Array of strings | Recognized `KeywordEffect` values; use `[]` when none |

### 5.1 Card enum values

`category`:

- `LEADER`
- `CHARACTER`
- `EVENT`
- `STAGE`
- `DON`

`colors`:

- `RED`
- `GREEN`
- `BLUE`
- `PURPLE`
- `BLACK`
- `YELLOW`

`attributes`:

- `SLASH`
- `STRIKE`
- `RANGED`
- `SPECIAL`
- `WISDOM`
- `QUESTION_MARK`

`keywords`:

- `RUSH`
- `DOUBLE_ATTACK`
- `BANISH`
- `BLOCKER`
- `TRIGGER`
- `RUSH_CHARACTER`
- `UNBLOCKABLE`

`types` is a list of strings, not a closed enum. The compiler MUST preserve the exact string values. The catalog MUST use one consistent language and canonical spelling for type names so selector comparisons remain reliable.

### 5.2 Characteristics versus abilities

Only recognized `KeywordEffect` values belong in `card.keywords`. Other rules text MUST be represented as one or more entries in `abilities`. Reminder text is not itself an additional ability.

The printed card statistics belong in `[card]`. Values modified during a game are handled by effects and MUST NOT be precomputed into the card definition.

## 6. `[metadata]`: client and catalog metadata

Metadata is preserved for future clients and catalog tools. It is not part of gameplay evaluation.

`[metadata]` has no required scalar fields in v1. It MAY contain zero or more `[[metadata.printings]]` entries.

### 6.1 `[[metadata.printings]]`

Each entry represents a distinct physical printing, release, or art variant.

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `id` | Yes | String | Stable printing identifier, unique within the logical card |
| `set_code` | Yes | String | Official product/set code, e.g. `OP01`, `ST01`, or the official promo code |
| `set_name` | No | String | Human-readable product/set name |
| `rarity` | Yes | String | Preserve the rarity code exactly as supplied by the trusted source; do not constrain it to a closed enum |
| `variant` | No | String | Printing/art designation such as `STANDARD`, `ALTERNATE_ART`, `MANGA`, `SP`, or `PROMO`; preserve source terminology |
| `release_date` | No | String | ISO `YYYY-MM-DD` when reliably available |
| `artist` | No | String | Illustrator credit when reliably available |
| `thumbnail_url` | No | String | Absolute HTTP(S) URL to a thumbnail/sample image |
| `thumbnail_source_url` | No | String | URL of the page/source record from which the thumbnail URL was obtained |
| `thumbnail_role` | No | String enum | `SAMPLE`, `CUSTOM`, or `FINAL`; use `SAMPLE` for temporary sample imagery |
| `thumbnail_rights` | No | String enum | `UNKNOWN`, `NOT_REVIEWED`, `OWNED`, `LICENSED`, or `PUBLIC_DOMAIN` |

Rules:

1. `rarity` is a string rather than a closed enum so new rarity codes and regional/product-specific designations do not require a schema revision.
2. The compiler MUST preserve rarity exactly; it MUST NOT infer rarity from the card number or image.
3. `thumbnail_url` MUST be a complete absolute `http://` or `https://` URL. Local paths, relative URLs, `data:` URLs and base64 content are forbidden in TOML.
4. The LLM MUST only copy a thumbnail URL found in its supplied raw data or a source it actually consulted. It MUST NOT fabricate, guess, or construct a URL.
5. If no verified URL exists, omit `thumbnail_url`. A missing thumbnail MUST NOT block gameplay compilation by itself.
6. If `thumbnail_url` is present and `thumbnail_role` is omitted, consumers MUST treat the role as `SAMPLE` for display-label purposes only. This does not establish permission to redistribute the image.
7. If rights are not known, `thumbnail_rights` MUST be `UNKNOWN` or `NOT_REVIEWED`; it MUST NOT be guessed.
8. Each printing MAY have its own thumbnail. The binary catalog MUST associate packaged image bytes with the corresponding printing ID, not just with `card.id`.
9. If two printings have different rarity or artwork, store separate printing entries. Do not overwrite one printing's metadata with another's.
10. A future client MAY choose one printing as its display default, but that selection is outside the gameplay definition.

A thumbnail URL is a source reference, not the image bytes. The future compiler will retrieve and validate the asset, compute its digest and package the image bytes in the binary artifact. TOML MUST NOT contain image bytes.

## 7. `[source]`: provenance and raw text

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `provider` | No | String | Name of the raw data provider/database |
| `record_url` | No | String | URL of the source card record |
| `language` | Yes | String | BCP-47 language tag for the preserved raw card text, e.g. `en` or `ja` |
| `card_text` | Yes | String | Complete rules/flavor text transcribed from the supplied card source, preserving wording and line breaks as closely as possible |
| `notes` | No | String | Provenance notes only; never interpreted as game rules |

Each ability table also has a required `source_text` field containing the exact excerpt from `source.card_text` represented by that ability. It exists for traceability and review; it is not executed.

The LLM MUST NOT silently paraphrase `source.card_text` or `source_text`. If the raw source does not provide complete text, the LLM MUST state that in `source.notes` and set translation status appropriately.

## 8. `[translation]`: completeness state

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `status` | Yes | String enum | `READY` or `BLOCKED` |
| `unsupported` | Yes | Array of strings | Reasons for any unrepresented, ambiguous, or unsupported gameplay text; MUST be `[]` for `READY` |

`READY` means that the complete printed gameplay meaning has been translated into the v1 source vocabulary without known omissions or unresolved ambiguity. It does not claim that every source-level operand has already been integrated into the current Kotlin runtime. Runtime integration compatibility is reported separately by the compiler/game integration and MUST NOT be confused with translation completeness.

`BLOCKED` means the file is useful as a reviewed draft/source record but MUST NOT enter a production executable catalog. The compiler MAY still parse it in a diagnostics-only mode, but MUST report the card as non-executable.

The LLM MUST set `BLOCKED` when:

- any rules text cannot be represented by the defined ability/instruction structures;
- an effect's target, timing, cost, duration, choice, or resolution is materially ambiguous;
- a required value or official card datum is missing and cannot be safely established;
- the LLM would otherwise need to invent a new opcode or undocumented runtime behavior.

The `unsupported` array MUST identify the specific text/ability and the reason. It MUST NOT be used for harmless reminder text that has no independent gameplay effect.

## 9. `[[abilities]]`: ability definitions

Each `[[abilities]]` entry represents one independently classified ability or one independently evaluated continuous/replacement effect.

Common fields:

| Field | Required | Rule |
|---|---:|---|
| `id` | Yes | Stable ability identifier, unique within `card.id` |
| `kind` | Yes | `AUTO`, `ACTIVATED`, `PERMANENT`, or `REPLACEMENT` |
| `source_text` | Yes | Exact printed-text excerpt represented by this entry |

Ability IDs SHOULD be deterministic, e.g. `${card.id}-A1`, `${card.id}-A2`. Program IDs SHOULD be deterministic and unique within the catalog, e.g. `${card.id}-A1-P1`. IDs used for cards, printings, abilities, programs, replacements and options MUST be non-empty, MUST NOT contain whitespace, and MUST be stable across recompilations. Variable IDs MUST be non-empty and MUST be unique within their program scope.

One printed ability containing several effects MUST normally be represented as one ability with an ordered program containing all effects. Separate abilities MUST be used when the card has distinct independent triggers, activation methods, timings, or continuous effects.

### 9.1 `ACTIVATED`

Required fields:

- `id`
- `kind = "ACTIVATED"`
- `timing`
- `cost`
- `usage_limit`
- `program`
- `source_text`

Optional field: `condition`.

`timing` MUST be one of the `ActivationTiming` values in section 10. `cost` MUST use the `Cost` representation in section 12. `program` MUST use the representation in section 13.

### 9.2 `AUTO`

Required fields:

- `id`
- `kind = "AUTO"`
- `trigger`
- `usage_limit`
- `program`
- `source_text`

Optional field: `condition`.

`trigger` is a `GameEventPattern` as defined in section 14. The event pattern identifies which game event can schedule the ability. Do not use `AUTO` merely because the English text begins with a timing phrase; classify according to the actual rules semantics.

### 9.3 `PERMANENT`

Required fields:

- `id`
- `kind = "PERMANENT"`
- `modifier`
- `source_text`

Optional fields: `condition`, `selector`.

`modifier` MUST use the `Modifier` representation in section 15. A permanent ability is not represented as a `Program` in the current domain model.

### 9.4 `REPLACEMENT`

Required fields:

- `id`
- `kind = "REPLACEMENT"`
- `replacement`
- `source_text`

`replacement` MUST contain:

| Field | Required | Type |
|---|---:|---|
| `id` | Yes | String; stable effect ID |
| `pattern` | Yes | `GameEventPattern` |
| `condition` | No | `Condition` |
| `program` | Yes | `Program` |
| `priority` | Yes | Integer |
| `duration` | Yes | `Duration` |

Use the priority required by the card/rules semantics. Do not invent priority differences to influence ordering.

## 10. Enum vocabulary

Enum values are case-sensitive and MUST match exactly.

### 10.1 Timing and usage

`timing` (`ActivationTiming`):

- `AT_GAME_START`
- `AT_TURN_START`
- `AT_MAIN_PHASE_START`
- `ACTIVATE_MAIN`
- `COUNTER_STEP`
- `ON_PLAY`
- `WHEN_ATTACKING`
- `ON_BLOCK`
- `ON_KO`
- `ON_OPPONENT_ATTACK`
- `ON_TRIGGER`
- `END_OF_YOUR_TURN`
- `END_OF_OPPONENT_TURN`

`usage_limit` (`UsageLimit`):

- `UNLIMITED`
- `ONCE_PER_TURN`

### 10.2 Player and zone vocabulary

`PlayerRef`:

- `SELF` — controller of the ability source/context
- `OPPONENT` — the other player relative to the source controller
- `TURN_PLAYER` — current turn player
- `NON_TURN_PLAYER` — the player who is not the current turn player

`PlayerRef` is the source-format symbolic player reference. It is not a literal `PlayerId` and MUST NOT contain a runtime player UUID/string.

`ZoneType`:

- `DECK`
- `DON_DECK`
- `HAND`
- `TRASH`
- `LEADER`
- `CHARACTER`
- `STAGE`
- `COST`
- `LIFE`
- `ATTACHED_DON`

`CardOrientation`:

- `ACTIVE`
- `RESTED`
- `NOT_APPLICABLE`

`CardCategory`, `CardColor`, `CardAttribute`, and `KeywordEffect` are defined in section 5.1.

### 10.3 Numeric and comparison vocabulary

`NumericCharacteristic`:

- `POWER`
- `BASE_POWER`
- `COST`
- `BASE_COST`
- `LIFE`
- `COUNTER`

`NumericOperation`:

- `ADD`
- `SUBTRACT`
- `SET`

`ArithmeticOperator`:

- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`
- `MIN`
- `MAX`

`ComparisonOperator`:

- `EQUAL`
- `NOT_EQUAL`
- `LESS_THAN`
- `LESS_THAN_OR_EQUAL`
- `GREATER_THAN`
- `GREATER_THAN_OR_EQUAL`

### 10.4 Other enum vocabulary

`AbilityType`: `AUTO`, `ACTIVATED`, `PERMANENT`, `REPLACEMENT`.

`RuleAction`:

- `PLAY_CARD`
- `ACTIVATE_ABILITY`
- `DECLARE_ATTACK`
- `ACTIVATE_BLOCKER`
- `USE_COUNTER`
- `ATTACH_DON`

`KeywordOperation`: `ADD`, `REMOVE`.

`CardProperty`: `NAME`, `COLOR`, `TYPE`, `ATTRIBUTE`, `KEYWORDS`.

`DeckRuleProperty`: `MAIN_DECK_SIZE`, `DON_DECK_SIZE`, `MAXIMUM_COPIES`, `ALLOWED_COLORS`.

`Viewer`: `SOURCE_CONTROLLER`, `OWNER`, `OPPONENT`, `ALL_PLAYERS`.

`Placement`: `TOP`, `BOTTOM`, `DEFAULT`.

`LifePosition`: `TOP`, `BOTTOM`, `DEFAULT`.

`GameEndReason`: `LIFE_DEFEAT`, `DECK_OUT`, `CONCESSION`, `CARD_EFFECT`, `DRAW`.

`GameEventType`:

- `GAME_CREATED`
- `SETUP_STEP_CHANGED`
- `GAME_STARTED`
- `PROGRAM_FRAME_CHANGED`
- `PENDING_CHOICE_CHANGED`
- `TURN_STARTED`
- `PHASE_CHANGED`
- `TURN_ENDED`
- `CARD_PLAYED`
- `CARDS_DRAWN`
- `CARD_MOVED`
- `CARD_REVEALED`
- `CARD_STATE_CHANGED`
- `CARD_ACTIVATED`
- `CARD_KNOCKED_OUT`
- `DON_ADDED`
- `DON_RESTED`
- `DON_ATTACHED`
- `DON_DETACHED`
- `DON_RETURNED`
- `ATTACK_DECLARED`
- `BATTLE_STEP_CHANGED`
- `BLOCK_ACTIVATED`
- `COUNTER_ACTIVATED`
- `BATTLE_ENDED`
- `DAMAGE_PROCESSED`
- `LIFE_CHANGED`
- `EFFECT_ACTIVATED`
- `EFFECT_RESOLVED`
- `EFFECT_FAILED`
- `MODIFIER_ADDED`
- `MODIFIER_EXPIRED`
- `REPLACEMENT_APPLIED`
- `DECK_SHUFFLED`
- `CHOICE_SUBMITTED`
- `MULLIGAN_RESOLVED`
- `PLAYER_WON`
- `PLAYER_LOST`
- `GAME_DRAWN`
- `INITIAL_EFFECTS_PROGRESS_CHANGED`

## 11. Expressions and conditions

All expression/condition structures use a `kind` discriminator. Fields not listed for a selected kind are forbidden.

### 11.1 `ValueExpr`

| `kind` | Required fields | Meaning |
|---|---|---|
| `NUMBER` | `value` integer | Constant integer |
| `COUNT` | `selector` | Number of cards matching a selector |
| `VARIABLE` | `variable` string | Previously stored integer variable |
| `ARITHMETIC` | `operator`, `left`, `right` | Integer arithmetic expression |
| `CHARACTERISTIC` | `selector`, `characteristic` | Characteristic value of a selected card; selector must resolve according to runtime semantics |
| `CURRENT_CARD` | `characteristic` | Characteristic of the candidate card currently being evaluated |

Examples:

```toml
{ kind = "NUMBER", value = 2 }
{ kind = "COUNT", selector = { owner = "SELF", zones = ["CHARACTER"] } }
{ kind = "VARIABLE", variable = "count" }
{ kind = "ARITHMETIC", operator = "ADD", left = { kind = "NUMBER", value = 2 }, right = { kind = "NUMBER", value = 3 } }
{ kind = "CURRENT_CARD", characteristic = "POWER" }
```

`VARIABLE` refers to a variable in the current program frame. Its value MUST be integer-compatible when used by arithmetic/comparison evaluation. The source compiler SHOULD require an integer variable for predictable card programs; the current Kotlin evaluator also converts a stored boolean to `1` or `0` when an integer is requested.

`CHARACTERISTIC` MUST select exactly one card; zero or multiple matches are an evaluation error. `CURRENT_CARD` is valid only when the expression is evaluated with a candidate card, such as inside a selector's per-card `conditions`; using it without a candidate is an evaluation error.

### 11.2 `Condition`

| `kind` | Required fields | Meaning |
|---|---|---|
| `COMPARISON` | `left`, `operator`, `right` | Compare two integer expressions |
| `ALL` | `conditions` | Every nested condition must be true |
| `ANY` | `conditions` | At least one nested condition must be true |
| `NOT` | `condition` | Negate a nested condition |
| `EXISTS` | `selector` | At least one card matches the selector |

`ALL` and `ANY` MUST contain at least one condition. `COMPARISON` compares integer values. Use `EXISTS` for card-existence checks.

### 11.3 `CardSelector`

A selector is a table, not a discriminated union. All fields are optional; omitted filters impose no restriction.

| Field | Type | Meaning |
|---|---|---|
| `owner` | `PlayerRef` | Required owner relation |
| `controller` | `PlayerRef` | Required effective-controller relation |
| `zones` | Array of `ZoneType` | Allowed zones; omitted or empty means every zone supported by the selector engine |
| `categories` | Array of `CardCategory` | Allowed card categories |
| `colors` | Array of `CardColor` | Allowed colors; a card matches when it has at least one listed color |
| `types` | Array of strings | Allowed type/trait names; a card matches when it has at least one listed type |
| `attributes` | Array of `CardAttribute` | Allowed attributes; a card matches when it has at least one listed attribute |
| `name` | String | Exact effective card name |
| `conditions` | Array of `Condition` | Additional per-candidate conditions; all must pass |

Example:

```toml
{ owner = "SELF", zones = ["CHARACTER"], colors = ["RED"], types = ["Straw Hat Crew"] }
```

The `player` field in `SELECT_CARDS`, `CHOOSE_OPTION`, `CHOOSE_NUMBER`, and `ORDER_CARDS` identifies the player who makes the choice; it does not replace the selector's `owner` or `controller` filter. For `DRAW`, `ADD_DON`, `REST_DON`, `RETURN_DON`, `SHUFFLE_ZONE`, `DEAL_DAMAGE`, `SET_LIFE`, and similar instructions, `player` identifies the player whose zone/state is affected. `MOVE_CARDS.destination.player` identifies the owner of the destination zone.

Do not add selector fields such as `keywords`, `cost`, or `power`; they are not part of the current v1 selector contract.

## 12. Costs and durations

### 12.1 `Cost`

| `kind` | Required fields | Meaning |
|---|---|---|
| `NO_COST` | None | No additional activation cost |
| `COMBINED` | `costs` array of Cost | Pay every nested cost |
| `REST_SOURCE` | None | Rest the ability's source card |
| `REST_DON` | `amount` ValueExpr | Rest the specified amount of DON!! |
| `RETURN_DON` | `amount` ValueExpr | Return the specified amount of DON!! |
| `TRASH_CARDS` | `selector`, `amount` | Trash the specified number of matching cards |

A combined cost MUST contain at least one nested cost. Do not duplicate a cost in the program body when it is already paid as the ability's activation cost.

### 12.2 `Duration`

| `kind` | Required fields | Meaning |
|---|---|---|
| `DURING_BATTLE` | None | Until the current battle ends |
| `UNTIL_END_OF_TURN` | None | Until the current turn ends |
| `UNTIL_END_OF_PHASE` | None | Until the current phase ends |
| `UNTIL_EVENT` | `pattern` GameEventPattern | Until an event matching the pattern occurs |
| `WHILE_SOURCE_VALID` | None | While the source card remains valid/in its required zone |
| `WHILE_CONDITION` | `condition` Condition | While the condition remains true |
| `PERMANENT` | None | No scheduled expiration |

Do not replace a specific duration in the printed text with `PERMANENT` merely because the text does not repeat an expiration phrase. Interpret according to the official rules semantics.

## 13. Programs and instructions

### 13.1 `Program`

```toml
program = { id = "CARD-A1-P1", instructions = [{ op = "DRAW", player = "SELF", amount = { kind = "NUMBER", value = 1 } }] }
```

`id` is required and stable. `instructions` is required and ordered. An executable ability MUST have at least one instruction unless its official semantics explicitly require a no-op; a no-op MUST NOT be used to hide untranslated text.

Every instruction is an inline TOML table with a required `op` discriminator. Nested instruction arrays use the same representation recursively.

### 13.2 Control-flow instructions

| `op` | Required fields | Meaning |
|---|---|---|
| `SEQUENCE` | `instructions` | Execute nested instructions in order |
| `CONDITIONAL` | `condition`, `if_true`, `if_false` | Execute exactly one branch |
| `OPTIONAL` | `instructions` | Ask the resolving player whether to execute the nested block |
| `FOR_EACH` | `source`, `item`, `instructions` | Execute the block for each card in a card-list variable; bind the current card to `item` |
| `REPEAT_WHILE` | `condition`, `instructions` | Repeat while the condition is true; the VM's execution-step limit still applies |

`source`, `item`, and all other variable names are strings mapped to `VariableId`.

Additional instruction constraints:

- `LOOK_AT_TOP.zone` MUST be one of `DECK`, `LIFE`, or `DON_DECK`.
- `SHUFFLE_ZONE.zone` MUST be one of `DECK`, `DON_DECK`, `HAND`, `TRASH`, or `LIFE`.
- `MOVE_CARDS.destination.zone` MUST NOT be `ATTACHED_DON`.
- `CHOOSE_OPTION.options` MUST contain at least one unique option ID.
- Numeric minimum/maximum pairs MUST be non-negative when constant, and a constant maximum MUST NOT be less than a constant minimum.
- `SELECT_CARDS`, `CHOOSE_NUMBER`, `LOOK_AT_TOP`, and all amounts MUST use valid integer-valued expressions.
- `FOR_EACH.source` MUST resolve to a card or card-list variable.

### 13.3 Effect and state instructions

The following table is normative. Parameter names are TOML names; `PlayerRef` is used instead of runtime player IDs in source files.

| `op` | Required fields | Optional fields |
|---|---|---|
| `SELECT_CARDS` | `selector`, `player`, `minimum`, `maximum`, `destination` | — |
| `COUNT_CARDS` | `selector`, `destination` | — |
| `LOOK_AT_TOP` | `player`, `zone`, `amount`, `destination` | — |
| `REVEAL_CARDS` | `source`, `viewers` | — |
| `CHOOSE_OPTION` | `player`, `options`, `destination` | — |
| `CHOOSE_NUMBER` | `player`, `minimum`, `maximum`, `destination` | — |
| `ORDER_CARDS` | `source`, `player`, `destination` | — |
| `CHECK_CONDITION` | `condition`, `destination` | — |
| `DRAW` | `player`, `amount` | — |
| `MOVE_CARDS` | `source`, `destination`, `placement` | — |
| `TRASH_FROM_HAND` | `player`, `selector`, `amount` | — |
| `KNOCK_OUT` | `source` | `effect_id` |
| `PLAY_CARD` | `card`, `player`, `payment` | — |
| `SHUFFLE_ZONE` | `player`, `zone` | — |
| `DEAL_DAMAGE` | `player`, `amount` | `effect_id` |
| `SET_LIFE` | `player`, `source`, `position` | — |
| `PREVENT_DAMAGE` | `player`, `amount`, `duration` | — |
| `ADD_DON` | `player`, `amount` | — |
| `REST_DON` | `player`, `selector`, `amount` | — |
| `SET_DON_ACTIVE` | `source` | — |
| `ATTACH_DON` | `card`, `source` | — |
| `RETURN_DON` | `player`, `selector`, `amount` | — |
| `DETACH_DON` | `card`, `amount`, `destination` | — |
| `REST_CARDS` | `source` | — |
| `SET_CARD_STATE` | `source`, `orientation` | — |
| `CHANGE_CONTROL` | `source`, `player`, `duration` | — |
| `MODIFY_CHARACTERISTIC` | `source`, `characteristic`, `operation`, `value`, `duration` | — |
| `SET_BASE_CHARACTERISTIC` | `source`, `characteristic`, `value`, `duration` | — |
| `SET_POWER_TO_ZERO` | `source`, `duration` | — |
| `SET_CARD_PROPERTY` | `source`, `property`, `value`, `duration` | — |
| `COPY_CARD_PROPERTIES` | `source`, `reference`, `properties`, `duration` | — |
| `GRANT_KEYWORD` | `source`, `keyword`, `duration` | — |
| `REMOVE_KEYWORD` | `source`, `keyword`, `duration` | — |
| `GRANT_ABILITY` | `source`, `ability`, `duration` | — |
| `COPY_ABILITIES` | `source`, `reference`, `duration` | — |
| `NEGATE_EFFECTS` | `source`, `scope`, `duration` | — |
| `APPLY_RESTRICTION` | `source`, `restriction`, `duration` | — |
| `GRANT_PERMISSION` | `source`, `permission`, `duration` | — |
| `ADD_ACTIVATION_COST` | `selector`, `cost`, `duration` | — |
| `MODIFY_DECK_RULE` | `modifier`, `duration` | — |
| `REGISTER_REPLACEMENT` | `definition` | — |
| `ACTIVATE_ABILITY` | `selector` | `timing` |
| `SET_GAME_RESULT` | `result` | — |

### 13.4 Instruction-specific structures

`destination` for `MOVE_CARDS` is a table:

```toml
{ player = "SELF", zone = "TRASH" }
```

`selector` for `REST_DON` is a `DonSelector` table:

| Field | Required | Type |
|---|---:|---|
| `zones` | No | Array of `ZoneType` |
| `orientation` | No | `ACTIVE`, `RESTED`, or `NOT_APPLICABLE` |

`viewers` is an array of `Viewer` values.

`options` is an array of unique string option IDs. `destination` receives the selected answer.

`payment` for `PLAY_CARD` is a table with:

- `rested_don`: optional variable name containing a `CardIdsValue` with the DON!! cards to rest; omit when no DON!! is part of the payment;
- `additional_costs`: required array of `Cost`, use `[]` when there are no additional costs.

`card` for `PLAY_CARD`, `ATTACH_DON`, and `DETACH_DON` is a `CardReference` and MUST reference a runtime-selected card variable, never a literal instance ID.

`result` for `SET_GAME_RESULT` is a table with:

- `winner`: optional `PlayerRef`;
- `losers`: array of `PlayerRef`;
- `reason`: `GameEndReason`.

### 13.5 Variable contract

1. Variable names MUST be non-empty strings and SHOULD use lowercase `snake_case`.
2. Variables are local to one program execution frame. They MUST NOT depend on values from another card or another execution.
3. `destination` creates/assigns a variable.
4. `SELECT_CARDS`, `LOOK_AT_TOP`, and `ORDER_CARDS` produce card-list values.
5. `COUNT_CARDS` and `CHOOSE_NUMBER` produce integer values.
6. `CHOOSE_OPTION` produces an option value.
7. `CHECK_CONDITION` produces a boolean value.
8. `FOR_EACH.item` is bound to one card value for the nested block.
9. A variable MUST be assigned before it is read. The compiler MUST validate variable existence and compatible value type where statically determinable.
10. `SOURCE_CARD` is a reserved variable name representing the card that owns the executing ability. A program MAY reference it as a card source. The game-side runtime adapter MUST bind it from `EffectContext.sourceCardId`; this binding is a source/runtime integration requirement and does not alter the physical TOML syntax.

## 14. Event patterns

`GameEventPattern` is encoded as a table:

| Field | Required | Type |
|---|---:|---|
| `event_types` | Yes | Non-empty array of `GameEventType` |
| `source` | No | `CardSelector` |
| `target` | No | `CardSelector` |
| `target_player` | No | `PlayerRef` |
| `player` | No | `PlayerRef` |

Example:

```toml
trigger = { event_types = ["CARD_PLAYED"], player = "SELF" }
```

An event pattern MUST describe the event that actually triggers the ability. Do not replace an event distinction with a broad event type merely to make the ability execute more often.

## 15. Modifiers and nested rule structures

### 15.1 `Modifier`

Every modifier is a table with a required `kind` field.

| `kind` | Required fields | Meaning |
|---|---|---|
| `CHARACTERISTIC` | `characteristic`, `operation`, `value` | Numeric characteristic modification |
| `PROPERTY` | `property`, `value` | Card property replacement |
| `KEYWORD` | `keyword`, `operation` | Add/remove a keyword |
| `CONTROL` | `player` | Change controller to a symbolic player |
| `DAMAGE_PREVENTION` | `player`, `amount` | Damage prevention for a player |
| `PERMISSION` | `permission` | Rule permission |
| `RESTRICTION` | `restriction` | Rule restriction |
| `EFFECT_NEGATION` | `scope` | Negate matching effects |
| `ACTIVATION_COST` | `selector`, `cost` | Modify activation costs |
| `DECK_RULE_EFFECT` | `deck_rule` | Modify a game-format/deck rule |
| `REPLACEMENT` | `definition` | Replacement definition |
| `ABILITY` | `ability`, `add` | Grant/remove an ability |

### 15.2 `PropertyValue`

`value` for `PROPERTY` is a table with one of these `kind` values:

- `STRING` with `value` string;
- `COLORS` with `value` array of `CardColor`;
- `TYPES` with `value` array of strings;
- `ATTRIBUTES` with `value` array of `CardAttribute`;
- `KEYWORDS` with `value` array of `KeywordEffect`.

### 15.3 `RulePermission` and `RuleRestriction`

`permission` table:

- `action` — required `RuleAction`;
- `subject` — optional `CardSelector`;
- `condition` — optional `Condition`.

`restriction` table:

- `action` — required `RuleAction`;
- `subject` — optional `CardSelector`;
- `maximum_uses` — optional `ValueExpr`;
- `condition` — optional `Condition`.

### 15.4 `AbilitySelector`

A table with optional fields:

- `card_selector`: `CardSelector`;
- `ability_id`: string;
- `ability_type`: `AbilityType`;
- `timing`: `ActivationTiming`.

At least one selector field SHOULD be present. Do not make an unconstrained selector unless the printed effect genuinely applies to every matching ability.

### 15.5 `DeckRuleModifier`

A table with:

- `property`: required `DeckRuleProperty`;
- `numeric_operation`: optional `ADD`, `SUBTRACT`, or `SET`;
- `numeric_value`: optional `ValueExpr`;
- `allowed_colors`: optional array of `CardColor`;
- `card_number`: optional string.

Only fields relevant to the chosen `property` should be populated. Do not infer additional deck-construction effects.

### 15.6 `EffectScope`

Use one of:

- `{ kind = "ALL" }`
- `{ kind = "ABILITY", ability_id = "..." }`
- `{ kind = "CARD", card = { variable_id = "selected_card" } }`
- `{ kind = "CURRENT_BATTLE" }`

For `CARD`, `card` MUST be a dynamic `CardReference`; literal runtime instance IDs are forbidden.

### 15.7 `CardReference`

A card reference identifies a card selected or otherwise bound during the current program execution. The source format accepts only:

```toml
{ variable_id = "selected_card" }
```

`variable_id` MUST resolve to a `CardValue` containing exactly one card. A literal `instance_id` MUST NOT be written into a reusable card source file because card instance IDs are created at runtime and differ between games.

## 16. Source-level references and runtime integration

FL Card Source v1 deliberately represents player and card references symbolically:

- `PlayerRef` values identify a player relative to the current effect context/game state.
- `CardReference.variable_id` identifies a card bound in the current program frame.
- `SOURCE_CARD` is the reserved variable for the card whose ability is executing.

These references make source files reusable across matches. They are part of the source/binary intermediate representation and are resolved by the future game-side loader/runtime adapter.

The current Kotlin domain model still has several instruction fields typed as concrete `PlayerId` or `CardInstanceId`. Consequently, the future game integration will need to resolve or adapt these source-level references before constructing/executing those instructions. This is an integration task for the later game-side loading phase; it does not prevent authoring and compiling source records into the independent binary catalog.

The LLM MUST NOT write literal runtime player/card IDs. It MUST use the symbolic references defined here. A card MUST be marked `BLOCKED` only when its rules meaning cannot be represented by FL Card Source v1, not merely because the current game-side adapter has not yet been implemented.

## 17. Compiler responsibilities and catalog behavior

The future `content-compiler` module is JVM-only and is responsible for:

1. Discovering TOML files under the configured source directory.
2. Parsing strict TOML 1.0 and rejecting unknown keys or enum values.
3. Validating `schema_version`.
4. Validating identifiers, duplicate logical cards, duplicate printings, duplicate ability IDs, duplicate option IDs and duplicate values in set-like fields.
5. Validating required/optional fields, scalar types, nested union discriminators and instruction parameters.
6. Validating abilities and programs recursively, including variable references/types where determinable.
7. Rejecting `BLOCKED` cards from production executable output.
8. Resolving each declared thumbnail URL, validating that the response is an image, recording its media type and computing a cryptographic digest.
9. Associating image bytes with the exact `metadata.printings[].id` that references them.
10. Producing a deterministic catalog artifact from identical TOML content and identical asset bytes.
11. Reporting the source file, card ID, ability ID and nested instruction path for each diagnostic.
12. Preserving client metadata and source provenance in the compiled catalog; metadata MUST NOT be discarded merely because it is not needed by gameplay rules.

A missing `thumbnail_url` MUST NOT prevent an otherwise valid gameplay card from being compiled. If a URL is present but its asset cannot be retrieved or validated, the compiler MUST report an asset error. It MUST NOT silently replace the image with an unrelated asset or silently claim the image was packaged.

The compiler MUST calculate catalog version/hash from the canonical compiled content, including gameplay definitions and the packaged assets/metadata covered by the catalog contract. The exact hash algorithm and binary serialization are deferred to the binary-format specification.

## 18. Binary catalog requirements (not the binary byte format)

The future binary catalog MUST preserve, at minimum:

- schema/catalog version information;
- every logical card's identity and gameplay characteristics;
- all four ability kinds and their nested programs/modifiers;
- original card text and per-ability source excerpts, unless a later explicitly versioned release policy allows stripping them;
- metadata and every printing entry;
- thumbnail URL and provenance metadata;
- embedded thumbnail bytes, media type, dimensions when available, and digest;
- a mapping from printing ID to its packaged thumbnail resource;
- indexes sufficient to retrieve a card by `card.id` and official `card.number`.

The binary layout, compression, image transcoding, encryption, file extension and random-access strategy are intentionally **not fixed by this document**. They must be specified separately before binary implementation.

The compiler module MUST be independently runnable and MUST NOT host game execution. The future game-side loader/factory will be responsible for reading the binary artifact and creating the domain-facing catalog used by the game. The exact dependency relationship between modules is an implementation decision for that later integration; the source contract does not require `content-compiler` to depend on `GameEngine`.

## 19. Canonical complete example

The following is a structurally valid illustrative example. The card and its effect are fictitious and MUST NOT be treated as an official card record.

```toml
schema_version = 1

[card]
id = "DEMO-001"
number = "DEMO-001"
name = "Demo Character"
category = "CHARACTER"
colors = ["RED"]
types = ["Demo Crew"]
attributes = ["STRIKE"]
base_cost = 4
base_power = 5000
counter = 1000
keywords = []

[metadata]

[[metadata.printings]]
id = "DEMO-001-DEMO-STD"
set_code = "DEMO"
set_name = "Demonstration Set"
rarity = "R"
variant = "STANDARD"
thumbnail_url = "https://assets.example.invalid/demo-001.jpg"
thumbnail_source_url = "https://cards.example.invalid/demo-001"
thumbnail_role = "SAMPLE"
thumbnail_rights = "UNKNOWN"

[source]
provider = "Demonstration Dataset"
record_url = "https://cards.example.invalid/demo-001"
language = "en"
card_text = "On Play: Draw 1 card."

[translation]
status = "READY"
unsupported = []

[[abilities]]
id = "DEMO-001-A1"
kind = "ACTIVATED"
timing = "ON_PLAY"
cost = { kind = "NO_COST" }
usage_limit = "UNLIMITED"
source_text = "On Play: Draw 1 card."
[abilities.program]
id = "DEMO-001-A1-P1"
instructions = [
  { op = "DRAW", player = "SELF", amount = { kind = "NUMBER", value = 1 } },
]
```

`[abilities.program]` is a nested table of the most recently declared `[[abilities]]` entry. This uses TOML 1.0 table/array-of-table syntax; multiline inline tables are not used.

The `.invalid` domain is reserved for examples and is not a real image/card URL.

## 20. LLM translation contract

The following instructions are intended to be copied into prompts that convert raw card records into FL Card Source v1 files.

> You are a card-data translator for the FL-OPTCG project. Convert the supplied raw One Piece Card Game record into exactly one TOML 1.0 document conforming to **FL Card Source Specification v1**.
>
> Requirements:
>
> 1. Output only the TOML document. Do not output Markdown fences, explanations, JSON, Kotlin code, or additional files.
> 2. Preserve card identity, printed statistics, rarity, product/set information, thumbnail URLs and source provenance exactly as supplied by trusted input.
> 3. Never invent values, source text, rarity codes, card numbers, printing variants, image URLs, image rights, or unsupported instructions.
> 4. Keep gameplay data in `[card]`; keep client-facing printing metadata in `[metadata.printings]`; keep raw provenance and full printed text in `[source]`; keep translation completeness in `[translation]`.
> 5. Create one `[[abilities]]` entry for each independently classified ability. Preserve the exact printed excerpt in `source_text`.
> 6. Translate all effects using only the ability types, enum values, expressions, conditions, costs, durations, selectors, modifiers and instruction opcodes defined by the specification.
> 7. Do not invent opcodes or add fields that are not in the specification.
> 8. Use `PlayerRef` values (`SELF`, `OPPONENT`, `TURN_PLAYER`, `NON_TURN_PLAYER`) instead of literal player IDs.
> 9. Never put a runtime card-instance ID into a reusable card definition. Use `CardReference.variable_id` for dynamic card operands in `PLAY_CARD`, `ATTACH_DON`, `DETACH_DON` and `EffectScope.CARD`.
> 10. Do not mark a card BLOCKED merely because current game-side integration for symbolic `PlayerRef` or `CardReference` values is pending. Mark it BLOCKED only when the card's actual rules meaning cannot be represented by this source specification.
> 11. If any gameplay text is ambiguous, omitted by the source, or cannot be faithfully represented, set `translation.status = "BLOCKED"` and explain every issue in `translation.unsupported`. Do not approximate the effect.
> 12. Set `translation.status = "READY"` only when all gameplay text and keywords are accounted for and no unresolved issue remains. Use `unsupported = []` for READY.
> 13. Omit optional fields for unknown values. Use empty arrays for required collections with no entries. Never write `null`.
> 14. Keep IDs deterministic: use the official card number as `card.id` when unique; use stable ability IDs such as `<card.id>-A1`; use stable program IDs such as `<card.id>-A1-P1`; use a stable unique ID for each printing.
> 15. Return a single valid TOML 1.0 document. Before responding, check its syntax, required fields, enum spellings, nested discriminators, references and completeness status against the specification.

The caller MUST provide the full v1 specification or make it available to the model. The prompt above is a behavioral summary, not a replacement for the detailed schema.

## 21. Versioning and changes

1. `schema_version = 1` identifies this exact source contract.
2. A change that adds/removes/renames a field, changes a field type, changes enum meaning, or changes instruction semantics requires a schema version decision. Do not silently reinterpret existing v1 files.
3. Adding a new opcode or ability representation requires documenting its exact fields and semantics and updating the compiler validator before LLM production use.
4. New optional metadata fields may be added only through a formally versioned schema revision; v1 strict validation rejects unknown keys.
5. The binary catalog has its own format/version contract and MUST NOT assume that its version is identical to `schema_version`.

---

## 22. v1 freeze checklist

- [x] One TOML file per logical card definition.
- [x] Gameplay identity and characteristics explicitly defined.
- [x] Rarity, set, printing variants, artist and thumbnail metadata supported.
- [x] Thumbnail URL provenance and rights status supported.
- [x] Original card text and per-ability source excerpts preserved.
- [x] Translation completeness and unsupported-effect reporting defined.
- [x] Four ability kinds defined.
- [x] Values, conditions, selectors, costs, durations, event patterns and modifiers defined.
- [x] All current instruction opcodes inventoried, with runtime player/card references represented symbolically for later game-side resolution.
- [x] Future JVM compiler and binary catalog responsibilities separated from game-side loading.
- [x] Binary byte layout intentionally deferred to a separate specification.
