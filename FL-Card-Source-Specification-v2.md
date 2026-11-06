# FL Card Source Specification v2

**Status:** contrato normativo de autoria; conformidade completa da VM depende das 13 etapas do cronograma v2  
**Format:** TOML 1.0  
**Scope:** definições de cartas de One Piece Card Game, abilities, programas, efeitos contínuos, regras, metadados e printings  
**Canonical extension:** `.toml`  
**Specification language:** português; identifiers, opcodes and enum values are English

> **Importante sobre o estado do código:** esta versão define o contrato final que o compilador e a VM devem cumprir. Ela não afirma que as 13 extensões descritas aqui já estejam implementadas na branch atual. Até concluir o cronograma `FL-OPCG-VM-Implementation-Roadmap-v2.md`, uma carta que dependa de uma extensão ainda ausente deve permanecer `BLOCKED` para execução. Depois da implementação e da validação de conformidade, este documento passa a ser o contrato de produção para conversão das cartas.

---

## 1. Purpose and pipeline

FL Card Source is the human-authored source format for the FL-OPTCG card catalog. A language model translates trusted raw card records into one TOML file per **logical card definition**. The source compiler validates and compiles the TOML files into a binary catalog. The game-side loader maps the catalog records to the domain models consumed by `GameEngine`, `CardCatalog` and `VirtualMachine`.

```text
Trusted raw card data
    -> LLM translation
    -> FL Card Source TOML files
    -> content compiler
    -> binary card catalog
    -> game-side catalog loader / factory
    -> CardCatalog and GameEngine
    -> VirtualMachine executes card programs
```

The TOML files are the editable source of truth. The binary catalog is generated and MUST be reproducible from identical source files and identical packaged assets.

This document defines the **source-language contract and the gameplay meaning of its constructs**. It does not define the physical byte layout, compression algorithm, encryption, cryptographic key management, file extension, or random-access strategy of the binary catalog. Those belong to a separate binary-format specification.

The LLM's job is translation, not rules invention. It MUST preserve the official card meaning, represent it using this vocabulary, and report any unsupported or ambiguous part instead of silently approximating it.

## 2. Normative language and global rules

The words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

1. Every source file MUST be valid TOML 1.0 encoded as UTF-8.
2. Every file MUST describe exactly one logical card definition.
3. The root MUST contain only the keys defined in this specification.
4. Keys MUST use `snake_case` exactly as shown.
5. Enum values, discriminators and instruction opcodes MUST use the exact uppercase spelling shown in this document.
6. Unknown keys, unknown enum values, duplicate keys, invalid discriminators and duplicate identifiers MUST be rejected. The compiler MUST NOT silently ignore them.
7. Optional fields MUST be omitted when absent. TOML has no `null`; the strings `"null"`, `"none"` and similar placeholders MUST NOT be used to simulate it.
8. Collection fields MUST be present even when empty, unless this document explicitly marks the whole field as optional.
9. Arrays preserve source order. Order is significant for abilities, instructions, branches, costs, options, selected-card lists and printings.
10. The LLM MUST NOT invent card statistics, card text, rarity, printing data, artwork variants, release dates, artists, identifiers from an external provider, or URLs.
11. The LLM MUST preserve official English card text exactly in `source.card_text` and the corresponding `abilities[].source_text`. It MUST NOT paraphrase the source text in those fields.
12. A printed keyword and a printed ability are different things. A keyword that is represented in `card.keywords` MUST NOT be duplicated as a separate ability unless the card has an additional printed effect associated with it.
13. Each independently resolving ability MUST have its own ability definition. A single printed ability with multiple ordered effects MUST normally have one program containing those effects in order.
14. Every selected card, player, zone, target, timing, cost, duration, optional branch and resolution order MUST be represented explicitly when it changes gameplay.
15. An LLM MUST NOT invent an opcode, condition, modifier, selector field or runtime behavior not defined in this version.
16. If an ability requires a feature not yet supported by the VM build used for the catalog, the card MUST be `BLOCKED`, even when the TOML can describe the intended behavior.
17. Metadata and provenance MUST NOT alter game rules or be interpreted as instructions.
18. Gameplay comparisons MUST use effective characteristics when the rules refer to a card's current characteristic. A printed/base characteristic MUST be used only when the rules specifically refer to the printed/base value.
19. A selector with multiple filters uses logical **AND** between different fields. A field containing a set of alternatives uses logical **OR** within that field, unless this document explicitly says otherwise.
20. The LLM MUST not mark a card `READY` merely because its text looks similar to an available opcode. It must account for the complete effect, its timing, its choice semantics, the effect source, and the final zone/state of every affected card.

## 3. Logical card identity and physical printings

Recommended source organization:

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

Directory names are organizational only. They MUST NOT determine a card's gameplay rules, color, rarity or identity.

### 3.1 Logical card versus printing

- `card.id` identifies one logical gameplay definition and MUST be unique across the complete catalog.
- `card.number` is the official card number printed on the card. It is not guaranteed to be globally unique across all products, regions or printings.
- `metadata.printings[]` describes physical printings of one logical card.
- `metadata.printings[].id` identifies one physical printing and MUST be unique within the logical card. It SHOULD be globally unique in the catalog.
- Printings with different artwork, rarity, set, artist or other non-gameplay presentation data MUST share the same `card.id` and ability definitions when their gameplay characteristics and rules text are identical.
- A printing MUST NOT be merged into another logical card when any gameplay-relevant characteristic differs. Gameplay-relevant differences include category, color, type/trait, attribute, base cost, base power, life, counter, keyword, ability text, ability timing or ability effect.
- In particular, if two printings with the same printed number have different types/traits or different rules text, they MUST use separate `card.id` values. A difference in a type/trait can change selector results and is therefore a gameplay difference.
- Do not append an art-variant suffix to `card.id` merely because the artwork differs.

For an initial catalog, `card.id` SHOULD equal `card.number` when that value identifies exactly one gameplay definition. When it does not, use a stable disambiguated ID and preserve the official number separately in `card.number`.

## 4. Root document structure

A valid source file has this structure:

```toml
schema_version = 2

[card]

[metadata]
[[metadata.printings]]

[source]

[translation]

[[abilities]]
```

The exact root keys are:

| Root key | Required | Type | Rule |
|---|---:|---|---|
| `schema_version` | Yes | Integer | MUST equal `2` |
| `card` | Yes | Table | Gameplay definition |
| `metadata` | Yes | Table | Catalog/client metadata |
| `source` | Yes | Table | Provenance and original printed text |
| `translation` | Yes | Table | Translation/runtime readiness |
| `abilities` | No | Array of tables | Omit when the card has no abilities |

A card with no abilities MUST omit all `[[abilities]]` tables. It MUST NOT contain a dummy ability or empty program.

## 5. `[card]`: gameplay definition

`[card]` maps to one logical `CardDefinition`.

