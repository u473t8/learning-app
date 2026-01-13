# Domains

**Registrar:** namecheap.com (owner: @u473t8)

# Hosting

**Provider:** hetzner.com (owner: @u473t8)

# Nginx

## Config
The canonical site config lives at `/etc/nginx/sites-available/learning-app.conf` and should be
symlinked to `/etc/nginx/sites-enabled/learning-app.conf`.

## SSL certificate
We use Let's Encrypt with a deploy hook that copies certs into a root‑only nginx directory.

### Certbot
```sh
sudo certbot --nginx -d sprecha.de -d www.sprecha.de
sudo certbot renew --dry-run
```

### Nginx cert path (root-only)
Nginx must read certs from `/etc/nginx/ssl/sprecha.de/`:

```
ssl_certificate     /etc/nginx/ssl/sprecha.de/fullchain.pem;
ssl_certificate_key /etc/nginx/ssl/sprecha.de/privkey.pem;
```

**Why this exists**
- Certbot writes nginx configs that point to `/etc/letsencrypt/live/...`.
- Those files are root-owned and too permissive to expose to worker users.
- We keep a root-only directory for nginx and copy certs there on every renewal.

**Nginx processes and key access**
- Nginx runs a **master process** as root and forks **worker processes** (usually `www-data`).
- The master reads the TLS private key at startup/reload; workers handle client traffic.
- Workers are exposed to untrusted input, so if a worker is compromised it could leak any readable key.
- The master has a smaller attack surface and does not handle requests directly, so keeping the key root-only limits exposure.

**How it is automated**
- The infra deb installs a deploy hook that copies certs from `/etc/letsencrypt/live/sprecha.de`
  into `/etc/nginx/ssl/sprecha.de` and reloads nginx.
- The deb postinst rewrites nginx SSL paths from `/etc/letsencrypt/live/...` to
  `/etc/nginx/ssl/sprecha.de/...` so certbot’s default config is corrected after issuance.

### Auto-renewal
Systemd timer:

```sh
systemctl status learning-app-certbot.timer
systemctl list-timers | grep learning-app-certbot
```

# Production deploy: runbook

## Fresh server bootstrap (Ubuntu)
1) Deployer user:
```sh
sudo adduser --disabled-password --gecos "" deployer
```

3) SSH access for deployer:
```sh
sudo -u deployer mkdir -p /home/deployer/.ssh
sudo -u deployer chmod 700 /home/deployer/.ssh
sudo -u deployer touch /home/deployer/.ssh/authorized_keys
sudo -u deployer chmod 600 /home/deployer/.ssh/authorized_keys
sudo -u deployer sh -c 'echo "ssh-ed25519 AAAA... deployer@ci" >> /home/deployer/.ssh/authorized_keys'
```

2) Install infra deb:
```sh
sudo dpkg -i learning-app-infra.deb || sudo apt-get -f install
```
Postinst creates system users, installs nginx config, sets up certbot hook + timer, enables systemd units, and prompts for secrets (Borg/OpenAI).

3) Service checks:
```sh
systemctl status learning-app-run.service
systemctl status learning-app-restart.path
systemctl status learning-app-certbot.timer
nginx -t
```

## Deploy (CI/CD pipeline)
CI uploads `target/learning-app.jar` and performs an atomic replace:
```sh
mv -f /opt/learning-app/learning-app.jar.tmp /opt/learning-app/learning-app.jar
```
This triggers `learning-app-restart.path`, which restarts `learning-app-run.service`.

## Rollback
```sh
sudo systemctl stop learning-app-run.service
sudo cp /opt/learning-app/learning-app.jar.backup /opt/learning-app/learning-app.jar
sudo systemctl start learning-app-run.service
```
Keep a `learning-app.jar.backup` before each deploy.

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
- `/etc/credstore.encrypted/openai_api_key` exists (or `OPENAI_API_KEY` env is set)

# Users

## Interactive users
Users who can log in remotely.

Create a user:
```sh
sudo adduser <user>
```

SSH access setup:
```sh
sudo su - <user>
mkdir -p ~/.ssh
chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
echo "ssh-ed25519 AAAA... <user>@laptop" >> ~/.ssh/authorized_keys
exit
```

## System users
Used for automated services.

```sh
sudo adduser --system --group --shell /bin/bash --home /opt/learning-app webapp
```

## Common user operations

Add to group:
```sh
sudo usermod -aG <group> <user>
```

Apply new group without logout:
```sh
newgrp <group>
```

Remove a user:
```sh
sudo deluser --remove-home --remove-all-files --remove-mailspool <user>
sudo groupdel <user>
```

## Known users (current)
| User | Home | Groups | Password | SSH | System |
| - | - | - | - | - | - |
| maslov | /home/maslov | sudo | yes | yes | no |
| shundeev | /home/shundeev | sudo | yes | yes | no |
| webapp | /opt/learning-app | (system) | no | no | yes |
| deployer | /home/deployer | (manual) | no | yes | no |

### webapp
Why `/opt/learning-app`?

`/opt` is for manually installed third‑party apps. See the [Filesystem Hierarchy Standard](https://refspecs.linuxfoundation.org/FHS_3.0/fhs-3.0.html#optAddonApplicationSoftwarePackages).

> `/var/www/` is not suitable: nginx serves that directory by default, which increases exposure.

Extra directories:
```sh
sudo mkdir -p /var/log/learning-app
sudo chown webapp:webapp /var/log/learning-app
```

### deployer
The deployer user is used by GitHub Actions for deployment (SSH only).

### dbadmin
Role under consideration. Avoid unless we need manual DB fixes outside the app.

# Services

## App service
`/etc/systemd/system/learning-app-run.service`

```sh
sudo systemctl daemon-reload
sudo systemctl start learning-app-run.service
```

### Environment variables
Primary env file: `/etc/environment.d/learning-app.conf`.
Optional override: `/etc/learning-app/environment`.

Expected values:
- Absolute DB path
- OpenAI API key (via systemd creds or `OPENAI_API_KEY` env)

## Restart on deploy
`/etc/systemd/system/learning-app-restart.service`
`/etc/systemd/system/learning-app-restart.path`

The path unit watches the jar artifact and restarts the service.

## DB backup
Systemd units: `learning-app-backup-db.service` and `learning-app-backup-db.timer`.

Note: configure the Borg repo path for production use.

## SSL renew
Systemd unit: `learning-app-certbot.*` with timer.
