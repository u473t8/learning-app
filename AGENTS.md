# Repository Guidelines

## Project Structure & Module Organization
- `src/backend` runs the Clojure services; SQLite helpers now mostly persist session state while CouchDB-facing endpoints live under `reitit/`.
- `src/client` contains the offline-first PWA plus the service worker entry (`sw.cljs`); Shadow-CLJS writes bundles to `resources/public/js/app/`.
- `src/shared/application.cljc` exposes the router shared by backend and service worker to serve htmx fragments or full pages when JS is absent.
- `src/shared` provides cross-runtime utilities, `dev/` wires REPL tooling, `infra/` stores deployment templates, `resources/public/` serves static assets, and `target/` receives build artifacts.

## Build, Test, and Development Commands
- `clj -T:build uber` cleans and assembles `target/learning-app.jar`.
- `clj -T:build run` boots the packaged backend.
- `clj -M:dev` starts a REPL with debux and CIDER middleware.
- `npm install` refreshes JS dependencies after Node or package updates.
- `npx shadow-cljs watch :app` runs the client watch build; after “Build completed”, repoint the service worker via `ln -sf js/sw/main.js sw.js`.

## Coding Style & Naming Conventions
- Stick to two-space indentation; align maps and `let` bindings as in `src/backend/core.clj`.
- Keep namespaces directory-aligned and `kebab-case`; use `snake_case` for SQL columns and `SCREAMING_SNAKE_CASE` for env vars (`LEARNING_APP_DB_AUTH_SECRET`).
- Remove unused requires and resolve reflection warnings before requesting review.

## Testing & Offline Sync Guidelines
- Backend tests belong in `test/backend/...` with `clojure.test`; mirror client and service worker specs under `test/client/...` or a Shadow-CLJS `:test` build.
- Validate PouchDB ↔ CouchDB sync by adding vocabulary online and confirming Nginx forwards `/db` requests with auth headers.
- Record manual QA (Start Lesson flow, retention ordering, example availability offline) in PRs until automated coverage is in place.

## Commit & Pull Request Guidelines
- Follow `GH-<issue> Short summary` commit messages (e.g., `GH-38 Add vocabulary layer`).
- Group backend, client, and infra work in separate commits when possible to simplify bisects.
- PRs should outline intent, schema or config changes, linked issues, screenshots or terminal captures, and the commands you ran.

## Architecture & Roadmap Notes
- Backend ChatGPT integration generates example sentences when new words arrive; results persist in CouchDB and replicate to PouchDB.
- The service worker intercepts htmx fetches, querying local PouchDB through Reitit interceptors and falling back to backend rendering when offline or JS-disabled.
- Immediate priorities: harden bidirectional sync, refine logout semantics for offline clients, and migrate sentence-generation logic from legacy SQLite paths to the CouchDB pipeline.
