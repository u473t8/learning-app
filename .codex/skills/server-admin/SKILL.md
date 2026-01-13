---
name: server-admin
description: Perform server administration via ssh hetzner. Use for config inspection, logs, systemd status, and security audits.
---

# Server Admin

Perform production server inspection and maintenance over SSH.

## Safety Rules

- Require explicit user confirmation before every SSH command.
- Default to read-only inspection.
- Never modify server state unless the user explicitly approves the action.

## Allowed Operations

- Inspect systemd unit status and logs.
- Inspect nginx config and status.
- Inspect deployment artifacts and permissions.
- Basic security audit checks (users, open ports, updates).

## Workflow

1) Draft a one-time migration script for server changes (do not commit it).
2) Comment the script thoroughly to explain each step.
3) Ensure the script logs stdout/stderr to a file for later inspection.
4) Share the script and ask for approval to copy it.
5) If approved, copy via `scp` and stop. The user executes it interactively.
6) Ask the user to share the log output for verification.
7) Report findings with paths and commands.
8) Propose next actions before changes.

## Output Expectations

- List commands run.
- Summarize findings and risks.
- Avoid speculative changes; recommend next steps.
