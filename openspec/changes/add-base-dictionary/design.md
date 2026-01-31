## Context
The app needs a predefined German dictionary to support autocomplete, canonical spelling, and best-effort error checking when users add vocabulary. Updates to base dictionary are rare and should not require polling. The app remains offline-first and avoids collecting user data.

## Goals / Non-Goals
- Goals:
  - Provide a base German dictionary (CEFR A1-C2) with RU translations and metadata.
  - Fast autocomplete that matches inflected forms and suggests canonical values.
  - Load dictionary from public CouchDB server with automatic retry on interruption.
  - Keep user data (vocab/reviews) syncable and separate from device-only data.

- Non-Goals:
  - Full morphological analysis or exhaustive conjugation tables.
  - Automatic translation frequency inference without source data.
  - UI for dictionary sync (happens silently in background).
  - Dictionary write access for clients (read-only for public content).

## Decisions
- Separate databases:
  - `user-db`: vocab + reviews (local only, syncable in future).
  - `device-db`: tasks + examples (local only).
  - `dictionary-db`: dictionary entries/forms/state (synced from public server).

- CouchDB infrastructure:
  - Install CouchDB latest stable on production server.
  - Admin credentials stored via systemd encrypted creds.
  - `dictionary-db` is public read-only (no auth required for clients).
  - User databases (future) use proxy auth via `/auth/check`.

- Nginx configuration:
  - Route `/db/*` to CouchDB.
  - Rate limiting: 10 req/s, burst 10.
  - No authentication for `dictionary-db`.
  - Proxy auth for user databases (future).

- Use two document types in `dictionary-db`:
  - `dictionary-entry` for canonical values and translations.
  - `surface-form` for fast prefix lookup of inflected forms.

- Document IDs encode lookup keys:
  - Entry docs use `_id: "lemma:<normalized-value>:<pos>"` (deterministic, handles homonyms via article).
  - Surface-form docs use `_id: "sf:<normalized-form>"`.
  - Enables `allDocs` key-range queries on the built-in primary B-tree index.

- Surface-form docs inline entry data:
  - Each `sf:` doc contains `entries[]` with denormalized `{lemma-id, lemma, rank}`.
  - Eliminates N+1 lookups during autocomplete (one query returns display-ready data).
  - Safe because dictionary is server-generated, read-only on client — no consistency risk.

- CEFR-based ranking:
  - Each entry includes a `rank` field (integer).
  - Computed server-side from CEFR level (A1 highest) and optionally word frequency.
  - Autocomplete results sorted by rank after deduplication.
  - Ensures common/beginner words appear first.

- Store `normalized-value` (and normalized form values) for indexed lookup; keep canonical `value` for display.

- Task documents use `data` payloads; `word-id` moves into `data` when needed.

- Prefill add-word input only on exact normalized match; fuzzy matches are suggestions only.

- Dictionary sync strategy:
  - Check `dictionary-state` document on app start.
  - If not loaded, trigger `PouchDB/replicate.from` with `live: false`.
  - Sync runs in background, non-blocking.
  - PouchDB handles interruptions, retries, and checkpoints automatically.
  - No UI feedback, dev-only logging.

- Database migration:
  - Migrate `local-db` to `user-db` + `device-db`.
  - Keep `local-db` for safety (remove later).
  - Migration marker prevents repeated runs.

- Testing approach:
  - Test documentation with manual QA scenarios.
  - No automated tests for this initial implementation.

## Alternatives considered
- Single database with `type` filters (rejected: sync bloat and tighter coupling).
- HTTP chunked loading with retry (rejected: CouchDB replication handles this natively with less complexity).
- Static token authentication (rejected: Overkill for public read-only dictionary).
- Per-install tokens (rejected: Unnecessary complexity for public content).
- CouchDB backup for dictionary (rejected: Re-import from source files is simpler).
- `find()` with secondary index on `normalized-value` for autocomplete (rejected: cold start problem — first query after replication triggers full index rebuild of ~150K docs; `allDocs` key-range uses the built-in primary B-tree with zero index overhead).
- Foreign-key reference (`base-id`) in form docs requiring second lookup to resolve entries (rejected: N+1 performance cost during autocomplete; denormalized `entries[]` in surface-form docs eliminates secondary lookups).
- Two separate databases for dictionary entries and surface-forms (rejected: adds operational complexity with no benefit; single `dictionary-db` with `_id` prefix conventions provides equivalent query isolation).

## Risks / Trade-offs
- Dictionary size and index overhead increase storage usage; mitigated with sizing reports and measured import.
- Source licensing may restrict redistribution; mitigated by a source/license matrix before ingestion.
- More DBs add operational complexity; mitigated with clear module boundaries and naming.
- Public read-only access means dictionary can be scraped; mitigated by Nginx rate limiting.
- CouchDB server required (not previously present); mitigated by straightforward setup and alignment with future user sync.

## Migration Plan
- On first run after the change, migrate existing `local-db` content:
  - Move vocab + reviews to `user-db`.
  - Move tasks + examples to `device-db`.
  - Leave a marker doc to avoid repeated migration.
- Keep `local-db` for fallback until migration completes; delete later if safe.
