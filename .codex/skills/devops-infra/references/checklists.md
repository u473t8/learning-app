# Infra Checklists

## Production change checklist

- Identify which unit(s) change: systemd, nginx, env file, certbot.
- If changing the artifact path or name, update:
  - systemd unit
  - restart path unit
  - deploy workflow
- Prefer atomic artifact replacement (upload tmp, move into place).
- Validate nginx (`nginx -t`) before reload.
- Confirm systemd reload and status:
  - `systemctl daemon-reload`
  - `systemctl status learning-app-run.service`
- Verify HTTPS endpoint.

## Local deploy checklist

- Use mkcert and a host that does not collide with `sprecha.local`.
- Bind on non-default ports to avoid running dev server conflicts.
- Keep local stack to app + nginx (no extra services unless required).
- Provide a one-liner that builds the jar and starts containers.

## CI/CD change checklist

- Ensure `build.clj` and `deploy.yaml` agree on entrypoint.
- Keep Java version consistent with production.
- Avoid manual steps in pipeline where possible.
