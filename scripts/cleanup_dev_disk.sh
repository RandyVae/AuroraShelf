#!/usr/bin/env bash
set -euo pipefail

# Netgrey 本地研发磁盘与模拟器最小化瘦身脚本
# 默认执行：
#   1. 关闭所有正在运行的 iOS Simulator 与 Android Emulator（平时 0 常驻）
#   2. 删除白名单之外的冗余 iOS 模拟器（如验收临时创建的 iPad 或多余 iPhone）
#   3. 当保留的 iOS 模拟器数据膨胀 > 2GB 或传入 --erase-sim 时自动 erase 瘦身
#   4. 清理 Android AVD 本地快照与过大的 userdata qcow2（> 3.5GB 或 --erase-sim）
#   5. 清理项目 build.nosync / ios/build.nosync 中非白名单历史 DerivedData 与临时 xcarchive
#   6. 清理未跟踪测试录屏、临时截图与 Simulator 日志，并检查本机磁盘健康水位

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

KEEP_IOS_SIM="${IOS_KEEP_SIMULATOR:-iPhone 18 Pro}"
KEEP_AVD_NAME="${ANDROID_KEEP_AVD:-netgrey_phone}"
IOS_SIM_MAX_MB="${IOS_SIM_MAX_MB:-2048}"
AVD_DATA_MAX_MB="${AVD_DATA_MAX_MB:-3584}"
MIN_FREE_GB="${MIN_FREE_GB:-25}"

DRY_RUN=0
FORCE_ERASE=0
PRUNE_XCODE=0

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

选项:
  --dry-run       仅检查并输出将要执行的清理与当前磁盘状态，不删除任何文件
  --erase-sim     强制重置保留的 iOS 模拟器与 Android AVD 数据分区
  --prune-xcode   同步清空 ~/Library/Developer/Xcode/DerivedData 与 ios/build.nosync/DerivedData
  -h, --help      显示帮助
EOF
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --erase-sim) FORCE_ERASE=1 ;;
    --prune-xcode) PRUNE_XCODE=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $arg" >&2; usage; exit 1 ;;
  esac
done

run_action() {
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "  [DRY-RUN] $*"
  else
    "$@"
  fi
}

echo "=========================================="
echo "Netgrey 本地模拟器与磁盘最小化巡检"
echo "项目根目录 : ${PROJECT_ROOT}"
echo "保留 iOS   : ${KEEP_IOS_SIM}"
echo "保留 AVD   : ${KEEP_AVD_NAME}"
echo "模式       : $([ "$DRY_RUN" -eq 1 ] && echo '只读预演 (--dry-run)' || echo '执行清理')"
echo "=========================================="

echo "[1/5] 关闭全部运行中的 iOS 模拟器与 Android 模拟器..."
if command -v xcrun >/dev/null 2>&1; then
  BOOTED_IOS="$(xcrun simctl list devices booted 2>/dev/null | grep '(Booted)' || true)"
  if [ -n "$BOOTED_IOS" ]; then
    echo "  发现运行中 iOS 模拟器:"
    echo "$BOOTED_IOS" | sed 's/^/    /'
    run_action xcrun simctl shutdown all
  else
    echo "  iOS 模拟器当前 0 台运行。"
  fi
fi

if command -v adb >/dev/null 2>&1; then
  RUNNING_EMUS="$(adb devices 2>/dev/null | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1}' || true)"
  if [ -n "$RUNNING_EMUS" ]; then
    for emu in $RUNNING_EMUS; do
      echo "  发现运行中 Android 模拟器: ${emu}"
      run_action adb -s "$emu" emu kill
    done
    [ "$DRY_RUN" -eq 0 ] && sleep 2
  else
    echo "  Android 模拟器当前 0 台运行。"
  fi
fi

echo "[2/5] 检查并收敛 iOS 模拟器数量与沙盒体积..."
if command -v xcrun >/dev/null 2>&1; then
  python3 - "$KEEP_IOS_SIM" "$IOS_SIM_MAX_MB" "$DRY_RUN" "$FORCE_ERASE" <<'PY'
import json
import os
import subprocess
import sys
from pathlib import Path

keep_name = sys.argv[1]
max_mb = int(sys.argv[2])
dry_run = sys.argv[3] == "1"
force_erase = sys.argv[4] == "1"

raw = subprocess.check_output(["xcrun", "simctl", "list", "devices", "-j"], text=True)
data = json.loads(raw)
devices_root = Path.home() / "Library/Developer/CoreSimulator/Devices"

kept_once = False
for runtime, devs in data.get("devices", {}).items():
    for d in devs:
        name = d.get("name", "")
        udid = d.get("udid", "")
        avail = d.get("isAvailable", False)
        if not avail or name != keep_name or kept_once:
            print(f"  -> 删除多余/不可用 iOS 模拟器: {name} ({udid})")
            if not dry_run:
                subprocess.run(["xcrun", "simctl", "delete", udid], check=False)
            continue

        kept_once = True
        dev_dir = devices_root / udid
        size_mb = 0
        if dev_dir.exists():
            out = subprocess.check_output(["du", "-sm", str(dev_dir)], text=True).split()[0]
            size_mb = int(out)
        print(f"  -> 保留主 iOS 模拟器: {name} ({udid}), 当前体积 {size_mb} MB")
        if force_erase or size_mb > max_mb:
            print(f"     触发瘦身重置 (阈值 {max_mb} MB): xcrun simctl erase {udid}")
            if not dry_run:
                subprocess.run(["xcrun", "simctl", "erase", udid], check=False)

