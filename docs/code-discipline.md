# Code Discipline

This document defines how we plan, implement, and validate changes. The terms **MUST**, **SHOULD**, and **MAY** are normative.

## Tooling

- If `clojure-mcp` is installed, you **MUST** use it to evaluate code and run tests.

## Planning and Communication

- You **MUST** understand what should be done before you start implementing.
- You **SHOULD** split work into atomic, meaningful steps.
- You **MUST** publish the implementation plan as a comment on the current issue.
- If the plan changes, you **MUST** comment on the issue with *what* changed and *why*.

## Behavior Tests (Feature Intent)

- You **MAY** write behavior tests early to describe feature intent.
- Behavior tests **MAY** fail initially; that is acceptable during exploration.

## Implementation Flow

1. Break each step into independent functions that do the work.
2. Start with the simplest function and move to more complex ones.
3. When possible, validate functions in the REPL with typical inputs and edge cases.
4. Integrate functions into source code *only* after they work in the REPL.

## Unit/Function Tests (Documentation)

- You **MUST** write tests for each function.
- Tests **SHOULD** serve as documentation by showing typical and edge cases.
- Tests **SHOULD** reveal a function's purpose and intent.
- The primary goal of tests is to show programmer intent, *not* implementation detail.
NOTE: distinguish between behavior and unit/function tests

## Behavior Tests Passing

- You **MUST** ensure behavior tests pass before declaring work done.
- If behavior tests require external services, you **SHOULD** propose architecture changes to make them testable.
- Acceptable patterns **MAY** include dependency inversion, hexagonal architecture, or an eDSL.
- You **SHOULD** suggest only the simplest reasonable pattern. Avoid unnecessary complexity and avoid showcasing sophisticated patterns without need.

## Architecture Discipline

- If a simpler solution solves the task, you **MUST** prefer it.
- You **MUST NOT** design for distant future requirements without evidence.
- You **SHOULD** use sophisticated architectural patterns only when current needs require them.
- If flexibility is later needed, you **MAY** refactor with evidence.
NOTE: duscuss such changes

## Domain First

- The current understanding of the domain **MUST** come first.
- The more you understand the domain, the more natural your solutions will be.
NOTE: ask if you need to clarify things
