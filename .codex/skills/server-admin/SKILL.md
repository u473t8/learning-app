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

1) Propose exact SSH command(s).
2) Ask for explicit approval.
3) Execute only after approval.
4) Report findings with paths and commands.
5) Propose next actions before changes.

## Output Expectations

- List commands run.
- Summarize findings and risks.
- Avoid speculative changes; recommend next steps.
