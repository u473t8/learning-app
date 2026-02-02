# Decisions

- Lesson state lives in `device-db`.
- Migration is copy-only; `local-db` is preserved.
- Completion marker is written only after a successful migration.
- Migration auto-retries with exponential backoff (cap 60s).
- A migration shell renders during migration with HTMX auto-refresh.
- Task runner uses `device-db`.
