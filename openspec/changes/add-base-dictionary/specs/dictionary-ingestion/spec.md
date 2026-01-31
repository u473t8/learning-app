## Purpose
Defines how raw dictionary sources are transformed into dictionary-entry and surface-form documents by the ingestion pipeline.

## ADDED Requirements

### Requirement: Pipeline ingests two external sources
The system SHALL download and process two external data sources to build the dictionary.

#### Scenario: Kaikki provides lemmas, forms, and translations
- **WHEN** the pipeline runs
- **THEN** it downloads the Kaikki German extract (a gzipped JSONL file from wiktextract)
- **AND** Kaikki records supply lemmas, parts of speech, inflected forms, and translations

#### Scenario: Goethe provides CEFR level reference
- **WHEN** the pipeline runs
- **THEN** it downloads the Goethe-Institut A1/A2/B1 stems CSV
- **AND** the Goethe index maps lowercase stems to CEFR levels ("a1", "a2", "b1")

### Requirement: Only eligible entries become dictionary entries
The system SHALL filter Kaikki records so that only valid German lemmas with Russian translations are ingested.

#### Scenario: German language filter
- **WHEN** a Kaikki record has `lang_code` other than `"de"`
- **THEN** the record is skipped
- **AND** when `lang_code` is `"de"`, the word must use the standard German alphabet (letters, umlauts, eszett, common loanword accents, spaces, hyphens, apostrophes)

Example: `"der Hund"` passes; `"犬"` is skipped.

#### Scenario: Lemma entries only
- **WHEN** every sense of a Kaikki record carries a `form_of` tag (indicating it is an inflected-form redirect, not a headword)
- **THEN** the record is skipped
- **AND** records where at least one sense lacks `form_of` are kept as lemma entries

Example: The record for "ging" (past tense of "gehen") where all senses point to "gehen" is excluded; the record for "gehen" itself is kept.

#### Scenario: Must have at least one Russian translation
- **WHEN** a Kaikki record has no translations with `lang_code` `"ru"`
- **THEN** the entry is skipped after transformation (no dictionary-entry document is produced)

#### Scenario: Proper names require a CEFR level
- **WHEN** an entry has POS `"name"` and no Goethe CEFR match
- **THEN** the entry is skipped
- **AND** proper names that do have a CEFR match are kept

### Requirement: Duplicate entries are merged
The system SHALL merge multiple Kaikki records that share the same word and POS into a single entry.

#### Scenario: Same word+POS from multiple Kaikki records
- **WHEN** the pipeline encounters two or more Kaikki records with the same `[word, pos]` key
- **THEN** their translations are unioned (duplicates removed)
- **AND** their inflected forms are unioned (duplicates removed)
- **AND** their senses are unioned (duplicates removed)
- **AND** a single merged entry is carried forward

Example: Two records for "Bank" as a noun (one with financial senses, one with furniture senses) merge into one entry with both sense sets.

### Requirement: Each entry receives a CEFR level via the Goethe index
The system SHALL assign a CEFR level to entries by matching against the Goethe stem index.

#### Scenario: Longest-prefix stem matching
- **WHEN** a dictionary entry's word is looked up in the Goethe index
- **THEN** the system finds all stems that are a prefix of the lowercased word
- **AND** selects the longest matching stem
- **AND** assigns the corresponding CEFR level ("a1", "a2", or "b1")

Example: For "Hundefutter", stems "hund" (a1) and "hundefutter" would both match; the longest prefix wins. For "Hund", the stem "hund" matches, yielding "a1".

#### Scenario: No match yields no CEFR level
- **WHEN** no Goethe stem is a prefix of the lowercased word
- **THEN** the entry has no CEFR level (nil)

### Requirement: Entries are ranked for autocomplete ordering
The system SHALL compute a numeric rank for each dictionary entry so that CEFR entries dominate autocomplete ordering.

#### Scenario: CEFR entries rank higher than non-CEFR entries
- **WHEN** an entry has a CEFR level
- **THEN** its rank starts from a base value determined by its level (A1 highest, then A2, then B1)
- **AND** additional points are added based on sense count and translation count
- **AND** the resulting rank is substantially higher than any non-CEFR entry

