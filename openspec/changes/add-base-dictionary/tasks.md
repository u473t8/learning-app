## 0. CouchDB Infrastructure
- [ ] 0.1 Install CouchDB on production server (latest stable).
- [ ] 0.2 Configure admin user with systemd encrypted credentials.
- [ ] 0.3 Create and configure `dictionary-db` with public read-only security.
- [ ] 0.4 Configure Nginx for CouchDB access with rate limiting.
- [ ] 0.5 Document CouchDB + infra runbook and verification in `docs/ops/runbook.md` and `docs/ops/verification.md`.
- [ ] 0.6 Add `learning-app-admin-setup` to centralize admin steps (secrets, deployer key, BORG_REPO, cert issuance, systemd enablement).
- [ ] 0.7 Make infra deb noninteractive; start services only when required credentials/config exist; remove obsolete checklist.
- [ ] 0.8 Add CI deploy workflows for infra/app with integration gating and explicit app restart.

## 1. Pre-requisites
- [ ] 1.1 Research CEFR A1-C2 sources and RU translations; document licenses and chosen sources in `docs/dictionary-sources.md`.
- [ ] 1.2 Implement a dictionary ingestion script that downloads source data from Kaikki (enwiktionary) and Goethe-Institut CEFR word lists (via sprach-o-mat), outputs JSONL for `dictionary-entry` and `surface-form` plus a manifest (counts, bytes, checksum).
- [ ] 1.3 Implement a CouchDB import pipeline that reads JSONL files and imports to `dictionary-db` with size metrics.

## 2. Database Split
- [ ] 2.1 Split client storage into `user-db`, `device-db`, and `dictionary-db`; add a one-time migration from `local-db`.

## 3. Task Runner Updates
- [ ] 3.1 Update task runner to use `device-db` and task payloads via `data`; update existing tasks accordingly.

## 4. Dictionary Sync Module
- [ ] 4.1 Implement background dictionary sync module that checks `dictionary-state` and triggers `PouchDB/replicate.from` on initial load.
- [ ] 4.2 Implement dictionary loader with public API for initialization and status checks.

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
