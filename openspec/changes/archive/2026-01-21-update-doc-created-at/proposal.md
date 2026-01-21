# Change: Require created-at on all data documents

## Why
The data model requires creation timestamps on only some documents, which makes auditing and sync reconciliation inconsistent across doc types.

## What Changes
- **BREAKING** require ISO 8601 `created-at` on every document type except lessons (lessons use `started-at`).
- Add `created-at` to example document shapes.
- Require `created-at` on dead-lettered task documents alongside `failed-at`.

## Impact
- Affected specs: data-model
- Affected code: local storage, migrations, fixtures/tests
