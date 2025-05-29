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
sudo certbot --nginx -d spreha.de
sudo certbot renew --dry-run
``` 


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
`/etc/systemd/system/learning-app.service`

```shell
sudo systemctl daemon-reload
sudo systemctl start learning-app.service
```

### Переменные среды для приложения
Переменные хранятся в `/etc/learning-app/env`
- Абсолютный путь до базы
- Абсолютный путь до приложения
- Токен ChatGPT
Указываем абсолютные пути, чтобы понятнее и надёжнее.


## Репликация базы данных
Сервиса нет.

 - [ ] Написать сервис.


## Перезапуск приложения после деплоя
Сервиса нет.

- [ ] Написать сервис.


## Обновление SSL сертификатов
Сервиса нет.

- [ ] Написать сервис.



<br/><br/><br/>
# База данных
- [ ] Настроить резервное копирование базы (гугл диск или Hetzner Storage Box)
- [ ] Нужен ли нам Write Ahead Log (WAL)?
- [ ] Настроить VACUUM базы и сжатие WAL, если нужно.
- [ ] Настроить логирование в приложени.
