#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="/etc/letsencrypt/live/sprecha.de"
DST_DIR="/etc/nginx/ssl/sprecha.de"

install -m 600 -o root -g root "${SRC_DIR}/fullchain.pem" "${DST_DIR}/fullchain.pem"
install -m 600 -o root -g root "${SRC_DIR}/privkey.pem" "${DST_DIR}/privkey.pem"

nginx -t
systemctl reload nginx
