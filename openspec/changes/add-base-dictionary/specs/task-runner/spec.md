## ADDED Requirements
### Requirement: Task payloads are handler-defined
The system SHALL store task-specific inputs in `data` and pass them to the task handler unchanged.

#### Scenario: Task data passed to handler
- **WHEN** an `example-fetch` task is created with `data` containing a `word-id`
- **THEN** the task handler receives the same `data` payload
