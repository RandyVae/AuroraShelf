#!/usr/bin/env bash
set -euo pipefail

# Netgrey iOS TestFlight 合规预检、归档与自动化上传脚本
# 用法:
#   ./scripts/publish_testflight.sh --check        仅执行合规预检与签名环境诊断
#   ./scripts/publish_testflight.sh --export-only  构建并导出 App Store Connect IPA（不上传）
#   ./scripts/publish_testflight.sh                构建归档并直接上传至 App Store Connect / TestFlight

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${PROJECT_ROOT}/ios"
BUILD_DIR="${IOS_DIR}/build.nosync"
OUTPUT_DIR="${BUILD_DIR}/outputs-testflight"
ARCHIVE_PATH="${BUILD_DIR}/AuroraShelf-TestFlight.xcarchive"
EXPORT_OPTIONS="${BUILD_DIR}/ExportOptions-TestFlight.plist"
CRED_FILE="${PROJECT_ROOT}/.appstore_credentials"

if [ -f "${CRED_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${CRED_FILE}"
fi

TEAM_ID="${DEVELOPMENT_TEAM:-U4MXHLA3GF}"
CONFIGURATION="${TESTFLIGHT_CONFIGURATION:-Release}"
ASC_KEY_ID="${ASC_KEY_ID:-}"
ASC_ISSUER_ID="${ASC_ISSUER_ID:-}"
ASC_KEY_PATH="${ASC_KEY_PATH:-}"
ASC_APP_ID="${ASC_APP_ID:-6835272581}"

MODE="upload"
SKIP_ARCHIVE=0
for arg in "$@"; do
  case "${arg}" in
    --check)
      MODE="check"
      ;;
    --export-only)
      MODE="export"
      ;;
    --skip-archive)
      SKIP_ARCHIVE=1
      ;;
    -h|--help)
      echo "用法: $0 [--check | --export-only | --skip-archive]"
      exit 0
      ;;
    *)
      echo "未知参数: ${arg}"
      echo "用法: $0 [--check | --export-only | --skip-archive]"
      exit 1
      ;;
  esac
done

PRIVACY_MANIFEST="${IOS_DIR}/AuroraShelf/Resources/PrivacyInfo.xcprivacy"
INFO_PLIST="${IOS_DIR}/AuroraShelf/Resources/Info.plist"
PBXPROJ="${IOS_DIR}/AuroraShelf.xcodeproj/project.pbxproj"
APP_ICON_1024="${IOS_DIR}/AuroraShelf/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"

echo "=========================================="
echo "Netgrey TestFlight 内部测试预检与发布"
echo "模式    : ${MODE}"
echo "团队 ID : ${TEAM_ID}"
echo "构建配置: ${CONFIGURATION}"
echo "=========================================="

# [1/3] 工程合规预检
echo "[1/3] 正在执行 TestFlight 合规预检..."
PREFLIGHT_ERRORS=0

