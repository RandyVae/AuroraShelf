#!/usr/bin/env python3
"""Compile-time generator for iOS ComicProtocolSecrets without bundling SourceSecrets.plist."""
import json
import os
import plistlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLIST_PATH = ROOT / "ios/AuroraShelf/Resources/SourceSecrets.plist"
PROPS_PATH = ROOT / "source-secrets.properties"
OUTPUT_PATH = ROOT / "ios/AuroraShelf/Data/Comic/GeneratedSourceSecrets.swift"

KEYS = [
    "JM_TOKEN_SECRET",
    "JM_TOKEN_SECRET_2",
    "JM_DATA_SECRET",
    "PICACG_API_KEY",
    "PICACG_SIGNATURE_SECRET",
]


def load_secrets() -> dict[str, str]:
    values: dict[str, str] = {}
    if PLIST_PATH.exists():
        try:
            with PLIST_PATH.open("rb") as f:
                data = plistlib.load(f)
            if isinstance(data, dict):
                for k in KEYS:
                    v = data.get(k)
                    if isinstance(v, str) and v:
                        values[k] = v
        except Exception:
            pass

    if PROPS_PATH.exists():
        try:
            for line in PROPS_PATH.read_text(encoding="utf-8").splitlines():
                stripped = line.strip()
                if not stripped or stripped.startswith("#") or "=" not in stripped:
                    continue
                k, v = stripped.split("=", 1)
                k = k.strip()
                v = v.strip()
                if k in KEYS and v and k not in values:
                    values[k] = v
        except Exception:
            pass

    for k in KEYS:
        env_val = os.environ.get(k, "").strip()
        if env_val and k not in values:
            values[k] = env_val

    return values


def render_swift(values: dict[str, str]) -> str:
    if not values:
        entries = "[:]"
    else:
        lines = []
        for k in KEYS:
            if k in values:
                lines.append(f"        {json.dumps(k)}: {json.dumps(values[k])},")
        entries = "[\n" + "\n".join(lines) + "\n    ]"
    return (
        "// Auto-generated at build time by scripts/generate_ios_source_secrets.py. Do not commit.\n"
        "// swiftlint:disable all\n"
        "enum GeneratedSourceSecrets {\n"
        f"    static let values: [String: String] = {entries}\n"
        "}\n"
    )


def main() -> None:
    values = load_secrets()
    content = render_swift(values)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT_PATH.exists() and OUTPUT_PATH.read_text(encoding="utf-8") == content:
        print(f"[generate_ios_source_secrets] up-to-date ({len(values)} keys)")
        return
    OUTPUT_PATH.write_text(content, encoding="utf-8")
    print(f"[generate_ios_source_secrets] wrote {len(values)} keys")


if __name__ == "__main__":
    main()
