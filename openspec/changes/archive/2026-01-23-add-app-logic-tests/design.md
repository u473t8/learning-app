## Context
Client app logic mixes pure transformations with DB/network side effects. We need to evaluate architectural changes that enable deterministic tests.

## Current Weak Points
- `src/client/lesson.cljs` mixes trial logic with persistence, time, and network side effects.
- Random trial selection (`rand-nth`) makes tests nondeterministic.
- `src/client/vocabulary.cljs` couples retention calculations and search/filtering to DB queries.
- Filtering/search rules are not codified in domain logic, making tests unclear.
- Time-based calculations (`utils/now-*`) are not injectable, causing flaky tests.

## Goals / Non-Goals
- Goals: Identify minimal refactors to isolate pure logic and enable unit tests for core scenarios.
- Non-Goals: Full e2e Playwright coverage (tracked separately).

## Decisions
- Decision: Extract pure logic into `src/client/domain` modules.
- Decision: Inject selector and time dependencies for deterministic tests.
- Decision: Use a local PouchDB-backed test DB with shared fixtures/helpers.
- Decision: Codify filtering/search behavior in domain vocabulary helpers.
- Alternatives considered: heavy mocking of global DB calls (rejected for coupling).

## Risks / Trade-offs
- Refactor cost in client code → mitigate with minimal, local extraction.
- More modules/functions → keep near their call sites.

## Migration Plan
- Document IO boundaries
- Introduce pure helpers
- Switch callers incrementally
- Add tests alongside each change

## Open Questions
- None. Pure logic lives in `src/client/domain`.
