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

: "${SERVER_IP:?Missing SERVER_IP in .server_credentials or env}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-}"

DEFAULT_PB_VERSION="0.40.6"
DEFAULT_PB_SHA256="0e1f609158ed945f809f8a02ae012aa0427129c66ebb63c1aee76f2c0309373f"
POCKETBASE_VERSION="${POCKETBASE_VERSION:-${DEFAULT_PB_VERSION}}"
if [ "${POCKETBASE_VERSION}" = "${DEFAULT_PB_VERSION}" ]; then
  POCKETBASE_SHA256="${POCKETBASE_SHA256:-${DEFAULT_PB_SHA256}}"
else
  POCKETBASE_SHA256="${POCKETBASE_SHA256:-}"
fi

SSH_OPTS=(
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
  -o ControlMaster=auto
  -o ControlPath=/tmp/netgrey-auth-%r@%h:%p
  -o ControlPersist=60s
)
SSHPASS_BIN=""
if [ -n "${SERVER_PASS}" ]; then
  SSH_OPTS+=(-o PubkeyAuthentication=no)
  SSHPASS_BIN="$(command -v sshpass 2>/dev/null || echo /opt/homebrew/bin/sshpass)"
  if [ ! -x "${SSHPASS_BIN}" ]; then
    echo "Missing sshpass executable required for SERVER_PASS authentication" >&2
    exit 1
  fi
fi

REMOTE="${SERVER_USER}@${SERVER_IP}"

run_ssh() {
  local cmd="$1"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" "${SSHPASS_BIN}" -e ssh "${SSH_OPTS[@]}" "${REMOTE}" "${cmd}"
  else
    ssh "${SSH_OPTS[@]}" "${REMOTE}" "${cmd}"
  fi
}

run_scp() {
  local src="$1"
  local dest="$2"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" "${SSHPASS_BIN}" -e scp "${SSH_OPTS[@]}" "${src}" "${REMOTE}:${dest}"
  else
    scp "${SSH_OPTS[@]}" "${src}" "${REMOTE}:${dest}"
  fi
}

TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

ARCHIVE="${TMP_DIR}/pocketbase.zip"
curl -fsSL "https://github.com/pocketbase/pocketbase/releases/download/v${POCKETBASE_VERSION}/pocketbase_${POCKETBASE_VERSION}_linux_amd64.zip" -o "${ARCHIVE}"

if [ -n "${POCKETBASE_SHA256}" ]; then
  if command -v shasum >/dev/null 2>&1; then
    printf '%s  %s\n' "${POCKETBASE_SHA256}" "${ARCHIVE}" | shasum -a 256 -c -
  else
    printf '%s  %s\n' "${POCKETBASE_SHA256}" "${ARCHIVE}" | sha256sum -c -
  fi
fi

unzip -oq "${ARCHIVE}" pocketbase -d "${TMP_DIR}"

# Verify unpacked PocketBase is a non-empty valid Linux ELF executable.
python3 - "${TMP_DIR}/pocketbase" <<'PY'
import os
import sys

bin_path = sys.argv[1]
if not os.path.isfile(bin_path) or os.path.getsize(bin_path) < 1024 * 1024:
    raise SystemExit(f"Invalid or unexpectedly small PocketBase binary: {bin_path}")
with open(bin_path, "rb") as fh:
    magic = fh.read(4)
if magic != b"\x7fELF":
    raise SystemExit("Unpacked PocketBase binary failed ELF header verification")
PY

run_ssh 'set -e
mkdir -p /var/backups/netgrey-auth /var/lib/netgrey-pocketbase/pb_data /var/lib/netgrey-pocketbase/pb_migrations
STAMP="$(date +%Y%m%d%H%M%S)"
if [ -f /etc/nginx/sites-available/netgrey ]; then
  cp -a /etc/nginx/sites-available/netgrey "/var/backups/netgrey-auth/netgrey.${STAMP}.conf"
fi
if [ -f /var/lib/netgrey-pocketbase/pb_data/data.db ]; then
  if command -v sqlite3 >/dev/null 2>&1; then
    BK_STAGE="$(mktemp -d)"
    mkdir -p "${BK_STAGE}/pb_data"
    sqlite3 /var/lib/netgrey-pocketbase/pb_data/data.db ".backup '\''${BK_STAGE}/pb_data/data.db'\''"
    if [ -f /var/lib/netgrey-pocketbase/pb_data/auxiliary.db ]; then
      sqlite3 /var/lib/netgrey-pocketbase/pb_data/auxiliary.db ".backup '\''${BK_STAGE}/pb_data/auxiliary.db'\''"
    fi
    tar -czf "/var/backups/netgrey-auth/pb_data.${STAMP}.tgz" -C "${BK_STAGE}" pb_data
    rm -rf "${BK_STAGE}"
  else
    WAS_ACTIVE=0
    if systemctl is-active --quiet netgrey-pocketbase 2>/dev/null; then
      WAS_ACTIVE=1
      systemctl stop netgrey-pocketbase
    fi
    tar -czf "/var/backups/netgrey-auth/pb_data.${STAMP}.tgz" -C /var/lib/netgrey-pocketbase pb_data
    if [ "${WAS_ACTIVE}" -eq 1 ]; then
      systemctl start netgrey-pocketbase || true
    fi
  fi
fi
ls -1dt /var/backups/netgrey-auth/pb_data.*.tgz 2>/dev/null | tail -n +6 | xargs -r rm -f
ls -1dt /var/backups/netgrey-auth/netgrey.*.conf 2>/dev/null | tail -n +6 | xargs -r rm -f
id -u netgrey-auth >/dev/null 2>&1 || useradd --system --home /var/lib/netgrey-pocketbase --shell /usr/sbin/nologin netgrey-auth
chown -R netgrey-auth:netgrey-auth /var/lib/netgrey-pocketbase
chmod 700 /var/lib/netgrey-pocketbase /var/backups/netgrey-auth'

run_scp "${TMP_DIR}/pocketbase" "/tmp/pocketbase.netgrey.new"
run_scp "${PROJECT_ROOT}/server/pocketbase/netgrey-pocketbase.service" "/etc/systemd/system/netgrey-pocketbase.service"

if [ -d "${PROJECT_ROOT}/server/pocketbase/pb_migrations" ]; then
  for mig in "${PROJECT_ROOT}/server/pocketbase/pb_migrations/"*.js; do
    if [ -f "${mig}" ]; then
      run_scp "${mig}" "/var/lib/netgrey-pocketbase/pb_migrations/$(basename "${mig}")"
    fi
  done
fi

run_ssh 'set -e
install -o root -g root -m 755 /tmp/pocketbase.netgrey.new /usr/local/bin/pocketbase
rm -f /tmp/pocketbase.netgrey.new
chown -R netgrey-auth:netgrey-auth /var/lib/netgrey-pocketbase
systemctl daemon-reload
systemctl enable netgrey-pocketbase
systemctl restart netgrey-pocketbase
systemctl is-active --quiet netgrey-pocketbase
for attempt in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:8090/api/health >/dev/null; then
    echo pocketbase=healthy
    exit 0
  fi
  sleep 1
done
echo pocketbase=healthcheck_failed >&2
exit 1'

echo "PocketBase binary, migrations, and service deployed and restarted."
