# Change: Specify example generation and storage

## Why
Example creation behavior is not explicitly specified, which makes it hard to test and evolve. We need a clear spec for example documents and how example-fetch tasks are created.

## What Changes
- Define example document schema as source of truth
- Specify when example-fetch tasks are created (on word creation)
- Clarify how example docs attach to vocab words

## Impact
- Affected specs: examples (new)
- Affected code: vocab/task creation, example worker
