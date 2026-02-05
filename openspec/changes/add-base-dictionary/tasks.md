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
- [x] 4.1 Implement background dictionary sync module that checks `dictionary-meta` and triggers `PouchDB/replicate.from` on initial load.
- [x] 4.2 Implement dictionary loader with public API for initialization and status checks.
  - Plan:
    - Add CLJS helper `replicate-from` in `src/shared/db.cljc` for one-way pull replication with default remote URL, retry/backoff, and replication object return.
    - Implement `dictionary_sync` module with in-memory state (`:idle`/`:syncing`/`:ready`/`:failed`), `loaded?`, and `start-sync!`.
    - **Dropped** BroadcastChannel leader election — the SW is single-instance, so multi-tab coordination is handled by architecture. PouchDB checkpoints make concurrent replication idempotent.
    - **Dropped** separate `init!` — redundant; `ensure-loaded!` is the sole public entry point.
    - Integrate `ensure-loaded!` in SW `activate` handler (`waitUntil` chain after `tasks/start!`) instead of `application.cljs`.
    - Manual verification: fresh install triggers sync, subsequent starts skip, single SW means only one sync runs across tabs.

## 5. Autocomplete
- [x] 5.0 Extend `db/all-docs` to accept options map (`startkey`, `endkey`, `limit`, `include_docs`).
- [x] 5.1 Add dictionary autocomplete using `allDocs` key-range query on `surface-form` documents and prefill on exact normalized match.
  - Plan:
    - Add `db/all-docs` arities to accept an options map and pass it through to PouchDB/CouchDB; keep existing default call unchanged.
    - Create a dictionary autocomplete function that:
      - Normalizes input via `utils/normalize-german`.
      - Queries `dictionary-db` with `startkey "sf:<norm>"`, `endkey "sf:<norm>\uffff"`, `include_docs true`, `limit 50`.
      - Flattens `:entries` from matched `surface-form` docs, dedupes by `:lemma-id`, sorts with exact matches first and then `:rank` desc, and caps suggestions.
      - Detects exact normalized match (doc `:value` equals normalized input) to mark a selectable canonical prefill; only replace input when user selects a suggestion.
      - Batch-fetches `dictionary-entry` docs by `lemma-id` and attaches the first RU translation (if present) to each suggestion.
    - Add a lightweight manual checklist for: prefix lookup, exact match prefill, translation hinting, case/diacritic insensitivity, and performance sanity.

## 6. Integration
- [x] 6.1 Integrate autocomplete into add-word flow.
- [x] 6.2 Initialize dictionary loader in application startup.

### 6. Integration (Plan)

- 6.1 Add RESTful dictionary resource endpoint.
  - Route: `GET /dictionary-entries?value=<text>`
  - Use `dictionary/suggest` with `:dictionary-db` from request context.
  - Return HTML fragment with suggestions + `prefill` metadata + `translation` hints.
- 6.1 Wire autocomplete UI.
  - Add suggestions container next to the German input in:
    - `src/client/views/home.cljs` (quick add)
    - `src/client/views/vocabulary.cljs` (edit mode)
  - Use a `word-autocomplete` custom element to encapsulate behavior and reset logic.
  - HTMX: `hx-get="/dictionary-entries"`, `hx-trigger="input changed delay:200ms"`,
    `hx-target` to the suggestions container, send `value` from input.
  - Keyboard UX: arrows move active item; Tab/Enter select; Escape clears.
  - Selection applies canonical value; translation fills from suggestion when present; no auto-prefill on input.
  - Include `/js/word-autocomplete.js` in base layout and precache in the service worker.
- 6.2 Dictionary loader startup.
  - No UI startup hook.
  - Schedule `dictionary-sync` task from SW after `tasks/start!` and avoid blocking `waitUntil`.

#### 6.x Autocomplete Fixes (UI + Data)

- Fix suggestion list clipping.
  - `.home__panel` has `overflow: hidden`, which clips the dropdown.
  - Make the add panel allow overflow when suggestions are open:
    - Preferred: `.home__panel--add:has(.suggestions:not(:empty)) { overflow: visible; }`
    - Simpler fallback: `.home__panel--add { overflow: visible; }`
- Populate translation on selection.
  - Extend `/dictionary-entries` handler to load `dictionary-entry` docs for suggestions by `lemma-id`.
  - Attach `translation` (first RU translation, or nil) to each suggestion.
- Apply translation in UI.
  - In `views.dictionary/suggestions`, add `data-translation`.
  - On click/selection: set German input, set translation from `data-translation` when present, then focus translation input.
  - Show translation ghost hint when translation input is empty.

#### 6.y Dictionary Sync (Fire-and-Forget)

- [x] Fire `ensure-loaded!` from SW activate handler without awaiting.
  - Sync runs in background, doesn't block app startup.
  - Errors are logged but don't affect activation.
- [x] Keep `loaded?` function for UI status checks.

## 7. Testing
- [x] 7.0 Add automated tests for `dictionary/suggest` (empty input, dedupe, ranking, prefill) in `test/client/dictionary_test.cljs`.
- [ ] 7.1 Create test documentation for dictionary sync scenarios (fresh install, interruption, offline, no re-sync).
- [ ] 7.2 Create test documentation for autocomplete scenarios (prefix lookup, exact match, case insensitivity, performance).
- [ ] 7.3 Create regression checklist for existing flows after database split.

## 8. Verification
- [ ] 8.1 Measure dictionary DB size and index overhead after import; document in `docs/dictionary-size.md`.
- [ ] 8.2 Smoke test: new install syncs dictionary, autocomplete suggestions appear, add-word prefills canonical value.
