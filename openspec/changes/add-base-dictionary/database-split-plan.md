# Database Split + Migration Plan

## Goals
- Split local storage into `user-db`, `device-db`, and `dictionary-db`.
- Migrate legacy `local-db` data without deletion (copy-only).
- Preserve Wortschatz at all costs.
- Ensure migration is idempotent and safe across restarts.

## Data Mapping
- `user-db`: `vocab`, `review`
- `device-db`: `task`, `example`, `lesson`
- `dictionary-db`: dictionary entries/forms/state (already separate)

## Implementation Outline
1. **DB accessors**
   - Add `src/client/dbs.cljs` with shared DB names and `db/use` helpers.

2. **Migration runner**
   - Add `src/client/db_migrations.cljs`.
   - Copy docs from `local-db` into target DBs based on `:type`.
   - Strip `:_rev` before insert.
   - Skip conflicts (already migrated).
   - Write marker doc in `device-db` after successful copy:
     - `_id: "migration:local-db-split"`
   - Never delete from `local-db`.

3. **Startup gating**
   - Run migration during SW `activate` before `tasks/start!`.
   - Add a request interceptor to ensure migration before app handlers.

4. **Update call sites**
   - `application.cljs` injects `user-db` + `device-db` into request context.
   - `lesson.cljs` uses `user-db` for vocab/reviews and `device-db` for lesson state.
   - `tasks.cljs` uses `device-db`.

5. **Tests + fixtures**
   - Update fixtures to create both DBs.
   - Seed vocab/reviews into `user-db`, examples into `device-db`.

## Safety
- Migration is copy-only and idempotent.
- Marker prevents re-running on subsequent loads.
- `local-db` is preserved for fallback or recovery.
