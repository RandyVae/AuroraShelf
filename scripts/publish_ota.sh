#!/usr/bin/env bash
set -euo pipefail

# Netgrey 自动化发布到 OTA 服务器脚本
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${PROJECT_ROOT}/ios"
OUTPUT_DIR="${IOS_DIR}/build.nosync/outputs"
OTA_WEB_DIR="${SCRIPT_DIR}/ota_web"
CRED_FILE="${PROJECT_ROOT}/.server_credentials"

TARGET="${1:-Release}"

# 0. 双端与 OTA 静态清单版本一致性预检
verify_version_consistency() {
  python3 - "${PROJECT_ROOT}" <<'PY'
import plistlib
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
gradle_text = (root / "app" / "build.gradle.kts").read_text(encoding="utf-8")
ios_yml_text = (root / "ios" / "project.yml").read_text(encoding="utf-8")
index_html = (root / "scripts" / "ota_web" / "index.html").read_text(encoding="utf-8")
with (root / "scripts" / "ota_web" / "manifest.plist").open("rb") as fh:
    manifest = plistlib.load(fh)

m_and_ver = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
m_and_code = re.search(r'versionCode\s*=\s*(\d+)', gradle_text)
m_ios_ver = re.search(r'MARKETING_VERSION:\s*"?([0-9]+\.[0-9]+\.[0-9]+)"?', ios_yml_text)
m_ios_code = re.search(r'CURRENT_PROJECT_VERSION:\s*"?(\d+)"?', ios_yml_text)

if not (m_and_ver and m_and_code and m_ios_ver and m_ios_code):
    raise SystemExit("ERROR: Failed to parse version fields from app/build.gradle.kts or ios/project.yml")

and_ver, and_code = m_and_ver.group(1), m_and_code.group(1)
ios_ver, ios_code = m_ios_ver.group(1), m_ios_code.group(1)
manifest_ver = str(manifest["items"][0]["metadata"]["bundle-version"]).strip()

errors = []
if and_ver != ios_ver:
    errors.append(f"Android versionName ({and_ver}) != iOS MARKETING_VERSION ({ios_ver})")
if and_code != ios_code:
    errors.append(f"Android versionCode ({and_code}) != iOS CURRENT_PROJECT_VERSION ({ios_code})")
if manifest_ver != and_ver:
    errors.append(f"manifest.plist bundle-version ({manifest_ver}) != app version ({and_ver})")

expected_badge = f"v{and_ver} (Build {and_code})"
if index_html.count(expected_badge) < 2:
    errors.append(f"scripts/ota_web/index.html missing dual-tab badge '{expected_badge}'")
if f"/Netgrey.apk?v={and_ver}" not in index_html or f"Netgrey-v{and_ver}.apk" not in index_html:
    errors.append(f"scripts/ota_web/index.html APK download link does not match v{and_ver}")

if errors:
    for err in errors:
        print(f"[VERSION MISMATCH] {err}", file=sys.stderr)
    raise SystemExit(1)

print(f"[PASS] Version consistency verified: v{and_ver} (Build {and_code})")
PY
}

echo "[0/4] 正在核验 Android / iOS / manifest.plist / index.html 版本一致性..."
verify_version_consistency

if [ "${TARGET}" = "--check-versions" ]; then
  exit 0
fi

if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

: "${SERVER_IP:?Missing SERVER_IP in .server_credentials or env}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-}"
REMOTE_DIR="/var/www/netgrey"
RUN_ID="$$"

case "${TARGET}" in
  Release|All|Android|iOS|UploadOnly)
    ;;
  *)
    echo "未知构建发布目标: ${TARGET}（支持: Release | All | Android | iOS | UploadOnly | --check-versions）" >&2
    exit 1
    ;;
esac

echo "=========================================="
echo "Netgrey OTA 自动化构建与发布"
echo "目标模式  : ${TARGET}"
echo "远端目录  : ${REMOTE_DIR}"
echo "=========================================="

# 1. 检查服务器上是否有新登记的手机设备 UDID
echo "[1/4] 正在检查服务器最新注册的设备 UDID..."
if [ -n "${OTA_ADMIN_TOKEN:-}" ]; then
  LATEST_DEVICE=$(curl -fsS -H "X-Admin-Token: ${OTA_ADMIN_TOKEN}" "https://bananarey.us/api/latest-udid" 2>/dev/null || echo "{}")
  echo "服务器最新设备数据已读取（字段数: $(printf '%s' "${LATEST_DEVICE}" | wc -c | tr -d ' ') bytes）"
