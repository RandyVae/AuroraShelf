#!/usr/bin/env bash
set -euo pipefail

# Deploy the isolated PocketBase account service without rebuilding or touching OTA artifacts.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CRED_FILE="${PROJECT_ROOT}/.server_credentials"
if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

SERVER_IP="${SERVER_IP:-123.108.70.177}"
SERVER_PASS="${SERVER_PASS:-}"
POCKETBASE_VERSION="${POCKETBASE_VERSION:-0.40.6}"
POCKETBASE_SHA256="${POCKETBASE_SHA256:-}"
SSHPASS_BIN="$(command -v sshpass 2>/dev/null || echo /opt/homebrew/bin/sshpass)"

if [ -z "${SERVER_PASS}" ] || [ ! -x "${SSHPASS_BIN}" ]; then
  echo "Missing SERVER_PASS or sshpass" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

ARCHIVE="${TMP_DIR}/pocketbase.zip"
curl -fsSL "https://github.com/pocketbase/pocketbase/releases/download/v${POCKETBASE_VERSION}/pocketbase_${POCKETBASE_VERSION}_linux_amd64.zip" -o "${ARCHIVE}"
if [ -n "${POCKETBASE_SHA256}" ]; then
  printf '%s  %s\n' "${POCKETBASE_SHA256}" "${ARCHIVE}" | shasum -a 256 -c -
fi
unzip -oq "${ARCHIVE}" pocketbase -d "${TMP_DIR}"

REMOTE="root@${SERVER_IP}"
SSH_OPTS=(-o StrictHostKeyChecking=no -o PubkeyAuthentication=no -o ConnectTimeout=10 -o PreferredAuthentications=password)

"${SSHPASS_BIN}" -p "${SERVER_PASS}" ssh "${SSH_OPTS[@]}" "${REMOTE}" \
  'set -e; mkdir -p /var/backups/netgrey-auth /var/lib/netgrey-pocketbase/pb_data; if [ -f /etc/nginx/sites-available/netgrey ]; then cp -a /etc/nginx/sites-available/netgrey /var/backups/netgrey-auth/netgrey.$(date +%Y%m%d%H%M%S).conf; fi; if [ -d /var/lib/netgrey-pocketbase/pb_data ]; then tar -czf /var/backups/netgrey-auth/pb_data.$(date +%Y%m%d%H%M%S).tgz -C /var/lib/netgrey-pocketbase pb_data 2>/dev/null || true; fi; id -u netgrey-auth >/dev/null 2>&1 || useradd --system --home /var/lib/netgrey-pocketbase --shell /usr/sbin/nologin netgrey-auth; chown -R netgrey-auth:netgrey-auth /var/lib/netgrey-pocketbase'

"${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${TMP_DIR}/pocketbase" "${REMOTE}:/tmp/pocketbase.netgrey.new"
"${SSHPASS_BIN}" -p "${SERVER_PASS}" ssh "${SSH_OPTS[@]}" "${REMOTE}" 'install -o root -g root -m 755 /tmp/pocketbase.netgrey.new /usr/local/bin/pocketbase; rm -f /tmp/pocketbase.netgrey.new'
"${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/server/pocketbase/netgrey-pocketbase.service" "${REMOTE}:/etc/systemd/system/netgrey-pocketbase.service"

"${SSHPASS_BIN}" -p "${SERVER_PASS}" ssh "${SSH_OPTS[@]}" "${REMOTE}" 'set -e; chmod 755 /usr/local/bin/pocketbase; chown root:root /usr/local/bin/pocketbase; systemctl daemon-reload; systemctl enable --now netgrey-pocketbase; systemctl is-active --quiet netgrey-pocketbase; for attempt in $(seq 1 20); do if curl -fsS http://127.0.0.1:8090/api/health >/dev/null; then echo pocketbase=healthy; exit 0; fi; sleep 1; done; echo pocketbase=healthcheck_failed >&2; exit 1'

echo "PocketBase binary and service deployed. Nginx routing is intentionally a separate step."
