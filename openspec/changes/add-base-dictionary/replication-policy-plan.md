# Replication Policy Gate Plan

## Goals
- Use a server-driven policy to allow or block replication by client version.
- Block replication on policy errors or denials.
- Store policy check results in device-db.
- Keep user experience silent for now.

## Server Policy Endpoint
- Add a lightweight endpoint (e.g., `GET /api/replication-policy`).
- Response example:
  ```json
  {
    "allowed": true,
    "minVersion": "2026.02.01",
    "policyVersion": "v1"
  }
  ```
- Server performs version check and returns `allowed`.

## Client Version Source
- Recommended: build-time constant via Shadow CLJS `:closure-defines`.
- Alternative options: git SHA, manifest field, or a hardcoded constant.

## Client Gate
- Add a client module (e.g., `src/client/replication_policy.cljs`).
- Fetch policy before starting dictionary replication.
- If `allowed: true`, proceed to `PouchDB/replicate.from`.
- If `allowed: false` or fetch fails, skip replication (silent block).

## Policy State Storage (device-db)
- Store policy decision for diagnostics only.
- Doc example:
  ```json
  {
    "_id": "replication-policy:dictionary",
    "type": "replication-policy",
    "allowed": false,
    "checked-at": "2026-02-01T12:00:00.000Z",
    "server-min-version": "2026.02.01",
    "client-version": "2026.01.15",
    "reason": "version-too-old"
  }
  ```

## Failure Behavior
- Policy endpoint request failures are treated as `allowed: false`.
- Replication remains blocked until a successful allow response.

## Tests
- Unit tests for policy module: allow, deny, and error paths.
- Integration test: replication should not start when policy denies or fails.