if [ -f "${PRIVACY_MANIFEST}" ] && plutil -lint "${PRIVACY_MANIFEST}" >/dev/null 2>&1; then
  MISSING_APIS=()
  for api in \
    "NSPrivacyAccessedAPICategoryUserDefaults" \
    "NSPrivacyAccessedAPICategorySystemBootTime" \
    "NSPrivacyAccessedAPICategoryFileTimestamp" \
    "NSPrivacyAccessedAPICategoryDiskSpace"; do
    if ! grep -q "${api}" "${PRIVACY_MANIFEST}"; then
      MISSING_APIS+=("${api}")
    fi
  done
  if [ ${#MISSING_APIS[@]} -eq 0 ] && grep -q "PrivacyInfo.xcprivacy in Resources" "${PBXPROJ}"; then
    echo "  [PASS] PrivacyInfo.xcprivacy 语法有效，已包含 4 类 Required Reason API 且已纳入 Xcode 资源阶段"
  else
    echo "  [FAIL] PrivacyInfo.xcprivacy 缺少必要声明 (${MISSING_APIS[*]:-未纳入 project.pbxproj})"
    PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
  fi
else
  echo "  [FAIL] 缺少或无效的 ${PRIVACY_MANIFEST}"
  PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
fi

if [ -f "${INFO_PLIST}" ] && plutil -lint "${INFO_PLIST}" >/dev/null 2>&1; then
  ENCRYPTION_FLAG=$(/usr/libexec/PlistBuddy -c "Print :ITSAppUsesNonExemptEncryption" "${INFO_PLIST}" 2>/dev/null || echo "missing")
  if [ "${ENCRYPTION_FLAG}" = "false" ] || [ "${ENCRYPTION_FLAG}" = "true" ]; then
    echo "  [PASS] Info.plist 语法有效，ITSAppUsesNonExemptEncryption=${ENCRYPTION_FLAG}"
  else
    echo "  [FAIL] Info.plist 缺少 ITSAppUsesNonExemptEncryption 出口合规声明"
    PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
  fi
else
  echo "  [FAIL] 缺少或无效的 ${INFO_PLIST}"
  PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
fi

if [ -f "${APP_ICON_1024}" ]; then
  ICON_W=$(sips -g pixelWidth "${APP_ICON_1024}" 2>/dev/null | awk '/pixelWidth/{print $2}')
  ICON_H=$(sips -g pixelHeight "${APP_ICON_1024}" 2>/dev/null | awk '/pixelHeight/{print $2}')
  ICON_ALPHA=$(sips -g hasAlpha "${APP_ICON_1024}" 2>/dev/null | awk '/hasAlpha/{print $2}')
  if [ "${ICON_W}" = "1024" ] && [ "${ICON_H}" = "1024" ] && [ "${ICON_ALPHA}" = "no" ]; then
    echo "  [PASS] AppIcon-1024.png 规格合规 (${ICON_W}x${ICON_H}, hasAlpha=${ICON_ALPHA})"
  else
    echo "  [FAIL] AppIcon-1024.png 不符合规范 (${ICON_W}x${ICON_H}, hasAlpha=${ICON_ALPHA})"
    PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
  fi
else
  echo "  [FAIL] 未找到 ${APP_ICON_1024}"
  PREFLIGHT_ERRORS=$((PREFLIGHT_ERRORS + 1))
fi

SHORT_VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "${INFO_PLIST}" 2>/dev/null || echo "unknown")
if [ "${SHORT_VERSION}" = "\$(MARKETING_VERSION)" ] || [ "${SHORT_VERSION}" = "unknown" ]; then
  SHORT_VERSION=$(grep -m1 "MARKETING_VERSION:" "${IOS_DIR}/project.yml" | awk '{print $2}' | tr -d '"' || echo "unknown")
fi
BUILD_NUMBER=$(grep -m1 "CURRENT_PROJECT_VERSION:" "${IOS_DIR}/project.yml" | awk '{print $2}' | tr -d '"' || echo "unknown")
DEPLOY_TARGET=$(grep -m1 'iOS: "' "${IOS_DIR}/project.yml" | sed -E 's/.*"([^"]+)".*/\1/' || echo "unknown")
echo "  [INFO] 版本: v${SHORT_VERSION} (Build ${BUILD_NUMBER}) · iOS Deployment Target: ${DEPLOY_TARGET}"

if [ "${PREFLIGHT_ERRORS}" -gt 0 ]; then
  echo "预检失败：发现 ${PREFLIGHT_ERRORS} 项工程合规错误，请先修复。"
  exit 1
fi

# [2/3] 生成 ExportOptions-TestFlight.plist 与诊断签名/凭据环境
mkdir -p "${BUILD_DIR}" "${OUTPUT_DIR}"
EXPORT_DEST="upload"
if [ "${MODE}" = "export" ]; then
  EXPORT_DEST="export"
fi

cat <<EOF > "${EXPORT_OPTIONS}"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>destination</key>
	<string>${EXPORT_DEST}</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>teamID</key>
	<string>${TEAM_ID}</string>
	<key>uploadSymbols</key>
	<true/>
	<key>manageAppVersionAndBuildNumber</key>
	<false/>
</dict>
</plist>
EOF

plutil -lint "${EXPORT_OPTIONS}" >/dev/null
echo "  [PASS] ExportOptions-TestFlight.plist 已生成并验证 (method=app-store-connect, destination=${EXPORT_DEST})"

echo "[2/3] 正在诊断本机分发证书与 App Store Connect 凭据状态..."
HAS_DIST_CERT=0
if security find-identity -v -p codesigning 2>/dev/null | grep -qE "Apple Distribution|iPhone Distribution"; then
  HAS_DIST_CERT=1
  echo "  [PASS] 本机钥匙串已存在 Apple Distribution / iPhone Distribution 分发证书"
else
  echo "  [WARN] 本机钥匙串未检测到 Apple Distribution 分发证书（仅检测到 Apple Development）"
fi

HAS_ASC_API_KEY=0
if [ -n "${ASC_KEY_ID}" ] && [ -n "${ASC_ISSUER_ID}" ] && [ -n "${ASC_KEY_PATH}" ] && [ -f "${ASC_KEY_PATH}" ]; then
  HAS_ASC_API_KEY=1
  echo "  [PASS] 已配置 App Store Connect API Key"
else
  echo "  [INFO] 未检测到 ASC API Key（可在 .appstore_credentials 配置 ASC_KEY_ID / ASC_ISSUER_ID / ASC_KEY_PATH）"
fi

HAS_XCODE_ACCOUNT=0
if defaults read com.apple.dt.Xcode DVTDeveloperAccountManagerAppleIDLists 2>/dev/null | grep -q '@'; then
  HAS_XCODE_ACCOUNT=1
  echo "  [PASS] Xcode 已登录 Apple ID 开发者账号"
else
  echo "  [WARN] Xcode 当前未检测到已登录的 Apple ID 账号（IDEProvisioning / DVTDeveloperAccountManagerAppleIDLists 为空）"
fi

print_next_steps() {
  echo ""
  echo "------------------------------------------"
  echo "操作指引（完成以下 3 步即可自动上传 TestFlight）："
  echo "1. 注册显式 App ID 与新建 App："
  echo "   - 在 Apple Developer 后台注册 Explicit App ID: com.aurorashelf.app"
  echo "   - 在 App Store Connect 新建 iOS App，套装 ID 选择 com.aurorashelf.app"
  echo "2. 配置分发证书与认证方式（二选一）："
  echo "   - 方式 A（推荐）：在 Xcode -> Settings -> Accounts 登录团队 ${TEAM_ID} 的 Apple ID，并在 Manage Certificates... 点击 + 签发 Apple Distribution 证书"
  echo "   - 方式 B（纯 CLI）：在 App Store Connect -> 用户和访问 -> 集成 生成 API 密钥(.p8)，写入项目根 .appstore_credentials："
  echo "     ASC_KEY_ID=\"...\" / ASC_ISSUER_ID=\"...\" / ASC_KEY_PATH=\"/path/to/AuthKey_XXX.p8\""
  echo "3. 执行上传："
  echo "   ./scripts/publish_testflight.sh"
  echo "------------------------------------------"
}

if [ "${MODE}" = "check" ]; then
  echo "=========================================="
  echo "TestFlight 工程合规预检全部 PASS！"
  if [ "${HAS_DIST_CERT}" -eq 0 ] || { [ "${HAS_XCODE_ACCOUNT}" -eq 0 ] && [ "${HAS_ASC_API_KEY}" -eq 0 ]; }; then
    echo "提示：工程侧阻断已全部解决，但本机尚缺 Apple Distribution 证书或 App Store Connect 认证状态。"
    print_next_steps
  else
    echo "本机签名与 App Store Connect 凭据已就绪，可直接运行 ./scripts/publish_testflight.sh 上传。"
  fi
  echo "=========================================="
  exit 0
fi

# [3/3] 执行 Archive 与 Export / Upload
AUTH_ARGS=()
if [ "${HAS_ASC_API_KEY}" -eq 1 ]; then
  AUTH_ARGS=(
    -authenticationKeyPath "${ASC_KEY_PATH}"
    -authenticationKeyID "${ASC_KEY_ID}"
    -authenticationKeyIssuerID "${ASC_ISSUER_ID}"
  )
fi

echo "[3/3] 正在执行 Xcode Archive 归档与导出/上传 (${EXPORT_DEST})..."
if [ "${SKIP_ARCHIVE}" -eq 0 ] || [ ! -d "${ARCHIVE_PATH}" ]; then
  if ! xcodebuild archive \
    -project "${IOS_DIR}/AuroraShelf.xcodeproj" \
    -scheme AuroraShelf \
    -configuration "${CONFIGURATION}" \
    -destination "generic/platform=iOS" \
    -archivePath "${ARCHIVE_PATH}" \
    -allowProvisioningUpdates \
    DEVELOPMENT_TEAM="${TEAM_ID}" \
    CODE_SIGN_STYLE=Automatic \
    CODE_SIGNING_ALLOWED=YES \
    CODE_SIGNING_REQUIRED=YES \
    ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"}; then
    echo "错误: xcodebuild archive 失败。"
    print_next_steps
    exit 1
  fi
else
  echo "  [INFO] 已按 --skip-archive 复用现有归档: ${ARCHIVE_PATH}"
fi

rm -f "${OUTPUT_DIR}"/*.ipa
if ! xcodebuild -exportArchive \
  -archivePath "${ARCHIVE_PATH}" \
  -exportPath "${OUTPUT_DIR}" \
  -exportOptionsPlist "${EXPORT_OPTIONS}" \
  -allowProvisioningUpdates \
  ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"}; then
  if [ "${MODE}" = "upload" ] && [ "${HAS_ASC_API_KEY}" -eq 1 ]; then
    echo "警告: xcodebuild 直接上传失败（常见于 Apple /v1/apps 上游 500 超时），正在自动降级为本地导出 + altool 上传..."
    FALLBACK_OPTIONS="${BUILD_DIR}/ExportOptions-TestFlight-Local.plist"
    cat <<EOF > "${FALLBACK_OPTIONS}"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>destination</key>
	<string>export</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>teamID</key>
	<string>${TEAM_ID}</string>
	<key>uploadSymbols</key>
	<true/>
	<key>manageAppVersionAndBuildNumber</key>
	<false/>
</dict>
</plist>
EOF
    rm -rf "${OUTPUT_DIR}"/*.ipa
    if ! xcodebuild -exportArchive \
      -archivePath "${ARCHIVE_PATH}" \
      -exportPath "${OUTPUT_DIR}" \
      -exportOptionsPlist "${FALLBACK_OPTIONS}" \
      -allowProvisioningUpdates \
      ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"}; then
      echo "错误: TestFlight 本地 IPA 导出失败。"
      print_next_steps
      exit 1
    fi
    IPA_FILE=$(find "${OUTPUT_DIR}" -maxdepth 1 -name "*.ipa" | head -n 1 || true)
    if [ -z "${IPA_FILE}" ] || [ ! -f "${IPA_FILE}" ]; then
      echo "错误: 未在 ${OUTPUT_DIR} 找到导出的 IPA 安装包。"
      exit 1
    fi
    echo "正在通过 xcrun altool --use-old-altool 上传 ${IPA_FILE} (App ID: ${ASC_APP_ID}, v${SHORT_VERSION} Build ${BUILD_NUMBER})..."
    if ! API_PRIVATE_KEYS_DIR="$(dirname "${ASC_KEY_PATH}")" xcrun altool --use-old-altool \
      --upload-package "${IPA_FILE}" \
      --type ios \
      --asc-public-id "${ASC_ISSUER_ID}" \
      --apple-id "${ASC_APP_ID}" \
      --bundle-version "${BUILD_NUMBER}" \
      --bundle-short-version-string "${SHORT_VERSION}" \
      --bundle-id "com.aurorashelf.app" \
      --apiKey "${ASC_KEY_ID}" \
      --apiIssuer "${ASC_ISSUER_ID}"; then
      echo "错误: altool 上传 TestFlight 失败。"
      exit 1
    fi
  else
    echo "错误: xcodebuild -exportArchive 失败（通常因缺少 Apple Distribution 证书、App Store 描述文件或 App Store Connect 登录态）。"
    print_next_steps
    exit 1
  fi
fi

echo "=========================================="
if [ "${MODE}" = "export" ]; then
  echo "TestFlight IPA 导出成功: ${OUTPUT_DIR}"
else
  echo "已成功归档并上传至 App Store Connect / TestFlight！"
  echo "请在 App Store Connect -> TestFlight -> 内部测试 中添加测试员邮箱。"
fi
echo "=========================================="
