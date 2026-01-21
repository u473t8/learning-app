## 1. Task Schema & Indexes
- [x] 1.1 Define task document schema
- [x] 1.2 Add task query index (type, task-type, run-at)

## 2. Task Runner
- [x] 2.1 Implement worker pool with concurrency config
- [x] 2.2 Add offline pause + resume
- [x] 2.3 Add changes feed + polling fallback
- [x] 2.4 Implement retry + run-at backoff (max 1 min)

## 3. Cleanup & Maintenance
- [x] 3.1 Document compaction/cleanup requirement

## 4. Tests
- [x] 4.1 Task runner processes tasks in parallel
- [x] 4.2 Offline pause prevents execution
- [x] 4.3 Backoff schedules run-at + increments attempts