| Field | Required | TOML type | Meaning |
|---|---:|---|---|
| `id` | Yes | String | Unique logical card ID |
| `number` | Yes | String | Official printed card number |
| `name` | Yes | String | Official card name in the catalog language |
| `category` | Yes | String enum | `CardCategory` |
| `colors` | Yes | Array of strings | `CardColor`; use `[]` only if the source truly has no color |
| `types` | Yes | Array of strings | Official card types/traits; preserve canonical spelling |
| `attributes` | Yes | Array of strings | `CardAttribute`; use `[]` when not applicable |
| `base_cost` | No | Integer | Printed cost; omit when not applicable/unavailable; preserve `0` |
| `base_power` | No | Integer | Printed power; omit when not applicable/unavailable; preserve `0` |
| `life` | No | Integer | Printed life; omit when not applicable/unavailable; preserve `0` |
| `counter` | No | Integer | Printed counter; omit when absent; do not replace absence with `0` |
| `keywords` | Yes | Array of strings | Recognized `KeywordEffect` values; use `[]` when none |

### 5.1 Closed card enums

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

`types` is a list of strings, not a closed enum. The compiler MUST preserve each value exactly. The catalog MUST use one consistent language and canonical spelling for type names so selector comparisons are reliable.

### 5.2 Characteristics versus abilities

Only recognized `KeywordEffect` values belong in `card.keywords`. Other rules text belongs in `abilities`. Reminder text is not an additional ability. Printed values belong in `[card]`; game-time changes are represented by effects and MUST NOT be precomputed into the card definition.

## 6. `[metadata]`: catalog and client metadata

Metadata is preserved for clients and catalog tools. It is not part of gameplay evaluation.

`[metadata]` has no required scalar fields in v2. It MAY contain zero or more `[[metadata.printings]]` entries.

### 6.1 `[[metadata.printings]]`

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `id` | Yes | String | Stable printing ID, unique within the logical card |
| `set_code` | Yes | String | Official product/set code, e.g. `OP01`, `ST01`, or official promo code |
| `set_name` | No | String | Human-readable product/set name |
| `rarity` | Yes | String | Preserve source rarity code exactly; not a closed enum |
| `variant` | No | String | Preserve source designation such as `STANDARD`, `ALTERNATE_ART`, `MANGA`, `SP` or `PROMO` |
| `release_date` | No | String | ISO `YYYY-MM-DD` if verified |
| `artist` | No | String | Illustrator credit if verified |
| `thumbnail_url` | No | String | Absolute HTTP(S) thumbnail URL |
| `thumbnail_source_url` | No | String | Source page URL for the image |
| `thumbnail_role` | No | String enum | `SAMPLE`, `CUSTOM` or `FINAL` |
| `thumbnail_rights` | No | String enum | `UNKNOWN`, `NOT_REVIEWED`, `OWNED`, `LICENSED` or `PUBLIC_DOMAIN` |

Rules:

1. `rarity` remains an open string so new product/regional rarity codes do not require a schema revision.
2. Rarity MUST NOT be inferred from a card number or image.
3. `thumbnail_url` MUST be an absolute `http://` or `https://` URL. Local paths, relative URLs, `data:` URLs and base64 data are forbidden in TOML.
4. The LLM MUST copy only a URL supplied in the raw record or obtained from a source it actually consulted. It MUST NOT fabricate or construct a URL.
5. If no verified URL exists, omit `thumbnail_url`. A missing thumbnail MUST NOT block gameplay compilation.
6. If `thumbnail_url` exists and `thumbnail_role` is omitted, consumers MUST treat it as `SAMPLE` for display labeling only. This does not establish redistribution rights.
7. Unknown image rights MUST be marked `UNKNOWN` or `NOT_REVIEWED`; never guessed.
8. Each printing MAY have its own thumbnail. Packaged image bytes MUST be associated with the exact printing ID.
9. Different artwork or rarity produces separate printing records, not duplicate gameplay definitions.
10. A client MAY choose a default printing for display; that choice is outside gameplay rules.

A thumbnail URL is a source reference, not image bytes. The compiler retrieves and validates the asset, records media type/digest and packages its bytes. TOML MUST NOT contain image bytes.

## 7. `[source]`: provenance and original text

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `provider` | No | String | Raw-data provider/database name |
| `record_url` | No | String | URL of the source card record |
| `language` | Yes | String | BCP-47 language tag for `card_text`, e.g. `en` or `ja` |
| `card_text` | Yes | String | Complete printed rules/flavor text from the trusted source |
| `notes` | No | String | Provenance notes only; never interpreted as rules |

Every ability table MUST include `source_text`, the exact printed excerpt represented by that ability. The excerpt is for traceability and is not executed.

The LLM MUST NOT silently paraphrase `source.card_text` or `abilities[].source_text`. If the raw source is incomplete, describe that limitation in `source.notes` and set `translation.status = "BLOCKED"` when it prevents a reliable interpretation.

## 8. `[translation]`: translation and runtime readiness

| Field | Required | TOML type | Rule |
|---|---:|---|---|
| `status` | Yes | String enum | `READY` or `BLOCKED` |
| `unsupported` | Yes | Array of strings | MUST be `[]` for `READY`; otherwise identify exact unresolved text and reason |

`READY` means all of the following are true:

1. The complete printed gameplay meaning has been translated.
2. Every ability is classified correctly and has correct timing, cost, condition, targets, choices, duration and resolution order.
3. Every operation is represented by the v2 vocabulary without invented semantics.
4. Every required v2 feature is implemented by the VM build that will execute this catalog.
5. The file contains no unresolved ambiguity that could change game state or legal actions.

`BLOCKED` means the file may be retained as a reviewed source draft but MUST NOT enter a production executable catalog. The compiler MAY parse it for diagnostics, but MUST mark it non-executable.

The LLM MUST use `BLOCKED` when any printed rules text cannot be represented, when a required runtime feature is not implemented, when official meaning is materially ambiguous, or when required source data is missing. The `unsupported` array MUST identify the exact ability/phrase and the reason. Harmless reminder text MUST NOT be listed as unsupported.

A card MUST NOT be marked `READY` merely because all TOML fields validate. TOML validity, semantic completeness and runtime support are separate requirements.

## 9. `[[abilities]]`: ability definitions

Every `[[abilities]]` table represents one independently classified ability or continuous/replacement effect.

Common fields:

| Field | Required | Rule |
|---|---:|---|
| `id` | Yes | Stable ability ID, unique within `card.id` |
| `kind` | Yes | `AUTO`, `ACTIVATED`, `PERMANENT` or `REPLACEMENT` |
| `source_text` | Yes | Exact printed excerpt represented by the ability |

Ability IDs SHOULD be deterministic, e.g. `${card.id}-A1`. Program IDs SHOULD be deterministic, e.g. `${card.id}-A1-P1`. Card, printing, ability, program, replacement and option IDs MUST be non-empty, contain no whitespace and remain stable across recompilations. Variable IDs MUST be non-empty and unique within their program scope.

One printed ability with several effects normally uses one ordered program. Separate abilities are required for independent triggers, activation timings or continuous/replacement effects.

### 9.1 `ACTIVATED`

Required fields: `id`, `kind = "ACTIVATED"`, `timing`, `cost`, `usage_limit`, `program`, `source_text`. Optional field: `condition`.

`timing` MUST be a value from section 10. `cost` MUST use section 13.1. `program` MUST use section 14.

### 9.2 `AUTO`

Required fields: `id`, `kind = "AUTO"`, `trigger`, `usage_limit`, `program`, `source_text`. Optional field: `condition`.

`trigger` is a `GameEventPattern` (section 15). Use `AUTO` when the ability is scheduled by an actual game event. Do not classify an ability as `AUTO` merely because its English text begins with a timing phrase; follow the game's timing semantics.

