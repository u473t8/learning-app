---
name: devops-infra
description: Design and maintain low-overhead infrastructure, CI/CD, production and local deployments, and infra documentation for the learning-app. Use for systemd/nginx/certbot changes, GitHub Actions deploy pipeline, Docker-based local deploy, and when updating infra docs or server runbooks.
---

# Devops Infra

## Overview

Design and maintain the cheapest reliable infrastructure, CI/CD, and deploy tooling for this app, and keep production/local infra docs accurate enough for handoff.

## Gate: Check Task Status First

Use the `beads` skill to verify the task is assigned before doing infra work.

Stop if:
- Status is not `in_progress`
- Assignee is not `devops-infra`
- Task has unresolved dependencies (blocked)
- Description is missing or unclear

If not ready, report that the task needs planning first.

## Workflow

### 1) Confirm scope and constraints

- Optimize for low overhead and cost while keeping reliability.
- Prefer systemd + nginx + simple scripts over managed services.
- Avoid new dependencies unless they remove operational risk.

### 2) Load the right references

Use these only when relevant:
- `references/entrypoints.md` for the exact files and locations.
- `references/checklists.md` for safe deploy and doc updates.

### 3) Change the pipeline or runtime

- For CI/CD: update `.github/workflows/deploy.yaml` and `build.clj` together.
- For runtime: update `infra/production/etc/systemd/system/*.service` and nginx config.
- Prefer atomic artifact updates (upload tmp, move into place).

### 4) Local deploy parity

- Provide a local HTTPS deploy sandbox that does not conflict with `sprecha.local`.
- Use mkcert and a host like `sprecha.localtest.me` on non-default ports.

### 5) Documentation is part of the change

- Update `docs/ops/server-configuration.md` with any operational change.
- Keep docs minimal but complete: commands, paths, and expectations.

### 6) Verify

- Systemd status: service, restart path, cert renewal timer.
- Nginx config test and reload.
- App endpoint responds via HTTPS.

## Output expectations

- Provide exact file edits and commands.
- Call out risks, required secrets, and any manual steps.
- Prefer one-liner local deploy commands.
- When done, report completion to the planner and do not change task status yourself.
