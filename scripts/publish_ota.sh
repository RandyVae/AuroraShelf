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
LATEST_DEVICE=$(curl -s "https://bananarey.us/api/latest-udid" || echo "{}")
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
SSH_OPTS=(-o StrictHostKeyChecking=no -o PubkeyAuthentication=no)
REMOTE_CLEAN_AND_CHECK="chmod 644 ${REMOTE_DIR}/Netgrey.ipa ${REMOTE_DIR}/Netgrey.apk ${REMOTE_DIR}/index.html ${REMOTE_DIR}/manifest.plist 2>/dev/null || true; \
find ${REMOTE_DIR} -maxdepth 2 -type f \( -name '*.tmp' -o -name '*.old' -o -name '*.bak' -o -name '*~' \) -delete; \
echo '--- 服务器最新版本清单 ---'; \
ls -lh ${REMOTE_DIR}/Netgrey.*; \
echo '--- 服务器磁盘剩余空间 ---'; \
df -h /"

if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ] || [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
  echo "[3/3] 正在上传最新安装包并清理服务器老版本残留..."
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS_BIN="$(command -v sshpass 2>/dev/null || echo "/opt/homebrew/bin/sshpass")"
    if [ -x "${SSHPASS_BIN}" ]; then
      if [ -f "${PROJECT_ROOT}/scripts/ota_web/index.html" ]; then
        echo "上传 OTA 网页 index.html 与 manifest.plist..."
        "${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/scripts/ota_web/index.html" "root@${SERVER_IP}:${REMOTE_DIR}/index.html"
        if [ -f "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" ]; then
          "${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" "root@${SERVER_IP}:${REMOTE_DIR}/manifest.plist"
        fi
      fi
      if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
        echo "上传 iOS 通用包 Netgrey.ipa..."
        "${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${OUTPUT_DIR}/Netgrey.ipa" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.ipa"
        sleep 1
      fi
      if [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
        echo "上传 Android 正式包 Netgrey.apk..."
        "${SSHPASS_BIN}" -p "${SERVER_PASS}" scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/dist/Netgrey.apk" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.apk"
        sleep 1
      fi
      "${SSHPASS_BIN}" -p "${SERVER_PASS}" ssh "${SSH_OPTS[@]}" "root@${SERVER_IP}" "${REMOTE_CLEAN_AND_CHECK}"
    else
      if [ -f "${PROJECT_ROOT}/scripts/ota_web/index.html" ]; then
        echo "上传 OTA 网页 index.html 与 manifest.plist..."
        scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/scripts/ota_web/index.html" "root@${SERVER_IP}:${REMOTE_DIR}/index.html"
        if [ -f "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" ]; then
          scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/scripts/ota_web/manifest.plist" "root@${SERVER_IP}:${REMOTE_DIR}/manifest.plist"
        fi
      fi
      if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
        echo "上传 iOS 通用包 Netgrey.ipa..."
        scp "${SSH_OPTS[@]}" "${OUTPUT_DIR}/Netgrey.ipa" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.ipa"
      fi
      if [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
        echo "上传 Android 正式包 Netgrey.apk..."
        scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/dist/Netgrey.apk" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.apk"
      fi
      ssh "${SSH_OPTS[@]}" "root@${SERVER_IP}" "${REMOTE_CLEAN_AND_CHECK}"
    fi
  else
    if [ -f "${PROJECT_ROOT}/scripts/ota_web/index.html" ]; then
      echo "上传 OTA 网页 index.html..."
      scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/scripts/ota_web/index.html" "root@${SERVER_IP}:${REMOTE_DIR}/index.html"
    fi
    if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
      echo "上传 iOS 通用包 Netgrey.ipa..."
      scp "${SSH_OPTS[@]}" "${OUTPUT_DIR}/Netgrey.ipa" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.ipa"
    fi
    if [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
      echo "上传 Android 正式包 Netgrey.apk..."
      scp "${SSH_OPTS[@]}" "${PROJECT_ROOT}/dist/Netgrey.apk" "root@${SERVER_IP}:${REMOTE_DIR}/Netgrey.apk"
    fi
    ssh "${SSH_OPTS[@]}" "root@${SERVER_IP}" "${REMOTE_CLEAN_AND_CHECK}"
  fi
  echo "=========================================="
  echo "发布成功！手机端现在可以在网页上点击安装！"
  echo "访问地址: https://bananarey.us"
  echo "=========================================="
else
  echo "错误: 未找到生成的 Netgrey.ipa，请检查构建日志！"
  exit 1
fi
