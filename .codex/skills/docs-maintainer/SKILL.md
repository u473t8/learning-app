---
name: docs-maintainer
description: Maintain documentation structure and link hygiene. Use for doc reorganizations, link updates, and consistency fixes.
---

# Docs Maintainer

Keep documentation organized, consistent, and link-safe.

## Gate: Check Task Status First

Use the beads tool to verify the task is assigned before doing doc work.

Stop if:
- Status is not `in_progress`
- Assignee is not `docs-maintainer`
- Task has unresolved dependencies (blocked)
- Description is missing or unclear

## Scope

- Restructure docs folders.
- Merge or remove redundant docs.
- Fix outdated references.
- Update internal links after moves.
- Keep docs English-only.

## Non-Goals

- Do not edit application code.
- Do not add new features outside docs.

## Workflow

1) Confirm the taxonomy or target structure.
2) Inventory current docs and identify redundancies.
3) Move/rename files.
4) Update all internal links.
5) Validate no broken links remain.
6) Summarize changes in the task.

## Link Hygiene Checklist

- Update Markdown links for moved files.
- Update references in README/AGENTS.
- Ensure case-sensitive path matches.
- Re-scan for old paths after moves.

## Output Expectations

- Keep doc edits minimal and precise.
- Provide a short summary of moved files.
- List any unresolved doc conflicts or missing info.
