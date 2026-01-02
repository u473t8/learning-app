# Learning application


## &#x1F6E0;&#xFE0E; Конфигурация прод инфраструктуры ##


```shell
dpkg --install learning-app-infra.deb
```

> TODO: добавить настройку BorgBackup репозитория.

### Создание deb package ###

#### Задать разрешения скриптов ####

```shell
chmod +x infra/usr/share/learning-app/backup.sh
chmod +x infra/DEBIAN/postinstall
chmod +x infra/postrm
```

#### Собрать deb ####

```shell
dpkg-deb --build infra/production learning-app-infra.deb
```

---

## Development

See [docs/development-setup.md](docs/development-setup.md) for the complete development setup guide.

### Quick Start

```bash
# 1. Install dependencies
npm install

# 2. Initialize database
sqlite3 app.db < initial-setup.sql

# 3. Start CouchDB
brew services start couchdb  # macOS

# 4. Start shadow-cljs (terminal 1)
npx shadow-cljs watch app

# 5. Start backend (terminal 2)
clj -M:dev -m core
```

Open http://127.0.0.1:8083/ in browser.

### Production Build

```shell
clj -T:build uber
clj -T:build run
```
© 2025. Egor Shundeev, Petr Maslov.
