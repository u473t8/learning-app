## Context
Async work is currently handled via fire-and-forget calls inside app logic, which is hard to test and reason about. A centralized task runner provides determinism and isolation of side effects.

## Goals / Non-Goals
- Goals: deterministic task processing, offline pause, retries with backoff, parallel execution.
- Non-Goals: distributed task execution or server-side scheduling.

## Decisions
- Use task documents in local DB with minimal schema and explicit retry metadata.
- Store both `created-at-ms` and derived `created-at` for observability while avoiding drift; `created-at` is computed from the ms value.
- Process tasks via a worker pool; when any worker finishes, it pulls the next eligible task.
- Ordering of task execution is not important; parallelism is preferred.
- Combine changes feed with polling fallback for responsiveness.
- Use `run-at` to schedule backoff and prevent hot retries.
- Require indexes on task docs for efficient querying.
- Note cleanup need for tombstoned task docs (PouchDB deletions).
- Task runner is the only mechanism for example fetching; remove direct hooks elsewhere.

## Risks / Trade-offs
- Task tombstones require cleanup (compaction or scheduled pruning).
- Parallelism increases throughput but makes ordering nondeterministic; ordering is not required.

## Migration Plan
- Introduce task schema and runner module
- Add indexes
- Start runner on app launch
- Add tests for worker behavior

## Open Questions
- Default pool size (configurable)
