# Project Context

## Purpose
Practice vocabulary without ceremony. The app is offline-first and avoids collecting user data. Registration is optional only for backup and cross-device sync, not required to use the app.

## Tech Stack
- Clojure backend (Ring, HTTP Kit, Reitit)
- ClojureScript frontend (shadow-cljs)
- SQLite for backend storage
- PouchDB (local) + CouchDB (sync)
- Node.js + npm dependencies for client tooling
- Hosting provider: Hetzner

## Project Conventions

### Code Style
- Follow the Clojure Style Guide plus project rules in `docs/process/code-style.md`
- Every required namespace must use an alias; avoid `:refer`
- Function arguments go on the next line after the function name
- Two empty lines between top-level forms
- Map keys sorted alphabetically with `:id`/`:_id` first
- No commas in maps

### Architecture Patterns
- Shared code lives in `src/shared`
- Client code in `src/client`, backend code in `src/backend`
- Offline-first with local data and sync via PouchDB/CouchDB
- UI markup uses Hiccup; BEM conventions exist and will be refined later

### Testing Strategy
- Expanding coverage with unit tests, user scenarios, and Playwright (in progress)
- Clojure tests via `clj -M:test`
- ClojureScript tests via shadow-cljs node tests

### Git Workflow
- Conventional Commits
- Short-lived feature branches

## Domain Context
- Language learning app focused on fast, playful practice
- Guest/offline usage is first-class; minimal friction to start learning
- Data model documented in `openspec/specs/data-model/spec.md`

## Important Constraints
- Do not collect user data
- Offline-first is mandatory
- No registration required; backup/sync must be optional

## External Dependencies
- None beyond infrastructure services (CouchDB for sync)