For an `[On K.O.]` ability that triggers when **this card** is K.O.'d, the pattern MUST constrain the event source to the ability's own source card, using `source = { same_as_source = true }`. A broad selector such as `owner = "SELF"` is not equivalent: it would also match another friendly Character being K.O.'d.

### 9.3 `PERMANENT`

Required fields: `id`, `kind = "PERMANENT"`, `modifier`, `source_text`. Optional fields: `condition`, `selector`.

A permanent ability is a continuously evaluated rule effect, not a one-shot `Program`. Its modifier and dynamic values are defined in section 16.

### 9.4 `REPLACEMENT`

Required fields: `id`, `kind = "REPLACEMENT"`, `replacement`, `source_text`.

`replacement` is a table with:

| Field | Required | Type |
|---|---:|---|
| `id` | Yes | Stable effect ID |
| `pattern` | Yes | `GameEventPattern` |
| `condition` | No | `Condition` |
| `program` | Yes | `Program` |
| `priority` | Yes | Integer |
| `duration` | Yes | `Duration` |

Use only priority differences established by rules semantics. Do not invent priority to influence ordering.

## 10. Enum vocabulary

Enum values are case-sensitive and MUST match exactly.

### 10.1 Timing and usage

`ActivationTiming`:

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

`UsageLimit`:

- `UNLIMITED`
- `ONCE_PER_TURN`

Specific mappings:

- Character/Stage `[On Play]` effects use `ACTIVATED` with `timing = "ON_PLAY"` when the runtime schedules them from the card-play operation.
- Event `[Main]` effects use `ACTIVATED` with `timing = "ACTIVATE_MAIN"`. They MUST NOT be mislabeled as `ON_PLAY`.
- Event `[Counter]` effects use `ACTIVATED` with `timing = "COUNTER_STEP"`.
- A card's `[Trigger]` ability uses `ACTIVATED` with `timing = "ON_TRIGGER"`. The runtime's Life-damage/Trigger flow schedules it when the Life card is revealed and the Trigger is activated. Do not invent `TRIGGER_ACTIVATED` or `LIFE_CARD_REVEALED` event opcodes for this purpose.
- When a Trigger says to activate this card's `[Main]` or `[Counter]` effect, represent the Trigger as its own `ON_TRIGGER` ability and place the same effect instructions in that program. The Main/Counter ability remains separately defined with its real timing. Do not assume that a normal `ACTIVATE_ABILITY` call can invoke an ability from every zone or timing window.

### 10.2 Player and zone vocabulary

`PlayerRef`:

- `SELF` — controller of the current ability/effect context
- `OPPONENT` — the other player relative to `SELF`
- `TURN_PLAYER` — current turn player
- `NON_TURN_PLAYER` — player who is not the current turn player

A `PlayerRef` is symbolic. It MUST NOT contain a runtime `PlayerId` or a literal player UUID/string.

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

### 10.3 Numeric and comparison vocabulary

`NumericCharacteristic`:

- `POWER`
- `BASE_POWER`
- `COST`
- `BASE_COST`
- `LIFE`
- `COUNTER`
- `ATTACHED_DON_COUNT`

`ATTACHED_DON_COUNT` is the number of DON!! cards currently attached to the referenced card. It is a live count, not a printed characteristic and not the sum of DON!! power bonuses.

`NumericOperation`: `ADD`, `SUBTRACT`, `SET`.

`ArithmeticOperator`: `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, `MIN`, `MAX`.

`ComparisonOperator`: `EQUAL`, `NOT_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`.

Integer division in `DIVIDE` truncates toward zero. For non-negative card-count expressions this is equivalent to floor division.

### 10.4 Other enum vocabulary

`AbilityType`: `AUTO`, `ACTIVATED`, `PERMANENT`, `REPLACEMENT`.

`RuleAction`:

- `PLAY_CARD`
- `ACTIVATE_ABILITY`
- `DECLARE_ATTACK`
- `ACTIVATE_BLOCKER`
- `USE_COUNTER`
- `ATTACH_DON`
- `ATTACK_ACTIVE_CHARACTER`
- `BE_KNOCKED_OUT`

`DECLARE_ATTACK` is a general permission to declare an attack. `ATTACK_ACTIVE_CHARACTER` is a specific permission to attack an opponent's active Character; a permission with this action MUST declare a `target` selector. It overrides exclusively the `TARGET_NOT_RESTED` restriction for a matching target; it MUST NOT allow attacks against own cards, ignore zone restrictions, bypass Rush timing, or ignore any other applicable restriction.

`KnockOutCause`: `BATTLE`, `EFFECT`, `ANY`.

`KeywordOperation`: `ADD`, `REMOVE`.

`CardProperty`: `NAME`, `COLOR`, `TYPE`, `ATTRIBUTE`, `KEYWORDS`.

`CardPropertyComparisonOperator`: `EQUAL`, `NOT_EQUAL`.

For set-valued properties (`COLOR`, `TYPE`, `ATTRIBUTE`, `KEYWORDS`), `EQUAL` means the two effective sets are identical and `NOT_EQUAL` means they are not identical. It does not mean “has no member in common”. The translator MUST use only the comparison that matches the official card meaning; it MUST NOT guess when a multicolor interaction makes the phrase ambiguous.

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

Do not add a new event type when an existing event plus a selector already identifies the required event.

## 11. Values and conditions

All expression/condition objects use a `kind` discriminator. Fields not defined for a selected kind are forbidden.

### 11.1 `ValueExpr`

| `kind` | Required fields | Meaning |
|---|---|---|
| `NUMBER` | `value` integer | Constant integer |
| `COUNT` | `selector` | Number of cards matching a `CardSelector` |
| `VARIABLE` | `variable` | Integer stored in a program variable |
| `ARITHMETIC` | `operator`, `left`, `right` | Integer arithmetic |
| `CHARACTERISTIC` | `selector`, `characteristic` | Read one numeric characteristic from exactly one selected card |
| `CURRENT_CARD` | `characteristic` | Read the current candidate card's characteristic; valid only inside per-card selector conditions |
| `CARD_CHARACTERISTIC` | `card`, `characteristic` | Read a numeric characteristic from one referenced card |

`CHARACTERISTIC` MUST resolve to exactly one card. Zero or multiple matches are evaluation errors.

`CURRENT_CARD` is valid only in a condition evaluated for an individual candidate, such as `CardSelector.conditions`. The candidate is the current card being checked by the selector.

`CARD_CHARACTERISTIC.card` is a `CardReference`. It MUST resolve to exactly one card. The characteristic is read from the current effective game state. For `ATTACHED_DON_COUNT`, it counts the card's currently attached DON!! IDs. For `COST` and `POWER`, it reads the effective value, including applicable continuous modifiers.

`VARIABLE` MUST resolve to an integer-valued variable. A source compiler MUST reject statically evident type mismatches. A missing variable is a runtime/program validation error; it MUST NOT silently become zero.

Examples:

```toml
# DON!! count on the ability's source card is at least 2.
{ kind = "COMPARISON",
  left = { kind = "CARD_CHARACTERISTIC", card = { variable_id = "SOURCE_CARD" }, characteristic = "ATTACHED_DON_COUNT" },
  operator = "GREATER_THAN_OR_EQUAL",
  right = { kind = "NUMBER", value = 2 } }
