# Decisions

- Lesson state lives in `device-db`.
- Migration is copy-only; `local-db` is preserved.
- Completion marker is written only after a successful migration.
- Migration auto-retries with exponential backoff (cap 60s).
- A migration shell renders during migration with HTMX auto-refresh.
- Task runner uses `device-db`.
- SW update uses `BroadcastChannel` (not PouchDB) for capability detection between active and waiting SWs. Direct messaging, no DB reads/writes, no cleanup needed.
- Dictionary import tool sets `_security` with empty members after DB creation to ensure public read access.