#### Scenario: Non-CEFR entries are ranked by richness with a cap
- **WHEN** an entry has no CEFR level
- **THEN** its rank is computed from sense count and translation count
- **AND** the rank is capped so it never exceeds the minimum possible CEFR rank

#### Scenario: Richer entries rank higher within a tier
- **WHEN** two entries share the same CEFR level (or both lack one)
- **THEN** the entry with more senses and translations receives a higher rank

### Requirement: Text is normalized for lookup
The system SHALL normalize text to enable case-insensitive, diacritic-insensitive matching.

#### Scenario: Case folding and German-character ASCII mapping
- **WHEN** text is normalized
- **THEN** the system lowercases the text
- **AND** maps German special characters to ASCII equivalents (ae, oe, ue, ss)
- **AND** collapses whitespace and trims

Example: `"der Hund"` normalizes to `"der hund"`; `"Straße"` normalizes to `"strasse"`; `"Ärger"` normalizes to `"aerger"`.

### Requirement: Surface forms are generated for autocomplete
The system SHALL produce surface-form entries for every text variant a user might type when searching for a word.

#### Scenario: Lemma value is a surface form
- **WHEN** a dictionary entry is created
- **THEN** the entry's display value becomes a surface form

Example: For the entry "gehen" (verb), `"gehen"` is a surface form.

#### Scenario: Nouns get article-prefixed and bare-word forms
- **WHEN** a dictionary entry is a noun with a gender
- **THEN** the article-prefixed value (e.g., `"der Hund"`) is a surface form
- **AND** the bare word without article (e.g., `"Hund"`) is also a surface form

Example: The noun entry with value `"der Hund"` produces surface forms for both `"der hund"` and `"hund"` (after normalization).

#### Scenario: Valid inflected forms become surface forms
- **WHEN** a Kaikki entry has inflected forms
- **THEN** each valid form becomes a surface form pointing back to the lemma entry
- **AND** each surface-form document's entries are sorted by rank descending

Example: The entry "der Hund" produces surface forms for inflections like `"hundes"`, `"hunde"`, `"hunden"`.

#### Scenario: Invalid forms are excluded
- **WHEN** an inflected form fails validation
- **THEN** it is not generated as a surface form
- **AND** validation excludes:
  - Forms shorter than 2 characters
  - Forms containing non-German characters
  - Auxiliary verb markers ("haben", "sein", "werden")
  - The lemma word itself (already covered by the lemma surface form)

### Requirement: Pipeline produces JSONL output with manifest
The system SHALL write output as JSONL files accompanied by a manifest for integrity verification.

#### Scenario: dictionary-entries.jsonl contains one document per lemma+POS
- **WHEN** the pipeline completes
- **THEN** `dictionary-entries.jsonl` is written with one JSON object per line
- **AND** each line represents a dictionary-entry document with `_id` following the `lemma:<normalized-value>:<pos>` convention
- **AND** keys are serialized as snake_case (converted from internal kebab-case)

Example line (abbreviated):
```json
{"_id":"lemma:der hund:noun","type":"dictionary-entry","value":"der Hund","pos":"noun","rank":30012,"translation":[{"lang":"ru","value":"собака"}],"forms":["Hundes","Hunde","Hunden"],"meta":{"normalized_value":"der hund","cefr_level":"a1","gender":"der","sense_count":3,"source":"kaikki-enwiktionary"}}
```

#### Scenario: surface-forms.jsonl contains one document per normalized form
- **WHEN** the pipeline completes
- **THEN** `surface-forms.jsonl` is written with one JSON object per line
- **AND** each line represents a surface-form document with `_id` following the `sf:<normalized-form>` convention
- **AND** entries within each document are sorted by rank descending

Example line (abbreviated):
```json
{"_id":"sf:hund","type":"surface-form","value":"hund","entries":[{"lemma_id":"lemma:der hund:noun","lemma":"der Hund","rank":30012}]}
```

#### Scenario: manifest.json records counts, sizes, and checksums
- **WHEN** the pipeline completes
- **THEN** `manifest.json` is written containing:
  - A generation timestamp
  - For each output file: document count, byte size, and SHA-256 checksum
