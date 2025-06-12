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
© 2025. Egor Shundeev, Petr Maslov.