else
  echo "提示: 未设置 OTA_ADMIN_TOKEN，跳过 /api/latest-udid 预读（不影响 Xcode 自动拉取 Apple 设备名录）。"
fi

# 2. 按 TARGET 执行对应端构建与签名
UPLOAD_APK=0
UPLOAD_IPA=0

if [ "${TARGET}" = "Android" ] || [ "${TARGET}" = "All" ] || [ "${TARGET}" = "Release" ]; then
  echo "[2/4] 执行 Android Release APK 构建..."
  GRADLE_ARGS=(assembleRelease)
  if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    GRADLE_ARGS+=("-Dorg.gradle.java.installations.paths=${JAVA_HOME}")
  fi
  "${PROJECT_ROOT}/gradlew" "${GRADLE_ARGS[@]}"
  mkdir -p "${PROJECT_ROOT}/dist"
  cp -f "${PROJECT_ROOT}/build.nosync/app/outputs/apk/release/app-release.apk" "${PROJECT_ROOT}/dist/Netgrey.apk"
  UPLOAD_APK=1
fi

if [ "${TARGET}" = "iOS" ] || [ "${TARGET}" = "All" ] || [ "${TARGET}" = "Release" ]; then
  echo "[2/4] 执行 iOS 本地签名归档与导出 IPA..."
  IOS_CFG="Release"
  "${SCRIPT_DIR}/build_ios_signed.sh" "${IOS_CFG}"
  UPLOAD_IPA=1
fi

