## Context
Examples are fetched asynchronously and stored as documents linked to words. This needs clear specification to support testing and future behavior changes.

## Goals / Non-Goals
- Goals: define example doc schema and task creation rules.
- Non-Goals: example rotation/editing behavior (future spec).

## Decisions
- Example-fetch tasks are created on word creation.
- Example documents are stored separately and linked via word-id.

## Risks / Trade-offs
- Example generation is async; UI must tolerate missing examples.

## Migration Plan
- Add task creation on word creation
- Store example docs when tasks complete
