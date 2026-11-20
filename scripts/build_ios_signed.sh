#!/usr/bin/env bash
set -euo pipefail

# Netgrey iOS 自动签名打包脚本
# 支持通过已配置的 Apple 开发者账号自动生成签名、归档与导出 IPA

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${PROJECT_ROOT}/ios"
BUILD_DIR="${IOS_DIR}/build.nosync"
OUTPUT_DIR="${BUILD_DIR}/outputs"
ARCHIVE_PATH="${BUILD_DIR}/AuroraShelf.xcarchive"
EXPORT_OPTIONS="${BUILD_DIR}/ExportOptions.plist"

# 默认团队 ID（来自本地 Xcode 账号配置）
TEAM_ID="${DEVELOPMENT_TEAM:-U4MXHLA3GF}"
CONFIGURATION="${1:-Release}"
if [ "${CONFIGURATION}" = "All" ]; then
  CONFIGURATION="Release"
fi

echo "=========================================="
echo "Netgrey iOS 自动化签名打包"
echo "项目路径: ${IOS_DIR}"
echo "团队 ID : ${TEAM_ID}"
echo "构建配置: ${CONFIGURATION}"
echo "输出目录: ${OUTPUT_DIR}"
echo "=========================================="

mkdir -p "${BUILD_DIR}"
mkdir -p "${OUTPUT_DIR}"

# 导出前清理旧 IPA 产物，防止导出失败时误上传陈旧缓存包
rm -f "${OUTPUT_DIR}/AuroraShelf.ipa" "${OUTPUT_DIR}/Netgrey.ipa"

# 生成动态 ExportOptions.plist
cat <<EOF > "${EXPORT_OPTIONS}"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>development</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>teamID</key>
	<string>${TEAM_ID}</string>
	<key>compileBitcode</key>
	<false/>
	<key>thinning</key>
	<string>&lt;none&gt;</string>
</dict>
</plist>
EOF

echo "[1/2] 正在执行 Xcode Archive 归档与自动签名..."
DERIVED_DATA_PATH="${BUILD_DIR}/DerivedData"
mkdir -p "${DERIVED_DATA_PATH}"
rm -rf "${ARCHIVE_PATH}"
xcodebuild archive \
  -project "${IOS_DIR}/AuroraShelf.xcodeproj" \
  -scheme AuroraShelf \
  -configuration "${CONFIGURATION}" \
  -destination "generic/platform=iOS" \
  -derivedDataPath "${DERIVED_DATA_PATH}" \
  -archivePath "${ARCHIVE_PATH}" \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM="${TEAM_ID}" \
  CODE_SIGN_STYLE=Automatic \
  CODE_SIGNING_ALLOWED=YES \
  CODE_SIGNING_REQUIRED=YES \
  CODE_SIGN_IDENTITY="Apple Development"

echo "[2/2] 正在导出已签名的 IPA 安装包..."
xcodebuild -exportArchive \
  -archivePath "${ARCHIVE_PATH}" \
  -exportPath "${OUTPUT_DIR}" \
  -exportOptionsPlist "${EXPORT_OPTIONS}" \
  -allowProvisioningUpdates

if [ ! -f "${OUTPUT_DIR}/AuroraShelf.ipa" ]; then
  echo "错误: IPA 导出失败，未在 ${OUTPUT_DIR} 找到 AuroraShelf.ipa！" >&2
  exit 1
fi

# 原子重命名避免在本地保留双份 IPA（省 ~140 MB）
mv -f "${OUTPUT_DIR}/AuroraShelf.ipa" "${OUTPUT_DIR}/Netgrey.ipa"

ARCHIVE_APP="${ARCHIVE_PATH}/Products/Applications/AuroraShelf.app"
echo "=========================================="
echo "打包与签名成功！"
echo "产物路径: ${OUTPUT_DIR}/Netgrey.ipa"
if [ -d "${ARCHIVE_APP}" ]; then
  echo "归档包签名信息:"
  codesign -dv --verbose=4 "${ARCHIVE_APP}" 2>&1 || true
fi
# 导出完成后即刻回收临时 xcarchive，避免占用本机空间（省 ~210 MB）
rm -rf "${ARCHIVE_PATH}"
echo "=========================================="
