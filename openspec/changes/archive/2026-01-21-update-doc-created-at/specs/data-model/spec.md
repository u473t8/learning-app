## MODIFIED Requirements
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

### Requirement: Dead-lettered tasks are recorded
The system SHALL record failed tasks as dead-lettered task documents with ISO 8601 creation and failure timestamps.

#### Scenario: Dead-letter task shape
- **WHEN** a task is dead-lettered
- **THEN** it includes `status`, `failure-reason`, `created-at`, and `failed-at`

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
