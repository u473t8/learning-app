# Change: Add task runner for asynchronous work

## Why
We need a reliable, testable mechanism to process asynchronous work (e.g., example fetching) without fire-and-forget side effects. A task runner provides determinism, retries, offline-aware execution, and a single contour for example fetching.

## What Changes
- Introduce a task document schema for async work
- Add a task runner that processes tasks in a parallel worker pool
- Add retry + exponential backoff (capped at 1 minute)
- Add offline pause and resumable execution
- Add task indexing for efficient lookup
- Note cleanup/compaction requirement for deleted tasks
- Task runner becomes the only mechanism for example fetching (no direct hooks elsewhere)

## Impact
- Affected specs: task-runner (new)
- Affected code: src/client/tasks.cljs, src/shared/db.cljc, tests
