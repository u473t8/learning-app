## MODIFIED Requirements
### Requirement: Task runner processes tasks in parallel
The system SHALL execute tasks using a configurable worker pool with fair task ordering.

#### Scenario: Worker pool handles multiple tasks
- **WHEN** multiple tasks are pending
- **THEN** the runner processes them concurrently up to the configured pool size

#### Scenario: Fair task ordering
- **WHEN** tasks are selected for execution
- **THEN** they are ordered by `run-at` and then `created-at`
- **AND** completion order MAY differ due to concurrent execution
