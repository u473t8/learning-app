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

## Разработка ##

### Запуск СouchDB сервера ###

TBD

### Запуск Nginx конфигурации, настройка хоста ###

TBD

### Сборка и запуск сервера ###

```shell
clj -T:build uber
clj -T:build run
```

#### Почему не объеденили build и run ####

Чтобы не ждать сборки, если нужно просто запустить.

### Cборка клиента ###

```shell
npm install
npx shadow-clj watch :app
```

В отдельном терминале после того, как shadow-cls сообщит, что Build completed:
```shell
ln -s js/sw/main.js sw.js
```
© 2025. Egor Shundeev, Petr Maslov.
