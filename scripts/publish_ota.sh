#!/usr/bin/env bash
set -euo pipefail

# Netgrey 自动化发布到 OTA 服务器脚本
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${PROJECT_ROOT}/ios"
OUTPUT_DIR="${IOS_DIR}/build.nosync/outputs"
CRED_FILE="${PROJECT_ROOT}/.server_credentials"

if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

SERVER_IP="${SERVER_IP:-123.108.70.177}"
SERVER_PASS="${SERVER_PASS:-}"
REMOTE_DIR="/var/www/netgrey"

echo "=========================================="
echo "Netgrey OTA 自动化构建与发布"
echo "目标服务器: ${SERVER_IP}:${REMOTE_DIR}"
echo "=========================================="

# 1. 检查服务器上是否有新登记的手机设备 UDID
echo "[1/3] 正在检查服务器最新注册的设备 UDID..."
if [ -n "${OTA_ADMIN_TOKEN:-}" ]; then
  LATEST_DEVICE=$(curl -s -H "X-Admin-Token: ${OTA_ADMIN_TOKEN}" "https://bananarey.us/api/latest-udid" || echo "{}")
else
  LATEST_DEVICE=$(curl -s "https://bananarey.us/api/latest-udid" || echo "{}")
fi
echo "服务器最新设备数据: ${LATEST_DEVICE}"

# 2. 执行自动签名打包
TARGET="${1:-Release}"
if [ "${TARGET}" = "Android" ] || [ "${TARGET}" = "All" ] || [ "${TARGET}" = "Release" ]; then
  echo "[2/3] 执行 Android Release APK 构建..."
  "${PROJECT_ROOT}/gradlew" assembleRelease
  mkdir -p "${PROJECT_ROOT}/dist"
  cp "${PROJECT_ROOT}/build.nosync/app/outputs/apk/release/app-release.apk" "${PROJECT_ROOT}/dist/Netgrey.apk"
fi

if [ "${TARGET}" != "Android" ] && [ "${TARGET}" != "UploadOnly" ]; then
  echo "[2/3] 执行 iOS 本地签名归档与导出 IPA..."
  "${SCRIPT_DIR}/build_ios_signed.sh" "${TARGET}"
fi

# 3. 上传到远程服务器与清理老版本
SSH_OPTS=(
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
  -o ControlMaster=auto
  -o ControlPath=/tmp/netgrey-ota-%r@%h:%p
  -o ControlPersist=60s
)
SSHPASS_BIN=""
if [ -n "${SERVER_PASS}" ]; then
  SSH_OPTS+=(-o PubkeyAuthentication=no)
  SSHPASS_BIN="$(command -v sshpass 2>/dev/null || echo "/opt/homebrew/bin/sshpass")"
fi

run_scp() {
  local src="$1"
  local dest="$2"
  if [ -n "${SERVER_PASS}" ] && [ -x "${SSHPASS_BIN}" ]; then
    SSHPASS="${SERVER_PASS}" "${SSHPASS_BIN}" -e scp "${SSH_OPTS[@]}" "${src}" "root@${SERVER_IP}:${dest}"
  else
    scp "${SSH_OPTS[@]}" "${src}" "root@${SERVER_IP}:${dest}"
  fi
}

run_ssh() {
  local cmd="$1"
  if [ -n "${SERVER_PASS}" ] && [ -x "${SSHPASS_BIN}" ]; then
    SSHPASS="${SERVER_PASS}" "${SSHPASS_BIN}" -e ssh "${SSH_OPTS[@]}" "root@${SERVER_IP}" "${cmd}"
  else
    ssh "${SSH_OPTS[@]}" "root@${SERVER_IP}" "${cmd}"
  fi
}

upload_atomic() {
  local local_file="$1"
  local remote_name="$2"
  local tmp_name="${remote_name}.tmp.$$"
  run_scp "${local_file}" "${REMOTE_DIR}/${tmp_name}"
  run_ssh "chmod 644 '${REMOTE_DIR}/${tmp_name}' && mv -f '${REMOTE_DIR}/${tmp_name}' '${REMOTE_DIR}/${remote_name}'"
}

REMOTE_CLEAN_AND_CHECK="chmod 644 ${REMOTE_DIR}/Netgrey.ipa ${REMOTE_DIR}/Netgrey.apk ${REMOTE_DIR}/index.html ${REMOTE_DIR}/manifest.plist ${REMOTE_DIR}/source-manifest.json 2>/dev/null || true; \
find ${REMOTE_DIR} -maxdepth 2 -type f \( -name '*.tmp*' -o -name '*.old' -o -name '*.bak' -o -name '*~' \) -delete; \
echo '--- 服务器最新版本清单 ---'; \
ls -lh ${REMOTE_DIR}/Netgrey.*; \
echo '--- 服务器磁盘剩余空间 ---'; \
df -h /"

if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ] || [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
  echo "[3/3] 正在原子上传最新安装包并清理服务器老版本残留..."
  if [ -f "${PROJECT_ROOT}/scripts/ota_web/index.html" ]; then
    echo "上传 OTA 网页 index.html、manifest.plist 与 source-manifest.json..."
    upload_atomic "${PROJECT_ROOT}/scripts/ota_web/index.html" "index.html"
    if [ -f "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" ]; then
      upload_atomic "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" "manifest.plist"
    fi
    if [ -f "${PROJECT_ROOT}/scripts/ota_web/source-manifest.json" ]; then
      upload_atomic "${PROJECT_ROOT}/scripts/ota_web/source-manifest.json" "source-manifest.json"
    fi
  fi
  if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
    echo "上传 iOS 通用包 Netgrey.ipa..."
    upload_atomic "${OUTPUT_DIR}/Netgrey.ipa" "Netgrey.ipa"
    sleep 1
  fi
  if [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
    echo "上传 Android 正式包 Netgrey.apk..."
    upload_atomic "${PROJECT_ROOT}/dist/Netgrey.apk" "Netgrey.apk"
    sleep 1
  fi
  run_ssh "${REMOTE_CLEAN_AND_CHECK}"
  echo "=========================================="
  echo "发布成功！手机端现在可以在网页上点击安装！"
  echo "访问地址: https://bananarey.us"
  echo "=========================================="
else
  echo "错误: 未找到待上传的 Netgrey.ipa 或 Netgrey.apk，请检查构建日志！"
  exit 1
fi
