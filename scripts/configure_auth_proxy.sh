#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CRED_FILE="${PROJECT_ROOT}/.server_credentials"
if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

SERVER_IP="${SERVER_IP:-123.108.70.177}"
SERVER_PASS="${SERVER_PASS:-}"
SSHPASS_BIN="$(command -v sshpass 2>/dev/null || echo /opt/homebrew/bin/sshpass)"
SSH_OPTS=(-o StrictHostKeyChecking=no -o PubkeyAuthentication=no -o ConnectTimeout=10 -o PreferredAuthentications=password)

if [ -z "${SERVER_PASS}" ] || [ ! -x "${SSHPASS_BIN}" ]; then
  echo "Missing SERVER_PASS or sshpass" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
REMOTE="root@${SERVER_IP}"
"${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" \
  "${PROJECT_ROOT}/server/pocketbase/netgrey-auth-location.conf" \
  "${REMOTE}:/tmp/netgrey-auth-location.conf"

"${SSHPASS_BIN}" -p "${SERVER_PASS}" ssh "${SSH_OPTS[@]}" "${REMOTE}" 'set -e; cp -a /etc/nginx/sites-available/netgrey /etc/nginx/sites-available/netgrey.auth-backup.$(date +%Y%m%d%H%M%S); python3 - <<"PY"
from pathlib import Path
site = Path("/etc/nginx/sites-available/netgrey")
fragment = Path("/tmp/netgrey-auth-location.conf").read_text()
text = site.read_text()
marker = "    location /api/ {"
if marker not in text:
    raise SystemExit("nginx API location marker missing")
if "location = /auth-api" in text:
    start = text.index("    location = /auth-api")
    end = text.index("    location /api/ {", start)
    text = text[:start] + fragment + "\n" + text[end:]
elif "location ^~ /auth-api/" in text:
    start = text.index("    location ^~ /auth-api/")
    end = text.index("    location /api/ {", start)
    text = text[:start] + fragment + "\n" + text[end:]
else:
    text = text.replace(marker, fragment + "\n" + marker, 1)
site.write_text(text)
PY
nginx -t; systemctl reload nginx; rm -f /tmp/netgrey-auth-location.conf; echo auth_proxy=enabled'

echo "Auth API proxy enabled at https://bananarey.us/auth-api"
