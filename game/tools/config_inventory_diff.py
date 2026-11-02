#!/usr/bin/env python3
"""Compare packaged config names with the retained live config manifest."""

import argparse
import json
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live-map", default="data/live_config_map.json")
    parser.add_argument("--packaged-dir", default="data/configs")
    parser.add_argument("--output", default="data/config_inventory_diff.json")
    return parser.parse_args()


def main():
    args = parse_args()
    live_map = json.loads(Path(args.live_map).read_text(encoding="utf-8"))["config_data"]
    packaged_dir = Path(args.packaged_dir)
    packaged = {path.stem for path in packaged_dir.glob("*.json")}
    live = set(live_map)

    result = {
        "counts": {
            "live": len(live),
            "packaged_json": len(packaged),
            "intersection": len(live & packaged),
            "live_only": len(live - packaged),
            "packaged_only": len(packaged - live),
            "net_live_minus_packaged": len(live) - len(packaged),
        },
        "live_only": sorted(live - packaged),
        "packaged_only": sorted(packaged - live),
        "intersection": sorted(live & packaged),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for key, value in result["counts"].items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()