```

```toml
# Candidate card has effective cost 3 or less.
{ kind = "COMPARISON",
  left = { kind = "CURRENT_CARD", characteristic = "COST" },
  operator = "LESS_THAN_OR_EQUAL",
  right = { kind = "NUMBER", value = 3 } }
```

### 11.2 `Condition`

| `kind` | Required fields | Meaning |
|---|---|---|
| `COMPARISON` | `left`, `operator`, `right` | Compare two integer values |
| `ALL` | `conditions` | All nested conditions must be true |
| `ANY` | `conditions` | At least one nested condition must be true |
| `NOT` | `condition` | Negate one condition |
| `EXISTS` | `selector` | At least one card matches the selector |
| `CARD_PROPERTY_COMPARISON` | `left_card`, `property`, `operator`, `right_card` | Compare one effective property of two referenced cards |

`ALL` and `ANY` MUST contain at least one condition. `COMPARISON` compares integer expressions. Use `EXISTS` for existence checks.

`CARD_PROPERTY_COMPARISON` uses `CardReference` for both cards and compares their effective values for the selected `CardProperty`. `CURRENT_CARD` MAY be used as one reference only while the condition is being evaluated for a selector candidate. Other references MUST resolve through the current program context/variables. For set-valued properties, apply the semantics defined in section 10.4.

Example for OP01-002-style cross-card color comparison:

```toml
{ kind = "CARD_PROPERTY_COMPARISON",
  left_card = { variable_id = "CURRENT_CARD" },
  property = "COLOR",
  operator = "NOT_EQUAL",
  right_card = { variable_id = "returned_character" } }
```

### 11.3 Your Turn / Opponent's Turn

The current selector/condition model can express turn ownership without a new special event or a new condition opcode. A player's Leader remains in that player's Leader area, so use the Leader's effective controller relation as the turn test:

```toml
# Your Turn
condition = { kind = "EXISTS", selector = {
  owner = "SELF", controller = "TURN_PLAYER",
  zones = ["LEADER"], categories = ["LEADER"]
} }
```

```toml
# Opponent's Turn
condition = { kind = "EXISTS", selector = {
  owner = "SELF", controller = "NON_TURN_PLAYER",
  zones = ["LEADER"], categories = ["LEADER"]
} }
```

Do not use `owner = "TURN_PLAYER"` as a replacement for `controller = "TURN_PLAYER"`; ownership and control are different concepts.

## 12. `CardSelector`

A selector is a table, not a discriminated union. All fields are optional. Omitted filters impose no restriction.

| Field | Type | Meaning |
|---|---|---|
| `owner` | `PlayerRef` | Required owner relation |
| `controller` | `PlayerRef` | Required effective-controller relation |
| `zones` | Array of `ZoneType` | Allowed zones; omitted/empty means all zones supported by the selector evaluator |
| `categories` | Array of `CardCategory` | Allowed categories |
| `colors` | Array of `CardColor` | Match if the card has at least one listed color |
| `types` | Array of strings | Match if the card has at least one listed type |
| `attributes` | Array of `CardAttribute` | Match if the card has at least one listed attribute |
| `keywords` | Array of `KeywordEffect` | Match if the card currently has at least one listed effective keyword |
| `orientation` | `ACTIVE` or `RESTED` | Required current orientation; omit for no orientation filter |
| `name` | String | Exact effective primary name or active name alias |
| `same_as_source` | Boolean | When true, only the exact card instance that owns the executing ability/context matches |
| `exclude_source` | Boolean | When true, the exact source card instance is excluded |
| `conditions` | Array of `Condition` | Additional per-candidate conditions; all must pass |

`same_as_source` and `exclude_source` MUST NOT both be true. If `same_as_source = true` and no source card exists in the current context, the selector matches nothing. If `exclude_source = true` and no source card exists, no candidate is excluded.

`orientation` filters the current `CardInstance` orientation. It does not infer orientation from a card's category. Omit the field when orientation is irrelevant. `NOT_APPLICABLE` is not a valid selector filter; use omission for no filter.

`keywords` uses effective keywords, including keywords gained or removed by active effects. It uses OR semantics within the array: a card matches when it has at least one listed keyword.

`conditions` are evaluated separately for each candidate. During each evaluation `CURRENT_CARD` refers to that exact candidate. All conditions must pass.

The `player` field of choice instructions identifies who makes the choice; it does not replace `owner` or `controller`. For movement instructions, the destination identifies the destination owner/player as specified by that instruction.

**Do not add `cost_min`, `cost_max`, `power_min` or similar shortcut fields.** Cost and power filters MUST use `conditions` with `CURRENT_CARD`, so comparisons use effective characteristics and remain extensible.

Example: opponent Character with effective cost 3 or less, currently rested:

```toml
{ owner = "OPPONENT", zones = ["CHARACTER"], categories = ["CHARACTER"], orientation = "RESTED",
  conditions = [{ kind = "COMPARISON",
    left = { kind = "CURRENT_CARD", characteristic = "COST" },
    operator = "LESS_THAN_OR_EQUAL",
    right = { kind = "NUMBER", value = 3 } }] }
