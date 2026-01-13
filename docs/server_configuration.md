# Домен
namecheap.com
Ответственный – @u473t8


<br/><br/><br/>
# Хостинг
hetzner.com
Ответственный – @u473t8


<br/><br/><br/>
# Настройка nginx

## Конфиг
Конфиг хранится `/etc/nginx/sites-available/learning-app`.

## SSL–cертификат
Пока используем Let's Encrypt сертификаты (каковы минусы бесплатных сертификатов для нас)

### Настройки Certbot
```sh
sudo certbot --nginx -d sprecha.de -d www.sprecha.de
sudo certbot renew --dry-run
```

### Автообновление сертификатов
Используется systemd-таймер:

```sh
systemctl status learning-app-certbot.timer
systemctl list-timers | grep learning-app-certbot
```

<br/><br/><br/>
# Прод деплой: runbook

## Fresh server bootstrap (Ubuntu)
1) Базовые пакеты:
```sh
sudo apt-get update
sudo apt-get install -y nginx systemd systemd-sysusers systemd-tmpfiles certbot borgbackup openjdk-21-jre-headless
```

2) Пользователь деплоя:
```sh
sudo adduser --disabled-password --gecos "" deployer
sudo usermod -aG webdev deployer
```

3) SSH доступ для deployer:
```sh
sudo -u deployer mkdir -p /home/deployer/.ssh
sudo -u deployer chmod 700 /home/deployer/.ssh
sudo -u deployer touch /home/deployer/.ssh/authorized_keys
sudo -u deployer chmod 600 /home/deployer/.ssh/authorized_keys
sudo -u deployer sh -c 'echo "ssh-ed25519 AAAA... deployer@ci" >> /home/deployer/.ssh/authorized_keys'
```

4) Установить infra deb:
```sh
sudo dpkg -i learning-app-infra.deb
```
Postinst создаст системных пользователей, установит nginx конфиг, включит systemd юниты,
и запросит секреты (Borg/OpenAI).

5) Проверка сервисов:
```sh
systemctl status learning-app-run.service
systemctl status learning-app-restart.path
systemctl status learning-app-certbot.timer
nginx -t
```

## Deploy (CI/CD pipeline)
CI загружает `target/learning-app.jar` и делает atomic replace:
```sh
mv -f /opt/learning-app/learning-app.jar.tmp /opt/learning-app/learning-app.jar
```
Срабатывает `learning-app-restart.path`, который перезапускает `learning-app-run.service`.

## Rollback
```sh
sudo systemctl stop learning-app-run.service
sudo cp /opt/learning-app/learning-app.jar.backup /opt/learning-app/learning-app.jar
sudo systemctl start learning-app-run.service
```
Перед новым деплоем сохраняйте `learning-app.jar.backup`.

## Secret rotation: OpenAI token
```sh
sudo systemd-ask-password -n "Enter API key for OpenAI" \
  | systemd-creds --name=openai_api_key encrypt - /etc/credstore.encrypted/openai_api_key
sudo systemctl restart learning-app-run.service
```

## Preflight checklist (before merge/deploy)
- `systemctl is-enabled learning-app-run.service`
- `systemctl is-enabled learning-app-restart.path`
- `systemctl is-enabled learning-app-certbot.timer`
- `systemctl status learning-app-run.service`
- `nginx -t`
- `/opt/learning-app` writable by deployer (artifact upload)
- `/etc/credstore.encrypted/openai_api_key` exists (or OPENAI_API_KEY env is set)


<br/><br/><br/>
# Пользователи

## Обычные пользователи 
Имеются ввиду пользователи, которые могут залогиниться извне
### Создать нового пользователя:
```sh
# Создать нового пользователя:
sudo adduser <user name>
```
### Настройка SSH доступа:
```sh
# Переключиться на пользователя
sudo su - <user name>

# Создать папку с эксклюзивным доступом
mkdir -p ~/.ssh
chmod 700 ~/.ssh

# Создать ключи авторизации
touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

# Добавить публичный ключ
echo "ssh-rsa AAAAB3NzaC1yc2E... <user name>@laptop" >> ~/.ssh/authorized_keys

# Вернуться
exit
```

