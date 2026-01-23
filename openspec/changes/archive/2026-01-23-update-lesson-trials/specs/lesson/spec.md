## ADDED Requirements

### Requirement: Lesson documents follow data-model spec
The system SHALL store lesson documents according to `specs/data-model/spec.md`.

#### Scenario: Lesson document shape
- **WHEN** a lesson is persisted
- **THEN** it matches the lesson document shape in `specs/data-model/spec.md`

### Requirement: Lesson trials follow data-model spec
The system SHALL store lesson trials according to `specs/data-model/spec.md`.

#### Scenario: Trial document shape
- **WHEN** a lesson trial is persisted
- **THEN** it matches the lesson trial shape in `specs/data-model/spec.md`

### Requirement: Lesson answer checks update lesson state
The system SHALL return updated lesson state after answer checks, recording the result in `:last-result`.

#### Scenario: Answer check response
- **WHEN** an answer is checked
- **THEN** the response includes `lesson-state` with `:last-result` containing `:correct?` and `:answer`

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word and each example, using denormalized prompts and answers.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each example produces an example trial

### Requirement: Lesson state is denormalized
The system SHALL store lesson state without a separate `:words` collection.

#### Scenario: Lesson state fields
- **WHEN** lesson state is stored
- **THEN** it includes `:trials`, `:remaining-trials`, `:current-trial`, and `:last-result`
- **AND** it omits a `:words` field
