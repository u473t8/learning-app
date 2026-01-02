---
name: code-builder
description: Build or modify code in this project using the established coding guides. Use when implementing features, refactors, or bug fixes that require adhering to project coding principles, discipline, and style.
---

# Code Builder

## Overview

Implement changes with the project's coding guidance as the source of truth. Keep solutions minimal, clear, and consistent with established style and discipline.

## Workflow

1. Always invoke `gh-task-executor` to obtain the shaped task context.
2. Read the relevant guides in `docs/` before coding.
3. Confirm the goal and the smallest change that satisfies it.
4. Implement changes with clear data flow and minimal abstraction.
5. Suggest verification steps if applicable.

## References

- `docs/coding-principles.md`
- `docs/code-discipline.md`
- `docs/code-style.md`
- `docs/philosophy.md`