## Системные пользователи
Пользователи для автоматизированного выполнения скриптов на сервере.
```sh
# Создать нового пользователя:
sudo adduser --system --group --shell /bin/bash --home /opt/learning-app webapp
```

## Общие операции с пользователями

Добавить пользователя в группу:
```sh
sudo usermod -aG <group name> <user name>
```

Залогиниться в группу, в которую только что добавили текущего пользователя (без выхода и входа в систему):
```sh
newgrp <group name>
```

Удалить пользователя:
```sh
sudo deluser --remove-home --remove-all-files --remove-mailspool <user name>
sudo groupdel <user name>
```

## Список пользователей
|Пользователь|Домашная папка|Группы|Есть пароль?|SSH доступ|Системный?|
|-|-|-|-|-|-|
|maslov|/home/maslov|sudo, webdev|+|+|-|
|shundeev|/home/shundeev|sudo, webdev|+|+|-|
|webapp|/opt/learning-app|webdev|-|-|+|
|deployer|/home/deployer|webdev|-|+|-|

### Пользователь `webapp`

Почему домашняя папка – `/opt/learning-app`?

Директория `/opt` предназначена для хранения сторонних приложений, установленных вручную и не управляемых пакетным менеджером. См. [Filesystem Hierarchy Standard](https://refspecs.linuxfoundation.org/FHS_3.0/fhs-3.0.html#optAddonApplicationSoftwarePackages).

> ⚠️ Папка `/var/www/` не подходит для хранения приложения.
>
> Поскольку, nginx, по умолчанию, делает содержимое этой папки публичным. То есть, даже для соблюдения минимальной гигиены требуется особая настройка nginx для папки приложения.

#### Дополнительные папки:
```sh
# Папка для  логов
sudo mkdir -p /var/log/learning-app
sudo chown webapp:webapp /var/log/learning-app
```


### Пользователь `deployer`
Пользователь, обеспечивающий деплой. Используется [Github Actions](https://github.com/u473t8/learning-app/blob/master/.github/workflows/deploy.yaml) для деплоя приложения. Вход только по SSH.


### Пользователь `dbadmin`
Администратор базы данных для сторонних сервисов. Роль под вопросом.

**Нужна ли нам эта роль?**

*Нужна:*
- Иметь возможность что-либо экстренно поправить в базе руками. 

*Не нужна:*
- Предполагает конкурентный доступ внешних сервисов и приложения.



<br/><br/><br/>
# Сервисы

## Запуск приложения
`/etc/systemd/system/learning-app-run.service`

```shell
sudo systemctl daemon-reload
sudo systemctl start learning-app.service
```

### Переменные среды для приложения
Переменные хранятся в `/etc/learning-app/environment`
- Абсолютный путь до базы
- Абсолютный путь до приложения
- Токен ChatGPT
Указываем абсолютные пути, чтобы понятнее и надёжнее.

## Перезапуск приложения после деплоя
`/etc/systemd/system/learning-app-restart.service`

`/etc/systemd/system/learning-app-restart.path`

Path unit [наблюдает](https://www.freedesktop.org/software/systemd/man/latest/systemd.path.html#PathExists=) за артефактом приложения.

Служба `learning-app-restart.service` не включена через `systemd enable`, чтобы не выполняться при запуске системы.

## Репликация базы данных
Сервиса нет.

 - [ ] Написать сервис.

## Обновление SSL сертификатов
Сервис установлен в `/etc/systemd/system/learning-app-certbot.*` и запускается таймером.

- `systemctl status learning-app-certbot.timer`
- `systemctl list-timers | grep learning-app-certbot`



<br/><br/><br/>
# База данных
- [ ] Настроить резервное копирование базы (гугл диск или Hetzner Storage Box)
- [ ] Нужен ли нам Write Ahead Log (WAL)?
- [ ] Настроить VACUUM базы и сжатие WAL, если нужно.
- [ ] Настроить логирование в приложени.
