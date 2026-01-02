# Development Setup

This guide describes how to set up and run the Learning App in development mode.

## Prerequisites

### Required Software

1. **Java 21+** - Required for Clojure
2. **Clojure CLI** - [Installation guide](https://clojure.org/guides/install_clojure)
3. **Node.js 18+** - Required for shadow-cljs and npm dependencies
4. **SQLite3** - Usually pre-installed on macOS/Linux
5. **CouchDB** - Document database for data synchronization

### Installing CouchDB

**macOS (Homebrew):**
```bash
brew install couchdb
```

**Ubuntu/Debian:**
```bash
sudo apt-get install couchdb
```

**Other platforms:** See the [official CouchDB installation guide](https://docs.couchdb.org/en/stable/install/index.html)

#### CouchDB Admin Setup (Required)

CouchDB requires an admin account before starting. Uncomment the default admin line in the config file:

**macOS (Homebrew):**

Edit `/opt/homebrew/opt/couchdb/etc/local.ini` and uncomment the admin line in the `[admins]` section:

```ini
[admins]
admin = mysecretpassword
```

CouchDB will hash the password on first start.

> **Note:** For production, use a strong password and configure it before deployment.

## Setup Steps

### 1. Install npm dependencies

```bash
npm install
```

### 2. Initialize the SQLite database

```bash
sqlite3 app.db < initial-setup.sql
```

### 3. Start CouchDB

Run directly in a terminal:
```bash
couchdb
```

> **Note:** `brew services start couchdb` may fail with I/O errors. Running `couchdb` directly is more reliable.

Verify CouchDB is running:
```bash
curl http://localhost:5984/
```

CouchDB web interface (Fauxton) is available at: http://localhost:5984/_utils/

### 4. Start shadow-cljs (ClojureScript compiler)

```bash
npx shadow-cljs watch app
```

Wait for the message: `Build completed`

This starts:
- Dev server at http://localhost:9630
- nREPL server on port 4444

### 5. Start the backend server

In a separate terminal:

```bash
clj -M:dev -m core
```

The backend server runs at: **http://127.0.0.1:8083/**

> **Note:** Use `127.0.0.1` instead of `localhost` in Safari to avoid cookie issues.

## Creating a Test User

Run this command to create a test user (login: `test`, password: `test123`):

```bash
clj -M:dev -e '
(require (quote [buddy.hashers :as hashers]))
(require (quote [next.jdbc :as jdbc]))
(let [db {:dbtype "sqlite" :dbname "app.db"}
      password-hash (hashers/derive "test123" {:alg :argon2id})]
  (jdbc/execute! db ["INSERT INTO users (name, password) VALUES (?, ?)" "test" password-hash]))
'
```

## Summary

| Service | URL | Port |
|---------|-----|------|
| Backend | http://127.0.0.1:8083/ | 8083 |
| shadow-cljs | http://localhost:9630/ | 9630 |
| nREPL | - | 4444 |
| CouchDB | http://localhost:5984/ | 5984 |
| CouchDB UI (Fauxton) | http://localhost:5984/_utils/ | 5984 |

## Troubleshooting

### "Can't connect to server" in Safari
Use `http://127.0.0.1:8083/` instead of `http://localhost:8083/`

### Service Worker registration fails
Clear browser cache and reload. The `Service-Worker-Allowed` header is required for the sw.js file.

### CouchDB connection errors in logs
These errors appear when CouchDB is not running. Start CouchDB with `brew services start couchdb` or `couchdb`.

### "no such table: sessions" error
Run the database initialization: `sqlite3 app.db < initial-setup.sql`
