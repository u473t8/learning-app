# Production Runbook

Goal: get a production server to a running state with the shortest, sequential steps possible.
All steps are idempotent unless marked NON-IDEMPOTENT.

## CI-run steps (infra updates + deploy)

Merge to `master` triggers `.github/workflows/deploy-master.yml`.
The workflow enforces this order:
- Deploy infra first when `infra/production/**` changes.
- Deploy app and dictionary only after infra readiness checks pass.
- Run app and dictionary deploy in parallel.
- If infra changed, app and dictionary deploy are forced even when their own paths are unchanged.

1) Install or upgrade the infra package (idempotent).
```sh
sudo dpkg -i learning-app-infra.deb || sudo apt-get -f install
```
Notes: postinst is noninteractive. It configures users, tmpfiles, nginx config, certbot hook, and systemd units. It skips starting the app and backups if secrets are missing.

2) Upload the new app artifact and atomically replace it (idempotent).
```sh
mv -f /opt/learning-app/learning-app.jar.tmp /opt/learning-app/learning-app.jar
```
This triggers `learning-app-restart.path`, which restarts `learning-app-run.service`.

3) Upload dictionary artifact/data and run import (idempotent).
```sh
systemctl start learning-app-dictionary-import.service
```
The import validates input checksums and writes `/var/lib/learning-app/dictionary/import-metrics.json`.

## Infra deploy failed: manual recovery

If infra deployment fails in GitHub Actions, `deploy-master` stops before app/dictionary deploy. This is expected and safe.

CI prerequisite: deploy SSH user must have passwordless sudo for deploy commands (`dpkg`, `apt-get`, `systemctl`, `nginx`).

Quick fix when logs show `sudo: a terminal is required`:
```sh
sudo visudo -f /etc/sudoers.d/learning-app-deployer-ci
# add exactly this line and save:
# deployer ALL=(root) NOPASSWD: /usr/bin/dpkg, /usr/bin/apt-get, /usr/bin/systemctl, /usr/sbin/nginx
sudo chmod 440 /etc/sudoers.d/learning-app-deployer-ci
sudo visudo -cf /etc/sudoers.d/learning-app-deployer-ci
sudo -u deployer sudo -n systemctl --version >/dev/null
```

1) Inspect server state.
```sh
sudo systemctl status couchdb.service --no-pager
sudo journalctl -u couchdb.service -n 200 --no-pager
sudo nginx -t
sudo systemctl cat learning-app-dictionary-import.service
```

2) Fix issues directly on the server (packages, config, credentials, permissions).

3) Re-run deployment.
```sh
# option A: re-run failed jobs in the same GitHub Actions run
# option B: merge/push a new commit to master
```

4) Verify after recovery.
```sh
curl -sf http://127.0.0.1:5984/ >/dev/null
curl -sf --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/dictionary-db/dictionary-meta >/dev/null
```

## Manual admin steps (bootstrap + secrets + one-off ops)

Run the admin setup script (idempotent, safe to re-run).
```sh
sudo learning-app-admin-setup --bootstrap --issue-cert
```
Notes:
- Command path: `/usr/local/bin/learning-app-admin-setup` (symlink to `/usr/share/learning-app/admin-setup.sh`).
- The script prompts for missing secrets, deployer SSH key, and `BORG_REPO`.
- If the command is missing, run the CI step that installs the infra deb first.
- `--bootstrap` installs base packages and the CouchDB repo. NON-IDEMPOTENT: CouchDB package prompts once for admin setup.
- On changes, the script reloads nginx when certs are issued and restarts the app when OpenAI credentials change.
- For GitHub-hosted runners, use SSH key restrictions without `from=`:
  `no-agent-forwarding,no-port-forwarding,no-pty,no-X11-forwarding`.
- If DNS/ports are not ready, run without `--issue-cert` and re-run later with the flag.
- Secret rotation is explicit: `--rotate-openai`, `--rotate-couchdb`, `--rotate-borg`.
Examples:
```sh
sudo learning-app-admin-setup --bootstrap --issue-cert
sudo learning-app-admin-setup
sudo learning-app-admin-setup --rotate-openai
```

Verification: see `docs/ops/verification.md`.
