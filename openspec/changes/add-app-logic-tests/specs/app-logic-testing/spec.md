## ADDED Requirements

### Requirement: Word operations are covered by unit tests
The system SHALL provide unit tests for adding, updating, filtering, and deleting words using a local PouchDB instance, without external network services.

#### Scenario: Add, update, filter, delete words offline
- **WHEN** word operations run in tests with local DB fixtures
- **THEN** they complete deterministically without external services

### Requirement: Lesson flow is covered by unit tests
The system SHALL provide unit tests for lesson trial generation, answer checking, and lesson completion using local DB fixtures, without external services.

#### Scenario: Lesson flow runs deterministically
- **WHEN** lesson logic is executed in tests with local DB fixtures
- **THEN** trial selection, answer checking, and completion behave predictably

### Requirement: Testability architecture is evaluated
The system SHALL isolate pure app logic from side effects by extracting domain logic into `src/client/domain` and documenting the IO boundary decisions.

#### Scenario: IO boundaries are explicit
- **WHEN** app logic modules are reviewed
- **THEN** IO boundaries and implemented refactors are documented
