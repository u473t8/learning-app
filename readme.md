# Learning application


## Development

Quick start (local, https://sprecha.local):
1) Follow the "Local Domain Setup (sprecha.local)" section in `docs/dev/development-setup.md` (nginx + mkcert).
2) Run:
```bash
npm install
sqlite3 app.db < initial-setup.sql
npx shadow-cljs watch app  # terminal 1
clj -M:dev -m core         # terminal 2
```
Open https://sprecha.local/ in browser.

Full guide: [docs/dev/development-setup.md](docs/dev/development-setup.md).
© 2025. Egor Shundeev, Petr Maslov.
