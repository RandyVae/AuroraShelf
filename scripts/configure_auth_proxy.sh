#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CRED_FILE="${PROJECT_ROOT}/.server_credentials"
if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

: "${SERVER_IP:?Missing SERVER_IP in .server_credentials or env}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-}"

SSH_OPTS=(
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
  -o ControlMaster=auto
  -o ControlPath=/tmp/netgrey-proxy-%r@%h:%p
  -o ControlPersist=60s
)
ASKPASS_SCRIPT=""
cleanup_ssh_helpers() {
  if [ -n "${ASKPASS_SCRIPT}" ] && [ -f "${ASKPASS_SCRIPT}" ]; then
    rm -f "${ASKPASS_SCRIPT}"
  fi
}
trap cleanup_ssh_helpers EXIT

if [ -n "${SERVER_PASS}" ]; then
  SSH_OPTS+=(-o PubkeyAuthentication=no)
  ASKPASS_SCRIPT="$(mktemp)"
  chmod 700 "${ASKPASS_SCRIPT}"
  cat > "${ASKPASS_SCRIPT}" <<'EOF'
#!/bin/sh
printf '%s\n' "${SSHPASS}"
EOF
fi

REMOTE="${SERVER_USER}@${SERVER_IP}"

run_ssh() {
  local cmd="$1"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" SSH_ASKPASS="${ASKPASS_SCRIPT}" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
      ssh "${SSH_OPTS[@]}" "${REMOTE}" "${cmd}"
  else
    ssh "${SSH_OPTS[@]}" "${REMOTE}" "${cmd}"
  fi
}

run_scp() {
  local src="$1"
  local dest="$2"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" SSH_ASKPASS="${ASKPASS_SCRIPT}" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
      scp "${SSH_OPTS[@]}" "${src}" "${REMOTE}:${dest}"
  else
    scp "${SSH_OPTS[@]}" "${src}" "${REMOTE}:${dest}"
  fi
}

run_scp "${PROJECT_ROOT}/server/pocketbase/netgrey-auth-location.conf" "/tmp/netgrey-auth-location.conf"

run_ssh 'set -e
mkdir -p /var/backups/netgrey-auth
BACKUP_FILE="/var/backups/netgrey-auth/nginx.netgrey.$(date +%Y%m%d%H%M%S).conf"
cp -a /etc/nginx/sites-available/netgrey "${BACKUP_FILE}"
ls -1dt /var/backups/netgrey-auth/nginx.netgrey.*.conf 2>/dev/null | tail -n +6 | xargs -r rm -f
rm -f /etc/nginx/sites-available/netgrey.auth-backup.* 2>/dev/null || true

python3 - <<"PY"
from pathlib import Path

site = Path("/etc/nginx/sites-available/netgrey")
fragment = Path("/tmp/netgrey-auth-location.conf").read_text().rstrip() + "\n"
text = site.read_text()

begin_marker = "    # BEGIN NETGREY AUTH & STATIC HARDENING"
end_marker = "    # END NETGREY AUTH & STATIC HARDENING"
plain_api_marker = "    location /api/ {"
pref_api_marker = "    location ^~ /api/ {"

if plain_api_marker in text:
    text = text.replace(plain_api_marker, pref_api_marker, 1)

api_marker = pref_api_marker
if api_marker not in text:
    raise SystemExit("nginx API location marker missing")

if begin_marker in text and end_marker in text:
    start = text.index(begin_marker)
    end = text.index(end_marker, start) + len(end_marker)
    if end < len(text) and text[end:end + 1] == "\n":
        end += 1
    text = text[:start] + fragment + text[end:]
else:
    legacy_starts = [
        "    location ^~ /auth-api/_/ {",
        "    location = /auth-api {",
        "    location ^~ /auth-api/ {",
    ]
    matched_start = None
    for candidate in legacy_starts:
        if candidate in text:
            idx = text.index(candidate)
            if matched_start is None or idx < matched_start:
                matched_start = idx
    if matched_start is not None:
        end = text.index(api_marker, matched_start)
        text = text[:matched_start] + fragment + "\n" + text[end:]
    else:
        text = text.replace(api_marker, fragment + "\n" + api_marker, 1)

site.write_text(text)
PY

if ! nginx -t; then
  cp -a "${BACKUP_FILE}" /etc/nginx/sites-available/netgrey
  rm -f /tmp/netgrey-auth-location.conf
  exit 1
fi
systemctl reload nginx
rm -f /tmp/netgrey-auth-location.conf
echo auth_proxy=enabled'

echo "Auth API allowlist proxy enabled at https://bananarey.us/auth-api"
