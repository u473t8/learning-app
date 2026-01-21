## Purpose
The data model spec defines the required document shapes stored in the local database.

## Requirements
### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations and ISO 8601 timestamps for creation and modification.

#### Scenario: Vocabulary document shape
- **WHEN** a vocabulary word is stored
- **THEN** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`

Example:
```json
{
  "type": "vocab",
  "value": "der Hund",
  "translation": [{"lang": "en", "value": "dog"}],
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z"
}
```

### Requirement: Review documents are stored
The system SHALL store review documents linked to vocabulary words with an ISO 8601 creation timestamp.

#### Scenario: Review document shape
- **WHEN** a review is recorded
- **THEN** the document includes `type`, `word-id`, `retained`, `created-at`, and `translation`

Example:
```json
{
  "type": "review",
  "word-id": "<vocab-id>",
  "retained": true,
  "created-at": "2026-01-20T10:00:00.000Z",
  "translation": [{"lang": "en", "value": "dog"}]
}
```

### Requirement: Example documents are stored
The system SHALL store example documents linked to vocabulary words with an ISO 8601 creation timestamp.

#### Scenario: Example document shape
- **WHEN** an example is stored
- **THEN** the document includes `type`, `word-id`, `word`, `value`, `translation`, `structure`, and `created-at`

Example:
```json
{
  "type": "example",
  "word-id": "<vocab-id>",
  "word": "der Hund",
  "value": "Der Hund schlaeft unter dem Tisch.",
  "translation": "The dog sleeps under the table.",
  "structure": [
    {"usedForm": "Hund", "dictionaryForm": "der Hund", "translation": "dog"}
  ],
  "created-at": "2026-01-20T10:02:00.000Z"
}
```

### Requirement: Lesson documents are stored
The system SHALL store lesson documents as denormalized trials without a words list.

#### Scenario: Lesson document shape
- **WHEN** a lesson is stored
- **THEN** it includes `type`, `started-at`, `trials`, `remaining-trials`, `current-trial`, and `last-result`
- **AND** `last-result` includes the user `answer`

Example:
```json
{
  "_id": "lesson",
  "type": "lesson",
  "started-at": "2026-01-20T10:00:00.000Z",
  "trials": [
    {
      "type": "word",
      "word-id": "<vocab-id>",
      "prompt": "dog",
      "answer": "der Hund"
    },
    {
      "type": "example",
      "word-id": "<vocab-id>",
      "prompt": "The dog sleeps.",
      "answer": "Der Hund schlaeft."
    }
  ],
  "remaining-trials": [
    {
      "type": "example",
      "word-id": "<vocab-id>",
      "prompt": "The dog sleeps.",
      "answer": "Der Hund schlaeft."
    }
  ],
  "current-trial": {
    "type": "word",
    "word-id": "<vocab-id>",
    "prompt": "dog",
    "answer": "der Hund"
  },
  "last-result": {
    "correct?": true,
    "correct-answer": "der Hund",
    "answer": "der hund"
  }
}
```

### Requirement: Lesson trials include prompt and answer
The system SHALL store lesson trials with prompt and answer strings.

#### Scenario: Lesson trial shape
- **WHEN** a lesson trial is stored
- **THEN** it includes `type`, `word-id`, `prompt`, and `answer`

Example:
```json
{
  "type": "word",
  "word-id": "<vocab-id>",
  "prompt": "dog",
  "answer": "der Hund"
}
```

### Requirement: Task documents are stored
The system SHALL store task documents for asynchronous work with ISO 8601 scheduling and creation timestamps.

#### Scenario: Task document shape
- **WHEN** a task is stored
- **THEN** it includes `type`, `task-type`, `word-id`, `attempts`, `run-at`, and `created-at`

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "word-id": "<vocab-id>",
  "attempts": 0,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z"
}
```

### Requirement: Dead-lettered tasks are recorded
The system SHALL record failed tasks as dead-lettered task documents with an ISO 8601 failure timestamp.

#### Scenario: Dead-letter task shape
- **WHEN** a task is dead-lettered
- **THEN** it includes `status`, `failure-reason`, and `failed-at`

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "word-id": "<vocab-id>",
  "attempts": 1,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z",
  "status": "failed",
  "failure-reason": "unknown-task-type",
  "failed-at": "2026-01-20T10:06:00.000Z"
}
```

