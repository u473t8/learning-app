# Learning application


## &#x1F6E0;&#xFE0E; Конфигурация инфраструктуры ##


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
dpkg-deb --build infra learning-app-infra.deb
```

---

## Запуск сервера

```sh
clj -T:build uber
clj -T:build run
```
### Почему не объеденили build и run

Чтобы не ждать сборки, если нужно просто запустить.
© 2025. Egor Shundeev, Petr Maslov.