```

## 13. Costs and durations

### 13.1 `Cost`

| `kind` | Required fields | Meaning |
|---|---|---|
| `NO_COST` | None | No activation cost |
| `COMBINED` | `costs` array of Cost | Pay every nested cost |
| `REST_SOURCE` | None | Rest the source card |
| `REST_DON` | `amount` ValueExpr | Rest the specified number of active DON!! |
| `RETURN_DON` | `amount` ValueExpr | Return the specified number of DON!! |
| `TRASH_CARDS` | `selector`, `amount` | Trash the specified number of matching cards |

`COMBINED` MUST contain at least one nested cost. A cost already declared in `ACTIVATED.cost` MUST NOT be duplicated in the program body.

Do not force every sentence containing a colon into `Cost`. An optional choice/action that occurs as part of effect resolution MAY be represented by `OPTIONAL` plus ordinary instructions. This distinction is important for card text such as “You may … : [effect]”.

The following patterns are supported by existing program operations and MUST NOT be represented using invented `Cost` kinds:

- **Life to hand:** use `LOOK_AT_TOP` with `zone = "LIFE"`, then `MOVE_CARDS` to `HAND`. This pattern selects the top Life card, not an arbitrary hidden Life card.
- **Hand to bottom of deck:** use `SELECT_CARDS` from `HAND`, then `MOVE_CARDS` to the same player's `DECK` with `placement = "BOTTOM"`.
- **Rest N Characters:** use `SELECT_CARDS` with the appropriate Character selector, then `REST_CARDS` on the selected variable.
- Enclose the optional payment/action and its dependent effect in the appropriate `OPTIONAL` block when the printed wording makes the whole action optional.

When the printed text says to return selected cards to **their owners' hands**, use `RETURN_CARDS_TO_OWNER_HAND` (section 14.4). `MOVE_CARDS` with a fixed `destination.player = "SELF"` is not equivalent when a selected card can be controlled by one player but owned by the other.

### 13.2 `Duration`

| `kind` | Required fields | Meaning |
|---|---|---|
| `DURING_BATTLE` | None | Until the current battle ends |
| `UNTIL_END_OF_TURN` | None | Until the current turn ends |
| `UNTIL_END_OF_PHASE` | None | Until the current phase ends |
| `UNTIL_EVENT` | `pattern` GameEventPattern | Until a matching event occurs |
| `WHILE_SOURCE_VALID` | None | While the source card remains valid/in its required zone |
| `WHILE_CONDITION` | `condition` Condition | While the condition remains true |
| `UNTIL_END_OF_NEXT_OPPONENT_TURN` | None | Until the end of the first opponent turn that starts after this duration is applied |
| `PERMANENT` | None | No scheduled expiration |

`UNTIL_END_OF_NEXT_OPPONENT_TURN` is not the same as `END_OF_OPPONENT_TURN` or a broad `UNTIL_EVENT(TURN_ENDED)`. The runtime MUST:

1. Identify the opponent relative to the effect controller when the duration is applied.
2. Ignore an opponent turn that was already in progress when the duration was applied.
3. Arm the duration when that opponent's next `TURN_STARTED` event occurs.
4. Expire the effect at the matching `TURN_ENDED` event for that same turn.

Do not replace a specific printed duration with `PERMANENT` or with a broad event pattern.

## 14. Programs and instructions

### 14.1 `Program`

```toml
program = { id = "CARD-A1-P1", instructions = [
  { op = "DRAW", player = "SELF", amount = { kind = "NUMBER", value = 1 } }
] }
```

`id` and `instructions` are required. Instructions execute in array order. An executable ability MUST have at least one instruction unless official semantics explicitly require a no-op. A no-op MUST NOT hide untranslated text.

Every instruction is an inline TOML table with a required `op` discriminator. Nested instruction arrays use the same representation recursively.

### 14.2 Control-flow instructions

| `op` | Required fields | Meaning |
|---|---|---|
| `SEQUENCE` | `instructions` | Execute nested instructions in order |
| `CONDITIONAL` | `condition`, `if_true`, `if_false` | Execute exactly one branch |
| `OPTIONAL` | `instructions` | Ask the resolving player whether to execute the nested block |
| `FOR_EACH` | `source`, `item`, `instructions` | Execute once per card in a card-list variable; bind the current card to `item` |
| `REPEAT_WHILE` | `condition`, `instructions` | Repeat while true; VM instruction limit still applies |

`source`, `item` and other variable names are strings mapped to `VariableId`.

### 14.3 Instruction catalog

The table is normative. Parameters are TOML names. Symbolic references such as `PlayerRef` and `CardReference` are resolved by the runtime; the LLM MUST NOT write literal runtime IDs.

| `op` | Required fields | Optional fields |
|---|---|---|
| `SELECT_CARDS` | `selector`, `player`, `minimum`, `maximum`, `destination` | — |
| `SELECT_FROM_VARIABLE` | `source`, `selector`, `player`, `minimum`, `maximum`, `destination` | `remainder_destination` |
| `COUNT_CARDS` | `selector`, `destination` | — |
| `LOOK_AT_TOP` | `player`, `zone`, `amount`, `destination` | — |
| `REVEAL_CARDS` | `source`, `viewers` | — |
| `CHOOSE_OPTION` | `player`, `options`, `destination` | — |
| `CHOOSE_NUMBER` | `player`, `minimum`, `maximum`, `destination` | — |
| `ORDER_CARDS` | `source`, `player`, `destination` | — |
| `CHECK_CONDITION` | `condition`, `destination` | — |
| `DRAW` | `player`, `amount` | — |
| `MOVE_CARDS` | `source`, `destination`, `placement` | — |
| `RETURN_CARDS_TO_OWNER_HAND` | `source` | — |
| `TRASH_FROM_HAND` | `player`, `selector`, `amount` | — |
| `KNOCK_OUT` | `source` | `effect_id` |
| `PLAY_CARD` | `card`, `player`, `payment` | — |
| `SHUFFLE_ZONE` | `player`, `zone` | — |
| `DEAL_DAMAGE` | `player`, `amount` | `effect_id` |
| `SET_LIFE` | `player`, `source`, `position` | — |
| `PREVENT_DAMAGE` | `player`, `amount`, `duration` | — |
| `ADD_DON` | `player`, `amount` | `destination` |
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

### 14.4 Instruction-specific structures and semantics

**`SELECT_FROM_VARIABLE`**

```toml
{ op = "SELECT_FROM_VARIABLE",
  source = "looked_cards",
  selector = { categories = ["CHARACTER"], types = ["Straw Hat Crew"] },
  player = "SELF",
  minimum = { kind = "NUMBER", value = 0 },
  maximum = { kind = "NUMBER", value = 1 },
  destination = "chosen_cards",
  remainder_destination = "remaining_cards" }
