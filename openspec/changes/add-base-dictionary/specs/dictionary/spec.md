## Purpose
The dictionary spec defines how to base dictionary is loaded and used for autocomplete and canonical spelling.

## ADDED Requirements

### Requirement: Dictionary is loaded from public CouchDB
The system SHALL load dictionary from a public read-only CouchDB database via PouchDB replication.

#### Scenario: Initial dictionary sync
- **WHEN** app starts on a device without a local dictionary
- **THEN** system triggers a one-way pull replication to populate `dictionary-db`
- **AND** replication runs in background without blocking the app
- **AND** PouchDB handles interruptions, retries, and checkpoints automatically

#### Scenario: No sync on subsequent starts
- **WHEN** app starts and `dictionary-meta` document exists
- **THEN** no sync is triggered (dictionary already loaded)

### Requirement: Surface-form documents support autocomplete
The system SHALL use `surface-form` documents and `allDocs` key-range queries to provide fast prefix matches for autocomplete.

#### Scenario: Autocomplete from inflected form
- **WHEN** user types a prefix
- **THEN** system queries `allDocs` with:
  - `startkey: "sf:" + normalize(prefix)`
  - `endkey: "sf:" + normalize(prefix) + "\uffff"`
  - `include_docs: true`
  - `limit: 50`
- **AND** system collects all `entries` from matched documents
- **AND** system deduplicates entries by `lemma-id`
- **AND** system prioritizes exact normalized matches before other results
- **AND** system sorts remaining results by `rank` descending
- **AND** system caps suggestions to 10
- **AND** system enriches suggestions with translation hints via a batch lookup

#### Scenario: Case-insensitive and diacritic-insensitive lookup
- **WHEN** user types a prefix with different case or diacritics
- **THEN** system normalizes the prefix before querying
- **AND** system returns matching suggestions from normalized surface-form keys

#### Scenario: Collision resolution (multiple entries per surface-form)
- **WHEN** a surface-form document contains multiple entries (e.g., `sf:masse` → "das Maß" and "die Masse")
- **THEN** all entries are included in the result set
- **AND** entries are deduplicated and sorted by `rank` alongside entries from other matched documents

### Requirement: Canonical values prefill add-word input
The system SHALL offer a canonical prefill on exact normalized match and apply it on selection.

#### Scenario: Canonical prefill
- **WHEN** user inputs `der hund` and an exact normalized match exists
- **THEN** the exact suggestion is marked as prefill
- **AND** selecting that suggestion fills add-word input with `der Hund`

### Requirement: Suggestions include translation hints
The system SHALL attach translation hints to suggestions for UI display.

#### Scenario: Translation enrichment
- **WHEN** suggestions are built from surface-form entries
- **THEN** the system batch-fetches `dictionary-entry` docs by `lemma-id`
- **AND** attaches the first RU translation (if present) as `translation` for each suggestion
