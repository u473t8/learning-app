## MODIFIED Requirements
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
