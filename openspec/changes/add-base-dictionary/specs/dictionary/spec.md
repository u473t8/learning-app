## Purpose
The dictionary spec defines how the base dictionary is loaded and used for autocomplete and canonical spelling.

## ADDED Requirements

### Requirement: Dictionary is loaded from public CouchDB
The system SHALL load dictionary from a public read-only CouchDB database via PouchDB replication.

#### Scenario: Initial dictionary sync
- **WHEN** app starts on a device without a local dictionary
- **THEN** system triggers a one-way pull replication to populate `dictionary-db`
- **AND** replication runs in background without blocking the app
- **AND** PouchDB handles interruptions, retries, and checkpoints automatically

#### Scenario: Startup incremental sync check
- **WHEN** app starts and `dictionary-meta` document already exists
- **THEN** the system still triggers a one-way pull replication to check for updates
- **AND** PouchDB checkpoints make the run incremental and quick when there are no changes

### Requirement: Dictionary sync runs via loader entry points
The system SHALL trigger dictionary synchronization through `dictionary-sync/ensure-loaded!` entry points (not via a queued task).

#### Scenario: Service worker startup trigger
- **WHEN** service worker activation runs startup hooks
- **THEN** the system invokes `dictionary-sync/ensure-loaded!`
- **AND** sync starts in the background without blocking app startup

#### Scenario: Request-time retry trigger
- **WHEN** an app request is handled while dictionary sync state is not ready
- **THEN** the dictionary-sync interceptor invokes `dictionary-sync/ensure-loaded!` again
- **AND** failed or idle states retry replication

#### Scenario: Low-pressure replication settings
- **WHEN** replication is started by the dictionary loader
- **THEN** it uses conservative settings to minimize load:
  - `batch_size: 100`
  - `batches_limit: 1`
  - `retry: true` with exponential backoff

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
