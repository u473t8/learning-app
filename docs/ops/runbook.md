# Production Runbook

Goal: get a production server to a running state with the shortest, sequential steps possible.
All steps are idempotent unless marked NON-IDEMPOTENT.

## CI-run steps (infra updates + deploy)

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
