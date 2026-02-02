## 0. CouchDB Infrastructure
- [x] 0.1 Install CouchDB on production server (latest stable).
- [x] 0.2 Configure admin user with systemd encrypted credentials.
- [x] 0.3 Create and configure `dictionary-db` with public read-only security.
- [x] 0.4 Configure Nginx for CouchDB access with rate limiting.
- [x] 0.5 Document CouchDB + infra runbook and verification in `docs/ops/runbook.md` and `docs/ops/verification.md`.
- [x] 0.6 Add `learning-app-admin-setup` to centralize admin steps (secrets, deployer key, BORG_REPO, cert issuance, systemd enablement).
- [x] 0.7 Make infra deb noninteractive; start services only when required credentials/config exist; remove obsolete checklist.
- [x] 0.8 Add CI deploy workflows for infra/app with integration gating and explicit app restart.

## 1. Pre-requisites
- [x] 1.1 Research CEFR A1-C2 sources and RU translations; document licenses and chosen sources in `docs/dictionary-sources.md`.
- [x] 1.2 Implement a dictionary ingestion script that downloads source data from Kaikki (enwiktionary) and Goethe-Institut CEFR word lists (via sprach-o-mat), outputs JSONL for `dictionary-entry` and `surface-form` plus a manifest (counts, bytes, checksum).
- [x] 1.3 Implement a CouchDB import pipeline that reads JSONL files and imports to `dictionary-db` with size metrics.

## 2. Database Split
- [x] 2.1 Split client storage into `user-db`, `device-db`, and `dictionary-db`; add a one-time migration from `local-db`.
  - Plan: `openspec/changes/add-base-dictionary/database-split-plan.md`.

## 3. Task Runner Updates
- [x] 3.1 Update task runner to use `device-db` and task payloads via `data`; update existing tasks accordingly.
  - Plan:
    - Update task doc shape to use `data` payloads; remove top-level `word-id` usage in task creation/handlers.
    - Add a migration step that rewrites legacy task docs in `device-db`:
      - If a task lacks `data` and has `word-id`, set `data` to `{:word-id <value>}` and remove `word-id`.
      - Keep the migration idempotent by skipping tasks that already have `data`.
    - Update task creation/handlers (example-fetch) to write/read `data`.
    - Update tests (tasks/examples/migrations) to assert `data` payloads and cover the migration.

## 4. Dictionary Sync Module
- [x] 4.1 Implement background dictionary sync module that checks `dictionary-state` and triggers `PouchDB/replicate.from` on initial load.
- [x] 4.2 Implement dictionary loader with public API for initialization and status checks.
  - Plan:
    - Add CLJS helper `replicate-from` in `src/shared/db.cljc` for one-way pull replication with default remote URL, retry/backoff, and replication object return.
    - Implement `dictionary_sync` module with in-memory state (`:idle`/`:syncing`/`:ready`/`:failed`), `loaded?`, and `start-sync!`.
    - **Dropped** BroadcastChannel leader election — the SW is single-instance, so multi-tab coordination is handled by architecture. PouchDB checkpoints make concurrent replication idempotent.
    - **Dropped** separate `init!` — redundant; `ensure-loaded!` is the sole public entry point.
    - Integrate `ensure-loaded!` in SW `activate` handler (`waitUntil` chain after `tasks/start!`) instead of `application.cljs`.
    - Manual verification: fresh install triggers sync, subsequent starts skip, single SW means only one sync runs across tabs.

## 5. Autocomplete
- [ ] 5.0 Extend `db/all-docs` to accept options map (`startkey`, `endkey`, `limit`, `include_docs`).
- [ ] 5.1 Add dictionary autocomplete using `allDocs` key-range query on `surface-form` documents and prefill on exact normalized match.

## 6. Integration
- [ ] 6.1 Integrate autocomplete into add-word flow.
- [ ] 6.2 Initialize dictionary loader in application startup.

## 7. Testing
- [ ] 7.1 Create test documentation for dictionary sync scenarios (fresh install, interruption, offline, no re-sync).
- [ ] 7.2 Create test documentation for autocomplete scenarios (prefix lookup, exact match, case insensitivity, performance).
- [ ] 7.3 Create regression checklist for existing flows after database split.

## 8. Verification
- [ ] 8.1 Measure dictionary DB size and index overhead after import; document in `docs/dictionary-size.md`.
- [ ] 8.2 Smoke test: new install syncs dictionary, autocomplete suggestions appear, add-word prefills canonical value.
