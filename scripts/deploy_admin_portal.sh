#!/usr/bin/env bash
set -euo pipefail

# 部署 Netgrey 管理后台 Web UI、分析引擎与路由配置（不触发移动客户端重新构建）
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
REMOTE_HOST="${SERVER_USER}@${SERVER_IP}"
RUN_ID="$$"

SSH_OPTS=(
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
  -o ControlMaster=auto
  -o ControlPath=/tmp/netgrey-admin-%r@%h:%p
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

run_ssh() {
  local cmd="$1"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" SSH_ASKPASS="${ASKPASS_SCRIPT}" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
      ssh "${SSH_OPTS[@]}" "${REMOTE_HOST}" "${cmd}"
  else
    ssh "${SSH_OPTS[@]}" "${REMOTE_HOST}" "${cmd}"
  fi
}

run_scp() {
  local src="$1"
  local dest="$2"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" SSH_ASKPASS="${ASKPASS_SCRIPT}" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
      scp "${SSH_OPTS[@]}" "${src}" "${REMOTE_HOST}:${dest}"
  else
    scp "${SSH_OPTS[@]}" "${src}" "${REMOTE_HOST}:${dest}"
  fi
}

for req in \
  "${SCRIPT_DIR}/server/analytics_store.py" \
  "${SCRIPT_DIR}/server/ota_service.py" \
  "${SCRIPT_DIR}/admin_web/index.html" \
  "${SCRIPT_DIR}/admin_web/styles.css" \
  "${SCRIPT_DIR}/admin_web/app.js"; do
  if [ ! -f "${req}" ]; then
    echo "Missing required file: ${req}" >&2
    exit 1
  fi
done

echo "[1/4] 上传服务端分析引擎与后台静态资源..."
run_scp "${SCRIPT_DIR}/server/analytics_store.py" "/tmp/analytics_store.py.tmp.${RUN_ID}"
run_scp "${SCRIPT_DIR}/server/ota_service.py" "/tmp/ota_service.py.tmp.${RUN_ID}"
run_scp "${SCRIPT_DIR}/admin_web/index.html" "/tmp/admin_index.html.tmp.${RUN_ID}"
run_scp "${SCRIPT_DIR}/admin_web/styles.css" "/tmp/admin_styles.css.tmp.${RUN_ID}"
run_scp "${SCRIPT_DIR}/admin_web/app.js" "/tmp/admin_app.js.tmp.${RUN_ID}"

echo "[2/4] 原子切换服务端文件、初始化管理员认证并重启服务..."
run_ssh "set -e
mkdir -p /usr/local/lib/netgrey-ota /var/lib/netgrey-ota /etc/netgrey-ota /var/www/netgrey/admin
chmod 700 /var/lib/netgrey-ota /etc/netgrey-ota

install -o root -g root -m 755 '/tmp/analytics_store.py.tmp.${RUN_ID}' /usr/local/lib/netgrey-ota/analytics_store.py
install -o root -g root -m 755 '/tmp/ota_service.py.tmp.${RUN_ID}' /usr/local/lib/netgrey-ota/ota_service.py
install -o root -g root -m 644 '/tmp/admin_index.html.tmp.${RUN_ID}' /var/www/netgrey/admin/index.html
install -o root -g root -m 644 '/tmp/admin_styles.css.tmp.${RUN_ID}' /var/www/netgrey/admin/styles.css
install -o root -g root -m 644 '/tmp/admin_app.js.tmp.${RUN_ID}' /var/www/netgrey/admin/app.js
rm -f '/tmp/analytics_store.py.tmp.${RUN_ID}' '/tmp/ota_service.py.tmp.${RUN_ID}' \
  '/tmp/admin_index.html.tmp.${RUN_ID}' '/tmp/admin_styles.css.tmp.${RUN_ID}' '/tmp/admin_app.js.tmp.${RUN_ID}'

python3 - <<'PY'
import sys
sys.path.insert(0, '/usr/local/lib/netgrey-ota')
import analytics_store
analytics_store.init_db()
created, pwd = analytics_store.ensure_admin_auth_initialized(username='admin')
if created and pwd:
    print(f'ADMIN_INIT_CREATED=1')
    print(f'ADMIN_INIT_USERNAME=admin')
    print(f'ADMIN_INIT_PASSWORD={pwd}')
else:
    print('ADMIN_INIT_CREATED=0')
PY

systemctl restart netgrey-ota.service
systemctl is-active --quiet netgrey-ota.service
echo 'netgrey_ota_service=active'
"

echo "[3/4] 同步 Nginx /admin/ 与代理配置..."
"${SCRIPT_DIR}/configure_auth_proxy.sh"

echo "[4/4] 执行公网冒烟检查..."
ADMIN_CODE="$(curl -s -o /dev/null -w '%{http_code}' https://bananarey.us/admin/)"
UNAUTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' https://bananarey.us/api/admin/overview)"
BAD_TEL_CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' https://bananarey.us/api/telemetry/batch)"
echo "smoke_admin_page=${ADMIN_CODE} smoke_unauth_api=${UNAUTH_CODE} smoke_bad_telemetry=${BAD_TEL_CODE}"
test "${ADMIN_CODE}" = "200"
test "${UNAUTH_CODE}" = "401"
test "${BAD_TEL_CODE}" = "400"
echo "管理后台部署与冒烟检查全部通过: https://bananarey.us/admin/"
