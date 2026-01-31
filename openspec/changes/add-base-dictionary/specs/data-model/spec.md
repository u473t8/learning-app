## MODIFIED Requirements
### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations, ISO 8601 timestamps for creation and modification, and optional metadata.

#### Scenario: Vocabulary document shape
- **WHEN** a vocabulary word is stored
- **THEN** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`
- **AND** the document MAY include `meta`

Example:
```json
{
  "type": "vocab",
  "value": "der Hund",
  "translation": [{"lang": "ru", "value": "sobaka"}],
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z",
  "meta": {"source": "dictionary"}
}
```

### Requirement: Task documents are stored
The system SHALL store task documents for asynchronous work with ISO 8601 scheduling and creation timestamps and an optional data payload.

#### Scenario: Task document shape
- **WHEN** a task is stored
- **THEN** it includes `type`, `task-type`, `attempts`, `run-at`, and `created-at`
- **AND** it MAY include `data` for task-specific payloads

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "data": {"word-id": "<vocab-id>"},
  "attempts": 0,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z"
}
```

## ADDED Requirements
### Requirement: Dictionary entry documents are stored
The system SHALL store dictionary entry documents with translations, canonical values, and ISO 8601 timestamps for creation and modification.

The `_id` follows the convention `lemma:<normalized-value>:<pos>`, which deterministically encodes the lookup key into the document ID. This enables primary-index queries and handles homonyms via the article in the normalized value.

#### Scenario: Dictionary entry document shape
- **WHEN** a dictionary entry is stored
- **THEN** the document `_id` follows the `lemma:<normalized-value>:<pos>` convention
- **AND** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`
- **AND** it MAY include `meta` for level, part-of-speech, normalized values, forms, rank, and source

Example:
```json
{
  "_id": "lemma:der hund:noun",
  "type": "dictionary-entry",
  "value": "der Hund",
  "translation": [{"lang": "ru", "value": "собака"}],
  "meta": {
    "level": "a1",
    "pos": "noun",
    "normalized-value": "der hund",
    "forms": ["Hunde"],
    "rank": 100,
    "source": "example-source"
  },
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z"
}
```

### Requirement: Surface-form documents are stored
The system SHALL store surface-form documents that map normalized inflected forms to dictionary entries for fast prefix-based autocomplete.

The `_id` follows the convention `sf:<normalized-form>`. Each document contains an `entries` array with denormalized entry data (`lemma-id`, `lemma`, `rank`), providing display-ready results without secondary lookups.

#### Scenario: Surface-form document shape
- **WHEN** a surface-form is stored
- **THEN** the document `_id` follows the `sf:<normalized-form>` convention
- **AND** the document includes `type` and `entries`
- **AND** each entry in `entries` includes `lemma-id`, `lemma`, and `rank`

Example:
```json
{
  "_id": "sf:hunde",
  "type": "surface-form",
  "entries": [
    {
      "lemma-id": "lemma:der hund:noun",
      "lemma": "der Hund",
      "rank": 100
    }
  ]
}
```

#### Scenario: Surface-form collision (multiple entries)
- **WHEN** a normalized form maps to multiple dictionary entries
- **THEN** the `entries` array contains one element per distinct entry

Example:
```json
{
  "_id": "sf:masse",
  "type": "surface-form",
  "entries": [
    { "lemma-id": "lemma:das mass:noun", "lemma": "das Maß", "rank": 80 },
    { "lemma-id": "lemma:die masse:noun", "lemma": "die Masse", "rank": 60 }
  ]
}
```

### Requirement: Dictionary state document is stored
The system SHALL store a dictionary state document that tracks version and checksum metadata.

#### Scenario: Dictionary state document shape
- **WHEN** the dictionary state is stored
- **THEN** the document includes `type`, `version`, `checksum`, and `updated-at`

Example:
```json
{
  "_id": "dictionary-state",
  "type": "dictionary-state",
  "version": "2026-01-26",
  "checksum": "sha256:example",
  "updated-at": "2026-01-26T12:00:00.000Z"
}
```
