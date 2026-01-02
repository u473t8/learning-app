---
name: gh-task-executor
description: Execute shaped GitHub issues by implementing code changes according to the issue description. Use when Codex needs to build or modify code from a shaped issue and must verify the issue description contains the shaped marker before writing implementation.
---

# GH Task Executor

Execute shaped work by implementing the issue's pitch. Follow `docs/executor-guide.md` and treat the issue description as the source of truth for scope and constraints. Provide implementation context before coding.

## Workflow

1. Read the issue description and verify it contains the shaped marker: `<!-- issue is shaped -->`.
2. If the marker is missing, do not write implementation; ask for shaping confirmation.
3. If the marker is present, extract and summarize the shaped scope, constraints, and relevant guides for downstream skills to use.
4. Keep the appetite fixed and scope flexible, per the executor guide.
