# Change: Add app logic tests and evaluate testability architecture

## Why
App logic lacked dedicated tests, and client modules mixed side effects with pure logic, making deterministic tests hard. We need a minimal refactor that isolates pure logic while validating core scenarios against a local database.

## What Changes
- Extract pure app logic into `src/client/domain` modules
- Add local PouchDB-backed fixtures/helpers for deterministic tests
- Add a new capability spec for app logic testing
- Add tests for two core scenarios:
  - Word operations: add, update, filter, delete
  - Lesson flow: trial selection, answer checking, advance/finish

## Impact
- Affected specs: app-logic-testing (new)
- Affected code: src/client/domain, src/client/lesson.cljs, src/client/vocabulary.cljs, test/client/support, test/client
