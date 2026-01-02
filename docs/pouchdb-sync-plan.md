# Client PouchDB Sync Plan

## Goal
Keep client PouchDB databases in sync with CouchDB only when the client is online. The service worker owns sync orchestration.

## Plan
- Extend `db/sync` to accept options and return a sync handle.
- Add a service worker sync manager with start/stop and online/offline listeners.
- Start sync after user registration and stop sync when offline or on logout.
- QA: create data offline, go online, confirm CouchDB replication and resume behavior.
- Verify Nginx forwards `/db` auth headers on each sync request.
