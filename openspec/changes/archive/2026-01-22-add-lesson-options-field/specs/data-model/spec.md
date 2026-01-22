## MODIFIED Requirements

### Requirement: Lesson documents are stored
The system SHALL store lesson documents with an options object containing configuration settings.

#### Scenario: Lesson document with options
- **WHEN** a lesson is stored
- **THEN** the document includes `type`, `started-at`, `options`, `trials`, `remaining-trials`, `current-trial`, and `last-result`
- **AND** `options` includes `trial-selector` with values `"first"` or `"random"`

Example:
```json
{
  "_id": "lesson",
  "type": "lesson",
  "started-at": "2026-01-20T10:00:00.000Z",
  "options": {
    "trial-selector": "random"
  },
  "trials": [
    {
      "type": "word",
      "word-id": "<vocab-id>",
      "prompt": "dog",
      "answer": "der Hund"
    }
  ],
  "remaining-trials": [
    {
      "type": "word",
      "word-id": "<vocab-id>",
      "prompt": "dog",
      "answer": "der Hund"
    }
  ],
  "current-trial": {
    "type": "word",
    "word-id": "<vocab-id>",
    "prompt": "dog",
    "answer": "der Hund"
  },
  "last-result": null
}
```

## ADDED Requirements

### Requirement: Lesson trial selection uses options
The system SHALL use the trial-selector from lesson options to determine trial selection behavior.

#### Scenario: Trial selection with options
- **WHEN** selecting trials for a lesson
- **THEN** the selection uses the `trial-selector` value from `options`
- **AND** defaults to `"random"` if options or trial-selector is missing