```

- `source` MUST contain a `CardIdsValue` produced earlier in the same program.
- Only cards present in `source` are candidates. The selector further filters those candidates; it MUST NOT search other game zones.
- `destination` receives the selected subset in the original source order unless the player explicitly orders it later.
- When `remainder_destination` is supplied, it receives every source card not selected, in the original source order. The two outputs MUST be disjoint and their union MUST equal the original source list.
- When `remainder_destination` is omitted, unselected source cards remain only in the original source variable; they are not moved or discarded.
- The selected result and remainder result MUST be written to the frame variables before the next instruction executes.

This instruction supports “look at N cards, choose a subset” and also supports splitting one looked-at group into different destinations. To place some cards on top and the remainder on the bottom **in any order**, select the top group with `remainder_destination`, run `ORDER_CARDS` independently on both groups, then move the ordered groups with `TOP` and `BOTTOM` respectively. Do not add a separate one-off “top or bottom” opcode.

**`MOVE_CARDS.destination`**

A table with:

- `player`: required `PlayerRef` identifying the destination player's zone;
- `zone`: required `ZoneType`.

`ATTACHED_DON` is not a valid destination for `MOVE_CARDS`; use `ATTACH_DON`.

For a multi-card move with `placement = "TOP"` or `"BOTTOM"`, the VM MUST preserve the explicit source-list order in the resulting destination zone. It MUST NOT reverse a user's chosen order as a side effect of sequential insertion.

**`RETURN_CARDS_TO_OWNER_HAND`**

- `source` is a `CardValue` or `CardIdsValue` variable.
- Each selected card moves to the `HAND` zone belonging to that card's actual `ownerId`, not its current controller and not automatically `SELF`.
- The instruction preserves the identity of every selected card and emits the normal card-movement events.
- This instruction is the required representation for text that says to return a selected card to its owner's hand.

**`LOOK_AT_TOP.zone`** MUST be one of `DECK`, `LIFE` or `DON_DECK`.

**`SHUFFLE_ZONE.zone`** MUST be one of `DECK`, `DON_DECK`, `HAND`, `TRASH` or `LIFE`.

**`CHOOSE_OPTION.options`** MUST contain at least one unique option ID.

Numeric minimum/maximum pairs MUST be non-negative when constant, and a constant maximum MUST NOT be less than a constant minimum. All amounts MUST use valid integer-valued expressions.

**`PLAY_CARD`**

- `card` is a `CardReference` to exactly one card currently in the acting player's hand.
- `player` identifies the player playing it.
- `payment` is defined below.
- `PLAY_CARD` enters a Character or Stage active by default, and an Event resolves according to Event rules.
- When the printed effect requires the played Character/Stage to be rested, execute `PLAY_CARD`, then immediately execute `SET_CARD_STATE` on the same card variable with `orientation = "RESTED"`. The VM MUST complete the remaining instructions in the current program before executing the follow-up programs generated by `PLAY_CARD`, so the state change is visible when those follow-up programs resolve. Do not invent a `PLAY_CARD.orientation` field.

`payment` is a table with:

- `rested_don`: optional variable name containing the DON!! IDs used for payment; omit when no DON!! payment is required;
- `additional_costs`: required array of `Cost`; use `[]` when none.

`card` for `PLAY_CARD`, `ATTACH_DON` and `DETACH_DON` is a `CardReference`; literal instance IDs are forbidden.

**`ADD_DON`**

- `player` identifies the player receiving DON!! from their DON!! deck into their Cost area.
- `amount` is a `ValueExpr`.
- When `destination` is present, it receives a `CardIdsValue` with the exact DON!! instance IDs added by this instruction, in the order added.
- A later instruction that must operate on those exact DON!! cards MUST use that variable; it MUST NOT select an arbitrary DON!! from the Cost area.

**`DonSelector`** (used by `REST_DON` and `RETURN_DON`):

| Field | Required | Type | Meaning |
|---|---:|---|---|
| `zones` | No | Array of `ZoneType` | Candidate DON!! zones |
| `orientation` | No | `ACTIVE` or `RESTED` | Candidate orientation |
| `source` | No | Variable name | Restrict candidates to exact DON!! IDs in this card-list variable |

If `source` is present, only DON!! IDs in that variable may be affected. `zones` and `orientation` further filter those IDs.

**`ATTACH_DON`**

- `card` references exactly one host card.
- `source` is a variable containing the exact DON!! card IDs to attach.
- Each ID MUST identify a DON!! currently in the Cost area and eligible to be attached by the target player. The instruction MUST preserve identities and MUST reject/skip invalid IDs according to the engine's normal instruction-failure policy; it MUST NOT substitute different DON!! cards.

**`REST_DON`** selects only cards matching its `DonSelector`. When the selector has `source`, it affects only those exact IDs.

**`result` for `SET_GAME_RESULT`** is a table with optional `winner` (`PlayerRef`), required `losers` array of `PlayerRef`, and required `reason` (`GameEndReason`).

### 14.5 Variable contract

1. Variables are local to one program execution frame.
2. Variable names MUST be non-empty strings and SHOULD use lowercase `snake_case`.
3. A destination instruction creates/assigns its variable. A variable MUST be assigned before it is read.
4. The compiler MUST validate statically determinable existence and value types.
5. `SELECT_CARDS`, `SELECT_FROM_VARIABLE`, `LOOK_AT_TOP` and `ORDER_CARDS` produce card-list values.
6. `COUNT_CARDS` and `CHOOSE_NUMBER` produce integer values.
7. `CHOOSE_OPTION` produces an option value.
8. `CHECK_CONDITION` produces a boolean value.
9. `ADD_DON` produces a card-list value only when `destination` is supplied.
10. `FOR_EACH.item` is bound to one `CardValue` for the nested block.
11. `SOURCE_CARD` is a reserved symbolic `CardReference` for the exact card instance whose ability is executing. The runtime MUST resolve it from `EffectContext.sourceCardId`.
12. `CURRENT_CARD` is a reserved symbolic `CardReference` valid only while a selector condition is evaluated for one candidate. It MUST resolve to that candidate's exact card instance.
13. `SOURCE_CARD` and `CURRENT_CARD` are reference sentinels, not normal mutable variables. A program MUST NOT overwrite them.
14. A card reference variable used as a single-card reference MUST contain exactly one card. A card-list variable MUST NOT be silently converted to an arbitrary first card.

## 15. Event patterns

`GameEventPattern` is a table:

| Field | Required | Type | Meaning |
|---|---:|---|---|
| `event_types` | Yes | Non-empty array of `GameEventType` | Event types that match |
| `source` | No | `CardSelector` | Selector applied to the event's source card |
| `target` | No | `CardSelector` | Selector applied to the event's target card(s) |
| `target_player` | No | `PlayerRef` | Required target player relation |
| `player` | No | `PlayerRef` | Required event player relation |

Example: opponent plays an Event:

```toml
trigger = { event_types = ["CARD_PLAYED"],
  source = { owner = "OPPONENT", categories = ["EVENT"] },
  player = "OPPONENT" }