if [ "${TARGET}" = "UploadOnly" ]; then
  if [ -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
    UPLOAD_APK=1
  fi
  if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
    UPLOAD_IPA=1
  fi
fi

if [ "${UPLOAD_APK}" -eq 0 ] && [ "${UPLOAD_IPA}" -eq 0 ]; then
  echo "错误: 当前目标 (${TARGET}) 未生成或未找到待上传的 Netgrey.ipa / Netgrey.apk！" >&2
  exit 1
fi

if [ "${UPLOAD_APK}" -eq 1 ] && [ ! -f "${PROJECT_ROOT}/dist/Netgrey.apk" ]; then
  echo "错误: 未找到 Android 构建产物 ${PROJECT_ROOT}/dist/Netgrey.apk" >&2
  exit 1
fi

verify_android_release_signature() {
  local apk_path="$1"
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  local apksigner_bin=""
  if [ -d "${sdk_root}/build-tools" ]; then
    apksigner_bin="$(ls -1 "${sdk_root}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
  fi
  if [ -z "${apksigner_bin}" ] || [ ! -x "${apksigner_bin}" ]; then
    echo "错误: 未找到 Android SDK build-tools/apksigner，无法校验正式 Release 签名！" >&2
    exit 1
  fi
  local java_env=()
  if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
    java_env=("JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
  fi
  local verify_out
  verify_out="$(env "${java_env[@]}" "${apksigner_bin}" verify --verbose --print-certs "${apk_path}")"
  if echo "${verify_out}" | grep -qi "CN=Android Debug"; then
    echo "错误: 检测到 ${apk_path} 仍使用 Android Debug 调试证书签名，已阻断发布！" >&2
    exit 1
  fi
  if ! echo "${verify_out}" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true"; then
    echo "错误: ${apk_path} 未开启 APK Signature Scheme v2！" >&2
    exit 1
  fi
  if ! echo "${verify_out}" | grep -q "Verified using v3 scheme (APK Signature Scheme v3): true"; then
    echo "错误: ${apk_path} 未开启 APK Signature Scheme v3！" >&2
    exit 1
  fi
  echo "[PASS] Android 正式 Release 签名校验通过:"
  echo "${verify_out}" | grep -E "(V3\.0 Signer|Signer #1).*certificate (DN|SHA-256 digest)|Verified using v[123] scheme" | sed 's/^/  /'
}

if [ "${UPLOAD_APK}" -eq 1 ]; then
  verify_android_release_signature "${PROJECT_ROOT}/dist/Netgrey.apk"
fi

if [ "${UPLOAD_IPA}" -eq 1 ] && [ ! -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
  echo "错误: 未找到 iOS 构建产物 ${OUTPUT_DIR}/Netgrey.ipa" >&2
  exit 1
fi

# 从 Netgrey.ipa 嵌入式 mobileprovision 提取已写入签名的 ProvisionedDevices 清单
extract_provisioned_udids() {
  local ipa_path="$1"
  local out_json="$2"
  local tmp_prov tmp_plist
  tmp_prov="$(mktemp)"
  tmp_plist="$(mktemp)"
  if unzip -p "${ipa_path}" 'Payload/*.app/embedded.mobileprovision' > "${tmp_prov}" 2>/dev/null \
     && [ -s "${tmp_prov}" ] \
     && security cms -D -i "${tmp_prov}" > "${tmp_plist}" 2>/dev/null \
     && [ -s "${tmp_plist}" ]; then
    python3 - "${tmp_plist}" "${out_json}" <<'PY'
import datetime
import json
import plistlib
import sys

plist_path, out_path = sys.argv[1], sys.argv[2]
with open(plist_path, "rb") as pf:
    plist = plistlib.load(pf)
udids = sorted({str(u).strip().lower() for u in (plist.get("ProvisionedDevices") or []) if str(u).strip()})
payload = {
    "updated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "provisions_all_devices": bool(plist.get("ProvisionsAllDevices", False)),
    "count": len(udids),
    "udids": udids,
}
with open(out_path, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, indent=2)
print(f"已从 IPA 签名描述文件提取 {len(udids)} 台授权设备 UDID")
PY
    chmod 600 "${out_json}"
    rm -f "${tmp_prov}" "${tmp_plist}"
  else
    rm -f "${tmp_prov}" "${tmp_plist}"
    echo "警告: 无法从 ${ipa_path} 提取 embedded.mobileprovision" >&2
    return 1
  fi
}

PROVISIONED_JSON="${OTA_WEB_DIR}/provisioned_udids.json"
if [ -f "${OUTPUT_DIR}/Netgrey.ipa" ]; then
  extract_provisioned_udids "${OUTPUT_DIR}/Netgrey.ipa" "${PROVISIONED_JSON}"
fi

# 3. 建立复用 SSH 通道并在上传前核验远端磁盘剩余空间 >= 5GB
SSH_OPTS=(
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
  -o ControlMaster=auto
  -o ControlPath=/tmp/netgrey-ota-%r@%h:%p
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

REMOTE_HOST="${SERVER_USER}@${SERVER_IP}"

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

run_ssh() {
  local cmd="$1"
  if [ -n "${SERVER_PASS}" ]; then
    SSHPASS="${SERVER_PASS}" SSH_ASKPASS="${ASKPASS_SCRIPT}" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
      ssh "${SSH_OPTS[@]}" "${REMOTE_HOST}" "${cmd}"
  else
    ssh "${SSH_OPTS[@]}" "${REMOTE_HOST}" "${cmd}"
  fi
}

echo "[3/4] 正在执行上传前服务器磁盘安全水位检查 (>= 5GB)..."
FREE_KB="$(run_ssh "df -Pk / | awk 'NR==2 {print \$4}'")"
MIN_FREE_KB=$((5 * 1024 * 1024))
if [ -z "${FREE_KB}" ] || [ "${FREE_KB}" -lt "${MIN_FREE_KB}" ]; then
  echo "错误: 远端服务器根分区可用空间不足 5GB (当前: ${FREE_KB:-unknown} KB)，已阻断大体积安装包上传！" >&2
  exit 1
fi
echo "远端磁盘可用空间检查通过 ($((FREE_KB / 1024)) MB 可用)。"

# 4. 先将全部二进制与静态文件上传为 .tmp.$$，最后在单条 SSH 命令中原子切换全部线上文件
stage_upload() {
  local local_file="$1"
  local remote_name="$2"
  run_scp "${local_file}" "${REMOTE_DIR}/${remote_name}.tmp.${RUN_ID}"
}

echo "[4/4] 正在分阶段上传临时产物并执行单次原子切换..."

if [ "${UPLOAD_IPA}" -eq 1 ]; then
  echo "  -> 上传 iOS 通用包 Netgrey.ipa (临时暂存)..."
  stage_upload "${OUTPUT_DIR}/Netgrey.ipa" "Netgrey.ipa"
fi

if [ "${UPLOAD_APK}" -eq 1 ]; then
  echo "  -> 上传 Android 正式包 Netgrey.apk (临时暂存)..."
  stage_upload "${PROJECT_ROOT}/dist/Netgrey.apk" "Netgrey.apk"
fi

if [ -f "${PROVISIONED_JSON}" ]; then
  echo "  -> 上传签名设备状态清单 provisioned_udids.json (临时暂存)..."
  stage_upload "${PROVISIONED_JSON}" "provisioned_udids.json"
fi

for asset in "icon57.png" "icon512.png" "source-manifest.json" "manifest.plist" "index.html"; do
  if [ -f "${OTA_WEB_DIR}/${asset}" ]; then
    echo "  -> 上传静态资源 ${asset} (临时暂存)..."
    stage_upload "${OTA_WEB_DIR}/${asset}" "${asset}"
  fi
done

STAGE_OTA_SERVICE=0
if [ -f "${SCRIPT_DIR}/server/ota_service.py" ]; then
  echo "  -> 上传后端守护脚本 ota_service.py (临时暂存)..."
  run_scp "${SCRIPT_DIR}/server/ota_service.py" "/tmp/ota_service.py.tmp.${RUN_ID}"
  STAGE_OTA_SERVICE=1
fi

run_ssh "set -e
cd '${REMOTE_DIR}'

# 统一设置权限并在单次事务内完成原子覆盖切换（杜绝 manifest/页面已更新但旧包尚未就位的窗口）
for pub in Netgrey.ipa Netgrey.apk icon57.png icon512.png source-manifest.json manifest.plist index.html; do
  if [ -f \"\${pub}.tmp.${RUN_ID}\" ]; then
    chmod 644 \"\${pub}.tmp.${RUN_ID}\"
  fi
done

if [ -f 'provisioned_udids.json.tmp.${RUN_ID}' ]; then
  chmod 600 'provisioned_udids.json.tmp.${RUN_ID}'
fi

for target_file in Netgrey.ipa Netgrey.apk provisioned_udids.json icon57.png icon512.png source-manifest.json manifest.plist index.html; do
  if [ -f \"\${target_file}.tmp.${RUN_ID}\" ]; then
    mv -f \"\${target_file}.tmp.${RUN_ID}\" \"\${target_file}\"
  fi
done

# 同步更新远端 ota_service.py 至独立目录 /usr/local/lib/netgrey-ota/ota_service.py 并移除 Web 根目录旧脚本
if [ '${STAGE_OTA_SERVICE}' = '1' ] && [ -f '/tmp/ota_service.py.tmp.${RUN_ID}' ]; then
  OTA_SCRIPT_DEST='/usr/local/lib/netgrey-ota/ota_service.py'
  mkdir -p \"\$(dirname \"\${OTA_SCRIPT_DEST}\")\" /var/lib/netgrey-ota
  chmod 700 /var/lib/netgrey-ota
  install -o root -g root -m 755 '/tmp/ota_service.py.tmp.${RUN_ID}' \"\${OTA_SCRIPT_DEST}\"
  rm -f '/tmp/ota_service.py.tmp.${RUN_ID}' '${REMOTE_DIR}/ota_service.py'
  if [ -f /etc/systemd/system/netgrey-ota.service ] && grep -q '/var/www/netgrey/ota_service.py' /etc/systemd/system/netgrey-ota.service; then
    sed -i 's|/var/www/netgrey/ota_service.py|/usr/local/lib/netgrey-ota/ota_service.py|g' /etc/systemd/system/netgrey-ota.service
    systemctl daemon-reload
  fi
  if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files netgrey-ota.service 2>/dev/null | grep -q 'netgrey-ota.service'; then
    systemctl restart netgrey-ota.service
  fi
fi

# 清理任何历史残留、临时碎片与备份文件
find '${REMOTE_DIR}' -maxdepth 2 -type f \\( -name '*.tmp*' -o -name '*.old' -o -name '*.bak' -o -name '*~' \\) -delete

echo '--- 服务器最新版本清单 ---'
ls -lh '${REMOTE_DIR}'/Netgrey.* '${REMOTE_DIR}'/index.html '${REMOTE_DIR}'/manifest.plist 2>/dev/null || true
echo '--- 服务器磁盘剩余空间 ---'
df -h /"

echo "=========================================="
echo "发布成功！双端原子覆盖与清理已完成。"
echo "访问地址: https://bananarey.us"
echo "=========================================="