if not dry_run:
    subprocess.run(["xcrun", "simctl", "delete", "unavailable"], check=False)
PY

  if [ -d "$HOME/Library/Logs/CoreSimulator" ]; then
    echo "  -> 清理 ~/Library/Logs/CoreSimulator"
    if [ "$DRY_RUN" -eq 0 ]; then
      rm -rf "$HOME/Library/Logs/CoreSimulator"/* 2>/dev/null || true
    fi
  fi
fi

echo "[3/5] 检查并收敛 Android AVD 快照与数据盘..."
AVD_DIR="$HOME/.android/avd/${KEEP_AVD_NAME}.avd"
if [ -d "$AVD_DIR" ]; then
  if [ -d "$AVD_DIR/snapshots" ]; then
    echo "  -> 删除 AVD 本地快照: ${AVD_DIR}/snapshots"
    run_action rm -rf "$AVD_DIR/snapshots"
  fi
  if [ -f "$AVD_DIR/userdata-qemu.img.qcow2" ]; then
    QCOW_MB="$(du -sm "$AVD_DIR/userdata-qemu.img.qcow2" | awk '{print $1}')"
    echo "  -> AVD userdata-qemu.img.qcow2 当前体积: ${QCOW_MB} MB"
    if [ "$FORCE_ERASE" -eq 1 ] || [ "$QCOW_MB" -gt "$AVD_DATA_MAX_MB" ]; then
      echo "     超过阈值 (${AVD_DATA_MAX_MB} MB) 或强制重置，清理膨胀镜像以便按 4G 上限重建"
      run_action rm -f "$AVD_DIR"/*.qcow2 "$AVD_DIR/userdata-qemu.img" "$AVD_DIR/hardware-qemu.ini" "$AVD_DIR"/*.lock
    fi
  fi
else
  echo "  未发现 AVD 目录: ${AVD_DIR}"
fi

echo "[4/5] 清理项目非白名单历史构建目录与临时产物..."
if [ -d "${PROJECT_ROOT}/build.nosync" ]; then
  while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    echo "  -> 删除非白名单 build.nosync 目录/文件: $(basename "$entry")"
    run_action rm -rf "$entry"
  done < <(find "${PROJECT_ROOT}/build.nosync" -mindepth 1 -maxdepth 1 ! -name 'app')
fi

if [ -d "${PROJECT_ROOT}/ios/build.nosync" ]; then
  while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    base="$(basename "$entry")"
    if [ "$base" = "DerivedData" ] && [ "$PRUNE_XCODE" -eq 0 ]; then
      continue
    fi
    if [ "$base" = "outputs" ] && [ "$PRUNE_XCODE" -eq 0 ]; then
      continue
    fi
    echo "  -> 删除非白名单 ios/build.nosync 产物: ${base}"
    run_action rm -rf "$entry"
  done < <(find "${PROJECT_ROOT}/ios/build.nosync" -mindepth 1 -maxdepth 1)
  if [ "$DRY_RUN" -eq 0 ]; then
    mkdir -p "${PROJECT_ROOT}/ios/build.nosync/DerivedData"
  fi
fi

if [ "$PRUNE_XCODE" -eq 1 ] && [ -d "$HOME/Library/Developer/Xcode/DerivedData" ]; then
  echo "  -> 清空全局 ~/Library/Developer/Xcode/DerivedData"
  if [ "$DRY_RUN" -eq 0 ]; then
    rm -rf "$HOME/Library/Developer/Xcode/DerivedData"/*
  fi
fi

if [ -d "${PROJECT_ROOT}/captures" ]; then
  CAPTURED_CNT="$(find "${PROJECT_ROOT}/captures" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')"
  if [ "$CAPTURED_CNT" -gt 0 ]; then
    echo "  -> 清理 captures/ 下 ${CAPTURED_CNT} 项未跟踪测试录屏/截图"
    if [ "$DRY_RUN" -eq 0 ]; then
      rm -rf "${PROJECT_ROOT}/captures"/*
    fi
  fi
fi

if [ "$DRY_RUN" -eq 0 ]; then
  rm -f /tmp/ios_release_*.png /tmp/android_release_*.png /tmp/ipad_*.png /tmp/iphone_*.png 2>/dev/null || true
fi

echo "[5/5] 磁盘与运行态核验..."
FREE_KB="$(df -Pk / | awk 'NR==2 {print $4}')"
FREE_GB="$((FREE_KB / 1024 / 1024))"
echo "  根分区状态:"
df -h / | sed 's/^/    /'
echo "  Swap 占用:"
sysctl vm.swapusage | sed 's/^/    /'
echo "  当前保留 iOS 模拟器:"
xcrun simctl list devices 2>/dev/null | grep -E 'iPhone|iPad' | sed 's/^/    /' || true
echo "  当前目录体积:"
du -shx "${PROJECT_ROOT}/build.nosync" "${PROJECT_ROOT}/ios/build.nosync" "$HOME/Library/Developer/CoreSimulator" "$HOME/.android/avd" 2>/dev/null | sed 's/^/    /' || true

if [ "$FREE_GB" -lt "$MIN_FREE_GB" ]; then
  echo "警告: 当前可用磁盘空间 ${FREE_GB} GB 低于安全红线 ${MIN_FREE_GB} GB，请检查大体积缓存或加 --prune-xcode --erase-sim！" >&2
else
  echo "磁盘健康检查通过: 当前可用约 ${FREE_GB} GB (>= ${MIN_FREE_GB} GB)。"
fi