```

The `source` selector examines the actual event source card. For an ability that must trigger only when its own source card is K.O.'d, use `source = { same_as_source = true }`. A selector such as `owner = "SELF"` alone is too broad.

Do not introduce new event types when existing `CARD_PLAYED`, `CARD_KNOCKED_OUT`, `CARD_REVEALED`, `CARD_MOVED`, `TURN_STARTED` or `TURN_ENDED` plus an appropriate selector/pattern already expresses the event.

## 16. Modifiers and nested rule structures

### 16.1 `Modifier`

Every modifier is a table with a required `kind`.

| `kind` | Required fields | Meaning |
|---|---|---|
| `CHARACTERISTIC` | `characteristic`, `operation`, `value` | Numeric characteristic modification |
| `PROPERTY` | `property`, `value` | Replace a card property |
| `KEYWORD` | `keyword`, `operation` | Add/remove a keyword |
| `NAME_ALIAS` | `name` | Add a rules-recognized alternate name without removing the original name |
| `CONTROL` | `player` | Change controller to a symbolic player |
| `DAMAGE_PREVENTION` | `player`, `amount` | Damage prevention for a player |
| `PERMISSION` | `permission` | Rule permission |
| `RESTRICTION` | `restriction` | Rule restriction |
| `EFFECT_NEGATION` | `scope` | Negate matching effects |
| `ACTIVATION_COST` | `selector`, `cost` | Modify activation costs |
| `DECK_RULE_EFFECT` | `deck_rule` | Modify a deck/game-format rule |
| `REPLACEMENT` | `definition` | Replacement definition |
| `ABILITY` | `ability`, `add` | Grant/remove an ability |

`NAME_ALIAS` is additive. It MUST NOT overwrite `CardDefinition.name` or remove the card's existing/effective primary name. A selector `name = "..."` matches the effective primary name or any active rules-recognized alias.

### 16.2 Dynamic values in continuous modifiers

A `PERMANENT` ability's modifier is continuously evaluated. If its `CHARACTERISTIC.value` contains `COUNT`, `ARITHMETIC`, `CARD_CHARACTERISTIC` or another state-dependent expression, the VM MUST recompute the value from the current game state whenever effective characteristics are calculated. The value MUST NOT be frozen when the permanent ability first enters play.

This includes expressions such as “+1000 power for every card in your hand” and “+1000 power for every 2 Events in your trash”. Use `COUNT` and `ARITHMETIC`; do not add a per-card custom opcode or a `dynamic = true` workaround field.

By contrast, `MODIFY_CHARACTERISTIC` and `SET_BASE_CHARACTERISTIC` instructions inside a resolving program create effects at resolution time. Their expression values are evaluated when the instruction resolves, and the resulting modification follows its declared `duration`. Do not continuously recalculate a one-shot instruction unless the card's printed rules explicitly define a continuous effect.

### 16.3 `PropertyValue`

`value` for `PROPERTY` is a table with one of these `kind` values:

- `STRING` with `value` string;
- `COLORS` with `value` array of `CardColor`;
- `TYPES` with `value` array of strings;
- `ATTRIBUTES` with `value` array of `CardAttribute`;
- `KEYWORDS` with `value` array of `KeywordEffect`.

`PROPERTY` remains a replacement operation. Use `NAME_ALIAS` for additive alternate names.

### 16.4 `RulePermission`

`permission` table:

- `action` — required `RuleAction`;
- `subject` — optional `CardSelector` restricting the card performing the action;
- `target` — optional `CardSelector` restricting the action target;
- `condition` — optional `Condition`.

For `DECLARE_ATTACK`, `subject` identifies the attacking card and `target` identifies the target card. `DECLARE_ATTACK` is a general permission to declare an attack.

For `ATTACK_ACTIVE_CHARACTER`, `subject` identifies the attacking card and `target` identifies the opponent's active Character(s). A permission with this action MUST declare a `target` selector; the compiler MUST reject a permission that omits it. It overrides exclusively the `TARGET_NOT_RESTED` restriction for a matching target. It MUST NOT allow attacks against own cards, ignore zone restrictions, bypass Rush timing, or ignore any other applicable restriction.

Example: permission to attack opponent's active Characters:

```toml
{ kind = "PERMISSION", permission = {
  action = "ATTACK_ACTIVE_CHARACTER",
  subject = { same_as_source = true },
  target = { owner = "OPPONENT", categories = ["CHARACTER"], zones = ["CHARACTER"], orientation = "ACTIVE" }
} }
```

### 16.5 `RuleRestriction`

`restriction` table:

- `action` — required `RuleAction`;
- `subject` — optional `CardSelector` restricting the card affected by the action;
- `maximum_uses` — optional `ValueExpr`;
- `condition` — optional `Condition`;
- `cause` — optional `KnockOutCause`, used with `BE_KNOCKED_OUT`;
- `source` — optional `CardSelector` matching the card or source responsible for the action, used with `BE_KNOCKED_OUT`.

For `BE_KNOCKED_OUT`, `subject` identifies the card that would be K.O.'d. `cause = "BATTLE"` restricts the protection to battle K.O.; `cause = "EFFECT"` restricts it to effect K.O.; `ANY` or omission applies to both. `source` filters the card causing the K.O. In battle this is the attacker; for effect K.O. it is the effect's source card when one exists.

Example: own Characters of a type cannot be K.O.'d in battle:

```toml
{ kind = "RESTRICTION", restriction = {
  action = "BE_KNOCKED_OUT",
  subject = { owner = "SELF", categories = ["CHARACTER"], types = ["Kurozumi Clan"] },
  cause = "BATTLE"
} }
```

Example: prevent battle K.O. only from an attacker with a specific attribute:

```toml
{ kind = "RESTRICTION", restriction = {
  action = "BE_KNOCKED_OUT",
  subject = { same_as_source = true },
  cause = "BATTLE",
  source = { attributes = ["SLASH"] }
} }
```

A K.O. restriction MUST be checked before emitting the final K.O./move-to-Trash result. Merely removing a K.O. event after the target has already moved is not equivalent.

### 16.6 `AbilitySelector`

A table with optional fields:

- `card_selector`: `CardSelector`;
- `ability_id`: string;
- `ability_type`: `AbilityType`;
- `timing`: `ActivationTiming`.

At least one field SHOULD be present. Do not create an unconstrained selector unless the printed effect genuinely applies to every matching ability.

### 16.7 `DeckRuleModifier`

A table with:

- `property`: required `DeckRuleProperty`;
- `numeric_operation`: optional `ADD`, `SUBTRACT` or `SET`;
- `numeric_value`: optional `ValueExpr`;
- `allowed_colors`: optional array of `CardColor`;
- `card_number`: optional string.

Only fields relevant to the selected property should be populated.

### 16.8 `EffectScope`

Use one of:

- `{ kind = "ALL" }`
- `{ kind = "ABILITY", ability_id = "..." }`
- `{ kind = "CARD", card = { variable_id = "selected_card" } }`
- `{ kind = "CURRENT_BATTLE" }`

For `CARD`, `card` MUST be a dynamic `CardReference`, never a literal runtime instance ID.

### 16.9 `CardReference`

A `CardReference` identifies one card bound in the current program/context. The source format accepts only:

```toml
{ variable_id = "selected_card" }
{ variable_id = "SOURCE_CARD" }
{ variable_id = "CURRENT_CARD" } # only in a per-candidate selector condition
```

`variable_id` MUST resolve to exactly one `CardValue`. `SOURCE_CARD` and `CURRENT_CARD` follow the reserved-reference rules in section 14.5. Literal `instance_id` values MUST NOT appear in reusable card source files.

## 17. Canonical encoding patterns for common card text

This section is prescriptive. When a printed effect matches one of these patterns, use the specified construct rather than inventing another representation.

### 17.1 `[DON!! xN]`

Represent “while this card has N or more DON!! attached” using `CARD_CHARACTERISTIC` with `characteristic = "ATTACHED_DON_COUNT"` and `card = SOURCE_CARD` (section 11.1). Do not count DON!! in the player's Cost area; only DON!! attached to the exact source card count.

### 17.2 Cost and power filters

Use `CardSelector.conditions` with `CURRENT_CARD` and the effective `COST` or `POWER` characteristic. Do not add `cost_max`, `power_max` or other selector shortcut fields.

### 17.3 Rested/active and keyword filters

Use `orientation = "RESTED"`/`"ACTIVE"` and `keywords = ["BLOCKER"]` or the corresponding keyword set. Keyword matching uses effective current keywords.

### 17.4 Excluding or identifying this exact card

- “all Characters other than this Character” → `exclude_source = true`.
- “[On K.O.] when this card is K.O.'d” → event `source = { same_as_source = true }`.
- “other than [Nami]” where the printed name is intended → use `name = "Nami"`; use `exclude_source` only when the wording excludes this exact instance, not every card sharing the same name.

### 17.5 Look at top N, choose a subset, and order the remainder

Use `LOOK_AT_TOP` followed by `SELECT_FROM_VARIABLE`. If the rest of the original group must be placed elsewhere, set `remainder_destination`. Use `ORDER_CARDS` independently for each group whose order is chosen by the player. Then use `MOVE_CARDS` with the correct placement.

### 17.6 “Top or bottom in any order”

Use one `SELECT_FROM_VARIABLE` whose `destination` is the group for the top and whose `remainder_destination` is the group for the bottom. Run `ORDER_CARDS` on each group independently. Move the first to `DECK` with `TOP`, the second to `DECK` with `BOTTOM`. The VM preserves each ordered group exactly.

### 17.7 DON!! added/rested/attached

When text requires manipulating the exact DON!! cards just added, provide `ADD_DON.destination` and use that variable in the next DON instruction. Never assume a later selector for “a rested DON!!” will identify the specific newly added DON!!.

For already-rested DON!! to be attached to a Character, select DON!! cards from `COST` with `orientation = "RESTED"`, then pass that exact selected variable to `ATTACH_DON`.

### 17.8 Returning a card to its owner's hand

Select the target cards into a variable and use `RETURN_CARDS_TO_OWNER_HAND`. Do not use a fixed destination player when the selected card's owner can differ from its controller.

### 17.9 Attack active Characters

Use a `PERMISSION` modifier with `action = "ATTACK_ACTIVE_CHARACTER"`, `subject` identifying the attacker, and `target` identifying only the opponent's active Characters. A permission with this action MUST declare a `target` selector. This permission overrides exclusively the `TARGET_NOT_RESTED` restriction for a matching target. It MUST NOT allow attacks against own cards, ignore zone restrictions, bypass Rush timing, or ignore any other applicable restriction.

### 17.10 Battle K.O. protection

Use a `RESTRICTION` modifier with `action = "BE_KNOCKED_OUT"`, `subject` identifying the protected card(s), `cause = "BATTLE"`, and optional `source` to filter the attacker. Do not encode this as generic effect negation.

### 17.11 Until end of opponent's next turn

Use `duration = { kind = "UNTIL_END_OF_NEXT_OPPONENT_TURN" }`. Do not use `UNTIL_END_OF_TURN`, `END_OF_OPPONENT_TURN` timing or an unscoped `UNTIL_EVENT`.

### 17.12 Cross-card color comparison

Bind the returned/selected reference to a variable. In the candidate selector's `conditions`, compare `CURRENT_CARD` with that variable using `CARD_PROPERTY_COMPARISON` and `property = "COLOR"`. Ensure the comparison semantics match the exact card wording, especially for multicolor cards.

### 17.13 Dynamic permanent scaling

Use a `PERMANENT` ability with a `CHARACTERISTIC` modifier whose `value` is a `COUNT` and/or `ARITHMETIC` expression. The VM reevaluates the value continuously from current game state. Do not model this as a one-time `MODIFY_CHARACTERISTIC` instruction.

### 17.14 Additive name alias

Use a `PERMANENT` modifier with `kind = "NAME_ALIAS"` and `name = "..."`. The original name remains valid; the alias is additional and is used by name selectors while the modifier is active.

## 18. Compiler responsibilities and validation

The source compiler is responsible for:

1. Discovering source files under the configured source directory.
2. Parsing strict TOML 1.0 and rejecting unknown keys/enums.
3. Validating `schema_version = 2`.
4. Validating logical card/printing/ability/program/replacement/option IDs and duplicates.
5. Validating required/optional fields, scalar types, nested union discriminators and instruction parameters.
6. Validating variables and their types where statically determinable, including `SOURCE_CARD`/`CURRENT_CARD` scope restrictions.
7. Validating `CardReference` values, selector conditions and all nested programs recursively.
8. Rejecting `BLOCKED` cards from production executable output.
9. Resolving thumbnail URLs, validating image responses, recording media type and computing digests.
10. Associating image bytes with the exact printing ID that references them.
11. Producing a deterministic binary catalog from identical source content and identical packaged assets.
12. Reporting source file, card ID, ability ID and nested instruction path for diagnostics.
13. Preserving source provenance, original text, metadata and printing entries.
14. Rejecting a card at production compilation when its required VM feature is not supported by the target runtime version/capability set.

A missing thumbnail MUST NOT block gameplay compilation. If a declared thumbnail cannot be retrieved or validated, report an asset error. Never silently substitute unrelated imagery.

The binary catalog version/hash MUST account for canonical compiled content and the assets/metadata covered by the binary-format contract. Exact hash algorithm and serialization remain defined by the binary-format specification.

## 19. Binary catalog preservation requirements

The binary catalog MUST preserve, at minimum:

- schema/catalog version information;
- each logical card's identity and gameplay characteristics;
- every ability kind, timing, condition, cost, program, modifier and nested rule structure;
- every new v2 construct: selector orientation/keywords/source identity, card references, card-list subset/remainder selection, owner-hand return, DON identity result, target-specific permissions, K.O. cause/source restriction, next-opponent-turn duration, cross-card property comparison, dynamic permanent expressions and additive name aliases;
- original card text and per-ability source excerpts, unless a later explicitly versioned release policy allows stripping them;
- all metadata and printing records;
- thumbnail URL/provenance metadata and embedded bytes, media type, dimensions when available and digest;
- mapping from printing ID to its packaged thumbnail resource;
- indexes to retrieve cards by `card.id` and official `card.number`.

The binary byte layout, compression, transcoding, encryption, extension and random-access strategy are intentionally not fixed here.

The compiler MUST remain independent from game execution. The game-side loader/factory is responsible for reading the binary catalog and creating domain-facing card definitions.

## 20. LLM conversion procedure and final checklist

For each logical card, the LLM MUST perform this sequence:

1. Identify the logical card and gather all physical printings that share exactly the same gameplay definition.
2. Split printings into separate logical `card.id` values whenever gameplay-relevant characteristics or rules text differ.
3. Transcribe the complete official card text to `source.card_text` and preserve the exact excerpt in each ability's `source_text`.
4. Extract category, colors, types, attributes, base statistics, counter and printed keywords without guessing.
5. Classify every printed ability as `ACTIVATED`, `AUTO`, `PERMANENT` or `REPLACEMENT` using actual timing semantics.
6. Encode Event `[Main]` as `ACTIVATE_MAIN` and Event `[Counter]` as `COUNTER_STEP`.
7. Encode Trigger from Life with `ON_TRIGGER`; do not invent a Trigger event type. If Trigger text says to activate the same card's Main/Counter effect, put the corresponding effect instructions in the `ON_TRIGGER` program as well as in the separately declared Main/Counter ability.
8. Encode `[DON!! xN]` by counting DON!! attached to `SOURCE_CARD`, not DON!! in the Cost area.
9. Encode cost/power limits through `CURRENT_CARD` selector conditions, and rested/active/keyword constraints through selector fields.
10. Use exact source identity filters whenever the text refers to “this Character” or “this card” as a specific instance.
11. For looked-at cards, keep the `LOOK_AT_TOP` result in a variable, select from that variable, preserve the unselected remainder when needed, and independently order groups when required.
12. Use `ADD_DON.destination` when subsequent instructions operate on the DON!! cards just added.
13. Use `RETURN_CARDS_TO_OWNER_HAND` whenever the destination is the card owner's hand.
14. Encode rule permissions/restrictions with both subject and target/cause/source details when relevant.
15. Use `UNTIL_END_OF_NEXT_OPPONENT_TURN` for that exact duration.
16. Use cross-card property comparison for properties that depend on another selected card.
17. Use dynamic `COUNT`/`ARITHMETIC` values in permanent modifiers; do not freeze dynamic scaling.
18. Use `NAME_ALIAS` for additive alternate names; do not overwrite the original name.
19. Confirm that each opcode/feature is implemented in the target VM build. If not, mark `BLOCKED` and name the missing feature.
20. Set `translation.status = "READY"` only after the entire printed gameplay meaning is represented and supported end-to-end.

Before finalizing a file, answer all of these:

- Is every printed ability present exactly once as the correct ability definition (except when a Trigger explicitly reproduces a Main/Counter effect as a second execution path)?
- Are all targets constrained by owner, controller, zone, category, color, type, attribute, keyword, orientation, identity and effective statistics as required?
- Are all DON!! counts tied to the correct host card and all DON!! operations tied to the exact card instances when the text requires it?
- Are all choices made by the correct player, with correct minimum/maximum and valid candidate set?
- Are unselected cards preserved and moved/ordered exactly as the text requires?
- Are moved cards sent to the correct player's zone, especially the actual owner's hand when required?
- Are optionality, payment, duration and turn scope exact?
- Are continuous values dynamically recalculated when the game state changes?
- Are aliases additive and is the original name preserved?
- Is every capability already implemented by the target VM build?

If any answer is “no” or “unknown”, the card MUST be `BLOCKED`.
